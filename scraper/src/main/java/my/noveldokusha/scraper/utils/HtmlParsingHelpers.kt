package my.noveldokusha.scraper.utils

import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.configs.SelectorRule
import my.noveldokusha.scraper.configs.SelectorValue
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * HTML parsing helper functions for scraper operations
 */

/**
 * Extract text from a selector, returns null if not found
 */
fun Document.selectText(selector: String): String? =
    if (selector.isBlank()) null else selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }

/**
 * Extract attribute value from a selector, returns null if not found
 */
fun Document.selectAttr(selector: String, attr: String): String? =
    if (selector.isBlank()) null else selectFirst(selector)?.attr(attr)?.trim()?.takeIf { it.isNotBlank() }

/**
 * Extract list of elements matching selector
 */
fun Document.selectList(selector: String): List<Element> =
    if (selector.isBlank()) emptyList() else select(selector)

/**
 * Extract text from a selector on an Element, returns null if not found
 */
fun Element.selectText(selector: String): String? =
    if (selector.isBlank()) null else selectFirst(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }

/**
 * Extract attribute value from a selector on an Element, returns null if not found
 */
fun Element.selectAttr(selector: String, attr: String): String? =
    if (selector.isBlank()) null else selectFirst(selector)?.attr(attr)?.trim()?.takeIf { it.isNotBlank() }

/**
 * Extract list of elements matching selector on an Element
 */
fun Element.selectList(selector: String): List<Element> =
    if (selector.isBlank()) emptyList() else select(selector)

/**
 * Extract chapter text from document (fallback logic)
 */
fun Document.extractChapterText(): String = when {
    // Try common chapter content selectors
    selectText("#chapter-content, #chr-content, .chapter-content, .reading-content") != null ->
        selectText("#chapter-content, #chr-content, .chapter-content, .reading-content") ?: ""

    // Try to extract from body if no specific selector works
    else -> selectFirst("body")?.let { TextExtractor.get(it) } ?: ""
}

/**
 * Select first element matching any of the comma-separated selectors
 */
fun Document.selectFirstFallback(selector: String): org.jsoup.nodes.Element? {
    if (selector.isBlank()) return null
    val selectors = selector.split(",").map { it.trim() }
    for (sel in selectors) {
        if (sel.isBlank()) continue
        val element = selectFirst(sel)
        if (element != null) return element
    }
    return null
}

/**
 * Select first element matching any of the comma-separated selectors on an Element
 */
fun org.jsoup.nodes.Element.selectFirstFallback(selector: String): org.jsoup.nodes.Element? {
    if (selector.isBlank()) return null
    val selectors = selector.split(",").map { it.trim() }
    for (sel in selectors) {
        if (sel.isBlank()) continue
        val element = selectFirst(sel)
        if (element != null) return element
    }
    return null
}

/**
 * Extract text from fallback selectors on an Element
 */
fun org.jsoup.nodes.Element.selectTextFallback(selector: String): String? {
    if (selector.isBlank()) return null
    return selectFirstFallback(selector)?.text()?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * Extract attribute from fallback selectors on an Element
 */
fun org.jsoup.nodes.Element.selectAttrFallback(selector: String, attr: String): String? {
    if (selector.isBlank()) return null
    return selectFirstFallback(selector)?.attr(attr)?.trim()?.takeIf { it.isNotBlank() }
}

/**
 * Check if this is the last page based on pagination selector
 */
fun Document.isLastPage(selector: String): Boolean {
    if (selector.isBlank()) return false
    val paginationElement = selectFirst(selector)
    return paginationElement == null ||
           paginationElement.hasClass("disabled") ||
           paginationElement.hasClass("active")
}

// Type-safe extraction functions using SelectorRule

/**
 * Extract text content using a SelectorRule
 */
fun Element.extractText(rule: SelectorRule): String? {
    require(rule.value == SelectorValue.TEXT)
    return rule.selectors.firstNotNullOfOrNull { selector ->
        selectFirst(selector)?.let { element ->
            // Apply context transforms to modify the element before extracting text
            rule.contextTransforms.forEach { transform ->
                transform(element, "") // Modify element, ignore returned text
            }
            TextExtractor.get(element)
        }
    }
}

/**
 * Extract HTML content using a SelectorRule
 */
fun Element.extractHtml(rule: SelectorRule): String? {
    require(rule.value == SelectorValue.HTML)
    return rule.selectors.firstNotNullOfOrNull { selectFirst(it)?.html() }
}

/**
 * Extract attribute value using a SelectorRule
 */
fun Element.extractAttr(rule: SelectorRule): String? {
    require(rule.value == SelectorValue.ATTR)
    return rule.selectors.firstNotNullOfOrNull { selector ->
        selectAttr(selector, rule.attr!!)
    }
}

/**
 * Extract elements using a SelectorRule
 */
fun Element.extractElements(rule: SelectorRule): List<Element> {
    require(rule.value == SelectorValue.ELEMENT)
    return if (rule.all) {
        rule.selectors.flatMap { select(it) }
    } else {
        rule.selectors.firstNotNullOfOrNull { selectFirst(it) }?.let { listOf(it) } ?: emptyList()
    }
}

/**
 * Extract value using a SelectorRule (universal function for any selector type)
 * Applies transformations and context transforms
 */
fun Element.extractValue(rule: SelectorRule): String? {
    return when (rule.value) {
        SelectorValue.TEXT -> extractText(rule)
        SelectorValue.ATTR -> extractAttr(rule)
        SelectorValue.HTML -> extractHtml(rule)
        SelectorValue.ELEMENT -> extractElements(rule).firstOrNull()?.text()
    }?.let { applyTransforms(this, it, rule) }
}

/**
 * Apply element-modifying context transforms before text extraction
 */
private fun applyElementTransforms(element: Element, contextTransforms: List<(Element, String) -> String>): Element {
    // Apply all transforms to the same element instance without cloning
    contextTransforms.forEach { transform ->
        transform(element, "")  // Modify the original element directly
    }
    return element  // Return the modified element
}

/**
 * Apply regular (text-only) transformations
 */
private fun applyRegularTransforms(text: String, rule: SelectorRule): String {
    var result = text

    // Apply regular transforms
    for (transform in rule.transforms) {
        result = transform(result)
    }

    return result
}

/**
 * Apply regular transformations to extracted text
 */
private fun applyTransforms(@Suppress("UNUSED_PARAMETER") element: Element, text: String, rule: SelectorRule): String {
    var result = text

    // Apply regular transforms
    for (transform in rule.transforms) {
        result = transform(result)
    }

    return result
}
