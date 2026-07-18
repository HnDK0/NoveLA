package my.noveldokusha.scraper.utils

import java.net.URI
import java.net.URLEncoder

/**
 * URL processing helper functions for scraper operations
 */

/**
 * Safely build URL by combining base URL and path without double slashes or other issues
 */
fun buildUrl(baseUrl: String?, relativePath: String?): String {
    val base = baseUrl?.trim() ?: ""
    val path = relativePath?.trim() ?: ""

    // If path is an absolute URL, return as-is
    if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("//")) {
        return path
    }

    // If base is empty, return path
    if (base.isEmpty()) return path

    // If path is empty, return base
    if (path.isEmpty()) return base

    // Handle relative paths (../ and ./)
    if (path.startsWith("../") || path.startsWith("./")) {
        return resolveRelativePath(base, path)
    }

    // Split base URL into path part and query/fragment part
    val (basePath, suffix) = splitUrl(base)

    // Remove trailing slash from base path
    val cleanBase = basePath.trimEnd('/')

    // Remove leading slash from path (unless it's just a slash)
    val cleanPath = when {
        path == "/" -> ""
        path.startsWith("/") -> path.substring(1)
        else -> path
    }

    // Combine paths
    val result = when {
        cleanPath.isEmpty() -> cleanBase
        cleanBase.isEmpty() -> cleanPath
        else -> "$cleanBase/$cleanPath"
    }

    // Add suffix back (query params and fragment)
    val finalUrl = if (suffix.isNotEmpty()) "$result$suffix" else result

    // Normalize the final URL to fix double slashes and other issues
    return normalizeUrl(finalUrl)
}

/**
 * Split URL into path part and suffix (query + fragment)
 */
private fun splitUrl(url: String): Pair<String, String> {
    val queryIndex = url.indexOf('?')
    val hashIndex = url.indexOf('#')

    val splitIndex = when {
        queryIndex >= 0 && hashIndex >= 0 -> minOf(queryIndex, hashIndex)
        queryIndex >= 0 -> queryIndex
        hashIndex >= 0 -> hashIndex
        else -> url.length
    }

    return url.substring(0, splitIndex) to url.substring(splitIndex)
}

/**
 * Resolve relative paths like ../ and ./
 */
private fun resolveRelativePath(baseUrl: String, relativePath: String): String {
    val (basePath, suffix) = splitUrl(baseUrl)

    // Split base path into segments
    val baseSegments = basePath.split('/').filter { it.isNotEmpty() }.toMutableList()

    // Process relative path segments
    val relativeSegments = relativePath.split('/')

    for (segment in relativeSegments) {
        when (segment) {
            ".", "" -> continue // current directory, ignore
            ".." -> {
                // go up one level
                if (baseSegments.isNotEmpty()) {
                    baseSegments.removeAt(baseSegments.size - 1)
                }
            }
            else -> {
                // add new segment
                baseSegments.add(segment)
            }
        }
    }

    // Reconstruct path
    val resultPath = when {
        baseSegments.isEmpty() -> "/"
        else -> "/${baseSegments.joinToString("/")}"
    }

    // Add suffix back
    return if (suffix.isNotEmpty()) "$resultPath$suffix" else resultPath
}

/**
 * Build URL with query parameters
 */
fun buildUrlWithQuery(baseUrl: String, path: String, queryParams: Map<String, String>): String {
    val url = buildUrl(baseUrl, path)
    if (queryParams.isEmpty()) return url

    val queryString = queryParams.entries.joinToString("&") { (key, value) ->
        "$key=${java.net.URLEncoder.encode(value, "UTF-8")}"
    }

    return if (url.contains("?")) {
        "$url&$queryString"
    } else {
        "$url?$queryString"
    }
}

/**
 * Extract slug from URL (last path segment)
 */
fun extractSlug(url: String): String? {
    return url.substringAfterLast("/", "")
        .takeIf { it.isNotBlank() }
        ?.substringBefore("?") // Remove query parameters
        ?.substringBefore("#") // Remove hash
}

/**
 * Extract series ID from URL (for sources that use numeric IDs)
 */
fun extractSeriesId(url: String): String? {
    val regex = Regex("series/(\\d+)/")
    return regex.find(url)?.groupValues?.getOrNull(1)
}

/**
 * Normalize URL by fixing double slashes and other common URL issues.
 * This function also encodes query parameter values to handle special characters.
 */
fun normalizeUrl(url: String): String {
    return try {
        // Encode query string parameters if present to handle special characters
        val queryStart = url.indexOf('?')
        val encodedUrl = if (queryStart >= 0) {
            val basePart = url.substring(0, queryStart)
            val queryPart = url.substring(queryStart + 1)

            // Split query into parameters and encode values
            val encodedQuery = queryPart.split("&").joinToString("&") { param ->
                val equalsIndex = param.indexOf('=')
                if (equalsIndex >= 0) {
                    val key = param.substring(0, equalsIndex)
                    val value = param.substring(equalsIndex + 1)
                    "$key=${URLEncoder.encode(value, "UTF-8")}"
                } else {
                    // No value, just key (rare but possible)
                    param
                }
            }
            "$basePart?$encodedQuery"
        } else {
            url
        }

        val uri = URI(encodedUrl)

        // Build normalized URL
        buildString {
            // Protocol
            if (uri.scheme != null) {
                append(uri.scheme.lowercase())
                append("://")
            }

            // Host
            if (uri.host != null) {
                append(uri.host.lowercase())

                // Port
                if (uri.port != -1) {
                    append(":")
                    append(uri.port)
                }
            }

            // Path with double slash handling
            val path = normalizePath(uri.rawPath)
            append(path)

            // Query parameters
            if (uri.rawQuery != null) {
                append("?")
                append(uri.rawQuery)
            }

            // Fragment
            if (uri.fragment != null) {
                append("#")
                append(uri.fragment)
            }
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to normalize URL: $url", e)
    }
}

/**
 * Normalize path - remove double slashes, ensure leading slash
 */
private fun normalizePath(path: String?): String {
    if (path.isNullOrEmpty()) {
        return "/"
    }

    return buildString {
        // Ensure leading slash
        if (!path.startsWith("/")) {
            append("/")
        }

        // Remove double slashes within path
        val normalized = path.replace("/{2,}".toRegex(), "/")
        append(normalized)

        // Remove trailing slash if not root
        if (length > 1 && endsWith("/")) {
            deleteAt(length - 1)
        }
    }
}

/**
 * Normalize a book URL for consistent identification in DownloadManager and Book table.
 *
 * Unlike [normalizeUrl], this function is intentionally conservative:
 * it does NOT re-encode query parameters, because that could change
 * the URL in ways that break matching with scraper output.
 *
 * Transformations applied:
 * - Scheme → lowercase
 * - Host → lowercase
 * - Trailing slash removed from path (unless path is just "/")
 *
 * Fragment (#...) is preserved — some sources use hash-based routing.
 * Query parameters are preserved as-is (not re-encoded).
 *
 * `local://` URLs are returned as-is.
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
        url // fallback — leave untouched
    }
}
