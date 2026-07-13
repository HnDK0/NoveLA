package my.noveldokusha.scraper.configs

import my.noveldokusha.scraper.TextExtractor
import org.jsoup.nodes.Element
import timber.log.Timber
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Common text transformations for SelectorRule
 */

// Unicode normalization - uses NFKC to convert compatibility characters
// (like mathematical bold/italic letters) to their standard equivalents
fun SelectorRule.normalizeUnicode(): SelectorRule = transform { text ->
    Normalizer.normalize(text, Normalizer.Form.NFKC)
}

// Trim whitespace
fun SelectorRule.trim(): SelectorRule = transform { it.trim() }

// Clean HTML tags (basic)
fun SelectorRule.cleanHtml(): SelectorRule = transform { text ->
    text.replace(Regex("<[^>]*>"), "").trim()
}

// Remove common ads patterns
fun SelectorRule.removeAds(): SelectorRule = transform { text ->
    text.replace(Regex("(?i)(please|click|visit|support|donate|patreon).*?\\n"), "")
        .replace(Regex("(?i)(ads|advertisement|promo).*?\\n"), "")
        .trim()
}

// Remove hidden content by CSS analysis (RoyalRoad-style)
fun SelectorRule.removeHiddenContent(): SelectorRule = contextTransform { element, text ->
    val doc = element.ownerDocument() ?: return@contextTransform text

    // Find CSS rules that hide elements
    val hiddenSelectors = mutableListOf<String>()
    doc.select("style").forEach { style ->
        val cssText = style.html()
        if (!cssText.isNullOrBlank()) {
            // Match patterns like: .class { display: none; } or .class{display:none;}
            val pattern = Pattern.compile("([.#][\\w-]+)\\s*\\{\\s*[^}]*display\\s*:\\s*none[^}]*\\}")
            val matcher = pattern.matcher(cssText)
            while (matcher.find()) {
                val selector = matcher.group(1)
                if (!selector.isNullOrBlank()) {
                    hiddenSelectors.add(selector)
                }
            }
        }
    }

    // Remove content from hidden elements
    var result = text
    hiddenSelectors.forEach { selector ->
        try {
            val hiddenElements = doc.select(selector)
            hiddenElements.forEach { hidden ->
                val hiddenText = hidden.text()
                if (!hiddenText.isNullOrBlank()) {
                    result = result.replace(hiddenText, "", ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            // Ignore invalid selectors
        }
    }

    result.trim()
}

// Regex replace transformation
fun SelectorRule.regexReplace(pattern: String, replacement: String): SelectorRule =
    transform { text -> text.replace(Regex(pattern), replacement) }

// Conditional transformation
fun SelectorRule.whenCondition(condition: (String) -> Boolean, transform: (String) -> String): SelectorRule =
    transform { text -> if (condition(text)) transform(text) else text }

// Context-aware transformations

/**
 * Universal access to Element and Document context
 */
fun SelectorRule.withContext(transform: (Element, org.jsoup.nodes.Document, String) -> String): SelectorRule =
    contextTransform { element, text ->
        val doc = element.ownerDocument() ?: return@contextTransform text
        transform(element, doc, text)
    }

/**
 * Remove HTML elements by CSS selectors from DOM before extracting text
 */
fun SelectorRule.removeElementsDOM(vararg selectors: String): SelectorRule = contextTransform { element, text ->
    selectors.forEach { selector ->
        try {
            element.select(selector).remove()
        } catch (e: Exception) {
            Timber.w("removeElementsDOM: Failed to remove selector '$selector': ${e.message}")
        }
    }

    // Return the original text (ignored, element is modified)
    text
}

/**
 * Remove text patterns using regular expressions from extracted text
 */
fun SelectorRule.removePatternsTEXT(vararg regexPatterns: String): SelectorRule = transform { text ->
    var result = text
    regexPatterns.forEach { pattern ->
        try {
            result = result.replace(Regex(pattern), "")
        } catch (e: Exception) {
            Timber.w("removePatternsTEXT: Failed to process pattern '$pattern': ${e.message}")
        }
    }
    result
}

/**
 * Основная очистка контента.
 */
fun SelectorRule.applyStandardContentTransforms(baseUrl: String): SelectorRule =
    this.normalizeUnicode()
        .regexReplace("(?i)${baseUrl.removePrefix("https://").removePrefix("www.").removeSuffix("/")}.*?\\n", "")
        .regexReplace("(?i)\\A[\\s\\p{Z}\\uFEFF]*((Глава\\s+\\d+|Chapter\\s+\\d+)[^\\n\\r]*[\\n\\r\\s]*)+", "")
        .regexReplace("(?im)^\\s*(Перевод|Переводчик|Редакция|Редактор|Аннотация|Сайт|Источник|Студия|Студия\\\\s+Нёи-Бо|Nyoi-Bo\\\\s+Studio)[:\\s][^\\n\\r]{0,70}(\\r?\\n|$)", "")
        .regexReplace("(?im)^\\s*(Translator|Editor|Proofreader|Read\\s+(at|on|latest))[:\\s][^\\n\\r]{0,70}(\\r?\\n|$)", "")
        .removeHiddenContent()
        .trim()

fun SelectorRule.Clean(): SelectorRule =
    this.normalizeUnicode()
        .regexReplace("""\s+""", " ")
        .trim()

/**
 * Advanced CSS analysis for content cleaning
 */
fun SelectorRule.analyzeCss(): SelectorRule = withContext { _, doc, text ->
    var result = text

    // Find all CSS rules that could hide content
    doc.select("style").forEach { style ->
        val cssText = style.html()
        if (cssText.isNotBlank()) {
            // Extract all CSS rules with display properties
            val displayRules = extractCssDisplayRules(cssText)

            // Find elements matching these selectors and remove their text
            displayRules.forEach { selector ->
                try {
                    val hiddenElements = doc.select(selector)
                    hiddenElements.forEach { hidden ->
                        val hiddenText = hidden.text()
                        if (hiddenText.isNotBlank() && hiddenText.length > 10) {
                            result = result.replace(hiddenText, "", ignoreCase = true)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore invalid selectors
                }
            }
        }
    }

    result.trim()
}

/**
 * Extract metadata from HTML structure
 */
fun SelectorRule.extractMetadata(): SelectorRule = withContext { _, doc, text ->
    val metadata = mutableMapOf<String, String>()

    // Try to extract common metadata
    doc.select("meta[name], meta[property]").forEach { meta ->
        val name = meta.attr("name") ?: meta.attr("property")
        val content = meta.attr("content")
        if (name.isNotBlank() && content.isNotBlank()) {
            metadata[name] = content
        }
    }

    // Extract structured data from JSON-LD
    doc.select("script[type='application/ld+json']").forEach { _ ->
        try {
            // Basic JSON-LD parsing could be added here
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    // If metadata found, append to text
    if (metadata.isNotEmpty()) {
        val metadataText = "\n\n--- Metadata ---\n" +
                metadata.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        text + metadataText
    } else {
        text
    }
}

/**
 * Conditional transformation with context
 */
fun SelectorRule.whenContext(
    condition: (Element, String) -> Boolean,
    transform: (Element, String) -> String
): SelectorRule = contextTransform { element, text ->
    if (condition(element, text)) transform(element, text) else text
}

/**
 * Batch processing of multiple elements
 */
fun SelectorRule.batchTransform(
    batchProcessor: (List<Element>, String) -> String
): SelectorRule = withContext { element, _, text ->
    // This would need access to all matched elements, not just the first one
    // For now, just pass the current element
    batchProcessor(listOf(element), text)
}

// Chain multiple transformations fluently
fun SelectorRule.chain(vararg transforms: (String) -> String): SelectorRule {
    var result = this
    transforms.forEach { transform ->
        result = result.transform(transform)
    }
    return result
}

// Helper functions

/**
 * Extract CSS selectors that have display:none or similar
 */
private fun extractCssDisplayRules(cssText: String): List<String> {
    val selectors = mutableListOf<String>()
    val pattern = Pattern.compile("([^{]+)\\s*\\{[^}]*display\\s*:\\s*(none|hidden)[^}]*\\}")
    val matcher = pattern.matcher(cssText)

    while (matcher.find()) {
        val selectorText = matcher.group(1)?.trim()
        if (!selectorText.isNullOrBlank()) {
            // Split multiple selectors (e.g., ".class1, .class2")
            selectorText.split(",").forEach { selector ->
                selectors.add(selector.trim())
            }
        }
    }

    return selectors.distinct()
}
