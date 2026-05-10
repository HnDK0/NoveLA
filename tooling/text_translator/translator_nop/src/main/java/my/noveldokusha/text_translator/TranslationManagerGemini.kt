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

class TranslationManagerGemini(
    private val coroutineScope: AppCoroutineScope,
    private val appPreferences: AppPreferences
) : TranslationManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

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
        addAll(SUPPORTED_LANGUAGES.map { TranslationModelState(it, available = true, downloading = false, downloadingFailed = false) })
    }

    override suspend fun hasModelDownloaded(language: String) = models.firstOrNull { it.language == language }

    override fun getTranslator(source: String, target: String) = TranslatorState(
        source = source,
        target = target,
        translate = { input -> translateWithGemini(input, source, target) }
    )

    // ─── Core translate ───────────────────────────────────────────────────────

    private suspend fun translateWithGemini(
        text: String,
        source: String,
        target: String,
        retries: Int = 3
    ): String = withContext(Dispatchers.IO) {
        val keys = apiKeys
        if (keys.isEmpty()) throw IllegalStateException("Gemini: No API keys configured.")

        val systemPrompt = buildPrompt(source, target)
        val startIndex = keyIndex.getAndIncrement() % keys.size
        var lastEx: Exception? = null

        repeat(retries * keys.size) { attempt ->
            val key = keys[(startIndex + attempt) % keys.size]
            val keyLabel = "key #${(startIndex + attempt) % keys.size + 1}"
            try {
                val response = sendRequest(systemPrompt, text, key)
                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: ""
                        val result = parseResponse(body)
                        if (result == BLOCKED_MARKER) throw IOException("Gemini: Content blocked. Try a different prompt/model.")
                        if (result.isNotBlank()) return@withContext result
                        lastEx = IOException("Gemini: Empty response"); return@repeat
                    }
                    429 -> { lastEx = IOException("Gemini: Rate limit ($keyLabel)"); return@repeat }
                    400 -> {
                        lastEx = IOException("Gemini: Bad request (400) on $keyLabel")
                        kotlinx.coroutines.delay(500L * (attempt / keys.size + 1)); return@repeat
                    }
                    in 500..599 -> {
                        lastEx = IOException("Gemini: Server error (${response.code})")
                        kotlinx.coroutines.delay(500L * (attempt / keys.size + 1))
                    }
                    else -> throw IOException("Gemini: API error ${response.code}")
                }
            } catch (e: IOException) { lastEx = e; if (attempt < retries * keys.size - 1) return@repeat else throw e }
        }
        throw lastEx ?: IOException("Gemini: All attempts failed")
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        val keys = apiKeys
        if (keys.isEmpty()) throw IllegalStateException("Gemini: No API keys configured.")

        val systemPrompt = buildPrompt(sourceLanguage, targetLanguage)
        val userText = texts.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n\n")

        var lastEx: Exception? = null
        val retries = 3
        val totalAttempts = retries * keys.size

        repeat(totalAttempts) { attempt ->
            val key = keys[attempt % keys.size]
            val keyLabel = "key #${attempt % keys.size + 1}"
            val attemptWithinKey = attempt / keys.size + 1
            try {
                val response = sendRequest(systemPrompt, userText, key)
                val code = response.code
                when {
                    code == 429 -> { lastEx = IOException("Gemini: Rate limit ($keyLabel)"); kotlinx.coroutines.delay(500); return@repeat }
                    code == 400 -> { lastEx = IOException("Gemini: Bad request (400)"); kotlinx.coroutines.delay(1000L * attemptWithinKey); return@repeat }
                    code in 500..599 -> { lastEx = IOException("Gemini: Server error ($code)"); kotlinx.coroutines.delay(2000L * attemptWithinKey); return@repeat }
                    code !in 200..299 -> throw IOException("Gemini: API error $code")
                }
                val body = response.body.string()
                val translated = parseResponse(body)
                if (translated == BLOCKED_MARKER) throw IOException("Gemini: Content blocked.")
                if (translated.isEmpty()) { lastEx = IOException("Gemini: Empty response"); kotlinx.coroutines.delay(500L * attemptWithinKey); return@repeat }
                return@withContext parseNumberedTranslations(translated, texts)
            } catch (e: Exception) {
                lastEx = e
                if (e is IOException && e.message?.contains("Content blocked") == true) throw e
                if (attempt < totalAttempts - 1) kotlinx.coroutines.delay(1000L * attemptWithinKey)
            }
        }
        throw lastEx ?: IOException("Gemini: Batch failed after $retries retries")
    }

    // ─── HTTP ─────────────────────────────────────────────────────────────────

    private fun sendRequest(systemPrompt: String, userText: String, apiKey: String): okhttp3.Response {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply { put(JSONObject().apply { put("text", systemPrompt) }) })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply { put(JSONObject().apply { put("text", userText) }) })
                })
            })
            put("generationConfig", JSONObject().apply { put("temperature", 0.2); put("topP", 0.95) })
            put("safetySettings", buildSafetySettings())
            put("tools", JSONArray()) // disable googleSearch
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiEndpoint(apiKey))
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""
        Log.d(TAG, "sendRequest: HTTP ${response.code}, preview=${bodyStr.take(200)}")
        return response.newBuilder().body(bodyStr.toResponseBody("application/json".toMediaType())).build()
    }

    private fun buildSafetySettings() = JSONArray().apply {
        listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        ).forEach { cat ->
            put(JSONObject().apply { put("category", cat); put("threshold", "BLOCK_NONE") })
        }
    }

    // ─── Parsing ──────────────────────────────────────────────────────────────

    private fun parseResponse(body: String): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            runCatching {
                return buildString {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val cands = arr.getJSONObject(i).getJSONArray("candidates")
                        if (cands.length() == 0) continue
                        val cand = cands.getJSONObject(0)
                        if (cand.optString("finishReason") in listOf("SAFETY", "PROHIBITED_CONTENT")) continue
                        append(cand.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text"))
                    }
                }.trim()
            }
        }
        if (trimmed.startsWith("{")) {
            runCatching {
                val json = JSONObject(trimmed)
                val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason")
                if (!blockReason.isNullOrEmpty() && blockReason != "BLOCK_REASON_UNSPECIFIED") return BLOCKED_MARKER
                val cands = json.optJSONArray("candidates") ?: return ""
                if (cands.length() == 0) return ""
                val cand = cands.getJSONObject(0)
                val reason = cand.optString("finishReason")
                if (reason in listOf("SAFETY", "PROHIBITED_CONTENT")) return BLOCKED_MARKER
                return cand.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.getJSONObject(0)
                    ?.getString("text")
                    ?.trim()
                    ?: ""
            }
        }
        return trimmed
    }

    private fun parseNumberedTranslations(text: String, originals: List<String>): Map<String, String> {
        val byIndex = mutableMapOf<Int, String>()
        val pattern = Regex("""^\*{0,2}[№#]?\s*(\d+)\s*[.)]\*{0,2}\s*""")
        var currentIdx = -1
        var current = StringBuilder()

        fun flush() {
            if (currentIdx >= 0 && current.isNotBlank()) byIndex[currentIdx] = current.toString().trim()
            current.clear()
        }

        for (line in text.split("\n")) {
            val match = pattern.find(line)
            if (match != null) {
                flush()
                currentIdx = (match.groupValues[1].toIntOrNull() ?: continue) - 1
                val rest = line.substring(match.value.length)
                if (rest.isNotBlank()) current.append(rest)
            } else {
                if (currentIdx == -1) continue
                if (current.isNotEmpty()) current.append("\n")
                current.append(line.trim())
            }
        }
        flush()

        return originals.mapIndexedNotNull { i, orig -> byIndex[i]?.let { orig to it } }.toMap()
    }

    private fun buildPrompt(source: String, target: String): String {
        val useEnglish = appPreferences.TRANSLATION_PROMPT_USE_ENGLISH_LOCALE.value
        val template = appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value.ifBlank { DEFAULT_TRANSLATION_PROMPT }
        return buildSystemPrompt(template, source, target, useEnglish)
    }

    override fun downloadModel(language: String) {}
    override fun removeModel(language: String) {}

    companion object {
        private const val TAG = "TranslationGemini"
        private const val BLOCKED_MARKER = "__GEMINI_BLOCKED__"
        val SUPPORTED_LANGUAGES = listOf(
            "en","zh","ja","ko","es","fr","de","it","pt","ru",
            "ar","hi","th","vi","id","tr","pl","nl","sv","da",
            "fi","no","cs","el","he","ro","hu","uk","bg","hr"
        )
    }
}
