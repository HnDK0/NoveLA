package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.network.interceptors.GLOBAL_USER_AGENT
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Translation via translate-pa.googleapis.com/v1/translateHtml —
 * same endpoint as WtrLab plugin. HTML-wrapped paragraphs → better quality than plain-text endpoint.
 *
 * Note: replaced Gson with org.json (already available via the existing Gemini/OpenAI managers)
 * to avoid an extra dependency.
 */
class TranslationManagerGooglePA(
    private val coroutineScope: AppCoroutineScope,
    private val appPreferences: AppPreferences,
    private val networkClient: ScraperNetworkClient
) : TranslationManager {

    private val client get() = networkClient.client

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

    // ─── Key management ───────────────────────────────────────────────────────

    private val keyFetchMutex = Mutex()
    private var keyFetchJob: Deferred<String>? = null
    private val keyHeaderRegex = Regex(""""X-Goog-API-Key"\s*:\s*"([^"]+)"""")

    private suspend fun getApiKey(): String = coroutineScope {
        val cached = appPreferences.TRANSLATION_GOOGLE_PA_CACHED_KEY.value
        val lastChecked = appPreferences.TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED.value
        if (cached.isNotBlank() && System.currentTimeMillis() - lastChecked < KEY_TTL_MS) return@coroutineScope cached

        val deferred: Deferred<String> = keyFetchMutex.withLock {
            val fresh = appPreferences.TRANSLATION_GOOGLE_PA_CACHED_KEY.value
            val freshTs = appPreferences.TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED.value
            if (fresh.isNotBlank() && System.currentTimeMillis() - freshTs < KEY_TTL_MS) return@coroutineScope fresh
            keyFetchJob?.let { return@withLock it }
            async(Dispatchers.IO) { fetchAndCacheKey() }.also { keyFetchJob = it }
        }
        try {
            deferred.await()
        } finally {
            keyFetchMutex.withLock { if (keyFetchJob === deferred) keyFetchJob = null }
        }
    }

    private suspend fun fetchAndCacheKey(): String {
        val now = System.currentTimeMillis()
        val keys = appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value
            .splitToSequence('\n').map { it.trim() }.filter { it.isNotBlank() }.toList()

        for (key in keys) {
            if (checkKey(key)) { cacheKey(key, now); return key }
        }

        val fetched = fetchKeyFromWtrLab()
            ?: throw IllegalStateException("Google PA: No working API key. Check Settings or retry.")
        addKeyToPreferences(fetched)
        cacheKey(fetched, now)
        return fetched
    }

    private suspend fun checkKey(key: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildPayload("<p>test</p>", "en", "en")
            val resp = client.newCall(
                Request.Builder().url(TRANSLATE_URL)
                    .addHeader("X-Goog-Api-Key", key)
                    .addHeader("Origin", "https://translate.google.com")
                    .post(body).build()
            ).execute()
            val ok = resp.isSuccessful
            resp.body.close()
            Log.d(TAG, "checkKey: ${key.take(12)}… → HTTP ${resp.code}")
            ok
        }.getOrDefault(false)
    }

    private fun cacheKey(key: String, ts: Long) {
        appPreferences.TRANSLATION_GOOGLE_PA_CACHED_KEY.value = key
        appPreferences.TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED.value = ts
    }

    private fun addKeyToPreferences(key: String) {
        val existing = appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value
            .splitToSequence('\n').map { it.trim() }.filter { it.isNotBlank() && it != key }.toList()
        appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value = (listOf(key) + existing).joinToString("\n")
    }

    private suspend fun fetchKeyFromWtrLab(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val rankingHtml = client.newCall(
                Request.Builder().url("https://wtr-lab.com/en/ranking/monthly")
                    .header("User-Agent", GLOBAL_USER_AGENT).build()
            ).execute().body.string()

            val novelUrl = Regex("""href=["']([^"']*/novel/[^"']+)["']""").findAll(rankingHtml)
                .map { if (it.groupValues[1].startsWith("http")) it.groupValues[1] else "https://wtr-lab.com${it.groupValues[1]}" }
                .firstOrNull() ?: return@withContext null

            val chapterHtml = client.newCall(
                Request.Builder().url("${novelUrl.trimEnd('/')}/chapter-1")
                    .header("User-Agent", GLOBAL_USER_AGENT).build()
            ).execute().body.string()

            keyHeaderRegex.find(chapterHtml)?.groupValues?.get(1)?.let { return@withContext it }

            val scriptUrls = Regex("""<script[^>]+src=["']([^"']*/_next/[^"']+\.js[^"']*)["']""")
                .findAll(chapterHtml)
                .map { if (it.groupValues[1].startsWith("http")) it.groupValues[1] else "https://wtr-lab.com${it.groupValues[1]}" }
                .filter { "_buildManifest" !in it && "_ssgManifest" !in it }
                .distinct().toList()

            searchKeyInScripts(scriptUrls)
        }.getOrNull()
    }

    private suspend fun searchKeyInScripts(urls: List<String>): String? = withContext(Dispatchers.IO) {
        for (url in urls) {
            runCatching {
                val js = client.newCall(Request.Builder().url(url).build()).execute().body.string()
                keyHeaderRegex.find(js)?.groupValues?.get(1)?.let { return@withContext it }
            }
        }
        null
    }

    // ─── Translation ──────────────────────────────────────────────────────────

    private suspend fun translateSingle(text: String, sl: String, tl: String): String =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext text
            val paragraphs = text.split("\n").filter { it.isNotBlank() }
            if (paragraphs.isEmpty()) return@withContext text
            translateChunks(paragraphs, sl.takeIf { it != "auto" } ?: "auto", tl).joinToString("\n")
        }

    private suspend fun translateChunks(paragraphs: List<String>, sl: String, tl: String): List<String> {
        data class Chunk(val indices: List<Int>, val html: String)

        val result = paragraphs.toMutableList()
        val chunks = mutableListOf<Chunk>()
        val curIdx = mutableListOf<Int>(); val curParts = mutableListOf<String>(); var curLen = 0

        for ((i, para) in paragraphs.withIndex()) {
            if (curLen > 0 && curLen + para.length + 4 > MAX_CHUNK_CHARS) {
                chunks += Chunk(curIdx.toList(), curParts.joinToString("<br>")); curIdx.clear(); curParts.clear(); curLen = 0
            }
            curIdx += i; curParts += para; curLen += para.length + 4
        }
        if (curParts.isNotEmpty()) chunks += Chunk(curIdx.toList(), curParts.joinToString("<br>"))

        val apiKey = getApiKey()
        var failedChunks = 0

        for ((i, chunk) in chunks.withIndex()) {
            if (i > 0) delay(400L)
            val translated = runCatching { translateHtml(chunk.html, sl, tl, apiKey) }.getOrElse { e ->
                Log.e(TAG, "Chunk ${i + 1}/${chunks.size} failed: ${e.message}")
                failedChunks++; return@getOrElse null
            } ?: continue

            if (translated == chunk.html) continue

            val translatedParas = translated
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .split("\n")
                .map { unescapeHtml(it.trim()) }
                .filter { it.isNotBlank() }

            for (pos in 0 until minOf(translatedParas.size, chunk.indices.size)) {
                result[chunk.indices[pos]] = translatedParas[pos]
            }
        }

        if (failedChunks == chunks.size && chunks.isNotEmpty())
            throw IllegalStateException("Google PA: All chunks failed. Check internet connection.")

        return result
    }

    private suspend fun translateHtml(html: String, sl: String, tl: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            val resp = client.newCall(
                Request.Builder().url(TRANSLATE_URL)
                    .addHeader("X-Goog-API-Key", apiKey)
                    .addHeader("Origin", "https://translate.google.com")
                    .post(buildPayload(html, sl, tl)).build()
            ).execute()
            if (!resp.isSuccessful) {
                val code = resp.code; resp.body.close()
                throw IllegalStateException("Google PA: HTTP $code")
            }
            val body = resp.body.string()
            try {
                JSONArray(body).getJSONArray(0).getString(0)
            } catch (e: Exception) {
                throw IllegalStateException("Google PA: Parse failed — ${e.message}")
            }
        }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        val sl = if (sourceLanguage == "auto") "auto" else sourceLanguage
        val boundaries = mutableListOf<IntRange>()
        val allParas = mutableListOf<String>()
        for (text in texts) {
            val lines = text.split("\n").filter { it.isNotBlank() }
            val start = allParas.size
            allParas += lines
            boundaries += start until start + lines.size
        }

        val translatedAll = translateChunks(allParas, sl, targetLanguage)

        buildMap {
            texts.forEachIndexed { i, text ->
                val range = boundaries[i]
                val safeEnd = range.last.coerceAtMost(translatedAll.size - 1)
                put(text, if (range.isEmpty() || safeEnd < range.first) text
                    else translatedAll.subList(range.first, safeEnd + 1).joinToString("\n").ifEmpty { text })
            }
        }.also { Log.d(TAG, "translateBatch: ${texts.size} total, ${it.size} translated") }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Build request body as JSON array (replaces Gson dependency). */
    private fun buildPayload(html: String, sl: String, tl: String) =
        JSONArray().apply {
            put(JSONArray().apply { put(html); put(sl); put(tl) })
            put("wt_lib")
        }.toString().toRequestBody("application/json+protobuf".toMediaType())

    private fun unescapeHtml(text: String) = text
        .replace("&quot;", "\"").replace("&amp;", "&")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }

    override fun downloadModel(language: String) {}
    override fun removeModel(language: String) {}

    companion object {
        private const val TAG = "TranslationGooglePA"
        private const val TRANSLATE_URL = "https://translate-pa.googleapis.com/v1/translateHtml"
        private const val KEY_TTL_MS = 24 * 60 * 60 * 1000L
        private const val MAX_CHUNK_CHARS = 8_000
    }
}
