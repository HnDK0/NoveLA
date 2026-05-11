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
 * Translation via the `translate-pa.googleapis.com/v1/translateHtml` endpoint —
 * the same backend used by the WtrLab browser plugin.
 *
 * Paragraphs are wrapped in HTML (`<br>` separated) before dispatch, which gives
 * Google's neural engine better sentence-boundary context and improves output quality
 * compared to the plain-text endpoint used by [TranslationManagerGoogleFree].
 *
 * This manager does NOT call an LLM, so token-overhead optimisation is not applicable.
 * No dependency on Gson — JSON is built with org.json (already present via Gemini/OpenAI).
 */
class TranslationManagerGooglePA(
    private val coroutineScope: AppCoroutineScope,
    private val appPreferences: AppPreferences,
    private val networkClient: ScraperNetworkClient,
) : TranslationManager {

    /** Reuses the app-wide scraper client (includes Cloudflare bypass interceptors). */
    private val client get() = networkClient.client

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

    // ─── API key management ───────────────────────────────────────────────────

    private val keyFetchMutex = Mutex()

    /**
     * Shared deferred used to coalesce concurrent key-fetch requests.
     * Multiple callers waiting for a key share a single in-flight coroutine.
     */
    private var keyFetchJob: Deferred<String>? = null

    private val keyHeaderRegex = Regex(""""X-Goog-API-Key"\s*:\s*"([^"]+)"""")

    /**
     * Returns a valid API key, preferring the cached value if it was checked
     * within the last [KEY_TTL_MS]. Concurrent calls are coalesced via [keyFetchMutex].
     */
    private suspend fun getApiKey(): String = coroutineScope {
        val cached = appPreferences.TRANSLATION_GOOGLE_PA_CACHED_KEY.value
        val lastChecked = appPreferences.TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED.value
        if (cached.isNotBlank() && System.currentTimeMillis() - lastChecked < KEY_TTL_MS) {
            return@coroutineScope cached
        }

        val deferred: Deferred<String> = keyFetchMutex.withLock {
            // Re-check inside the lock — another coroutine may have refreshed already.
            val fresh = appPreferences.TRANSLATION_GOOGLE_PA_CACHED_KEY.value
            val freshTs = appPreferences.TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED.value
            if (fresh.isNotBlank() && System.currentTimeMillis() - freshTs < KEY_TTL_MS) {
                return@coroutineScope fresh
            }
            keyFetchJob?.let { return@withLock it }
            async(Dispatchers.IO) { fetchAndCacheKey() }.also { keyFetchJob = it }
        }
        try {
            deferred.await()
        } finally {
            keyFetchMutex.withLock { if (keyFetchJob === deferred) keyFetchJob = null }
        }
    }

    /**
     * Tries each user-configured key in order, then falls back to scraping a fresh
     * key from the WtrLab website.
     */
    private suspend fun fetchAndCacheKey(): String {
        val now = System.currentTimeMillis()
        val userKeys = appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value
            .splitToSequence('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (key in userKeys) {
            if (checkKey(key)) { cacheKey(key, now); return key }
        }

        val scraped = scrapeKeyFromWtrLab()
            ?: throw IllegalStateException(
                "Google PA: No working API key found. Add one in Settings or retry."
            )
        addKeyToPreferences(scraped)
        cacheKey(scraped, now)
        return scraped
    }

    /** Validates a key by issuing a minimal translateHtml request. */
    private suspend fun checkKey(key: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resp = client.newCall(
                Request.Builder()
                    .url(TRANSLATE_URL)
                    .addHeader("X-Goog-Api-Key", key)
                    .addHeader("Origin", "https://translate.google.com")
                    .post(buildPayload("<p>test</p>", "en", "en"))
                    .build()
            ).execute()
            val ok = resp.isSuccessful
            resp.body.close()
            Log.d(TAG, "checkKey: ${key.take(12)}… → HTTP ${resp.code}")
            ok
        }.getOrDefault(false)
    }

    private fun cacheKey(key: String, timestamp: Long) {
        appPreferences.TRANSLATION_GOOGLE_PA_CACHED_KEY.value = key
        appPreferences.TRANSLATION_GOOGLE_PA_KEY_LAST_CHECKED.value = timestamp
    }

    /** Prepends the new key to the stored list, deduplicating as needed. */
    private fun addKeyToPreferences(key: String) {
        val existing = appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value
            .splitToSequence('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != key }
            .toList()
        appPreferences.TRANSLATION_GOOGLE_PA_API_KEYS.value = (listOf(key) + existing).joinToString("\n")
    }

    /**
     * Scrapes a Google PA API key from the WtrLab website by:
     * 1. Fetching the monthly novel ranking page to get a novel URL.
     * 2. Fetching chapter 1 of that novel and looking for the key in page HTML.
     * 3. If not found inline, searching through Next.js bundle scripts.
     */
    private suspend fun scrapeKeyFromWtrLab(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val rankingHtml = client.newCall(
                Request.Builder()
                    .url("https://wtr-lab.com/en/ranking/monthly")
                    .build()
            ).execute().body.string()

            val novelUrl = Regex("""href=["']([^"']*/novel/[^"']+)["']""")
                .findAll(rankingHtml)
                .map { m ->
                    m.groupValues[1].let { href ->
                        if (href.startsWith("http")) href else "https://wtr-lab.com$href"
                    }
                }
                .firstOrNull() ?: return@withContext null

            val chapterHtml = client.newCall(
                Request.Builder()
                    .url("${novelUrl.trimEnd('/')}/chapter-1")
                    .build()
            ).execute().body.string()

            // Try to find the key directly in the chapter HTML.
            keyHeaderRegex.find(chapterHtml)?.groupValues?.get(1)?.let { return@withContext it }

            // Fall back to searching through Next.js bundle scripts.
            val scriptUrls = Regex("""<script[^>]+src=["']([^"']*/_next/[^"']+\.js[^"']*)["']""")
                .findAll(chapterHtml)
                .map { m ->
                    m.groupValues[1].let { src ->
                        if (src.startsWith("http")) src else "https://wtr-lab.com$src"
                    }
                }
                .filter { "_buildManifest" !in it && "_ssgManifest" !in it }
                .distinct()
                .toList()

            searchKeyInScripts(scriptUrls)
        }.getOrNull()
    }

    /** Fetches each script URL in turn and returns the first API key found. */
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

    /**
     * Translates a single multi-paragraph text block.
     * Blank paragraphs are dropped before dispatch and the results are rejoined with "\n".
     */
    private suspend fun translateSingle(text: String, sl: String, tl: String): String =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext text
            val paragraphs = text.split("\n").filter { it.isNotBlank() }
            if (paragraphs.isEmpty()) return@withContext text
            translateChunks(paragraphs, sl.takeIf { it != "auto" } ?: "auto", tl).joinToString("\n")
        }

    /**
     * Groups paragraphs into ≤ [MAX_CHUNK_CHARS] HTML blocks, translates each sequentially
     * (with a 400 ms inter-chunk delay to avoid rate limiting), and reassembles the results.
     *
     * If all chunks fail, throws [IllegalStateException]. Partial failures are tolerated —
     * untranslated paragraphs remain in their original language.
     */
    private suspend fun translateChunks(paragraphs: List<String>, sl: String, tl: String): List<String> {
        data class Chunk(val indices: List<Int>, val html: String)

        val result = paragraphs.toMutableList()
        val chunks = mutableListOf<Chunk>()

        // Build chunks within the character limit.
        val curIdx = mutableListOf<Int>()
        val curParts = mutableListOf<String>()
        var curLen = 0
        for ((i, para) in paragraphs.withIndex()) {
            val cost = para.length + 4 // <br> overhead
            if (curLen > 0 && curLen + cost > MAX_CHUNK_CHARS) {
                chunks += Chunk(curIdx.toList(), curParts.joinToString("<br>"))
                curIdx.clear(); curParts.clear(); curLen = 0
            }
            curIdx += i; curParts += para; curLen += cost
        }
        if (curParts.isNotEmpty()) chunks += Chunk(curIdx.toList(), curParts.joinToString("<br>"))

        val apiKey = getApiKey()
        var failedChunks = 0

        for ((i, chunk) in chunks.withIndex()) {
            if (i > 0) delay(400L) // brief pause to avoid rate limiting

            val translated = runCatching {
                translateHtml(chunk.html, sl, tl, apiKey)
            }.getOrElse { e ->
                Log.e(TAG, "Chunk ${i + 1}/${chunks.size} failed: ${e.message}")
                failedChunks++
                return@getOrElse null
            } ?: continue

            if (translated == chunk.html) continue // unchanged — skip reassembly

            // Split translated HTML back into paragraphs.
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
            throw IllegalStateException("Google PA: All chunks failed. Check your internet connection.")

        return result
    }

    /** Issues the actual translateHtml HTTP request and extracts the translated HTML string. */
    private suspend fun translateHtml(html: String, sl: String, tl: String, apiKey: String): String =
        withContext(Dispatchers.IO) {
            val resp = client.newCall(
                Request.Builder()
                    .url(TRANSLATE_URL)
                    .addHeader("X-Goog-API-Key", apiKey)
                    .addHeader("Origin", "https://translate.google.com")
                    .post(buildPayload(html, sl, tl))
                    .build()
            ).execute()

            if (!resp.isSuccessful) {
                val code = resp.code
                resp.body.close()
                throw IllegalStateException("Google PA: HTTP $code")
            }

            val body = resp.body.string()
            try {
                JSONArray(body).getJSONArray(0).getString(0)
            } catch (e: Exception) {
                throw IllegalStateException("Google PA: Failed to parse response — ${e.message}")
            }
        }

    /**
     * Translates a batch of texts by flattening their paragraphs into a single run,
     * translating with [translateChunks], then reconstructing per-text segments.
     */
    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        val sl = if (sourceLanguage == "auto") "auto" else sourceLanguage

        // Flatten all paragraphs into one list, tracking each text's paragraph range.
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
                val translated = if (range.isEmpty() || safeEnd < range.first) {
                    text // no paragraphs — return original
                } else {
                    translatedAll.subList(range.first, safeEnd + 1).joinToString("\n").ifEmpty { text }
                }
                put(text, translated)
            }
        }.also { Log.d(TAG, "translateBatch: ${texts.size} total, ${it.size} translated") }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Serializes the translation request as a JSON array matching the translateHtml
     * wire format (`[[html, sl, tl], "wt_lib"]`). Using org.json avoids a Gson dependency.
     */
    private fun buildPayload(html: String, sl: String, tl: String) =
        JSONArray().apply {
            put(JSONArray().apply { put(html); put(sl); put(tl) })
            put("wt_lib")
        }.toString().toRequestBody("application/json+protobuf".toMediaType())

    /** Decodes the five most common HTML entities that Google's API returns. */
    private fun unescapeHtml(text: String) = text
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }

    override fun downloadModel(language: String) = Unit
    override fun removeModel(language: String) = Unit

    companion object {
        private const val TAG = "TranslationGooglePA"
        private const val TRANSLATE_URL = "https://translate-pa.googleapis.com/v1/translateHtml"

        /** How long a cached API key is considered valid before re-validation (24 hours). */
        private const val KEY_TTL_MS = 24L * 60 * 60 * 1_000

        /** Maximum number of HTML characters per translateHtml request. */
        private const val MAX_CHUNK_CHARS = 8_000
    }
}
