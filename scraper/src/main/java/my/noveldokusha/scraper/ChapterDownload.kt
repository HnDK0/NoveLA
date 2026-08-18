package my.noveldokusha.scraper

data class ChapterDownload(
    val body: String,
    val title: String?,
    /**
     * Страничные главы (манхва/манга): упорядоченные URL страниц из
     * getPageList. null — источник не вернул страницы, глава рендерится
     * по legacy HTML-пути ([body]). Для страничных глав body = "".
     */
    val pages: List<String>? = null
)