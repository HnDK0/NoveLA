package my.noveldokusha.features.reader.manga

/**
 * Модели tachiyomisy-style манга/манхва-читалки.
 *
 * Адаптация под NoveLA: одна глава = список URL страниц (getPageList),
 * страницы грузятся через существующий PageImageLoader (кэш + офлайн).
 */

/** Одна страница главы. */
internal data class MangaPage(
    val url: String,
    val index: Int,
)

/** Глава-картинка: упорядоченные страницы + метаданные для навигации. */
internal data class MangaChapter(
    /** URL главы в источнике (ключ кэша/загрузок). */
    val url: String,
    /** Заголовок главы из списка глав книги. */
    val title: String,
    /** Индекс главы внутри книги (для prev/next). */
    val index: Int,
    val pages: List<MangaPage>,
    /** Страница, с которой начать чтение (последняя сохранённая). */
    val startPage: Int = 0,
) {
    val pageCount: Int get() = pages.size
}
