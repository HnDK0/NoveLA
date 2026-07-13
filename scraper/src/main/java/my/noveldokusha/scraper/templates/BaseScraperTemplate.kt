package my.noveldokusha.scraper.templates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.tryConnect
import my.noveldokusha.network.tryFlatConnect
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Base abstract scraper template that implements common functionality for web novel sites.
 * Concrete scrapers extend this and provide CSS selectors for their specific site layout.
 *
 * This follows the template method pattern similar to lightnovel-crawler's Python templates.
 */
abstract class BaseScraperTemplate(
    protected val networkClient: NetworkClient
) : SourceInterface.Catalog {

    // CSS Selectors - Override these in subclasses for each site
    protected abstract val selectBookCover: String
    protected abstract val selectBookDescription: String
    protected abstract val selectChapterList: String
    protected abstract val selectChapterContent: String
    protected abstract val selectCatalogItems: String
    protected abstract val selectCatalogItemTitle: String
    protected abstract val selectCatalogItemUrl: String
    protected abstract val selectCatalogItemCover: String
    protected open val selectPaginationLastPage: String? = null

    // Optional CSS selectors for search functionality
    protected open val selectSearchItems: String? = null
    protected open val selectSearchItemTitle: String? = null
    protected open val selectSearchItemUrl: String? = null
    protected open val selectSearchItemCover: String? = null

    // Optional transformations
    protected open fun transformBookUrl(url: String): String =
        if (url.startsWith("http")) url else baseUrl + url.removePrefix("/")

    protected open fun chapterUrlTransform(url: String): String =
        if (url.startsWith("http")) url else baseUrl + url.removePrefix("/")

    protected open fun transformCoverUrl(url: String): String =
        if (url.startsWith("http")) url else baseUrl + url.removePrefix("/")

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst(selectChapterContent)?.let { TextExtractor.get(it) } ?: ""
    }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(selectBookCover)
                ?.let { extractImageUrl(it) }
                ?.let { transformCoverUrl(it) }
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(selectBookDescription)
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            fetchChapterList(bookUrl)
        }
    }

    protected open suspend fun fetchChapterList(bookUrl: String): List<ChapterResult> {
        return networkClient.get(bookUrl).toDocument()
            .select(selectChapterList)
            .map { element ->
                ChapterResult(
                    title = element.text(),
                    url = transformChapterUrl(element.attr("href"))
                )
            }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        val url = buildCatalogUrl(index)
        tryFlatConnect {
            val doc = networkClient.get(url).toDocument()
            parseCatalogPage(doc, index)
        }
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        if (input.isBlank())
            return@withContext Response.Success(PagedList.createEmpty(index = index))

        val url = buildSearchUrl(index, input)
        tryFlatConnect {
            val doc = networkClient.get(url).toDocument()
            parseSearchPage(doc, index, input)
        }
    }

    protected abstract fun buildCatalogUrl(index: Int): String
    protected abstract fun buildSearchUrl(index: Int, input: String): String

    protected open suspend fun parseCatalogPage(doc: Document, index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                val books = doc.select(selectCatalogItems).mapNotNull { element ->
                    parseCatalogItem(element)
                }

                PagedList(
                    list = books,
                    index = index,
                    isLastPage = isLastPage(doc)
                )
            }
        }

    protected open suspend fun parseSearchPage(doc: Document, index: Int, query: String): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            val searchItems = selectSearchItems ?: selectCatalogItems
            val searchTitle = selectSearchItemTitle ?: selectCatalogItemTitle
            val searchUrl = selectSearchItemUrl ?: selectCatalogItemUrl
            val searchCover = selectSearchItemCover ?: selectCatalogItemCover

            tryConnect {
                val books = doc.select(searchItems).mapNotNull { element ->
                    val title = element.selectFirst(searchTitle)?.text() ?: return@mapNotNull null
                    val url = element.selectFirst(searchUrl)?.attr("href") ?: return@mapNotNull null
                    val cover = element.selectFirst(searchCover)?.let { extractImageUrl(it) } ?: ""

                    BookResult(
                        title = title,
                        url = transformBookUrl(url),
                        coverImageUrl = processImageUrlWhen(transformCoverUrl(cover))
                    )
                }

                PagedList(
                    list = books,
                    index = index,
                    isLastPage = isLastPage(doc)
                )
            }
        }

    protected open fun parseCatalogItem(element: Element): BookResult? {
        val title = element.selectFirst(selectCatalogItemTitle)?.text() ?: return null
        val url = element.selectFirst(selectCatalogItemUrl)?.attr("href") ?: return null
        val cover = element.selectFirst(selectCatalogItemCover)?.let { extractImageUrl(it) } ?: ""

        return BookResult(
            title = title,
            url = transformBookUrl(url),
            coverImageUrl = processImageUrlWhen(transformCoverUrl(cover))
        )
    }

    protected open fun isLastPage(doc: Document): Boolean {
        return selectPaginationLastPage?.let { selector ->
            doc.selectFirst(selector)?.let { element ->
                // Check if element has "disabled" class or is marked as active
                element.hasClass("disabled") || element.hasClass("active")
            } ?: true
        } ?: true
    }
    // Fix cover image size, not work to NovelFull, AllNovel
    fun processImageUrlWhen(coverImageUrl: String?): String {
        if (coverImageUrl == null) return ""
        return when {
            coverImageUrl.contains("novel_200_89") -> coverImageUrl.replace("novel_200_89", "novel")
            coverImageUrl.contains("t-200x89") -> coverImageUrl.replace("t-200x89", "t-300x439")
            else -> coverImageUrl
        }
    }
    protected fun extractImageUrl(element: Element): String {
        return when {
            element.hasAttr("data-src") -> element.attr("data-src")
            element.hasAttr("src") -> element.attr("src")
            element.hasAttr("data-lazy-src") -> element.attr("data-lazy-src")
            else -> ""
        }
    }
}
