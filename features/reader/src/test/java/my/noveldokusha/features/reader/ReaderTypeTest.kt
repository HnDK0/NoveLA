package my.noveldokusha.features.reader

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.scraper.SourceInterface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Маршрутизация гейта ридера: "manga" → MANGA-путь (MangaReaderActivity),
 * всё остальное ("" / "novel" / null / отсутствие книги / ошибка БД) → NOVEL-путь.
 *
 * Книга, открытая напрямую из каталога (не в библиотеке), маршрутизируется
 * по contentType источника (плагина), чей baseUrl совпадает с host'ом bookUrl.
 */
class ReaderTypeTest {

    private val bookUrl = "https://example.com/book"

    // ---- resolveReaderType: чистая функция маршрутизации по метке ----

    @Test
    fun mangaContentTypeRoutesToManga() {
        assertEquals(ReaderType.MANGA, resolveReaderType("manga"))
    }

    @Test
    fun emptyContentTypeRoutesToNovel() {
        assertEquals(ReaderType.NOVEL, resolveReaderType(""))
    }

    @Test
    fun nullContentTypeRoutesToNovel() {
        assertEquals(ReaderType.NOVEL, resolveReaderType(null))
    }

    @Test
    fun novelContentTypeRoutesToNovel() {
        assertEquals(ReaderType.NOVEL, resolveReaderType("novel"))
    }

    // ---- resolveGateType: решение гейта по БД (mock LibraryBooksRepository.get) ----

    private fun mangaSource(baseUrl: String = "https://example.com"): SourceInterface.Catalog =
        mock<SourceInterface.Catalog>().apply {
            whenever(this.baseUrl).thenReturn(baseUrl)
            whenever(this.contentType).thenReturn("manga")
        }

    private fun scraperWith(vararg sources: SourceInterface.Catalog): ScraperRepository =
        mock<ScraperRepository>().apply {
            whenever(sourcesCatalogListFlow()).thenReturn(
                flowOf(sources.map { ScraperRepository.CatalogItem(it, pinned = false) })
            )
        }

    @Test
    fun storedMangaContentTypeRoutesToManga() = runBlocking {
        val repo = mock<LibraryBooksRepository>()
        whenever(repo.get(bookUrl)).thenReturn(
            Book(title = "Manga", url = bookUrl, contentType = "manga")
        )
        assertEquals(ReaderType.MANGA, resolveGateType(repo, scraperWith(), bookUrl))
    }

    @Test
    fun storedEmptyContentTypeRoutesToNovel() = runBlocking {
        val repo = mock<LibraryBooksRepository>()
        whenever(repo.get(bookUrl)).thenReturn(Book(title = "Novel", url = bookUrl))
        assertEquals(ReaderType.NOVEL, resolveGateType(repo, scraperWith(), bookUrl))
    }

    @Test
    fun missingBookWithoutMangaSourceRoutesToNovel() = runBlocking {
        val repo = mock<LibraryBooksRepository>()
        whenever(repo.get(bookUrl)).thenReturn(null)
        assertEquals(ReaderType.NOVEL, resolveGateType(repo, scraperWith(), bookUrl))
    }

    @Test
    fun databaseErrorRoutesToNovel() = runBlocking {
        val repo = mock<LibraryBooksRepository>()
        whenever(repo.get(bookUrl)).thenThrow(RuntimeException("broken db"))
        assertEquals(ReaderType.NOVEL, resolveGateType(repo, scraperWith(), bookUrl))
    }

    @Test
    fun missingBookWithMangaSourceRoutesToManga() = runBlocking {
        val repo = mock<LibraryBooksRepository>()
        whenever(repo.get(bookUrl)).thenReturn(null)
        val scraper = scraperWith(mangaSource(baseUrl = "https://example.com"))
        assertEquals(ReaderType.MANGA, resolveGateType(repo, scraper, bookUrl))
    }

    @Test
    fun missingBookWithMismatchedHostRoutesToNovel() = runBlocking {
        val repo = mock<LibraryBooksRepository>()
        whenever(repo.get(bookUrl)).thenReturn(null)
        val scraper = scraperWith(mangaSource(baseUrl = "https://other-site.com"))
        assertEquals(ReaderType.NOVEL, resolveGateType(repo, scraper, bookUrl))
    }

    @Test
    fun missingBookWithUnlabeledSourceRoutesToNovel() = runBlocking {
        val repo = mock<LibraryBooksRepository>()
        whenever(repo.get(bookUrl)).thenReturn(null)
        val source = mock<SourceInterface.Catalog>().apply {
            whenever(this.baseUrl).thenReturn("https://example.com")
            whenever(this.contentType).thenReturn("")
        }
        assertEquals(ReaderType.NOVEL, resolveGateType(repo, scraperWith(source), bookUrl))
    }
}
