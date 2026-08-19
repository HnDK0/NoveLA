package my.noveldokusha.features.reader.manga

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.ChapterBodyRepository
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.features.reader.ReaderRepository
import my.noveldokusha.features.reader.domain.ChapterState
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * MVI-логика [MangaReaderViewModel]: init-последовательность без гонок (Bug1a),
 * latest-wins загрузки глав, смена глав по индексу с явным «концом книги»,
 * InvalidChapter без фолбэка на главу 1, Retry без потери последнего
 * успешного состояния (6.5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MangaReaderViewModelTest {

    private val bookUrl = "https://example.com/book"
    private val ch1Url = "https://example.com/book/ch1"
    private val ch2Url = "https://example.com/book/ch2"

    private val dispatcher = StandardTestDispatcher()

    private lateinit var readerRepository: ReaderRepository
    private lateinit var bookChaptersRepository: BookChaptersRepository
    private lateinit var appRepository: AppRepository
    private lateinit var appPreferences: AppPreferences
    private lateinit var downloadedPageChaptersStore: DownloadedPageChaptersStore
    private lateinit var chapterBody: ChapterBodyRepository
    private lateinit var libraryBooks: LibraryBooksRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        chapterBody = mock()
        libraryBooks = mock()
        appRepository = mock<AppRepository>().also {
            whenever(it.chapterBody).thenReturn(chapterBody)
            whenever(it.libraryBooks).thenReturn(libraryBooks)
        }
        bookChaptersRepository = mock()
        readerRepository = mock()
        downloadedPageChaptersStore = mock()
        appPreferences = prefs()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** AppPreferences с замоканными объектами-преференсами (value — стабы). */
    private fun prefs(downloadOnOpen: Boolean = false): AppPreferences {
        val prefs = mock<AppPreferences>()
        fun <T> pref(value: T): AppPreferences.Preference<T> =
            mock<AppPreferences.Preference<T>>().also { whenever(it.value).thenReturn(value) }
        // Моки преференсов создаются ДО стабов геттеров: вызов pref() внутри
        // thenReturn запускает вложенный whenever, пока внешний стаб ещё не завершён
        // (UnfinishedStubbingException).
        val readingMode = pref(MangaReadingMode.WEBTOON.flagValue)
        val tappingInverted = pref(false)
        val transitionsPager = pref(true)
        val transitionsWebtoon = pref(true)
        val showPageNumber = pref(true)
        val keepScreenOn = pref(false)
        val fullscreen = pref(true)
        val webtoonSidePadding = pref(0)
        val navModePager = pref(0)
        val navModeWebtoon = pref(0)
        val singleTapToOpenSettings = pref(false)
        val downloadOnOpenPref = pref(downloadOnOpen)
        val autoscrollEnabled = pref(false)
        val autoscrollSpeed = pref(5)
        val colorFilter = pref(false)
        val customBrightness = pref(false)
        val customBrightnessValue = pref(0)
        val colorFilterValue = pref(0)
        val colorFilterMode = pref(0)
        val grayscale = pref(false)
        val invertedColors = pref(false)

        whenever(prefs.MANGA_READER_READING_MODE).thenReturn(readingMode)
        whenever(prefs.MANGA_READER_TAPPING_INVERTED).thenReturn(tappingInverted)
        whenever(prefs.MANGA_READER_TRANSITIONS_PAGER).thenReturn(transitionsPager)
        whenever(prefs.MANGA_READER_TRANSITIONS_WEBTOON).thenReturn(transitionsWebtoon)
        whenever(prefs.MANGA_READER_SHOW_PAGE_NUMBER).thenReturn(showPageNumber)
        whenever(prefs.MANGA_READER_KEEP_SCREEN_ON).thenReturn(keepScreenOn)
        whenever(prefs.MANGA_READER_FULLSCREEN).thenReturn(fullscreen)
        whenever(prefs.MANGA_READER_WEBTOON_SIDE_PADDING).thenReturn(webtoonSidePadding)
        whenever(prefs.MANGA_READER_NAV_MODE_PAGER).thenReturn(navModePager)
        whenever(prefs.MANGA_READER_NAV_MODE_WEBTOON).thenReturn(navModeWebtoon)
        whenever(prefs.READER_SINGLE_TAP_TO_OPEN_SETTINGS).thenReturn(singleTapToOpenSettings)
        whenever(prefs.MANGA_READER_DOWNLOAD_ON_OPEN).thenReturn(downloadOnOpenPref)
        whenever(prefs.MANGA_READER_AUTOSCROLL_ENABLED).thenReturn(autoscrollEnabled)
        whenever(prefs.MANGA_READER_AUTOSCROLL_SPEED).thenReturn(autoscrollSpeed)
        whenever(prefs.MANGA_READER_COLOR_FILTER).thenReturn(colorFilter)
        whenever(prefs.MANGA_READER_CUSTOM_BRIGHTNESS).thenReturn(customBrightness)
        whenever(prefs.MANGA_READER_CUSTOM_BRIGHTNESS_VALUE).thenReturn(customBrightnessValue)
        whenever(prefs.MANGA_READER_COLOR_FILTER_VALUE).thenReturn(colorFilterValue)
        whenever(prefs.MANGA_READER_COLOR_FILTER_MODE).thenReturn(colorFilterMode)
        whenever(prefs.MANGA_READER_GRAYSCALE).thenReturn(grayscale)
        whenever(prefs.MANGA_READER_INVERTED_COLORS).thenReturn(invertedColors)
        return prefs
    }

    private fun chapters(vararg urls: String): List<Chapter> =
        urls.mapIndexed { i, url -> Chapter(title = "Chapter $i", url = url, bookUrl = bookUrl, position = i) }

    private fun vm(): MangaReaderViewModel = MangaReaderViewModel(
        readerRepository = readerRepository,
        bookChaptersRepository = bookChaptersRepository,
        appRepository = appRepository,
        appPreferences = appPreferences,
        downloadedPageChaptersStore = downloadedPageChaptersStore,
    )

    private fun TestScope.collectEvents(vm: MangaReaderViewModel): MutableList<MangaReaderEvent> {
        val events = mutableListOf<MangaReaderEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events.add(it) } }
        return events
    }

    // ---- init-последовательность (Bug1a): главы → идентичность → глава → позиция ----

    @Test
    fun initLoadsTargetChapterWithRestoredPosition() = runTest(dispatcher) {
        val list = chapters(ch1Url, ch2Url).toMutableList()
        list[1] = list[1].copy(lastReadPosition = 1)
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(list)
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "Test Book", url = bookUrl))
        whenever(chapterBody.fetchPages(ch2Url)).thenReturn(Response.Success(listOf("p0", "p1", "p2")))
        val vm = vm()
        vm.init(bookUrl, ch2Url)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        state as MangaReaderUiState.Ready
        assertEquals("Test Book", state.bookTitle)
        assertEquals(ch2Url, state.chapter.url)
        assertEquals(1, state.currentChapterIndex)
        assertEquals(1, state.chapter.startPage) // восстановлена сохранённая позиция
        assertEquals(1, state.currentPage)
        assertEquals(1, state.chapterLoadId)
    }

    @Test
    fun initStartsWithLoadingAndSettlesToReady() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("p0")))
        val vm = vm()
        assertTrue(vm.uiState.value is MangaReaderUiState.Loading)
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is MangaReaderUiState.Ready)
    }

    // ---- latest-wins: последний запрос побеждает, устаревший отбрасывается ----

    @Test
    fun latestWinsSecondChapterRequestWinsOverInit() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url, ch2Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("p1")))
        whenever(chapterBody.fetchPages(ch2Url)).thenReturn(Response.Success(listOf("p2a", "p2b")))
        val vm = vm()
        vm.init(bookUrl, ch1Url) // цель зафиксирована до загрузки глав
        vm.loadChapter(ch2Url)   // последний запрос — ch2
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        assertEquals(ch2Url, (state as MangaReaderUiState.Ready).chapter.url)
        verify(chapterBody, never()).fetchPages(eq(ch1Url), any())
    }

    @Test
    fun initIsIdempotent() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("p0")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        vm.init(bookUrl, ch2Url) // повторный init игнорируется
        advanceUntilIdle()
        assertEquals(ch1Url, (vm.uiState.value as MangaReaderUiState.Ready).chapter.url)
    }

    // ---- смена глав ----

    @Test
    fun moveChapterLoadsNextChapterWithoutPositionRestore() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(
            listOf(
                Chapter(title = "Ch1", url = ch1Url, bookUrl = bookUrl, position = 0, lastReadPosition = 2),
                Chapter(title = "Ch2", url = ch2Url, bookUrl = bookUrl, position = 1),
            )
        )
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b", "c")))
        whenever(chapterBody.fetchPages(ch2Url)).thenReturn(Response.Success(listOf("x", "y")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        vm.moveChapter(1)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        state as MangaReaderUiState.Ready
        assertEquals(ch2Url, state.chapter.url)
        assertEquals(1, state.currentChapterIndex)
        assertEquals(0, state.chapter.startPage) // без восстановления позиции
        assertEquals(0, state.currentPage)
    }

    @Test
    fun moveChapterBeyondLastEmitsEndOfBook() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url, ch2Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch2Url)).thenReturn(Response.Success(listOf("a", "b")))
        val vm = vm()
        vm.init(bookUrl, ch2Url) // открыли последнюю главу
        advanceUntilIdle()
        val events = collectEvents(vm)

        vm.moveChapter(1) // выходим за границы — явный «конец книги»
        advanceUntilIdle()

        assertEquals(listOf(MangaReaderEvent.EndOfBook), events)
    }

    @Test
    fun moveChapterWithoutReadyStateDoesNotEmitEndOfBook() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(emptyList())
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a")))
        val vm = vm()
        vm.init(bookUrl, ch1Url) // глава не найдена в пустом списке → InvalidChapter
        advanceUntilIdle()
        val events = collectEvents(vm)

        vm.moveChapter(1) // без Ready-состояния — не должно быть ложного «конца книги»
        advanceUntilIdle()

        assertTrue(events.none { it == MangaReaderEvent.EndOfBook })
    }

    // ---- InvalidChapter: идентичность по URL, без фолбэка на главу 1 ----

    @Test
    fun initWithUnknownChapterEmitsInvalidChapter() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a")))
        val vm = vm()
        val events = collectEvents(vm)

        vm.init(bookUrl, "https://example.com/book/unknown")
        advanceUntilIdle()

        assertTrue(events.contains(MangaReaderEvent.InvalidChapter))
        assertTrue(vm.uiState.value is MangaReaderUiState.Loading) // ничего не открыто
    }

    @Test
    fun emptyPagesEmitInvalidChapter() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(emptyList()))
        val vm = vm()
        val events = collectEvents(vm)

        vm.init(bookUrl, ch1Url) // текстовая глава в манга-ридере
        advanceUntilIdle()

        assertTrue(events.contains(MangaReaderEvent.InvalidChapter))
    }

    // ---- Retry (6.5): ошибка не роняет последнее успешное состояние ----

    @Test
    fun networkErrorShowsErrorAndRetryRecovers() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url))
            .thenReturn(Response.Error("network", RuntimeException("boom")))
            .thenReturn(Response.Success(listOf("a", "b")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is MangaReaderUiState.Error)

        vm.retryChapter()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        assertEquals(ch1Url, (state as MangaReaderUiState.Ready).chapter.url)
    }

    @Test
    fun failedChapterSwitchKeepsLastSuccessfulState() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url, ch2Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b")))
        whenever(chapterBody.fetchPages(ch2Url)).thenReturn(Response.Error("network", RuntimeException("boom")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        vm.moveChapter(1) // ch2 не грузится
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        assertEquals(ch1Url, (state as MangaReaderUiState.Ready).chapter.url) // остались на ch1
    }

    // ---- позиция чтения ----

    @Test
    fun setCurrentPagePersistsPositionInOwnChapter() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b", "c")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        vm.setCurrentPage(1, ch1Url)
        advanceUntilIdle()

        assertEquals(1, (vm.uiState.value as MangaReaderUiState.Ready).currentPage)
        verify(bookChaptersRepository).updatePosition(eq(ch1Url), eq(1), eq(0))
    }

    @Test
    fun setCurrentPageForStaleChapterIsIgnored() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b", "c")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        vm.setCurrentPage(2, "https://example.com/book/other") // событие от устаревшего вьюера
        advanceUntilIdle()

        assertEquals(0, (vm.uiState.value as MangaReaderUiState.Ready).currentPage)
        verify(bookChaptersRepository, never()).updatePosition(any(), any(), any())
    }

    @Test
    fun saveStatePersistsChapterState() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b", "c")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        vm.setCurrentPage(2, ch1Url)
        vm.saveState()

        verify(readerRepository).saveBookLastReadPositionState(
            eq(bookUrl),
            eq(ChapterState(ch1Url, 2, 0)),
            isNull(),
        )
    }

    @Test
    fun markChapterReadSetsReadFlag() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        vm.markChapterRead(ch1Url)
        advanceUntilIdle()

        verify(bookChaptersRepository).setAsRead(eq(ch1Url), eq(true))
    }

    // ---- settings ----

    @Test
    fun settingsActionWritesPreferenceAndRebuildsState() = runTest(dispatcher) {
        val readingModePref = mock<AppPreferences.Preference<Int>>()
        // Первый read (конструктор VM) — WEBTOON, повторный read (пересборка
        // после действия) — PAGED: только тогда StateFlow пересоздаёт state.
        whenever(readingModePref.value)
            .thenReturn(MangaReadingMode.WEBTOON.flagValue, MangaReadingMode.PAGED.flagValue)
        whenever(appPreferences.MANGA_READER_READING_MODE).thenReturn(readingModePref)
        val vm = vm()
        val before = vm.settings.value

        vm.actions.setReadingMode(MangaReadingMode.PAGED)

        assertNotSame(before, vm.settings.value) // state пересобран
        verify(readingModePref).value = MangaReadingMode.PAGED.flagValue
    }

    @Test
    fun settingsDefaultsMatchPrefs() {
        val vm = vm()
        assertEquals(MangaReadingMode.WEBTOON, vm.settings.value.readingMode)
        assertEquals(false, vm.settings.value.singleTapToOpenSettings)
    }

    @Test
    fun navModeLegacyValuesMapToNewModes() {
        // Легаси старой схемы тап-зон: 0..4 → BOTH (прежнее поведение
        // по умолчанию), 5 (выключено) → SWIPE (только листание).
        assertEquals(MangaNavigationMode.BOTH, MangaNavigationMode.fromPreference(0))
        assertEquals(MangaNavigationMode.BOTH, MangaNavigationMode.fromPreference(4))
        assertEquals(MangaNavigationMode.SWIPE, MangaNavigationMode.fromPreference(5))
        // Новые значения — сами себе; старый BOTH_INVERTED (13) → BOTH
        // (инверсия теперь отдельным тоглом); неизвестные/отсутствующие → BOTH.
        assertEquals(MangaNavigationMode.SWIPE, MangaNavigationMode.fromPreference(10))
        assertEquals(MangaNavigationMode.TAP_EDGES, MangaNavigationMode.fromPreference(11))
        assertEquals(MangaNavigationMode.BOTH, MangaNavigationMode.fromPreference(12))
        assertEquals(MangaNavigationMode.BOTH, MangaNavigationMode.fromPreference(13))
        assertEquals(MangaNavigationMode.BOTH, MangaNavigationMode.fromPreference(99))
        assertEquals(MangaNavigationMode.BOTH, MangaNavigationMode.fromPreference(null))
    }

    // ---- download on open ----

    @Test
    fun downloadOnOpenDownloadsChapterPages() = runTest(dispatcher) {
        whenever(downloadedPageChaptersStore.downloadChapter(any(), any())).thenReturn(0L)
        appPreferences = prefs(downloadOnOpen = true)
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        verify(downloadedPageChaptersStore).downloadChapter(eq(ch1Url), eq(listOf("a", "b")))
    }

    @Test
    fun downloadOnOpenDisabledByDefault() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        verify(downloadedPageChaptersStore, never()).downloadChapter(any(), any())
    }

    // ---- helpers для webtoon-окна ----

    @Test
    fun buildChapterReturnsNullForUnknownChapter() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        val vm = vm()
        assertEquals(null, vm.buildChapter("https://example.com/book/unknown"))
    }

    @Test
    fun buildChapterBuildsNeighborChapter() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url, ch2Url))
        whenever(bookChaptersRepository.get(ch2Url)).thenReturn(
            Chapter(title = "Chapter 1", url = ch2Url, bookUrl = bookUrl, position = 1)
        )
        whenever(chapterBody.fetchPages(ch2Url)).thenReturn(Response.Success(listOf("x", "y")))
        val vm = vm()

        val chapter = vm.buildChapter(ch2Url)
        assertNotNull(chapter)
        assertEquals(ch2Url, chapter!!.url)
        assertEquals(1, chapter.index)
        assertEquals(2, chapter.pageCount)
    }

    @Test
    fun hasNextAndPrevChapterFollowReadyState() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url, ch2Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        assertTrue(vm.hasNextChapter())
        assertTrue(!vm.hasPrevChapter())
    }

    // ---- syncChapter: переход окна вебтуна на соседнюю главу ----

    @Test
    fun syncChapterUpdatesStateInPlaceFromWindowCache() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url, ch2Url))
        whenever(bookChaptersRepository.get(ch2Url)).thenReturn(
            Chapter(title = "Chapter 1", url = ch2Url, bookUrl = bookUrl, position = 1)
        )
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b")))
        whenever(chapterBody.fetchPages(ch2Url)).thenReturn(Response.Success(listOf("x", "y")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()
        val loadIdBefore = (vm.uiState.value as MangaReaderUiState.Ready).chapterLoadId
        vm.buildChapter(ch2Url) // окно строит соседнюю главу → кэш окна
        advanceUntilIdle()

        vm.syncChapter(ch2Url, 1, 1, listOf(MangaPage("x", 0), MangaPage("y", 1)))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        state as MangaReaderUiState.Ready
        assertEquals(ch2Url, state.chapter.url)
        assertEquals(1, state.currentChapterIndex)
        assertEquals(1, state.currentPage)
        assertEquals(2, state.chapter.pageCount) // страницы из кэша окна
        assertEquals(loadIdBefore, state.chapterLoadId) // вьюер не пересоздаётся
        verify(bookChaptersRepository).updatePosition(eq(ch2Url), eq(1), eq(0))
    }

    @Test
    fun syncChapterWithoutCacheFallsBackToStub() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url, ch2Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        // окно перешло на ch2, но buildChapter(ch2Url) не вызывался — кэша нет;
        // страницы передаёт вьюер (окно знает их всегда)
        vm.syncChapter(ch2Url, 1, 1, emptyList())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        state as MangaReaderUiState.Ready
        assertEquals(ch2Url, state.chapter.url)
        assertEquals(1, state.currentChapterIndex)
        assertEquals(1, state.currentPage)
        assertTrue(state.chapter.pages.isEmpty()) // заглушка без страниц
        verify(bookChaptersRepository).updatePosition(eq(ch2Url), eq(1), eq(0))
    }

    @Test
    fun syncChapterForUnknownChapterIsIgnored() = runTest(dispatcher) {
        whenever(bookChaptersRepository.chapters(bookUrl)).thenReturn(chapters(ch1Url))
        whenever(libraryBooks.get(bookUrl)).thenReturn(Book(title = "B", url = bookUrl))
        whenever(chapterBody.fetchPages(ch1Url)).thenReturn(Response.Success(listOf("a", "b")))
        val vm = vm()
        vm.init(bookUrl, ch1Url)
        advanceUntilIdle()

        vm.syncChapter("https://example.com/book/unknown", 0, 0, emptyList())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is MangaReaderUiState.Ready)
        assertEquals(ch1Url, (state as MangaReaderUiState.Ready).chapter.url)
        verify(bookChaptersRepository, never()).updatePosition(any(), any(), any())
    }
}
