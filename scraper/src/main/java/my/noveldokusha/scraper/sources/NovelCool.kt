// ponytail: ported from Paras fork (ParasNovelDokusha/scraper/.../sources/NovelCool.kt).
package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document
import java.net.URI

/**
 * NovelCool — https://www.novelcool.com
 */
class NovelCool(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "novel_cool"
    override val nameStrId = R.string.source_name_novel_cool
    override val baseUrl = "https://www.novelcool.com/"
    override val catalogUrl = "https://www.novelcool.com/category/latest.html"
    override val language = LanguageCode.ENGLISH
    override val iconUrl = "https://www.novelcool.com/favicon.ico"

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".chapter-title")?.text()
                ?: doc.selectFirst("h1")?.text()
                ?: doc.selectFirst("title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            val contentEl = doc.selectFirst(".chapter-content")
                ?: doc.selectFirst("#chapter-content")
                ?: doc.selectFirst(".read-content")
                ?: doc.selectFirst("#content")
                ?: doc.selectFirst(".reading-content")
                ?: doc.selectFirst("article.content")
                ?: throw NoSuchElementException("Chapter content not found on reader page")
            contentEl.select("script").remove()
            contentEl.select("style").remove()
            contentEl.select("iframe").remove()
            contentEl.select(".ads").remove()
            contentEl.select(".share-buttons").remove()
            contentEl.select(".pager").remove()
            TextExtractor.get(contentEl)
        }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            doc.selectFirst("img.bookinfo-pic-img[src]")?.attr("src")
                ?: doc.selectFirst(".bookinfo-pic img[src]")?.attr("src")
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            val desc = doc.selectFirst("div.bk-summary-txt")
                ?: doc.selectFirst("div.bk-summary")
                ?: return@tryConnect null
            desc.select("h3, h4, .bk-summary-title").remove()
            TextExtractor.get(desc).trim()
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            doc.select("a[href*='/chapter/']")
                .mapNotNull { a ->
                    val href = a.attr("href").ifBlank { return@mapNotNull null }
                    val title = (a.attr("title").ifBlank { a.text() })
                        .replace("\n", " ")
                        // ponytail: was inline Regex() — compiled per chapter. Hoisted to companion val.
                        .replace(WHITESPACE_REGEX, " ")
                        .trim()
                        .ifBlank { return@mapNotNull null }

                    ChapterResult(
                        title = title,
                        url = URI(baseUrl).resolve(href).toString()
                    )
                }
                .distinctBy { it.url }
                .reversed()
        }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect("index=$index") {
            val page = index + 1
            val url = catalogUrl
                .toUrlBuilderSafe()
                .apply { if (page > 1) add("page", page.toString()) }

            val doc = networkClient.get(url).toDocument()
            parseBooks(doc, index)
        }
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank()) {
                return@tryConnect PagedList.createEmpty(index = index)
            }

            val page = index + 1
            val url = baseUrl
                .toUrlBuilderSafe()
                .addPath("search")
                .add("searchkey", input)
                .apply { if (page > 1) add("page", page.toString()) }

            val doc = networkClient.get(url).toDocument()
            parseBooks(doc, index)
        }
    }

    private fun parseBooks(doc: Document, index: Int): PagedList<BookResult> {
        val books = doc.select("a[href*='/novel/']")
            .mapNotNull { a ->
                val href = a.attr("href").ifBlank { return@mapNotNull null }
                val title = a.attr("title").ifBlank {
                    a.selectFirst("h2, h3, h4, .title, .book-name")?.text()
                } ?: a.text().trim().ifBlank { return@mapNotNull null }
                if (title.length < 2) return@mapNotNull null

                val cover = a.selectFirst("img[src]")?.attr("src")?.let { c ->
                    when {
                        c.startsWith("http") -> c
                        c.startsWith("/") -> URI(baseUrl).resolve(c).toString()
                        else -> ""
                    }
                } ?: ""

                BookResult(
                    title = title.trim(),
                    url = URI(baseUrl).resolve(href).toString(),
                    coverImageUrl = cover
                )
            }
            .distinctBy { it.url }

        val isLast = (doc.selectFirst("a.next[href]") == null) &&
                     (doc.selectFirst(".pagination a[rel=next]") == null)

        return PagedList(
            list = books,
            index = index,
            isLastPage = isLast || books.isEmpty()
        )
    }

    // ponytail: hoisted from inline Regex() in getChapterList — compiled per chapter title.
    private val WHITESPACE_REGEX = Regex("\\s+")
}
