package my.noveldokusha.network

import android.util.Log
import org.jsoup.nodes.Document

/**
 * Парсит HTML-документ на наличие JS-редиректов (window.location, meta refresh)
 * и возвращает целевой URL, если найден.
 *
 * Некоторые сайты-прокладки (например readnovel.site) отдают HTML с "Loading..."
 * и JS-редиректом на реальный сайт. OkHttp не выполняет JS, поэтому нужно
 * извлекать URL редиректа вручную.
 */
object JsRedirectResolver {

    private const val TAG = "JsRedirectResolver"

    /**
     * Ищет URL редиректа в HTML-документе.
     * Проверяет: meta refresh, window.location, window.location.href, window.location.replace
     *
     * @param doc Jsoup Document страницы
     * @return URL для редиректа или null, если не найден
     */
    fun resolveRedirectUrl(doc: Document): String? {
        val html = doc.outerHtml()

        // 1. Meta refresh (самый простой случай)
        val metaRefresh = doc.select("meta[http-equiv=refresh]").first()
        if (metaRefresh != null) {
            val content = metaRefresh.attr("content")
            val urlMatch = Regex("""url\s*=\s*['"]?(https?://[^'">\s]+)""", RegexOption.IGNORE_CASE)
                .find(content)
            if (urlMatch != null) {
                val url = urlMatch.groupValues[1]
                Log.d(TAG, "Found meta refresh redirect: $url")
                return url
            }
        }

        // 2. window.location.href = "..." или window.location = "..."
        val locationPatterns = listOf(
            Regex("""window\.location\.href\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""window\.location\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""window\.location\.replace\s*\(\s*['"]([^'"]+)['"]"""),
            Regex("""location\.href\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""location\s*=\s*['"]([^'"]+)['"]"""),
        )

        for (pattern in locationPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                var url = match.groupValues[1]
                // Если URL относительный — превращаем в абсолютный
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("//")) {
                    try {
                        val baseUri = doc.location()
                        if (baseUri.isNotEmpty()) {
                            val base = java.net.URI(baseUri)
                            url = if (url.startsWith("/")) {
                                "${base.scheme}://${base.host}$url"
                            } else {
                                val parent = baseUri.substringBeforeLast("/")
                                "$parent/$url"
                            }
                        }
                    } catch (_: Exception) {
                        // Если не удалось — оставляем как есть
                    }
                }
                // Если URL начинается с // — добавляем протокол
                if (url.startsWith("//")) {
                    try {
                        val baseUri = doc.location()
                        if (baseUri.isNotEmpty()) {
                            val scheme = java.net.URI(baseUri).scheme
                            url = "$scheme:$url"
                        }
                    } catch (_: Exception) {
                        url = "https:$url"
                    }
                }
                Log.d(TAG, "Found JS redirect: $url")
                return normalizeUrl(url)
            }
        }

        // 3. Поиск в script-тегах через регулярку по всему HTML
        val scriptPattern = Regex("""(?:window\.)?location(?:\.href)?\s*=\s*['"]([^'"]+)['"]""")
        val scriptMatch = scriptPattern.find(html)
        if (scriptMatch != null) {
            val url = scriptMatch.groupValues[1]
            Log.d(TAG, "Found script redirect: $url")
            return normalizeUrl(url)
        }

        return null
    }

    /**
     * Нормализует URL: заменяет экранированные слеши \/ на /,
     * удаляет лишние пробелы.
     */
    private fun normalizeUrl(url: String): String {
        return url.replace("\\/", "/").trim()
    }
}