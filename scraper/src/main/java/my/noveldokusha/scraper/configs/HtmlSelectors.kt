package my.noveldokusha.scraper.configs

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.network.CloudflareConfig
import my.noveldokusha.scraper.domain.BookResult

/**
 * New declarative configuration for HTML-based scrapers.
 * Replaces HtmlScraperConfig with type-safe selector rules.
 */
data class HtmlSelectors(
    // Basic source properties
    val baseUrl: String,
    val language: LanguageCode,
    val charset: String? = null,

    // Declarative selectors
    val catalog: CatalogSelectors,
    val search: SearchSelectors? = null, // fallback to catalog if null
    val book: BookSelectors,
    val chapters: ChapterSelectors,



    // Pagination and advanced features
    val chapterPaginationType: ChapterPaginationType = ChapterPaginationType.NONE,
    val chapterPaginationConfig: ChapterPaginationConfig? = null,
    val ajaxChapterListProvider: (suspend (bookUrl: String, networkClient: my.noveldokusha.network.NetworkClient) -> List<my.noveldokusha.scraper.domain.ChapterResult>)? = null,

    // Chapters order
    val reverseChapters: Boolean = false,

    // Search configuration
    val postSearchEnabled: Boolean = false,
    val postSearchUrl: String? = null,
    val postSearchDataBuilder: ((String) -> Map<String, String>)? = null,
    val searchHeaders: Map<String, String> = emptyMap(),
    val postSearchUseRawBody: Boolean = false,  // Use Jsoup to preserve pre-encoded values (for GBK etc.)

    // Cloudflare bypass
    val cloudflareConfig: CloudflareConfig? = null,

    // Search pagination - true if search returns all results in single page (no pagination)
    val searchNoPagination: Boolean = false,

    // URL builders (unchanged)
    val buildCatalogUrl: (Int) -> String,
    val buildSearchUrl: (Int, String) -> String,

    // Custom search provider for complex search pagination (e.g., FanMTL with searchid)
    val customSearchProvider: (suspend (String, Int, my.noveldokusha.network.NetworkClient) -> PagedList<BookResult>)? = null,

    // URL transformers (unchanged)
    val transformBookUrl: (String) -> String = { it },
    val transformChapterUrl: (String) -> String = { it },
    val transformCoverUrl: (String, String) -> String = { cover, _ -> cover }
)
