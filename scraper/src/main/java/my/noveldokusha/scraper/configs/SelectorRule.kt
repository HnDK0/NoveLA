package my.noveldokusha.scraper.configs

import org.jsoup.nodes.Element

/**
 * Declarative selector types for HTML scraping.
 * Replaces string-based selectors with type-safe rules.
 */
enum class SelectorValue {
    TEXT,    // extract text content
    HTML,    // extract inner HTML
    ATTR,    // extract attribute value
    ELEMENT  // extract elements (for containers/lists)
}

/**
 * Declarative selector rule with fallback support and transformations.
 *
 * @param selectors List of CSS selectors to try in order (first match wins)
 * @param value What to extract from the matched element
 * @param attr Attribute name (required for ATTR type)
 * @param all Whether to extract all matching elements (for ELEMENT type)
 * @param transforms List of text transformations to apply after extraction
 * @param contextTransforms List of context-aware transformations (have access to Element)
 * @param filters List of element filters to apply before extraction
 */
data class SelectorRule(
    val selectors: List<String>,
    val value: SelectorValue = SelectorValue.TEXT,
    val attr: String? = null,
    val all: Boolean = false,
    val transforms: List<(String) -> String> = emptyList(),
    val contextTransforms: List<(Element, String) -> String> = emptyList(),
    val filters: List<(Element) -> Boolean> = emptyList()
) {
    init {
        require(selectors.isNotEmpty()) { "selectors list cannot be empty" }
        if (value == SelectorValue.ATTR) {
            requireNotNull(attr) { "attr must be specified for ATTR selector" }
        }
    }
}

// Convenience factories for creating selector rules

/**
 * Create a text selector rule
 */
fun text(vararg css: String) = SelectorRule(css.toList(), SelectorValue.TEXT)

/**
 * Create an HTML selector rule
 */
fun html(vararg css: String) = SelectorRule(css.toList(), SelectorValue.HTML)

/**
 * Create an attribute selector rule
 */
fun attr(attr: String, vararg css: String) = SelectorRule(css.toList(), SelectorValue.ATTR, attr)

/**
 * Create an elements selector rule (returns all matching elements)
 */
fun elements(vararg css: String) = SelectorRule(css.toList(), SelectorValue.ELEMENT, all = true)

// Extension functions for adding transformations

/**
 * Add a text transformation to the selector rule
 */
fun SelectorRule.transform(transform: (String) -> String): SelectorRule =
    copy(transforms = transforms + transform)

/**
 * Add a context-aware transformation to the selector rule
 */
fun SelectorRule.contextTransform(transform: (Element, String) -> String): SelectorRule =
    copy(contextTransforms = contextTransforms + transform)

/**
 * Add an element filter to the selector rule
 */
fun SelectorRule.filter(predicate: (Element) -> Boolean): SelectorRule =
    copy(filters = filters + predicate)
