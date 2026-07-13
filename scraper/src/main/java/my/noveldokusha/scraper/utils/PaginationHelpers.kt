package my.noveldokusha.scraper.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import my.noveldokusha.core.PagedList
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import timber.log.Timber

/**
 * Type alias for AJAX chapter list provider
 */
typealias AjaxChapterListProvider = suspend (bookUrl: String, networkClient: NetworkClient) -> List<ChapterResult>

/**
 * Type alias for catalog pagination provider
 */
typealias CatalogPaginationProvider = suspend (index: Int, networkClient: NetworkClient) -> PagedList<BookResult>

/**
 * Type alias for search pagination provider
 */
typealias SearchPaginationProvider = suspend (query: String, index: Int, networkClient: NetworkClient) -> PagedList<BookResult>

/**
 * Pagination helper functions for scraper operations
 */

/**
 * Load chapters from multiple pages asynchronously
 */
suspend fun loadChaptersPaginated(
    bookUrl: String,
    chapterSelector: String,
    maxPageExtractor: (org.jsoup.nodes.Document) -> Int,
    urlBuilder: (Int) -> String,
    networkClient: NetworkClient,
    transformUrl: (String) -> String = { it }
): List<ChapterResult> = coroutineScope {
    // Get first page to determine total pages
    val firstDoc = GET(bookUrl, networkClient = networkClient)
    val totalPages = maxPageExtractor(firstDoc)

    if (totalPages <= 0) return@coroutineScope emptyList()
    if (totalPages == 1) {
        // Only one page
        return@coroutineScope firstDoc.selectList(chapterSelector).map {
            ChapterResult(title = it.text(), url = transformUrl(it.attr("href")))
        }
    }

    // Load all pages asynchronously
    val urlsToLoad = (2..totalPages).map { pageIndex ->
        urlBuilder(pageIndex)
    }

    val deferredDocs = urlsToLoad.map { url ->
        async { GET(url, networkClient = networkClient) }
    }
    val additionalDocs = deferredDocs.awaitAll()

    val allDocs = listOf(firstDoc) + additionalDocs

    return@coroutineScope allDocs.flatMap { doc ->
        doc.selectList(chapterSelector).map {
            ChapterResult(title = it.text(), url = transformUrl(it.attr("href")))
        }
    }
}

/**
 * Extract last page number from pagination element
 */
fun extractLastPageNumber(doc: org.jsoup.nodes.Document, selector: String): Int {
    val paginationElement = doc.selectFirst(selector)
    return when {
        paginationElement == null -> 1
        paginationElement.hasClass("disabled") -> 1
        else -> {
            // Try to extract from href or text
            val href = paginationElement.attr("href")
            val pageMatch = Regex("page[=\\-/](\\d+)").find(href)
            pageMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        }
    }
}

/**
 * Create AJAX chapter provider for standard pagination
 */
fun createAjaxChapterProvider(
    paginationSelector: String,
    chapterSelector: String,
    urlBuilder: (String, Int) -> String = { bookUrl, page -> "$bookUrl?page=$page" }
): AjaxChapterListProvider = { bookUrl, networkClient ->
    loadChaptersPaginated(
        bookUrl = bookUrl,
        chapterSelector = chapterSelector,
        maxPageExtractor = { extractLastPageNumber(it, paginationSelector) },
        urlBuilder = { page -> urlBuilder(bookUrl, page) },
        networkClient = networkClient
    )
}

/**
 * Create AJAX chapter provider with dynamic parameters (e.g., FanMTL wjm parameter)
 */
fun createAjaxChapterProviderWithParams(
    paginationSelector: String,
    paramExtractor: (org.jsoup.nodes.Document) -> Map<String, String>,
    chapterSelector: String,
    urlBuilder: (String, Int, Map<String, String>) -> String
): AjaxChapterListProvider = { bookUrl, networkClient ->
    try {
        val firstDoc = GET(bookUrl, networkClient = networkClient)
        val params = paramExtractor(firstDoc)

        loadChaptersPaginated(
            bookUrl = bookUrl,
            chapterSelector = chapterSelector,
            maxPageExtractor = { extractLastPageNumber(it, paginationSelector) },
            urlBuilder = { page -> urlBuilder(bookUrl, page, params) },
            networkClient = networkClient
        )
    } catch (e: Exception) {
        Timber.w("Failed to load chapters with params: ${e.message}")
        emptyList()
    }
}

/**
 * Create catalog pagination provider for standard HTML catalog pagination
 */
fun createCatalogPaginationProvider(
    baseUrl: String,
    paginationSelector: String,
    itemSelector: String,
    titleSelector: String,
    urlSelector: String,
    coverSelector: String,
    urlBuilder: (Int) -> String = { page -> "$baseUrl?page=$page" },
    transformUrl: (String) -> String = { it },
    transformCoverUrl: (String, String) -> String = { cover, _ -> cover }
): CatalogPaginationProvider = { index, networkClient ->
    try {
        val url = urlBuilder(index)
        val doc = GET(url, networkClient = networkClient)
        val items = doc.selectList(itemSelector)

        val books = items.map { item ->
            val title = item.selectTextFallback(titleSelector) ?: ""
            val bookUrl = item.selectAttr(urlSelector.split(",").first().trim(), urlSelector.split(",").first().trim()) ?: ""
            val cover = item.selectAttr(coverSelector.split(",").first().trim(), coverSelector.split(",").first().trim()) ?: ""

            BookResult(
                title = title,
                url = transformUrl(bookUrl),
                coverImageUrl = transformCoverUrl(cover, transformUrl(bookUrl))
            )
        }

        val isLastPage = books.isEmpty() || doc.selectFirst(paginationSelector) == null
        PagedList(list = books, index = index, isLastPage = isLastPage)

    } catch (e: Exception) {
        Timber.w("Failed to load catalog page $index: ${e.message}")
        PagedList.createEmpty(index)
    }
}

/**
 * Create search pagination provider for complex search pagination (e.g., with searchid)
 */
fun createSearchPaginationProvider(
    searchIdExtractor: (org.jsoup.nodes.Document) -> String?,
    paginationSelector: String,
    itemSelector: String,
    titleSelector: String,
    urlSelector: String,
    coverSelector: String,
    @Suppress("UNUSED_PARAMETER") urlBuilder: (String, Int, String) -> String,
    transformUrl: (String) -> String = { it },
    transformCoverUrl: (String, String) -> String = { cover, _ -> cover }
): SearchPaginationProvider = { query, index, networkClient ->
    try {
        if (index == 0) {
            // First page: POST search and extract searchid
            val postData = mapOf("search" to query) // Customize based on site
            val doc = POST("search-url", postData, networkClient = networkClient) // Customize URL
            @Suppress("UNUSED_VARIABLE")
            val searchId = searchIdExtractor(doc) ?: ""

            // Parse first page results
            val items = doc.selectList(itemSelector)
            val books = items.map { item ->
                val title = item.selectTextFallback(titleSelector) ?: ""
                val bookUrl = item.selectAttr(urlSelector.split(",").first().trim(), urlSelector.split(",").first().trim()) ?: ""
                val cover = item.selectAttr(coverSelector.split(",").first().trim(), coverSelector.split(",").first().trim()) ?: ""

                BookResult(
                    title = title,
                    url = transformUrl(bookUrl),
                    coverImageUrl = transformCoverUrl(cover, transformUrl(bookUrl))
                )
            }

            PagedList(list = books, index = 0, isLastPage = books.isEmpty())

        } else {
            // Subsequent pages: use searchid
            val doc = GET("search-results-url?searchid=searchId&page=$index", networkClient = networkClient) // Customize
            val items = doc.selectList(itemSelector)

            val books = items.map { item ->
                val title = item.selectTextFallback(titleSelector) ?: ""
                val bookUrl = item.selectAttr(urlSelector.split(",").first().trim(), urlSelector.split(",").first().trim()) ?: ""
                val cover = item.selectAttr(coverSelector.split(",").first().trim(), coverSelector.split(",").first().trim()) ?: ""

                BookResult(
                    title = title,
                    url = transformUrl(bookUrl),
                    coverImageUrl = transformCoverUrl(cover, transformUrl(bookUrl))
                )
            }

            val isLastPage = books.isEmpty() || doc.selectFirst(paginationSelector) == null
            PagedList(list = books, index = index, isLastPage = isLastPage)
        }

    } catch (e: Exception) {
        Timber.w("Failed to load search page $index for '$query': ${e.message}")
        PagedList.createEmpty(index)
    }
}
