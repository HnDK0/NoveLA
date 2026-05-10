package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import my.noveldokusha.network.interceptors.GLOBAL_USER_AGENT
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

class TranslationManagerGoogleFree(
    private val coroutineScope: AppCoroutineScope
) : TranslationManager {

    private val client = OkHttpClient.Builder()
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

    override suspend fun hasModelDownloaded(language: String) = models.firstOrNull { it.language == language }

    override fun getTranslator(source: String, target: String) = TranslatorState(
        source = source,
        target = target,
        translate = { input ->
            translateRaw(input, source, target)
                ?: throw IllegalStateException("Google Translate: Failed. Check internet connection.")
        }
    )

    // ─── Language detection ───────────────────────────────────────────────────

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
            client.newCall(Request.Builder().url(url).header("User-Agent", GLOBAL_USER_AGENT).build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    json.parseToJsonElement(resp.body.string()).jsonArray
                        .getOrNull(2)?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.length in 2..6 }
                        ?.substringBefore("-")
                }
        }.getOrNull()
    }

    // ─── Translation ──────────────────────────────────────────────────────────

    /** Returns null on failure; callers decide to throw or fallback. */
    private suspend fun translateRaw(
        text: String,
        sl: String,
        tl: String,
        retries: Int = 2
    ): String? = withContext(Dispatchers.IO) {
        if (text.length > 13_000) return@withContext translateLongText(text, sl, tl)

        var lastEx: Exception? = null
        repeat(retries) { attempt ->
            runCatching {
                val request = if (text.length > 500) {
                    Request.Builder()
                        .url(BASE_URL)
                        .post(FormBody.Builder().add("client","gtx").add("sl",sl).add("tl",tl).add("dt","t").add("q",text).build())
                        .addHeader("User-Agent", GLOBAL_USER_AGENT)
                        .build()
                } else {
                    val url = BASE_URL.toHttpUrl().newBuilder()
                        .addQueryParameter("client", "gtx").addQueryParameter("sl", sl)
                        .addQueryParameter("tl", tl).addQueryParameter("dt", "t")
                        .addQueryParameter("q", text).build()
                    Request.Builder().url(url).addHeader("User-Agent", GLOBAL_USER_AGENT).build()
                }
                val resp = client.newCall(request).execute()
                val body = resp.body.string()
                if (resp.isSuccessful && body.isNotEmpty()) {
                    val result = buildString {
                        json.parseToJsonElement(body).jsonArray.getOrNull(0)?.jsonArray?.forEach { item ->
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

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        // Build chunks ≤ 8000 chars
        val chunks = mutableListOf<List<Pair<Int, String>>>()
        var currentChunk = mutableListOf<Pair<Int, String>>()
        var currentLen = 0
        texts.forEachIndexed { i, t ->
            val len = t.length + 10
            if (currentLen + len > 8_000 && currentChunk.isNotEmpty()) {
                chunks += currentChunk; currentChunk = mutableListOf(); currentLen = 0
            }
            currentChunk += i to t; currentLen += len
        }
        if (currentChunk.isNotEmpty()) chunks += currentChunk

        val translations = mutableMapOf<String, String>()
        var failed = 0

        coroutineScope {
            chunks.map { chunk ->
                async {
                    val wrapped = chunk.joinToString("\n\n") { (idx, t) -> "[$idx]\n$t" }
                    val body = translateRaw(wrapped, sourceLanguage, targetLanguage)

                    if (body == null) {
                        var chunkFailed = 0
                        chunk.forEach { (_, orig) ->
                            translateRaw(orig, sourceLanguage, targetLanguage)
                                ?.let { translations[orig] = it } ?: chunkFailed++
                        }
                        failed += chunkFailed; return@async
                    }

                    chunk.forEach { (idx, orig) ->
                        val regex = Regex(
                            """^\[\s*$idx\s*\.?\]\s*\n?(.*?)(?=\n*\[\s*\d+\s*\.?\]|\z)""",
                            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
                        )
                        val result = regex.find(body)?.groupValues?.get(1)?.trim()
                        if (!result.isNullOrEmpty()) {
                            translations[orig] = result
                        } else {
                            translateRaw(orig, sourceLanguage, targetLanguage)
                                ?.let { translations[orig] = it } ?: failed++
                        }
                    }
                }
            }.awaitAll()
        }

        if (translations.isEmpty() && texts.isNotEmpty())
            throw IllegalStateException("Google Translate: Failed. Check internet connection.")

        Log.d(TAG, "translateBatch: ${texts.size} total, ${translations.size} ok, $failed failed")
        translations
    }

    private suspend fun translateLongText(text: String, sl: String, tl: String): String? =
        withContext(Dispatchers.IO) {
            val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotEmpty() }
            if (sentences.size <= 1) return@withContext null
            val mid = sentences.size / 2
            coroutineScope {
                val d1 = async { translateRaw(sentences.take(mid).joinToString(" "), sl, tl) }
                val d2 = async { translateRaw(sentences.drop(mid).joinToString(" "), sl, tl) }
                val r1 = d1.await(); val r2 = d2.await()
                if (r1 != null && r2 != null) "$r1 $r2" else null
            }
        }

    override fun downloadModel(language: String) {}
    override fun removeModel(language: String) {}

    companion object {
        private const val TAG = "TranslationGoogleFree"
        private const val BASE_URL = "https://translate.googleapis.com/translate_a/single"
    }
}
