package my.noveldokusha.core.utils

import org.json.JSONArray
import java.net.URI

/**
 * Normalize a book URL for consistent identification (Book.url primary key,
 * reader/chapter-list lookups, deduplication of duplicate books).
 *
 * Conservative on purpose:
 *  - Scheme → lowercase
 *  - Host → lowercase
 *  - Trailing slash removed from path (unless path is just "/")
 *  - Query and fragment preserved as-is (not re-encoded)
 *
 * `local://` URLs are returned unchanged.
 */
fun normalizeBookUrl(url: String): String {
    if (url.startsWith("local://")) return url
    return try {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase() ?: return url
        val host = uri.host?.lowercase() ?: return url
        val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""
        "$scheme://$host$path$query$fragment"
    } catch (_: Exception) {
        url
    }
}

/**
 * Build a referer header value from a page URL.
 * Returns `"scheme://host/"` or empty string on parse failure.
 */
fun refererFor(url: String): String = try {
    val uri = URI(url)
    "${uri.scheme}://${uri.host}/"
} catch (_: Exception) {
    ""
}

fun encodePages(pages: List<String>): String = JSONArray(pages).toString()

fun decodePages(json: String): List<String>? = try {
    val arr = JSONArray(json)
    (0 until arr.length()).map { arr.getString(it) }
} catch (e: Exception) {
    null
}
