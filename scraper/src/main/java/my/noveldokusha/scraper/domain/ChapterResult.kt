package my.noveldokusha.scraper.domain

data class ChapterResult(
    val title: String,
    val url: String,
    val volume: String? = null
)