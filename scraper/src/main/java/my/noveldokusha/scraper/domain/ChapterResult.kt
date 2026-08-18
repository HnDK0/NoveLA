package my.noveldokusha.scraper.domain

data class ChapterResult(
    val title: String,
    val url: String,
    val volume: String? = null,
    /**
     * Дата публикации главы (unix epoch, секунды) или null, если источник
     * её не сообщает. Опционально — старые плагины/парсеры не заполняют.
     */
    val uploaded: Long? = null
)