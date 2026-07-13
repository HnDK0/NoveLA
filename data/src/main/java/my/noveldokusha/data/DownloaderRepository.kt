package my.noveldokusha.data

import timber.log.Timber
import android.content.Context
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.core.map
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.toDocument
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.feature.local_database.tables.Chapter
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.nodes.Document
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scraper: Scraper,
    private val networkClient: NetworkClient,
) {

    suspend fun bookCoverImageUrl(
        bookUrl: String,
    ): Response<String?> = withContext(Dispatchers.IO) {
        val error by lazy {
            """
			Incompatible source.

			Can't find compatible source for:
			$bookUrl
		""".trimIndent()
        }

        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Error(error, Exception())

        my.noveldokusha.network.tryFlatConnect {
            scrap.getBookCoverImageUrl(bookUrl)
        }
    }

    suspend fun bookTitle(
        bookUrl: String,
    ): Response<String?> = withContext(Dispatchers.IO) {
        val error by lazy {
            """
			Incompatible source.

			Can't find compatible source for:
			$bookUrl
		""".trimIndent()
        }

        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Error(error, Exception())

        val apiResponse = my.noveldokusha.network.tryFlatConnect {
            scrap.getBookTitle(bookUrl)
        }

        if (apiResponse is Response.Success && apiResponse.data != null) {
            return@withContext apiResponse
        }

        my.noveldokusha.network.tryFlatConnect {
            val doc = networkClient.get(bookUrl).use { it.toDocument() }

            val titleSelectors = listOf(
                "h1", ".novel-title", ".book-title", ".title",
                "title", ".entry-title", ".post-title"
            )

            for (selector in titleSelectors) {
                val titleElement = doc.selectFirst(selector)
                val title = titleElement?.text()?.trim()?.takeIf { it.isNotBlank() }
                if (title != null && title.length > 3) {
                    return@tryFlatConnect Response.Success(title)
                }
            }

            Response.Success(null)
        }
    }

    suspend fun bookGenres(
        bookUrl: String,
    ): Response<List<String>> = withContext(Dispatchers.IO) {
        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Success(emptyList())

        my.noveldokusha.network.tryFlatConnect {
            scrap.getBookGenres(bookUrl)
        }
    }

    suspend fun bookDescription(
        bookUrl: String,
    ): Response<String?> = withContext(Dispatchers.IO) {
        val error by lazy {
            """
			Incompatible source.

			Can't find compatible source for:
			$bookUrl
		""".trimIndent()
        }

        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Error(error, Exception())

        my.noveldokusha.network.tryFlatConnect {
            scrap.getBookDescription(bookUrl)
        }
    }

    suspend fun bookChapter(
        chapterUrl: String,
    ): Response<my.noveldokusha.scraper.ChapterDownload> = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var lastError: Response<my.noveldokusha.scraper.ChapterDownload>? = null

        for (attempt in 0 until maxRetries) {
            if (attempt > 0) {
                val backoffMs = (1000L * (1L shl (attempt - 1))).coerceAtMost(5000L)
                Timber.d("bookChapter: retry attempt $attempt/$maxRetries for $chapterUrl, waiting ${backoffMs}ms")
                delay(backoffMs)
            }

            val result = my.noveldokusha.network.tryFlatConnect {
                val request = my.noveldokusha.network.getRequest(chapterUrl)
                val realUrl = networkClient
                    .call(request, followRedirects = true)
                    .use { it.request.url.toString() }

                val error by lazy {
                    """
					Unable to load chapter from url:
					$chapterUrl

					Redirect url:
					$realUrl

					Source not supported
				""".trimIndent()
                }

                scraper.getCompatibleSource(realUrl)?.also { source ->
                    val chapterPageUrl = source.transformChapterUrl(realUrl)

                    // Всегда передаём Referer и базовые заголовки при загрузке страницы главы.
                    // Без Referer ряд сайтов (jaomix и др.) после нескольких запросов
                    // возвращает пустую страницу или редирект на защиту.
                    val headers = buildChapterHeaders(chapterPageUrl)

                    val doc = networkClient.getWithHeaders(chapterPageUrl, headers)
                        .use { it.toDocument(source.charset) }

                    // Если getChapterText вернул null или пустую строку — выходим из блока скрапера
                    val body = source.getChapterText(doc)?.takeIf { it.isNotBlank() }
                        ?: return@also

                    val data = my.noveldokusha.scraper.ChapterDownload(
                        body = body,
                        title = null
                    )
                    return@tryFlatConnect Response.Success(data)
                }

                // Fallback: heuristic extraction с поддержкой JS-редиректов
                val doc = networkClient.get(realUrl).use { it.toDocument() }

                // Проверяем HTML на JS-редирект (window.location, meta refresh)
                val redirectUrl = my.noveldokusha.network.JsRedirectResolver.resolveRedirectUrl(doc)
                if (redirectUrl != null) {
                    Timber.d("JS redirect resolved: $redirectUrl")
                    val redirectedDoc = networkClient.get(redirectUrl).use { it.toDocument() }
                    val chapter = heuristicChapterExtraction(redirectUrl, redirectedDoc)
                    if (chapter != null) {
                        return@tryFlatConnect Response.Success(chapter)
                    }
                }

                val chapter = heuristicChapterExtraction(realUrl, doc)
                when (chapter) {
                    null -> Response.Error(
                        error,
                        Exception("Unable to extract chapter data with heuristics")
                    )
                    else -> Response.Success(chapter)
                }
            }

            when (result) {
                is Response.Success -> return@withContext result
                is Response.Error -> {
                    val isTransient = result.exception is SocketTimeoutException ||
                            result.message.contains("Timeout", ignoreCase = true) ||
                            result.message.contains("timeout", ignoreCase = true) ||
                            result.message.contains("connect", ignoreCase = true) ||
                            result.message.contains("connection", ignoreCase = true)

                    if (!isTransient || attempt == maxRetries - 1) {
                        return@withContext result
                    }
                    lastError = result
                }
            }
        }

        lastError ?: Response.Error("Unknown error", Exception("Unexpected retry loop exit"))
    }

    private val chaptersListCache = ConcurrentHashMap<String, ChaptersListCacheEntry>()
    private val chaptersListCacheTtlMs = 120_000L

    private data class ChaptersListCacheEntry(
        val timestamp: Long,
        val chapters: List<Chapter>
    )

    suspend fun bookChaptersList(
        bookUrl: String,
    ): Response<List<Chapter>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        chaptersListCache[bookUrl]?.let { cached ->
            if (now - cached.timestamp < chaptersListCacheTtlMs) {
                Timber.d("bookChaptersList: CACHE HIT — ${cached.chapters.size} chapters for $bookUrl")
                return@withContext Response.Success(cached.chapters)
            }
        }
        Timber.d("bookChaptersList: CACHE MISS — loading chapters for $bookUrl")

        val error by lazy {
            """
			Incompatible source.

			Can't find compatible source for:
			$bookUrl
		""".trimIndent()
        }

        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
        if (scrap == null) {
            Timber.d("bookChaptersList: no compatible source for $bookUrl")
            return@withContext Response.Error(error, Exception())
        }

        Timber.d("bookChaptersList: source=${scrap.id}")

        val firstPageResult = try {
            scrap.parsePage(bookUrl, 1)
        } catch (e: Exception) {
            Response.Error(e.message ?: "Unknown error", e)
        }

        if (firstPageResult != null) {
            val firstPage = (firstPageResult as? Response.Success)?.data
                ?: return@withContext Response.Error(
                    (firstPageResult as Response.Error).message,
                    (firstPageResult as Response.Error).exception
                )

            Timber.d("bookChaptersList: parsePage supported, totalPages=${firstPage.totalPages}, page1 chapters=${firstPage.chapters.size}")

            val allChapters = mutableListOf<Chapter>()

            firstPage.chapters.forEachIndexed { idx, ch ->
                allChapters.add(Chapter(title = ch.title, url = ch.url, bookUrl = bookUrl, position = idx))
            }

            for (page in 2..firstPage.totalPages) {
                Timber.d("bookChaptersList: loading page $page/${firstPage.totalPages}")
                val pageData = (bookChaptersPage(bookUrl, page) as? Response.Success)?.data
                if (pageData == null) {
                    Timber.d("bookChaptersList: FAILED page $page, stopping early")
                    break
                }
                val offset = allChapters.size
                pageData.chapters.forEachIndexed { idx, ch ->
                    allChapters.add(Chapter(title = ch.title, url = ch.url, bookUrl = bookUrl, position = offset + idx))
                }
                Timber.d("bookChaptersList: page $page loaded, cumulative count=${allChapters.size}")
            }

            Timber.d("bookChaptersList: total ${allChapters.size} chapters via parsePage, caching...")
            chaptersListCache[bookUrl] = ChaptersListCacheEntry(now, allChapters)
            return@withContext Response.Success(allChapters)
        }

        Timber.d("bookChaptersList: parsePage not supported, falling back to getChapterList")
        my.noveldokusha.network.tryFlatConnect {
            scrap.getChapterList(bookUrl)
        }
            .map { chapters ->
                Timber.d("bookChaptersList: getChapterList returned ${chapters.size} chapters")
                chapters.mapIndexed { index, it ->
                    Chapter(
                        title = it.title,
                        url = it.url,
                        bookUrl = bookUrl,
                        position = index
                    )
                }
            }
            .onSuccess { chapters ->
                Timber.d("bookChaptersList: caching ${chapters.size} chapters from getChapterList")
                chaptersListCache[bookUrl] = ChaptersListCacheEntry(System.currentTimeMillis(), chapters)
            }
    }

    /**
     * Загружает одну страницу списка глав через parsePage().
     * Возвращает null если плагин не поддерживает parsePage.
     */
    suspend fun bookChaptersPage(
        bookUrl: String,
        page: Int,
    ): Response<SourceInterface.Catalog.PagedChapterResult>? = withContext(Dispatchers.IO) {
        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
        if (scrap == null) {
            Timber.d("bookChaptersPage: no source for $bookUrl, page=$page")
            return@withContext null
        }
        Timber.d("bookChaptersPage: loading page=$page for $bookUrl source=${scrap.id}")
        try {
            val result = scrap.parsePage(bookUrl, page)
            if (result is Response.Success) {
                Timber.d("bookChaptersPage: page=$page OK, chapters=${result.data.chapters.size}, totalPages=${result.data.totalPages}")
            } else if (result is Response.Error) {
                Timber.d("bookChaptersPage: page=$page ERROR — ${result.message}")
            } else {
                Timber.d("bookChaptersPage: page=$page → null (not supported)")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "bookChaptersPage: page=$page exception")
            Response.Error(e.message ?: "Unknown error", e)
        }
    }

    suspend fun bookChaptersListHash(
        bookUrl: String,
    ): Response<String?> = withContext(Dispatchers.IO) {
        val error by lazy {
            """
			Incompatible source.

			Can't find compatible source for:
			$bookUrl
		""".trimIndent()
        }

        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Error(error, Exception())

        my.noveldokusha.network.tryFlatConnect {
            scrap.getChapterListHash(bookUrl)
        }
    }

    // ── Заголовки для загрузки страницы главы ────────────────────────────────

    /**
     * Строит Accept-Language из системных локалей устройства.
     * Пример: "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"
     */
    private fun systemAcceptLanguage(): String {
        val locales = ConfigurationCompat.getLocales(context.resources.configuration)
        return buildString {
            for (i in 0 until locales.size()) {
                val locale = locales.get(i) ?: continue
                if (isNotEmpty()) append(',')
                append(locale.toLanguageTag())
                if (i > 0) {
                    val q = maxOf(0.1, 1.0 - i * 0.1)
                    append(";q=%.1f".format(q))
                }
            }
        }.ifEmpty { Locale.getDefault().toLanguageTag() }
    }

    /**
     * Формирует заголовки для запроса страницы главы.
     * Referer и Accept-Language критичны для сайтов с защитой от парсинга —
     * без них сервер после нескольких запросов возвращает пустую страницу.
     */
    private fun buildChapterHeaders(chapterUrl: String): Map<String, String> {
        val referer = try {
            val uri = java.net.URI(chapterUrl)
            "${uri.scheme}://${uri.host}/"
        } catch (_: Exception) {
            chapterUrl
        }
        return mapOf(
            "Referer"         to referer,
            "Accept"          to ACCEPT_HTML,
            "Accept-Language" to systemAcceptLanguage(),
        )
    }

    companion object {
        /** MIME-типы при загрузке HTML — аналог браузерного Accept, не зависит от устройства */
        private const val ACCEPT_HTML =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    }
}


private fun heuristicChapterExtraction(url: String, document: Document): my.noveldokusha.scraper.ChapterDownload? {
    Readability4JExtended(url, document).parse().also { article ->
        val content = article.articleContent ?: return null
        return my.noveldokusha.scraper.ChapterDownload(
            body = TextExtractor.get(content),
            title = article.title
        )
    }
}