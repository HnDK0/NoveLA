package my.noveldokusha.core

import java.net.URLEncoder

/**
 * Качество картинок страничных глав (манхва/манга). Реализовано как
 * app-side переписывание URL через weserv (та же схема, что dataSaver в
 * Tachiyomi-подобных читалках) — плагины отдают оригинальные CDN URL.
 */
enum class ImageQuality {
    HIGH, BALANCED, DATA_SAVER;

    companion object {
        fun parse(value: String?): ImageQuality = when (value) {
            "balanced" -> BALANCED
            "saver" -> DATA_SAVER
            else -> HIGH
        }
    }
}

/**
 * HIGH: оригинальный URL без изменений.
 * BALANCED: weserv q=80, ширина ~1280px (примерно -50% трафика).
 * DATA_SAVER: weserv q=60, ширина ~800px (в 4-5 раз меньше данных).
 */
fun rewritePageUrlForQuality(url: String, quality: ImageQuality): String = when (quality) {
    ImageQuality.HIGH -> url
    ImageQuality.BALANCED -> weservUrl(url, quality = 80, width = 1280)
    ImageQuality.DATA_SAVER -> weservUrl(url, quality = 60, width = 800)
}

private fun weservUrl(url: String, quality: Int, width: Int): String =
    "https://images.weserv.nl/?url=${URLEncoder.encode(url, "UTF-8")}" +
        "&w=$width&q=$quality&maxage=2592000"
