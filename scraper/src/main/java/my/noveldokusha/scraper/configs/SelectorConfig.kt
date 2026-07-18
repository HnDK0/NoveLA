package my.noveldokusha.scraper.configs

/**
 * Selector configurations for different scraping operations.
 * Groups related selectors together for better organization.
 */

/**
 * Common interface for selectors that have item/title/url/cover structure
 */
interface ItemSelectors {
    val item: SelectorRule
    val title: SelectorRule
    val url: SelectorRule
    val cover: SelectorRule
}

/**
 * Selectors for catalog/book list pages
 */
data class CatalogSelectors(
    override val item: SelectorRule,    // container elements for each book
    override val title: SelectorRule,   // title within each item
    override val url: SelectorRule,     // URL within each item
    override val cover: SelectorRule    // cover image within each item
) : ItemSelectors

/**
 * Selectors for search result pages
 * Can be same as catalog or different - allows separate configuration
 */
data class SearchSelectors(
    override val item: SelectorRule,    // container elements for each search result
    override val title: SelectorRule,   // title within each result
    override val url: SelectorRule,     // URL within each result
    override val cover: SelectorRule    // cover image within each result
) : ItemSelectors

/**
 * Selectors for individual book detail pages
 */
data class BookSelectors(
    val title: SelectorRule?,       // book title (optional)
    val cover: SelectorRule,       // book cover image
    val description: SelectorRule,  // book description text
    val latestChapterHash: SelectorRule? = null  // selector for quick chapter list change detection
)

/**
 * Selectors for chapter-related operations
 */
data class ChapterSelectors(
    val list: SelectorRule,        // chapter list items
    val content: SelectorRule,     // chapter content
    val title: SelectorRule?       // chapter title (optional)
)

/**
 * Selector for detecting chapter list changes.
 * Used for quick check before loading full chapter list.
 * Should point to element that changes when new chapters are added:
 * - Last chapter title/number
 * - Total chapters count
 * - Latest chapter date
 * 
 * Example: "div.chapters-list .chapter-item:last-child .chapter-title"
 */
data class LatestChapterSelector(
    val selector: String,  // CSS selector for the element
    val attribute: String? = null,  // Optional: attribute to extract (null = text content)
    val transform: (String) -> String = { it }  // Optional transform function
)
