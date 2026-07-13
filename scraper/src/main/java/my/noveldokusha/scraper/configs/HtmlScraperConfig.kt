package my.noveldokusha.scraper.configs

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.CloudflareConfig

/**
 * Configuration for HTML-based scrapers.
 * Defines all selectors and settings for parsing HTML novel sites.
 */
data class HtmlScraperConfig(
    // Basic source properties
    val baseUrl: String,
    val language: LanguageCode,

    // Catalog selectors
    val catalogItemSelector: String,
    val catalogTitleSelector: String,
    val catalogUrlSelector: String,
    val catalogCoverSelector: String,
    val catalogLastPageSelector: String?,

    // Search selectors (can be same as catalog or different)
    val searchItemSelector: String?,
    val searchTitleSelector: String?,
    val searchUrlSelector: String?,
    val searchCoverSelector: String?,

    // Book page selectors
    val bookCoverSelector: String,
    val bookDescriptionSelector: String,

    // Chapter selectors
    val chapterListSelector: String,
    val chapterContentSelector: String,
    val chapterTitleSelector: String?,

    // Content cleaning selectors
    val removeSelectors: List<String>,

    // POST search configuration
    val postSearchEnabled: Boolean,
    val postSearchUrl: String?,
    val postSearchDataBuilder: ((String) -> Map<String, String>)?,
    val searchHeaders: Map<String, String> = emptyMap(),

    // Encoding support
    val charset: String? = null,

    // Chapter pagination configuration
    val chapterPaginationType: ChapterPaginationType,
    val chapterPaginationConfig: ChapterPaginationConfig?,

    // AJAX chapter loading configuration (for AJAX_BASED pagination)
    val ajaxChapterListProvider: (suspend (bookUrl: String, networkClient: my.noveldokusha.network.NetworkClient) -> List<my.noveldokusha.scraper.domain.ChapterResult>)? = null,

    // Cloudflare bypass configuration
    val cloudflareConfig: CloudflareConfig? = null,

    // URL builders
    val buildCatalogUrl: (Int) -> String,
    val buildSearchUrl: (Int, String) -> String,

    // URL transformers
    val transformBookUrl: (String) -> String,
    val transformChapterUrl: (String) -> String,
    val transformCoverUrl: (String, String) -> String = { coverUrl, _ -> coverUrl }
)
