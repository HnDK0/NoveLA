package my.noveldokusha.scraper.utils

import java.net.URI

/**
 * Utility functions for URL transformations commonly used in scrapers
 */
object UrlTransformers {

    /**
     * Standard book URL transformer
     * Handles absolute URLs, protocol-relative URLs, and relative URLs
     */
    fun standardBookUrl(baseUrl: String): (String) -> String = { url ->
        when {
            url.startsWith("http") -> url // Already absolute
            url.startsWith("//") -> "https:$url" // Protocol-relative
            else -> URI(baseUrl).resolve(url).toString() // Relative - resolve against base
        }
    }

    /**
     * Standard chapter URL transformer
     * Same logic as book URL transformer
     */
    fun standardChapterUrl(baseUrl: String): (String) -> String = { url ->
        when {
            url.startsWith("http") -> url // Already absolute
            url.startsWith("//") -> "https:$url" // Protocol-relative
            else -> URI(baseUrl).resolve(url).toString() // Relative - resolve against base
        }
    }

    /**
     * Standard cover URL transformer
     * Handles absolute URLs, protocol-relative URLs, and relative URLs
     * For relative URLs, simply prepends baseUrl (no URI.resolve for images)
     */
    fun standardCoverUrl(baseUrl: String): (String, String) -> String = { coverUrl, _ ->
        when {
            coverUrl.startsWith("http") -> coverUrl // Already absolute
            coverUrl.startsWith("//") -> "https:$coverUrl" // Protocol-relative
            coverUrl.isBlank() -> "" // Empty
            else -> "$baseUrl$coverUrl" // Relative - prepend base URL
        }
    }

    /**
     * Simple cover URL transformer (just prepend base URL for relative paths)
     */
    fun simpleCoverUrl(baseUrl: String): (String, String) -> String = { coverUrl, _ ->
        when {
            coverUrl.startsWith("http") -> coverUrl
            coverUrl.startsWith("//") -> "https:$coverUrl"
            coverUrl.isBlank() -> ""
            else -> "$baseUrl$coverUrl"
        }
    }

    /**
     * URI.resolve based cover URL transformer
     */
    fun resolveCoverUrl(baseUrl: String): (String, String) -> String = { coverUrl, _ ->
        when {
            coverUrl.startsWith("http") -> coverUrl
            coverUrl.startsWith("//") -> "https:$coverUrl"
            coverUrl.isBlank() -> ""
            else -> URI(baseUrl).resolve(coverUrl).toString()
        }
    }

    /**
     * NovelBin-style cover URL transformer
     * Uses NovelBin image service for full covers when source provides only thumbnails
     */
    fun novelBinCoverUrl(): (String, String) -> String = { _, bookUrl ->
        // Extract slug from book URL (e.g., "/cultivation-online-novel.html" -> "cultivation-online-novel")
        val slug = bookUrl.substringAfterLast("/").removeSuffix(".html")
        "https://images.novelbin.me/novel/$slug.jpg"
    }

    fun ttkanCoverUrl(): (String, String) -> String = { _, bookUrl ->
        // bookUrl = "/novel/chapters/qingshan-huishuohuadezhouzi"
        // Извлекаем то, что после последнего слэша: "qingshan-huishuohuadezhouzi"
        val slug = bookUrl.substringAfterLast("/")
        "https://static.ttkan.co/cover/$slug.jpg?w=250&h=300&q=100"
    }

    /**
     * NovelBin catalog cover transformer
     * Replaces thumbnail URLs with full cover URLs
     */
    fun novelBinCatalogCoverUrl(): (String, String) -> String = { coverUrl, _ ->
        when {
            coverUrl.isBlank() -> ""
            coverUrl.contains("novel_200_89") -> coverUrl.replace("novel_200_89", "novel")
            else -> coverUrl
        }
    }

    /**
     * ReadNovelFull cover transformer
     * Replaces thumbnail URLs with full cover URLs
     */
    fun readNovelFullCoverUrl(): (String, String) -> String = { coverUrl, _ ->
        when {
            coverUrl.isBlank() -> ""
            coverUrl.contains("t-200x89") -> coverUrl.replace("t-200x89", "t-300x439")
            else -> coverUrl
        }
    }

    /**
     * Jaomix cover transformer
     * Removes -150x150 suffix from cover URLs to get full size images
     */
    fun jaomixCoverUrl(): (String, String) -> String = { coverUrl, _ ->
        when {
            coverUrl.isBlank() -> ""
            coverUrl.contains("-150x150") -> coverUrl.replace("-150x150", "")
            else -> coverUrl
        }
    }

    /**
     * Image proxy transformer using weserv.nl
     * Useful for sources that need image proxying
     */
    fun weservProxyCoverUrl(): (String, String) -> String = { coverUrl, _ ->
        when {
            coverUrl.isBlank() -> ""
            coverUrl.startsWith("http") -> {
                val trimmed = coverUrl.removePrefix("https://").removePrefix("http://")
                val encoded = java.net.URLEncoder.encode(trimmed, java.nio.charset.StandardCharsets.UTF_8.name())
                "https://images.weserv.nl/?url=$encoded&https=1"
            }
            else -> coverUrl // Already proxied or relative
        }
    }

    /**
     * Combined transformer that tries multiple strategies
     * First tries direct URL, then falls back to proxy
     */
    fun proxiedCoverUrl(proxyService: String = "weserv"): (String, String) -> String = { coverUrl, bookUrl ->
        when {
            coverUrl.isBlank() -> ""
            coverUrl.startsWith("http") -> {
                when (proxyService) {
                    "weserv" -> weservProxyCoverUrl()(coverUrl, bookUrl)
                    else -> coverUrl
                }
            }
            else -> coverUrl
        }
    }
}
