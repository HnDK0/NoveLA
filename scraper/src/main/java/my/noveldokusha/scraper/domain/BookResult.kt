package my.noveldokusha.scraper.domain

data class BookResult(
    val title: String,
    val url: String,
    val coverImageUrl: String = "",
    val description: String = "",
    // Рейтинг/ранг как строка (гетерогенные форматы источников: "4.5", "RANK 216", "#12").
    // null → источник не предоставил рейтинг в этом контексте (например, нет в карточке поиска).
    val rating: String? = null,
)


