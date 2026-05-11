package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Translates text via the Google Gemini API.
 *
 * Batch translation joins paragraphs with [BATCH_SEPARATOR] (≈1 token) to minimise
 * context overhead. Each paragraph is sanitized via [sanitizeParagraph] before
 * being sent, so the LLM never sees scraped artefacts (BOM, zero-width chars, etc.).
 *
 * Key rotation: [keyIndex] advances round-robin across all configured API keys.
 * Safety settings are set to BLOCK_NONE so mature web-novel content is not filtered.
 */
class TranslationManagerGemini(
    private val coroutineScope: AppCoroutineScope,
    private val appPreferences: AppPreferences,
) : TranslationManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Round-robin pointer across all configured API keys. */
    private val keyIndex = AtomicInteger(0)

    private val apiKeys: List<String>
        get() = appPreferences.TRANSLATION_GEMINI_API_KEY.value
            .splitToSequence('\n', ';', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    private fun apiEndpoint(key: String): String {
        val model = appPreferences.TRANSLATION_GEMINI_MODEL.value.ifBlank { "gemini-2.5-flash-lite" }
        return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
    }

    override val available = true
    override val isUsingOnlineTranslation: Boolean get() = apiKeys.isNotEmpty()

    override val models = mutableStateListOf<TranslationModelState>().apply {
        addAll(SUPPORTED_LANGUAGES.map {
            TranslationModelState(it, available = true, downloading = false, downloadingFailed = false)
        })
    }

    override suspend fun hasModelDownloaded(language: String) =
        models.firstOrNull { it.language == language }

    override fun getTranslator(source: String, target: String) = TranslatorState(
        source = source,
        target = target,
        translate = { input -> translateSingle(input, source, target) },
    )

    // ─── Single translation ───────────────────────────────────────────────────

    /**
     * Translates a single text block.
     * The input is sanitized before dispatch to strip scraped artefacts and
     * protect the batch separator from accidental collisions.
     */
    private suspend fun translateSingle(
        text: String,
        source: String,
        target: String,
        retries: Int = 3,
    ): String = withContext(Dispatchers.IO) {
        val keys = apiKeys
        if (keys.isEmpty()) throw IllegalStateException("Gemini: No API keys configured.")

        // Sanitize before sending — removes BOM, zero-width chars, invalid controls.
        val sanitized = sanitizeParagraph(text)
        if (sanitized.isBlank()) return@withContext text

        val systemPrompt = buildPrompt(source, target, isBatch = false)
        val startIndex = keyIndex.getAndIncrement() % keys.size
        var lastEx: Exception? = null

        repeat(retries * keys.size) { attempt ->
            val key = keys[(startIndex + attempt) % keys.size]
            val keyLabel = "key #${(startIndex + attempt) % keys.size + 1}"
            try {
                val response = sendRequest(systemPrompt, sanitized, key)
                when (response.code) {
                    200 -> {
                        val result = parseResponse(response.body.string())
                        if (result == BLOCKED_MARKER)
                            throw IOException("Gemini: Content blocked. Try a different prompt/model.")
                        if (result.isNotBlank()) return@withContext result
                        lastEx = IOException("Gemini: Empty response")
                    }
                    429 -> {
                        lastEx = IOException("Gemini: Rate limit ($keyLabel)")
                    }
                    400 -> {
                        lastEx = IOException("Gemini: Bad request (400) on $keyLabel")
                        delay(500L * (attempt / keys.size + 1))
                    }
                    in 500..599 -> {
                        lastEx = IOException("Gemini: Server error (${response.code})")
                        delay(500L * (attempt / keys.size + 1))
                    }
                    else -> throw IOException("Gemini: API error ${response.code}")
                }
            } catch (e: IOException) {
                lastEx = e
                if (attempt == retries * keys.size - 1) throw e
            }
        }
        throw lastEx ?: IOException("Gemini: All attempts failed")
    }

    // ─── Batch translation ────────────────────────────────────────────────────

    /**
     * Translates a batch of paragraphs in a single LLM call.
     *
     * Each paragraph is sanitized via [sanitizeParagraph] before joining with
     * [BATCH_SEPARATOR]. This ensures the delimiter is never accidentally present
     * inside the text and removes scraped artefacts that waste tokens.
     * The result is parsed back via [parseBatchTranslationResponse] using the
     * original (unsanitized) texts as map keys.
     */
    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        val keys = apiKeys
        if (keys.isEmpty()) throw IllegalStateException("Gemini: No API keys configured.")

        // Sanitize each paragraph; use originals as map keys after parsing.
        val sanitizedTexts = texts.map { sanitizeParagraph(it) }
        val userText = sanitizedTexts.joinToString("$BATCH_SEPARATOR\n")

        val systemPrompt = buildPrompt(sourceLanguage, targetLanguage, isBatch = true)
        val retries = 3
        val totalAttempts = retries * keys.size
        var lastEx: Exception? = null

        repeat(totalAttempts) { attempt ->
            val key = keys[attempt % keys.size]
            val keyLabel = "key #${attempt % keys.size + 1}"
            val backoffMultiplier = attempt / keys.size + 1
            try {
                val response = sendRequest(systemPrompt, userText, key)
                when (response.code) {
                    429 -> {
                        lastEx = IOException("Gemini: Rate limit ($keyLabel)")
                        delay(500)
                        return@repeat
                    }
                    400 -> {
                        lastEx = IOException("Gemini: Bad request (400) on $keyLabel")
                        delay(1_000L * backoffMultiplier)
                        return@repeat
                    }
                    in 500..599 -> {
                        lastEx = IOException("Gemini: Server error (${response.code})")
                        delay(2_000L * backoffMultiplier)
                        return@repeat
                    }
                    !in 200..299 -> throw IOException("Gemini: API error ${response.code}")
                }

                val translated = parseResponse(response.body.string())
                if (translated == BLOCKED_MARKER) throw IOException("Gemini: Content blocked.")
                if (translated.isEmpty()) {
                    lastEx = IOException("Gemini: Empty response")
                    delay(500L * backoffMultiplier)
                    return@repeat
                }

                // Map translations back to original (unsanitized) texts by index.
                return@withContext parseBatchTranslationResponse(translated, texts)

            } catch (e: Exception) {
                lastEx = e
                if (e is IOException && e.message?.contains("Content blocked") == true) throw e
                if (attempt < totalAttempts - 1) delay(1_000L * backoffMultiplier)
            }
        }
        throw lastEx ?: IOException("Gemini: Batch failed after $retries retries")
    }

    // ─── HTTP layer ───────────────────────────────────────────────────────────

    /**
     * Executes a single Gemini generateContent request and returns a buffered response
     * so the body can be read more than once (OkHttp bodies are one-shot streams).
     */
    private fun sendRequest(
        systemPrompt: String,
        userText: String,
        apiKey: String,
    ): okhttp3.Response {
        val requestBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", userText) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("topP", 0.95)
            })
            /**
            put("thinkingConfig", JSONObject().apply {
                put("thinkingBudget", 0)
            })
            */
            put("safetySettings", buildSafetySettings())
            put("tools", JSONArray()) // explicitly disable Google Search grounding
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiEndpoint(apiKey))
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val rawResponse = client.newCall(request).execute()
        // Buffer body so it can be read by the caller (OkHttp streams are one-shot).
        val bodyString = rawResponse.body.string()
        Log.d(TAG, "sendRequest: HTTP ${rawResponse.code}, preview=${bodyString.take(200)}")
        return rawResponse.newBuilder()
            .body(bodyString.toResponseBody("application/json".toMediaType()))
            .build()
    }

    /** Safety settings that allow all content categories (required for mature web-novel content). */
    private fun buildSafetySettings() = JSONArray().apply {
        listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT",
        ).forEach { category ->
            put(JSONObject().apply {
                put("category", category)
                put("threshold", "BLOCK_NONE")
            })
        }
    }

    // ─── Response parsing ─────────────────────────────────────────────────────

    /**
     * Extracts translated text from a Gemini API response body.
     * Handles both single-object and streaming-array response formats.
     * Returns [BLOCKED_MARKER] when the response is blocked by safety filters.
     */
    private fun parseResponse(body: String): String {
        val trimmed = body.trim()

        // Streaming / multi-chunk response (JSON array).
        if (trimmed.startsWith("[")) {
            runCatching {
                return buildString {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val candidates = arr.getJSONObject(i).getJSONArray("candidates")
                        if (candidates.length() == 0) continue
                        val candidate = candidates.getJSONObject(0)
                        if (candidate.optString("finishReason") in BLOCKED_REASONS) continue
                        append(
                            candidate
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                        )
                    }
                }.trim()
            }
        }

        // Standard single-object response.
        if (trimmed.startsWith("{")) {
            runCatching {
                val json = JSONObject(trimmed)

                // Check for prompt-level block.
                val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason")
                if (!blockReason.isNullOrEmpty() && blockReason != "BLOCK_REASON_UNSPECIFIED") {
                    return BLOCKED_MARKER
                }

                val candidates = json.optJSONArray("candidates") ?: return ""
                if (candidates.length() == 0) return ""

                val candidate = candidates.getJSONObject(0)
                if (candidate.optString("finishReason") in BLOCKED_REASONS) return BLOCKED_MARKER

                return candidate
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.getJSONObject(0)
                    ?.getString("text")
                    ?.trim()
                    ?: ""
            }
        }

        return trimmed
    }

    // ─── Prompt builder ───────────────────────────────────────────────────────

    /** Resolves the active system prompt template and appends the correct format suffix. */
    private fun buildPrompt(source: String, target: String, isBatch: Boolean): String {
        val useEnglish = appPreferences.TRANSLATION_PROMPT_USE_ENGLISH_LOCALE.value
        val template = appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value
            .ifBlank { DEFAULT_TRANSLATION_PROMPT }
        return buildSystemPrompt(template, source, target, useEnglish, isBatch)
    }

    override fun downloadModel(language: String) = Unit
    override fun removeModel(language: String) = Unit

    companion object {
        private const val TAG = "TranslationGemini"

        /** Sentinel returned by [parseResponse] when Gemini blocks the request. */
        private const val BLOCKED_MARKER = "__GEMINI_BLOCKED__"

        private val BLOCKED_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT")

        val SUPPORTED_LANGUAGES = listOf(
            "en", "zh", "ja", "ko", "es", "fr", "de", "it", "pt", "ru",
            "ar", "hi", "th", "vi", "id", "tr", "pl", "nl", "sv", "da",
            "fi", "no", "cs", "el", "he", "ro", "hu", "uk", "bg", "hr",
        )
    }
}
