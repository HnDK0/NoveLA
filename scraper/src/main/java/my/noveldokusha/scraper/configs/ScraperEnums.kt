package my.noveldokusha.scraper.configs

/**
 * Pagination types for chapter lists
 */
enum class ChapterPaginationType {
    NONE,           // Single page of chapters
    PAGE_BASED,     // Multiple pages (AllNovel/NovelFull style)
    AJAX_BASED,     // AJAX loading (ScribbleHub style)
    CUSTOM          // Custom pagination logic (FanMTL style)
}

/**
 * Configuration for chapter pagination
 */
data class ChapterPaginationConfig(
    val maxPageExtractor: (org.jsoup.nodes.Document) -> Int,
    val pageUrlBuilder: (String, Int) -> String,
    val chapterSelector: String
)
