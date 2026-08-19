package my.noveldokusha.interactor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.Response
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.CoverRepository
import my.noveldokusha.data.DownloaderRepository
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.tables.Book
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Регресс: updateBook освежает рейтинг/статус/дату обновления при КАЖДОМ вызове,
 * даже если в базе эти поля уже заполнены (fill-once-обёртки сняты).
 */
class LibraryUpdatesInteractionsTest {

    private val appRepository = mock<AppRepository>()
    private val bookChaptersRepository = mock<BookChaptersRepository>()
    private val downloaderRepository = mock<DownloaderRepository>()
    private val libraryDao = mock<LibraryDao>()
    private val coverRepository = mock<CoverRepository>()
    private val appFileResolver = mock<AppFileResolver>()

    private val interactions = LibraryUpdatesInteractions(
        appRepository = appRepository,
        downloaderRepository = downloaderRepository,
        libraryDao = libraryDao,
        coverRepository = coverRepository,
        appFileResolver = appFileResolver,
    )

    private val bookUrl = "https://example.com/book/123"

    private fun bookWithFilledMetadata() = Book(
        title = "Test Book",
        url = bookUrl,
        completed = false,
        inLibrary = true,
        coverImageUrl = "https://example.com/cover.jpg",
        description = "Description",
        genres = "Action",
        rating = "5.0",
        status = "Ongoing",
        lastUpdateDate = "2026-01-01",
    )

    private suspend fun stubSupportingCalls() {
        whenever(appRepository.bookChapters).thenReturn(bookChaptersRepository)
        // Список глав — пустой, чтобы стратегия обновления дошла до merge без данных.
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(emptyList())
        // Ковер: источник вернул null — syncCover не вызывается (нужен только чтобы пройти блок).
        whenever(downloaderRepository.bookCoverImageUrl(bookUrl)).thenReturn(Response.Success<String?>(null))
        // Главы: пустой список → легаси-путь завершается без новых глав.
        whenever(downloaderRepository.bookChaptersList(bookUrl)).thenReturn(Response.Success(emptyList()))
        // Хэш: null → хэш-скип и legacy-обновление хэша не срабатывают.
        whenever(downloaderRepository.bookChaptersListHash(bookUrl)).thenReturn(Response.Success<String?>(null))
    }

    @Test
    fun `updateBook refreshes rating status and lastUpdateDate even when fields are populated`() = runBlocking {
        whenever(downloaderRepository.bookRating(bookUrl)).thenReturn(Response.Success<String?>("5.0"))
        whenever(downloaderRepository.bookStatus(bookUrl)).thenReturn(Response.Success<String?>("Ongoing"))
        whenever(downloaderRepository.bookLastUpdate(bookUrl)).thenReturn(Response.Success<String?>("2026-01-01"))
        stubSupportingCalls()

        interactions.updateSpecificBooks(
            books = listOf(bookWithFilledMetadata()),
            countingUpdating = MutableStateFlow(null),
            currentUpdating = MutableStateFlow(emptySet()),
            newUpdates = MutableStateFlow(emptySet()),
            failedUpdates = MutableStateFlow(emptySet()),
        )

        // Все три метаданных запрошены у источника при каждом апдейте...
        verify(downloaderRepository).bookRating(bookUrl)
        verify(downloaderRepository).bookStatus(bookUrl)
        verify(downloaderRepository).bookLastUpdate(bookUrl)
        // ...и записаны в базу, хотя поля уже были заполнены.
        verify(libraryDao).updateRating(bookUrl, "5.0")
        verify(libraryDao).updateStatus(bookUrl, "Ongoing")
        verify(libraryDao).updateLastUpdateDate(bookUrl, "2026-01-01")
    }
}
