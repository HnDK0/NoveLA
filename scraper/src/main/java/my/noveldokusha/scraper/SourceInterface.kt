package my.noveldokusha.scraper

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document

sealed interface SourceInterface {
    val id: String

    @get:StringRes
    val nameStrId: Int

    /**
     * Динамическое имя источника — используется для Lua расширений,
     * у которых нет строкового ресурса.
     * Если не null, UI должен использовать это вместо nameStrId.
     */
    val name: String? get() = null

    val baseUrl: String
    val isLocalSource: Boolean get() = false
    val requiresLogin: Boolean get() = false
    val charset: String get() = "UTF-8"

    fun resolveName(context: android.content.Context): String =
        name ?: if (nameStrId != 0) context.getString(nameStrId) else "Unknown"
    suspend fun transformChapterUrl(url: String): String = url

    suspend fun getChapterText(doc: Document): String? = null

    interface Base : SourceInterface
    interface Catalog : SourceInterface {
        val catalogUrl: String
        val language: LanguageCode?
        val languageTag: String? get() = language?.iso639_1
        // String? — иконка всегда URL-строка (из YAML) или null
        val iconUrl: String? get() = null
        val iconResId: Int? get() = null

        suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
            Response.Success(null)

        suspend fun getBookDescription(bookUrl: String): Response<String?> = Response.Success(null)

        suspend fun getBookTitle(bookUrl: String): Response<String?> = Response.Success(null)

        /** Возвращает список жанров/тегов книги. Реализуется в Lua через getBookGenres(). */
        suspend fun getBookGenres(bookUrl: String): Response<List<String>> = Response.Success(emptyList())

        suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>>
        suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>>
        suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>>

        suspend fun getChapterListHash(bookUrl: String): Response<String?> = Response.Success(null)

        /**
         * Результат parsePage — список глав со страницы + общее количество страниц.
         */
        data class PagedChapterResult(
            val chapters: List<ChapterResult>,
            val totalPages: Int,
        )

        /**
         * Загружает одну страницу списка глав. Реализуется плагином опционально.
         *
         * Если плагин объявляет эту функцию, движок переходит в пагинированный режим:
         *   — первый парс: загружает все страницы от 1 до totalPages
         *   — обновление: перечитывает последнюю сохранённую страницу (ищет новые главы)
         *     + догружает страницы lastPage+1..newTotalPages если список вырос
         *
         * Если функция не объявлена (возвращает null), движок использует старый путь
         * через getChapterList() — полный парс при каждом обновлении.
         *
         * @param bookUrl  URL страницы книги
         * @param page     номер страницы, начиная с 1
         * @return         Response с результатом, или null если плагин не поддерживает пагинацию
         */
        suspend fun parsePage(bookUrl: String, page: Int): Response<PagedChapterResult>? = null
    }

    /**
     * Источник поддерживающий фильтрацию каталога.
     * Плагин объявляет getFilterList() и getCatalogFiltered().
     *
     * Проверка в UI: source is SourceInterface.FilterableCatalog
     * Кнопка фильтров показывается только для таких источников.
     *
     * Список фильтров всегда исходит из Lua — getFilterList() вызывает
     * Lua-функцию каждый раз, без кэширования в адаптере.
     */
    interface FilterableCatalog : Catalog {
        /**
         * Возвращает список доступных фильтров из Lua-плагина.
         * ViewModel вызывает один раз при init и кэширует в своём состоянии.
         */
        suspend fun getFilterList(): Response<List<LuaFilter>>

        /**
         * Загружает каталог с применёнными фильтрами.
         * Вызывается вместо getCatalogList() когда activeFilters.isEmpty == false.
         */
        suspend fun getCatalogFiltered(
            index: Int,
            filters: ActiveFilters
        ): Response<PagedList<BookResult>>
    }

    interface Configurable {
        @Composable
        fun ScreenConfig()
    }
}
