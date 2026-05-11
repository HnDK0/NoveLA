package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import my.noveldokusha.network.ScraperNetworkClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Translates text via any OpenAI-compatible API (OpenAI, OpenRouter, Mistral, DeepSeek, Ollama…).
 *
 * Key rotation: iterates round-robin across all configured API keys, skipping on 401/429
 * and throwing immediately on 5xx or unrecoverable network errors.
 *
 * Batch translation joins paragraphs with [BATCH_SEPARATOR] (≈1 token) so a full chapter
 * can be translated in a single API call. Each paragraph is sanitized via [sanitizeParagraph]
 * before dispatch to remove scraped artefacts (BOM, zero-width chars, invalid controls).
 */
class TranslationManagerOpenAI(
    private val coroutineScope: AppCoroutineScope,
    private val appPreferences: AppPreferences,
    networkClient: ScraperNetworkClient,
) : TranslationManager {

    private val client = networkClient.client.newBuilder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Round-robin pointer across all configured API keys. */
    private val keyIndex = AtomicInteger(0)

    private val apiKeys: List<String>
        get() = appPreferences.TRANSLATION_OPENAI_API_KEYS.value
            .splitToSequence('\n', ';', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    private val baseUrl: String
        get() = appPreferences.TRANSLATION_OPENAI_BASE_URL.value
            .trimEnd('/').ifBlank { "https://api.openai.com" }

    private val model: String
        get() = appPreferences.TRANSLATION_OPENAI_MODEL.value.ifBlank { "gpt-4o-mini" }

    private val systemPromptTemplate: String
        get() = appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value
            .ifBlank { DEFAULT_TRANSLATION_PROMPT }

    private val useEnglishLocale: Boolean
        get() = appPreferences.TRANSLATION_PROMPT_USE_ENGLISH_LOCALE.value

    override val available = true
    override val isUsingOnlineTranslation = true

    override val models = mutableStateListOf<TranslationModelState>().apply {
        addAll(TranslationManagerGemini.SUPPORTED_LANGUAGES.map {
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
     * The input is sanitized before dispatch; if the result is blank the
     * original text is returned unchanged (safe fallback).
     */
    private suspend fun translateSingle(text: String, source: String, target: String): String =
        withContext(Dispatchers.IO) {
            val sanitized = sanitizeParagraph(text)
            if (sanitized.isBlank()) return@withContext text

            val result = sendWithKeyRotation(
                systemPrompt = buildPrompt(source, target, isBatch = false),
                userMessage = sanitized,
            )
            result.trim().ifEmpty { text }
        }

    // ─── Batch translation ────────────────────────────────────────────────────

    /**
     * Translates a batch of paragraphs in a single LLM call.
     *
     * Each paragraph is sanitized via [sanitizeParagraph] before being joined with
     * [BATCH_SEPARATOR]. The response is parsed back using [parseBatchTranslationResponse]
     * with the original (unsanitized) texts as map keys so the caller can perform
     * straightforward key lookups.
     */
    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        // Sanitize paragraphs to remove artefacts; keep originals for map keys.
        val sanitizedTexts = texts.map { sanitizeParagraph(it) }
        val userMessage = sanitizedTexts.joinToString("\n$BATCH_SEPARATOR\n")

        val response = sendWithKeyRotation(
            systemPrompt = buildPrompt(sourceLanguage, targetLanguage, isBatch = true),
            userMessage = userMessage,
        )

        // Map response back to original (unsanitized) texts by position.
        parseBatchTranslationResponse(response, texts)
    }

    // ─── HTTP + key rotation ──────────────────────────────────────────────────

    /**
     * Sends a chat completion request, rotating through all available API keys.
     *
     * - 401 / 429 → skip to next key (auth failure or rate limit).
     * - 5xx        → throw immediately (server-side error, retrying won't help right away).
     * - Other non-2xx → throw with error body excerpt for debuggability.
     */
    private suspend fun sendWithKeyRotation(
        systemPrompt: String,
        userMessage: String,
    ): String = withContext(Dispatchers.IO) {
        val keys = apiKeys
        if (keys.isEmpty())
            throw IllegalStateException("OpenAI: No API keys configured. Add your key in Settings → Translation.")

        val startIndex = keyIndex.getAndIncrement() % keys.size
        var lastEx: Exception? = null

        for (attempt in keys.indices) {
            val key = keys[(startIndex + attempt) % keys.size]
            val keyLabel = "key #${(startIndex + attempt) % keys.size + 1}"
            try {
                val response = sendRequest(systemPrompt, userMessage, key)
                when {
                    response.code == 401 -> {
                        response.body.close()
                        lastEx = IllegalStateException("OpenAI: Invalid key ($keyLabel).")
                        continue
                    }
                    response.code == 429 -> {
                        response.body.close()
                        lastEx = IllegalStateException("OpenAI: Rate limit ($keyLabel).")
                        continue
                    }
                    response.code in 500..599 -> {
                        response.body.close()
                        throw IOException("OpenAI: Server error (${response.code}).")
                    }
                    !response.isSuccessful -> {
                        val excerpt = response.body.string().take(200)
                        throw IllegalStateException("OpenAI: Error (${response.code}): $excerpt")
                    }
                    else -> {
                        // Advance key pointer so the next call starts from the working key.
                        keyIndex.set((startIndex + attempt + 1) % keys.size)
                        return@withContext parseResponse(response.body.string())
                    }
                }
            } catch (e: IOException) {
                throw e // Network errors propagate immediately; no point retrying other keys.
            }
        }
        throw lastEx ?: IllegalStateException("OpenAI: All keys failed.")
    }

    private fun sendRequest(
        systemPrompt: String,
        userMessage: String,
        apiKey: String,
    ): okhttp3.Response {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
            })
            put("temperature", 0.3)
            put("top_p", 1.0)
            put("stream", false)
        }.toString().toRequestBody("application/json".toMediaType())

        return client.newCall(
            Request.Builder()
                .url("$baseUrl/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()
        ).execute()
    }

    // ─── Response parsing ─────────────────────────────────────────────────────

    private fun parseResponse(body: String): String =
        try {
            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            throw IllegalStateException("OpenAI: Failed to parse response — ${e.message}")
        }

    // ─── Prompt builder ───────────────────────────────────────────────────────

    /** Resolves the active system prompt template and appends the correct format suffix. */
    private fun buildPrompt(source: String, target: String, isBatch: Boolean) =
        buildSystemPrompt(systemPromptTemplate, source, target, useEnglishLocale, isBatch)

    override fun downloadModel(language: String) = Unit
    override fun removeModel(language: String) = Unit
    override suspend fun detectLanguage(text: String): String? = null

    companion object {
        @Suppress("unused")
        private const val TAG = "TranslationOpenAI"
    }
}
