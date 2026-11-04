package my.noveldokusha.data

import timber.log.Timber
import android.content.Context
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.core.chapterContentIssue
import my.noveldokusha.core.map
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.getRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.feature.local_database.tables.Chapter
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.nodes.Document
import okhttp3.CacheControl
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

    private fun <T> Response<T>.propagatePluginError(): Response<T> = when {
        this is Response.Error && exception is my.noveldokusha.scraper.domain.PluginShowErrorException -> {
            val e = exception as my.noveldokusha.scraper.domain.PluginShowErrorException
            Response.Error(
                message = e.message,
                exception = e,
                pluginErrorTitle = e.errorTitle,
                pluginErrorMessage = e.message,
                pluginAuthUrl = e.authUrl
            )
        }
        else -> this
    }

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
        }.propagatePluginError()
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
        }.propagatePluginError()

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
        }.propagatePluginError()
    }

    suspend fun bookRating(
        bookUrl: String,
    ): Response<String?> = withContext(Dispatchers.IO) {
        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Success(null)

        my.noveldokusha.network.tryFlatConnect {
            scrap.getBookRating(bookUrl)
        }.propagatePluginError()
    }

    /**
     * Статус книги (например, Ongoing/Completed), парсится с сайта источника.
     * Возвращает null, если источник не поддерживает статус.
     */
    suspend fun bookStatus(
        bookUrl: String,
    ): Response<String?> = withContext(Dispatchers.IO) {
        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Success(null)

        my.noveldokusha.network.tryFlatConnect {
            scrap.getBookStatus(bookUrl)
        }.propagatePluginError()
    }

    /**
     * Дата последнего обновления книги, парсится с сайта источника.
     * Возвращает null, если источник не поддерживает дату.
     */
    suspend fun bookLastUpdate(
        bookUrl: String,
    ): Response<String?> = withContext(Dispatchers.IO) {
        val scrap = scraper.getCompatibleSourceCatalog(bookUrl)
            ?: return@withContext Response.Success(null)

        my.noveldokusha.network.tryFlatConnect {
            scrap.getBookLastUpdate(bookUrl)
        }.propagatePluginError()
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
        }.propagatePluginError()
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
                    // Optimasi Networking: Cek apakah chapterUrl langsung cocok dengan sumber scraper yang ada.
                    // Jika cocok, kita langsung kirim request dengan header & tag yang sesuai tanpa perlu
                    // melakukan request probing awal yang membuang kuota/bandwidth dan menduplikasi latensi.
                    val directSource = scraper.getCompatibleSource(chapterUrl)
                    val matchingSource: SourceInterface?
                    val realUrl: String
                    val docFromDirectFetch: Document?

                    if (directSource != null) {
                        matchingSource = directSource
                        val chapterPageUrl = directSource.transformChapterUrl(chapterUrl)
                        val headers = buildChapterHeaders(chapterPageUrl)
                        Timber.d("bookChapter: direct match source=${directSource.id} for url=$chapterPageUrl")
                        val response = networkClient.call(
                            getRequest(chapterPageUrl).apply {
                                headers.forEach { (k, v) -> header(k, v) }
                                cacheControl(CacheControl.FORCE_NETWORK)
                                tag(String::class.java, "source:${directSource.id}")
                            },
                            followRedirects = true
                        )
                        realUrl = response.request.url.toString()
                        docFromDirectFetch = response.use { it.toDocument(directSource.charset) }
                    } else {
                        // Fallback probing: hanya dijalankan jika URL awal belum diketahui sumbernya (misal aggregator/shortlink)
                        val request = my.noveldokusha.network.getRequest(chapterUrl)
                        realUrl = networkClient
                            .call(request, followRedirects = true)
                            .use { it.request.url.toString() }
                        matchingSource = scraper.getCompatibleSource(realUrl)
                        docFromDirectFetch = null
                    }

                    val error by lazy {
                        """
					Unable to load chapter from url:
					$chapterUrl

					Redirect url:
					$realUrl

					Source not supported
				""".trimIndent()
                    }

                    // Validasi cross-domain redirect: jika terjadi redirect ke domain/sumber lain,
                    // perbarui source dan hindari menggunakan doc hasil directSource lama.
                    val effectiveSource = if (directSource != null && matchingSource != null) {
                        val redirectedSource = scraper.getCompatibleSource(realUrl)
                        redirectedSource ?: matchingSource
                    } else {
                        matchingSource
                    }
                    val canReuseDoc = docFromDirectFetch != null && effectiveSource?.id == directSource?.id

                    effectiveSource?.also { source ->
                        val chapterPageUrl = source.transformChapterUrl(realUrl)

                        val headers = buildChapterHeaders(chapterPageUrl)

                        Timber.d("bookChapter: setting tag=source:${source.id} for url=$chapterPageUrl (reusedDoc=$canReuseDoc)")
                        // Gunakan doc yang sudah diunduh jika direct match valid, jika tidak baru fetch chapterPageUrl
                        val doc = if (canReuseDoc) docFromDirectFetch else networkClient.call(
                            getRequest(chapterPageUrl).apply {
                                headers.forEach { (k, v) -> header(k, v) }
                                cacheControl(CacheControl.FORCE_NETWORK)
                                tag(String::class.java, "source:${source.id}")
                            }
                        ).use { it.toDocument(source.charset) }

                        val pages = source.getChapterPages(doc)
                        if (!pages.isNullOrEmpty()) {
                            val pageData = my.noveldokusha.scraper.ChapterDownload(
                                body = "",
                                title = null,
                                pages = pages
                            )
                            return@tryFlatConnect Response.Success(pageData)
                        }

                        if (pages != null) {
                            return@tryFlatConnect Response.Error(
                                "Chapter has no pages (possible auth required)",
                                Exception("Source returned empty page list for $chapterUrl")
                            )
                        }

                        val body = source.getChapterText(doc)?.takeIf { it.isNotBlank() }
                            ?: run {
                                Timber.w(
                                    "bookChapter: source ${source.id} returned no pages and no body for $chapterUrl"
                                )
                                return@also
                            }

                        val data = my.noveldokusha.scraper.ChapterDownload(
                            body = body,
                            title = null
                        )
                        return@tryFlatConnect Response.Success(data)
                    }

                    val mainResponse = networkClient.call(
                        getRequest(realUrl).apply {
                            cacheControl(CacheControl.FORCE_NETWORK)
                            matchingSource?.let { src ->
                                tag(String::class.java, "source:${src.id}")
                                buildChapterHeaders(realUrl).forEach { (k, v) -> header(k, v) }
                            }
                        }
                    )
                    // OkHttp не прошёл редирект (битый Location) — парсить нечего
                    if (mainResponse.code in 300..399) {
                        val location = mainResponse.header("Location")
                        mainResponse.close()
                        return@tryFlatConnect Response.Error(
                            "Redirect not followed: HTTP ${mainResponse.code} -> $location\n\nFor: $chapterUrl",
                            Exception("OkHttp returned unfollowed 3xx response")
                        )
                    }
                    val doc = mainResponse.use { it.toDocument() }

                    val redirectUrl = my.noveldokusha.network.JsRedirectResolver.resolveRedirectUrl(doc)
                    if (redirectUrl != null) {
                        Timber.d("JS redirect resolved: $redirectUrl")
                        val redirectedDoc = networkClient.call(
                            getRequest(redirectUrl)
                                // Ручной JS-хоп: Referer на него OkHttp не ставит,
                                // антиботы его проверяют.
                                .header("Referer", realUrl)
                                .cacheControl(CacheControl.FORCE_NETWORK)
                        ).use { it.toDocument() }
                        val chapter = heuristicChapterExtraction(redirectUrl, redirectedDoc)
                        // Редирект может указывать на login-страницу (requireSignIn внутри
                        // функции, а не автонавигация) — тогда исходная страница главы
                        // содержит реальный контент, и бросать её в пользу login нельзя.
                        if (chapter != null) {
                            val issue = chapterContentIssue(chapter.body)
                            if (issue == null) return@tryFlatConnect Response.Success(chapter)
                            Timber.w("bookChapter: JS redirect target rejected issue=$issue url=$redirectUrl")
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

            // tryAsResponse ловит все исключения внутри tryFlatConnect,
            // поэтому PluginShowErrorException приходит как Response.Error с исключением.
            val finalResult = if (result is Response.Error && result.exception is my.noveldokusha.scraper.domain.PluginShowErrorException) {
                val e = result.exception as my.noveldokusha.scraper.domain.PluginShowErrorException
                Response.Error(
                    message = e.message,
                    exception = e,
                    pluginErrorTitle = e.errorTitle,
                    pluginErrorMessage = e.message,
                    pluginAuthUrl = e.authUrl
                )
            } else {
                result
            }

            when (finalResult) {
                is Response.Success -> return@withContext finalResult
                is Response.Error -> {
                    // Плагин-ошибки не ретраим — это не сетевая проблема
                    if (finalResult.pluginErrorTitle != null) {
                        return@withContext finalResult
                    }
                    val isTransient = finalResult.exception is SocketTimeoutException ||
                            finalResult.message.contains("Timeout", ignoreCase = true) ||
                            finalResult.message.contains("timeout", ignoreCase = true) ||
                            finalResult.message.contains("connect", ignoreCase = true) ||
                            finalResult.message.contains("connection", ignoreCase = true)

                    if (!isTransient || attempt == maxRetries - 1) {
                        return@withContext finalResult
                    }
                    lastError = finalResult
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
                ?: return@withContext (firstPageResult as Response.Error).propagatePluginError()

            Timber.d("bookChaptersList: parsePage supported, totalPages=${firstPage.totalPages}, page1 chapters=${firstPage.chapters.size}")

            val allChapters = mutableListOf<Chapter>()

            firstPage.chapters.forEachIndexed { idx, ch ->
                allChapters.add(Chapter(title = ch.title, url = ch.url, bookUrl = bookUrl, position = idx, volume = ch.volume))
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
                    allChapters.add(Chapter(title = ch.title, url = ch.url, bookUrl = bookUrl, position = offset + idx, volume = ch.volume))
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
        }.propagatePluginError()
            .map { chapters ->
                Timber.d("bookChaptersList: getChapterList returned ${chapters.size} chapters")
                chapters.mapIndexed { index, it ->
                    Chapter(
                        title = it.title,
                        url = it.url,
                        bookUrl = bookUrl,
                        position = index,
                        volume = it.volume
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
            result?.propagatePluginError()
        } catch (e: Exception) {
            Timber.e(e, "bookChaptersPage: page=$page exception")
            Response.Error(e.message ?: "Unknown error", e).propagatePluginError()
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
        }.propagatePluginError()
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
            // ponytail: title из Readability мусорный (например "Chainsaw Man Chapter 3 - Read Manga Online").
            // Название приходит из списка глав, а не из тела — не затираем.
            title = null
        )
    }
}