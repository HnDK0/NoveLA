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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Translation via any OpenAI-compatible API (OpenAI, OpenRouter, Mistral, DeepSeek, Ollama…).
 * Key rotation: round-robin, skip on 401/429, throw on 5xx/network errors.
 */
class TranslationManagerOpenAI(
    private val coroutineScope: AppCoroutineScope,
    private val appPreferences: AppPreferences
) : TranslationManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val keyIndex = AtomicInteger(0)

    private val apiKeys: List<String>
        get() = appPreferences.TRANSLATION_OPENAI_API_KEYS.value
            .splitToSequence('\n', ';', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    private val baseUrl: String get() = appPreferences.TRANSLATION_OPENAI_BASE_URL.value.trimEnd('/').ifBlank { "https://api.openai.com" }
    private val model: String get() = appPreferences.TRANSLATION_OPENAI_MODEL.value.ifBlank { "gpt-4o-mini" }
    private val systemPromptTemplate: String get() = appPreferences.TRANSLATION_ACTIVE_SYSTEM_PROMPT.value.ifBlank { DEFAULT_TRANSLATION_PROMPT }
    private val useEnglishLocale: Boolean get() = appPreferences.TRANSLATION_PROMPT_USE_ENGLISH_LOCALE.value

    override val available = true
    override val isUsingOnlineTranslation = true

    override val models = mutableStateListOf<TranslationModelState>().apply {
        addAll(TranslationManagerGemini.SUPPORTED_LANGUAGES.map {
            TranslationModelState(it, available = true, downloading = false, downloadingFailed = false)
        })
    }

    override suspend fun hasModelDownloaded(language: String) = models.firstOrNull { it.language == language }

    override fun getTranslator(source: String, target: String) = TranslatorState(
        source = source,
        target = target,
        translate = { input -> translateSingle(input, source, target) }
    )

    // ─── Translate ────────────────────────────────────────────────────────────

    private suspend fun translateSingle(text: String, source: String, target: String): String =
        withContext(Dispatchers.IO) {
            val result = sendWithKeyRotation(buildPrompt(source, target), text)
            result.trim().ifEmpty { text }
        }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()
        val userMessage = texts.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n\n")
        val response = sendWithKeyRotation(buildPrompt(sourceLanguage, targetLanguage), userMessage)
        parseNumberedTranslations(response, texts)
    }

    // ─── HTTP + key rotation ──────────────────────────────────────────────────

    private suspend fun sendWithKeyRotation(systemPrompt: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val keys = apiKeys
            if (keys.isEmpty()) throw IllegalStateException("OpenAI: No API keys configured. Add your key in Settings → Translation.")

            val startIndex = keyIndex.getAndIncrement() % keys.size
            var lastEx: Exception? = null

            for (attempt in keys.indices) {
                val key = keys[(startIndex + attempt) % keys.size]
                val keyLabel = "key #${(startIndex + attempt) % keys.size + 1}"
                try {
                    val response = sendRequest(systemPrompt, userMessage, key)
                    when {
                        response.code == 401 -> { response.body.close(); lastEx = IllegalStateException("OpenAI: Invalid key ($keyLabel)."); continue }
                        response.code == 429 -> { response.body.close(); lastEx = IllegalStateException("OpenAI: Rate limit ($keyLabel)."); continue }
                        response.code in 500..599 -> { response.body.close(); throw IOException("OpenAI: Server error (${response.code}).") }
                        !response.isSuccessful -> {
                            val err = response.body.string().take(200)
                            throw IllegalStateException("OpenAI: Error (${response.code}): $err")
                        }
                        else -> {
                            keyIndex.set((startIndex + attempt + 1) % keys.size)
                            return@withContext parseResponse(response.body.string())
                        }
                    }
                } catch (e: IOException) { throw e }
            }
            throw lastEx ?: IllegalStateException("OpenAI: All keys failed.")
        }

    private fun sendRequest(systemPrompt: String, userMessage: String, apiKey: String): okhttp3.Response {
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

    private fun parseResponse(body: String): String {
        return try {
            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            throw IllegalStateException("OpenAI: Failed to parse response — ${e.message}")
        }
    }

    // ─── Numbered output parser ───────────────────────────────────────────────

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

        return buildMap {
            originals.forEachIndexed { i, orig ->
                put(orig, byIndex[i] ?: run {
                    Log.w(TAG, "parseNumberedTranslations: missing index $i, using original")
                    orig
                })
            }
        }
    }

    private fun buildPrompt(source: String, target: String) =
        buildSystemPrompt(systemPromptTemplate, source, target, useEnglishLocale)

    override fun downloadModel(language: String) {}
    override fun removeModel(language: String) {}
    override suspend fun detectLanguage(text: String): String? = null

    companion object {
        private const val TAG = "TranslationOpenAI"
    }
}
