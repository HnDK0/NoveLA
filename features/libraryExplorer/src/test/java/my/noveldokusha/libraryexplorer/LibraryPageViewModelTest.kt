package my.noveldokusha.libraryexplorer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.LibrarySortOption
import my.noveldokusha.core.appPreferences.SortConfig
import my.noveldokusha.core.appPreferences.SortDirection
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.feature.local_database.BookWithContext
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.interactor.WorkersInteractions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Тесты фильтра «Тип» (Все/Манга/Новелла) библиотеки:
 * чистая функция filterByType и переключение состояния setContentTypeFilter.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPageViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun book(url: String, contentType: String = ""): BookWithContext =
        BookWithContext(Book(title = "Book $url", url = url, contentType = contentType), 0, 0)

    // ── filterByType ─────────────────────────────────────────────────────

    @Test
    fun `ALL возвращает все книги без фильтрации`() {
        val books = listOf(
            book("manga1", "manga"),
            book("novel1", "novel"),
            book("empty1", "")
        )
        assertEquals(books, books.filterByType(ContentTypeFilter.ALL))
    }

    @Test
    fun `MANGA оставляет только книги с contentType manga`() {
        val books = listOf(
            book("manga1", "manga"),
            book("manga2", "manga"),
            book("novel1", "novel"),
            book("empty1", "")
        )
        val result = books.filterByType(ContentTypeFilter.MANGA)
        assertEquals(listOf("manga1", "manga2"), result.map { it.book.url })
    }

    @Test
    fun `MANGA не пропускает книги с пустой меткой`() {
        val empty = book("empty1", "")
        val result = listOf(empty).filterByType(ContentTypeFilter.MANGA)
        assertEquals(emptyList<BookWithContext>(), result)
    }

    @Test
    fun `NOVEL оставляет всё кроме manga включая пустую метку и novel`() {
        val books = listOf(
            book("manga1", "manga"),
            book("novel1", "novel"),
            book("empty1", ""),
            book("other1", "comic")
        )
        val result = books.filterByType(ContentTypeFilter.NOVEL)
        assertEquals(listOf("novel1", "empty1", "other1"), result.map { it.book.url })
    }

    // ── setContentTypeFilter ─────────────────────────────────────────────

    @Test
    fun `setContentTypeFilter меняет выбранный тип и сбрасывается в ALL`() = runTest {
        val model = createViewModel()

        assertEquals(ContentTypeFilter.ALL, model.selectedContentType.value)

        model.setContentTypeFilter(ContentTypeFilter.MANGA)
        assertEquals(ContentTypeFilter.MANGA, model.selectedContentType.value)

        model.setContentTypeFilter(ContentTypeFilter.NOVEL)
        assertEquals(ContentTypeFilter.NOVEL, model.selectedContentType.value)

        model.resetAllFilters()
        assertEquals(ContentTypeFilter.ALL, model.selectedContentType.value)
    }

    private fun createViewModel(): LibraryPageViewModel {
        val libraryBooks = mock<LibraryBooksRepository>().also {
            whenever(it.getBooksInLibraryWithContextFlow).thenReturn(flowOf(emptyList()))
        }
        val appRepository = mock<AppRepository>().also {
            whenever(it.libraryBooks).thenReturn(libraryBooks)
            whenever(it.eventDataRestored).thenReturn(MutableSharedFlow())
        }
        val libraryDao = mock<my.noveldokusha.feature.local_database.DAOs.LibraryDao>().also {
            whenever(it.getAllLibraryGenresRawFlow()).thenReturn(flowOf(emptyList()))
        }
        val workers = mock<WorkersInteractions>().also {
            whenever(it.isManualUpdateRunning()).thenReturn(flowOf(false))
        }
        val preferences = mock<AppPreferences>().also { prefs ->
            fun <T> pref(value: T): AppPreferences.Preference<T> =
                mock<AppPreferences.Preference<T>>().also { whenever(it.value).thenReturn(value) }
            // Все pref-моки создаём ДО thenReturn-стабов свойств: вложенный
            // whenever() внутри thenReturn() ломает stubbing (UnfinishedStubbingException).
            val categories = pref(emptySet<String>())
            val genres = pref(emptySet<String>())
            val sources = pref(emptySet<String>())
            val filterRead = pref(TernaryState.Inactive)
            val sortConfig = pref(SortConfig(LibrarySortOption.LAST_READ, SortDirection.DESC))

            whenever(filterRead.flow()).thenReturn(flowOf(TernaryState.Inactive))
            whenever(sortConfig.flow()).thenReturn(flowOf(SortConfig(LibrarySortOption.LAST_READ, SortDirection.DESC)))
            whenever(prefs.LIBRARY_SELECTED_CATEGORIES).thenReturn(categories)
            whenever(prefs.LIBRARY_SELECTED_GENRES).thenReturn(genres)
            whenever(prefs.LIBRARY_SELECTED_SOURCES).thenReturn(sources)
            whenever(prefs.LIBRARY_FILTER_READ).thenReturn(filterRead)
            whenever(prefs.LIBRARY_SORT_CONFIG).thenReturn(sortConfig)
        }

        return LibraryPageViewModel(
            appRepository = appRepository,
            preferences = preferences,
            toasty = mock(),
            workersInteractions = workers,
            libraryDao = libraryDao,
            scraper = mock(),
            context = mock(),
        )
    }
}
