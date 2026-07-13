package my.noveldokusha.scraper

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult

/**
 * Реализация SourceInterface.Catalog для использования из кэша.
 * Позволяет мгновенно отображать список источников без загрузки Lua-скриптов.
 * Живёт в scraper-модуле, чтобы соблюдать sealed interface ограничения.
 */
class CachedSource(
    override val id: String,
    override val nameStrId: Int,
    override val name: String?,
    override val baseUrl: String,
    override val isLocalSource: Boolean = false,
    override val language: LanguageCode? = null,
    override val catalogUrl: String = baseUrl,
) : SourceInterface.Catalog {

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        Response.Error("Cached source: not loaded", Exception("Cached source: use full Lua source for data"))

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        Response.Error("Cached source: not loaded", Exception("Cached source: use full Lua source for data"))

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> =
        Response.Error("Cached source: not loaded", Exception("Cached source: use full Lua source for data"))
}
