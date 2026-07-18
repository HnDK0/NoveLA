package my.noveldokusha.scraper.configs

import com.google.gson.JsonObject
import my.noveldokusha.core.LanguageCode

/**
 * Configuration for JSON API-based scrapers.
 * Defines all settings for parsing JSON API novel sites.
 */
data class JsonApiScraperConfig(
    val baseUrl: String,
    val apiBaseUrl: String,
    val language: LanguageCode,
    val siteId: String?,
    val headers: Map<String, String>,

    // Catalog parsing keys
    val catalogDataKey: String,
    val catalogTitleKeys: List<String>,
    val catalogUrlKey: String,
    val catalogCoverKey: String,
    val catalogHasNextKey: String,

    // Search parsing keys (can be different from catalog)
    val searchDataKey: String?,
    val searchTitleKeys: List<String>?,
    val searchUrlKey: String?,
    val searchCoverKey: String?,
    val searchHasNextKey: String?,

    // POST search configuration
    val postSearchEnabled: Boolean,
    val postSearchUrl: String?,
    val postSearchDataBuilder: ((String) -> Map<String, String>)?,

    // URL builders
    val buildCatalogUrl: (page: Int) -> String,
    val buildSearchUrl: (page: Int, query: String) -> String,
    val buildBookUrl: (slug: String) -> String,
    val buildChapterListUrl: (slug: String) -> String,
    val buildChapterUrl: (slug: String, chapter: Map<String, Any>) -> String,

    // Data parsers
    val parseBookData: (JsonObject) -> BookData,
    val parseChapterData: (JsonObject, String?) -> List<my.noveldokusha.scraper.domain.ChapterResult>,
    val parseChapterContent: (JsonObject) -> String
)

/**
 * Parsed book data from JSON API
 */
data class BookData(
    val title: String,
    val cover: String?,
    val description: String?
)

/**
 * Parsed chapter data from JSON API
 */
data class ChapterData(
    val title: String,
    val url: String,
    val number: String
)
