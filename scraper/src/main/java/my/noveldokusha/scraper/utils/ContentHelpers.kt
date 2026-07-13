package my.noveldokusha.scraper.utils

import my.noveldokusha.scraper.TextExtractor
import org.jsoup.nodes.Element

/**
 * Content processing helper functions for scraper operations
 */

/**
 * Clean chapter content by removing unwanted elements and extracting readable text
 */
fun Element.cleanChapterContent(removeSelectors: List<String> = emptyList()): String {
    // Always remove common unwanted elements
    select("script").remove()
    select(".ads, .advertisement, .hidden").remove()

    // Remove site-specific selectors
    removeSelectors.forEach { selector ->
        select(selector).remove()
    }

    // Extract clean text
    return TextExtractor.get(this)
}
