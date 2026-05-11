package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.network.interceptors.GLOBAL_USER_AGENT
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import my.noveldokusha.network.ScraperNetworkClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Translation via the unofficial Google Translate endpoint (no API key required).
 *
 * This manager does NOT send text to an LLM, so token-overhead optimisation and
 * paragraph sanitization are not applicable here. Chunking is still applied to
 * stay within Google's undocumented 13 000-character request limit.
 */
class TranslationManagerGoogleFree(
    private val coroutineScope: AppCoroutineScope,
    networkClient: ScraperNetworkClient,
) : TranslationManager {

    private val client = networkClient.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

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
        translate = { input ->
            translateRaw(input, source, target)
                ?: throw IllegalStateException("Google Translate: Failed. Check your internet connection.")
        },
    )

    // ─── Language detection ───────────────────────────────────────────────────

    /**
     * Detects the language of the given text by sending a short sample to Google Translate
     * and reading the detected-language field from the response.
     *
     * @param text Arbitrary text to inspect (only the first 100 chars are sent).
     * @return ISO 639-1 language code (e.g. "zh", "ja"), or null on failure.
     */
    override suspend fun detectLanguage(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        runCatching {
            val url = BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", "en")
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text.take(100))
                .build()
            client.newCall(
                Request.Builder().url(url).build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                json.parseToJsonElement(resp.body.string()).jsonArray
                    .getOrNull(2)?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.length in 2..6 }
                    ?.substringBefore("-")
            }
        }.getOrNull()
    }

    // ─── Translation ──────────────────────────────────────────────────────────

    /**
     * Translates a single text block. Returns null on failure; callers decide whether to
     * throw or fall back to the original text.
     *
     * Uses POST for texts > 500 chars (query-string size limit) and GET otherwise.
     * Delegates to [translateLongText] for texts exceeding 13 000 chars.
     */
    private suspend fun translateRaw(
        text: String,
        sl: String,
        tl: String,
        retries: Int = 2,
    ): String? = withContext(Dispatchers.IO) {
        if (text.length > 13_000) return@withContext translateLongText(text, sl, tl)

        var lastEx: Exception? = null
        repeat(retries) { attempt ->
            runCatching {
                val request = if (text.length > 500) {
                    // Long text: POST to avoid URI-length limits.
                    Request.Builder()
                        .url(BASE_URL)
                        .post(
                            FormBody.Builder()
                                .add("client", "gtx").add("sl", sl)
                                .add("tl", tl).add("dt", "t").add("q", text)
                                .build()
                        )
                        .build()
                } else {
                    // Short text: GET request.
                    val url = BASE_URL.toHttpUrl().newBuilder()
                        .addQueryParameter("client", "gtx").addQueryParameter("sl", sl)
                        .addQueryParameter("tl", tl).addQueryParameter("dt", "t")
                        .addQueryParameter("q", text)
                        .build()
                    Request.Builder().url(url).build()
                }

                val resp = client.newCall(request).execute()
                val body = resp.body.string()
                if (resp.isSuccessful && body.isNotEmpty()) {
                    val result = buildString {
                        json.parseToJsonElement(body).jsonArray
                            .getOrNull(0)?.jsonArray
                            ?.forEach { item ->
                                append(item.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: "")
                            }
                    }.trim()
                    if (result.isNotEmpty()) return@withContext result
                }
            }.onFailure { lastEx = it as? Exception }

            if (attempt < retries - 1) kotlinx.coroutines.delay(200L * (attempt + 1))
        }
        Log.w(TAG, "translateRaw: failed — ${lastEx?.message?.take(60)}")
        null
    }

    /**
     * Translates a batch of texts efficiently by grouping them into ≤ 8 000-char chunks
     * and dispatching each chunk concurrently. Falls back to per-item translation on chunk failure.
     *
     * Each chunk is formatted as `[index]\ntext` so translated segments can be matched back
     * to their originals via a regex after translation.
     */
    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        // Split into 8 000-char chunks to stay within Google's soft limit.
        val chunks = mutableListOf<List<Pair<Int, String>>>()
        var currentChunk = mutableListOf<Pair<Int, String>>()
        var currentLen = 0
        texts.forEachIndexed { i, text ->
            val cost = text.length + 10 // +10 for index prefix overhead
            if (currentLen + cost > 8_000 && currentChunk.isNotEmpty()) {
                chunks += currentChunk
                currentChunk = mutableListOf()
                currentLen = 0
            }
            currentChunk += i to text
            currentLen += cost
        }
        if (currentChunk.isNotEmpty()) chunks += currentChunk

        val translations = mutableMapOf<String, String>()
        var failedCount = 0

        coroutineScope {
            chunks.map { chunk ->
                async {
                    val wrapped = chunk.joinToString("\n\n") { (idx, t) -> "[$idx]\n$t" }
                    val body = translateRaw(wrapped, sourceLanguage, targetLanguage)

                    if (body == null) {
                        // Chunk failed — fall back to translating each item individually.
                        chunk.forEach { (_, original) ->
                            translateRaw(original, sourceLanguage, targetLanguage)
                                ?.let { translations[original] = it }
                                ?: run { failedCount++ }
                        }
                        return@async
                    }

                    // Parse translated segments back by index.
                    chunk.forEach { (idx, original) ->
                        val regex = Regex(
                            pattern = """^\[\s*$idx\s*\.?\]\s*\n?(.*?)(?=\n*\[\s*\d+\s*\.?\]|\z)""",
                            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
                        )
                        val result = regex.find(body)?.groupValues?.get(1)?.trim()
                        if (!result.isNullOrEmpty()) {
                            translations[original] = result
                        } else {
                            // Segment not found in batch response — translate individually.
                            translateRaw(original, sourceLanguage, targetLanguage)
                                ?.let { translations[original] = it }
                                ?: run { failedCount++ }
                        }
                    }
                }
            }.awaitAll()
        }

        if (translations.isEmpty() && texts.isNotEmpty())
            throw IllegalStateException("Google Translate: Failed. Check your internet connection.")

        Log.d(TAG, "translateBatch: ${texts.size} total, ${translations.size} ok, $failedCount failed")
        translations
    }

    /**
     * Splits an oversized text (> 13 000 chars) at the midpoint sentence boundary
     * and translates both halves concurrently. Returns null if splitting is not possible.
     */
    private suspend fun translateLongText(text: String, sl: String, tl: String): String? =
        withContext(Dispatchers.IO) {
            val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotEmpty() }
            if (sentences.size <= 1) return@withContext null
            val mid = sentences.size / 2
            coroutineScope {
                val firstHalf  = async { translateRaw(sentences.take(mid).joinToString(" "), sl, tl) }
                val secondHalf = async { translateRaw(sentences.drop(mid).joinToString(" "), sl, tl) }
                val r1 = firstHalf.await()
                val r2 = secondHalf.await()
                if (r1 != null && r2 != null) "$r1 $r2" else null
            }
        }

    override fun downloadModel(language: String) = Unit
    override fun removeModel(language: String) = Unit

    companion object {
        private const val TAG = "TranslationGoogleFree"
        private const val BASE_URL = "https://translate.googleapis.com/translate_a/single"
    }
}
