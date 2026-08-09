package my.noveldokusha.core

import java.net.URLEncoder

/**
 * Качество картинок страничных глав (манхва/манга). Реализовано как
 * app-side переписывание URL через weserv (та же схема, что dataSaver в
 * Tachiyomi-подобных читалках) — плагины отдают оригинальные CDN URL.
 */
enum class ImageQuality {
    HIGH, BALANCED, DATA_SAVER, LOW;

    companion object {
        fun parse(value: String?): ImageQuality = when (value) {
            "balanced" -> BALANCED
            "saver" -> DATA_SAVER
            "low" -> LOW
            else -> HIGH
        }
    }
}

/**
 * HIGH: оригинальный URL без изменений.
 * BALANCED: weserv q=80, ширина ~1280px (примерно -50% трафика).
 * DATA_SAVER: weserv q=60, ширина ~800px (в 4-5 раз меньше данных).
 * LOW: weserv q=50, ширина ~480px — минимальный размер и
 * максимальная скорость загрузки (в 8-10 раз меньше оригинала).
 */
fun rewritePageUrlForQuality(url: String, quality: ImageQuality): String = when (quality) {
    ImageQuality.HIGH -> url
    ImageQuality.BALANCED -> weservUrl(url, quality = 80, width = 1280)
    ImageQuality.DATA_SAVER -> weservUrl(url, quality = 60, width = 800)
    ImageQuality.LOW -> weservUrl(url, quality = 50, width = 480)
}

private fun weservUrl(url: String, quality: Int, width: Int): String =
    "https://images.weserv.nl/?url=${URLEncoder.encode(url, "UTF-8")}" +
        "&w=$width&q=$quality&maxage=2592000"
