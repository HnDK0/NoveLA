package my.noveldokusha.scraper.utils

import com.google.gson.JsonObject
import my.noveldokusha.core.PagedList
import my.noveldokusha.scraper.domain.BookResult
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber

/**
 * Catalog parsing helper functions for scraper operations
 */

/**
 * Get attribute value with priority - check all elements for primary attr, then secondary
 */
private fun getAttributePriority(elements: org.jsoup.select.Elements, primaryAttr: String, secondaryAttr: String): String? {
    // First try to find element with primary attribute
    val elementWithPrimary = elements.firstOrNull { it.hasAttr(primaryAttr) }
    if (elementWithPrimary != null) {
        return elementWithPrimary.attr(primaryAttr)
    }

    // Then try secondary attribute
    val elementWithSecondary = elements.firstOrNull { it.hasAttr(secondaryAttr) }
    return elementWithSecondary?.attr(secondaryAttr)
}

/**
 * Parse HTML catalog page into PagedList<BookResult>
 */
suspend fun parseHtmlCatalogPage(
    doc: Document,
    index: Int,
    itemSelector: String,
    titleSelector: String,
    urlSelector: String,
    coverSelector: String,
    lastPageSelector: String?,
    baseUrl: String? = null
): PagedList<BookResult> {
    if (itemSelector.isBlank()) {
        Timber.w("Catalog parsing: itemSelector is blank")
        return PagedList.createEmpty(index)
    }

    val itemElements = doc.selectList(itemSelector)
    if (itemElements.isEmpty()) {
        Timber.w("Catalog parsing: No items found with selector '$itemSelector'")
    }

    val books = itemElements.mapNotNull { element ->
        if (titleSelector.isBlank()) {
            Timber.w("Catalog parsing: titleSelector is blank")
            return@mapNotNull null
        }

        val title = element.selectTextFallback(titleSelector)
        if (title.isNullOrBlank()) {
            Timber.w("Catalog parsing: Title is empty with selector '$titleSelector' in element")
            return@mapNotNull null
        }

        if (urlSelector.isBlank()) {
            Timber.w("Catalog parsing: urlSelector is blank for title '$title'")
            return@mapNotNull null
        }

        val url = element.selectAttrFallback(urlSelector, "href")
        if (url.isNullOrBlank()) {
            Timber.w("Catalog parsing: URL is empty with selector '$urlSelector' in element with title '$title'")
            return@mapNotNull null
        }

        // Extract cover URL - special handling for NovelBin
        var cover = if (coverSelector == "transform") {
            // Use transformCoverUrl for cover generation
            "transform"
        } else if (coverSelector == "img[data-src], img[src]") {
            // NovelBin style - check all img elements with priority
            val imgElements = element.select("img")
            getAttributePriority(imgElements, "src", "data-src") ?: ""
        } else {
            val coverElement = element.selectFirst(coverSelector)
            // Сначала ПРИОРИТЕТ на data-src, потом src
            coverElement?.attr("data-src").takeIf { !it.isNullOrBlank() }
                ?: coverElement?.attr("src")
                ?: ""
        }

        // Transform cover URLs using standard transformers
        cover = my.noveldokusha.scraper.utils.UrlTransformers.novelBinCatalogCoverUrl()(cover, "")
        cover = my.noveldokusha.scraper.utils.UrlTransformers.readNovelFullCoverUrl()(cover, "")

        // Normalize relative URLs to absolute if baseUrl provided
        if (baseUrl != null && cover.isNotBlank() && !cover.startsWith("http")) {
            cover = if (cover.startsWith("//")) {
                "https:$cover"
            } else {
                "$baseUrl${if (cover.startsWith("/")) "" else "/"}$cover"
            }
        }

        BookResult(
            title = title,
            url = url,
            coverImageUrl = cover
        )
    }

    val isLastPage = when {
        // Special case for NovelBin - check if next button is disabled
        lastPageSelector == null && doc.selectFirst("ul.pagination li.next.disabled") != null -> true
        lastPageSelector != null -> doc.isLastPage(lastPageSelector)
        else -> books.isEmpty()
    }
    return PagedList(list = books, index = index, isLastPage = isLastPage)
}

/**
 * Parse JSON catalog response into PagedList<BookResult>
 */
suspend fun parseJsonCatalogPage(
    json: JsonObject,
    index: Int,
    dataArrayKey: String,
    titleKeys: List<String>,
    urlKey: String,
    coverKey: String,
    hasNextPageKey: String,
    baseUrl: String
): PagedList<BookResult> {
    val data = json.getAsJsonArray(dataArrayKey)
    val hasNextPage = json.get(hasNextPageKey)?.asBoolean ?: false

    val books = data.mapNotNull { element ->
        val obj = element.asJsonObject

        // Parse title from multiple possible keys
        val title = titleKeys.firstNotNullOfOrNull { obj.get(it)?.asString }

        // Parse URL - support nested keys with "." separator
        val slug = parseJsonValue(obj, urlKey)

        // Parse cover - support nested keys with "." separator
        val cover = parseJsonValue(obj, coverKey)

        if (title == null || slug == null) return@mapNotNull null
        BookResult(title = title, url = "$baseUrl$slug", coverImageUrl = cover ?: "")
    }

    return PagedList(list = books, index = index, isLastPage = !hasNextPage)
}

/**
 * Parse JSON value supporting nested keys with "." separator
 */
private fun parseJsonValue(obj: JsonObject, key: String): String? {
    val parts = key.split(".")
    var current: com.google.gson.JsonElement = obj

    for (part in parts) {
        when (current) {
            is JsonObject -> {
                current = current.get(part) ?: return null
            }
            else -> return null
        }
    }

    return when (current) {
        is com.google.gson.JsonPrimitive -> current.asString
        else -> null
    }
}
