package my.noveldokusha.features.reader.manga

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.features.reader.ReaderRepository
import my.noveldokusha.features.reader.domain.ChapterState
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.features.reader.manga.setting.MangaReaderOrientation
import my.noveldokusha.features.reader.manga.setting.MangaTappingInvertMode
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import my.noveldokusha.features.reader.manga.ui.MangaReaderSettingsActions
import my.noveldokusha.features.reader.manga.ui.MangaReaderSettingsState
import my.noveldokusha.feature.local_database.tables.Chapter
import javax.inject.Inject

/**
 * Состояние манга/манхва-читалки: главы книги, страницы текущей главы
 * (getPageList через ReaderRepository), текущая страница с сохранением
 * позиции, настройки чтения (читаются из AppPreferences в
 * [MangaReaderSettingsState]).
 */
@HiltViewModel
internal class MangaReaderViewModel @Inject constructor(
    private val readerRepository: ReaderRepository,
    private val bookChaptersRepository: BookChaptersRepository,
    private val appRepository: AppRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    lateinit var bookUrl: String

    var bookTitle = mutableStateOf<String?>(null)
    var chapters = mutableStateOf<List<Chapter>>(emptyList())
    var chapter = mutableStateOf<MangaChapter?>(null)
    var currentChapterIndex = mutableStateOf(0)
    var loading = mutableStateOf(true)
    var loadError = mutableStateOf(false)

    /** Текущая страница (индекс). */
    var currentPage = mutableStateOf(0)

    /** Инкремент при каждой успешной загрузке главы (ключ рекомпозиции). */
    var chapterLoadId = mutableStateOf(0)

    private var lastChapterUrl: String = ""

    /** Настройки для шита; actions пишут в AppPreferences и обновляют state. */
    val settings = mutableStateOf(buildSettingsState())
    val actions: MangaReaderSettingsActions = MangaReaderSettingsActionsImpl()

    fun init(bookUrl: String, chapterUrl: String) {
        if (::bookUrl.isInitialized) return
        this.bookUrl = bookUrl
        viewModelScope.launch(Dispatchers.IO) {
            val book = runCatching { appRepository.libraryBooks.get(bookUrl) }.getOrNull()
            bookTitle.value = book?.title
            val list = runCatching { bookChaptersRepository.chapters(bookUrl) }.getOrNull() ?: emptyList()
            chapters.value = list
        }
        loadChapter(chapterUrl, restorePosition = true)
    }

    /** Загружает главу: getPageList → MangaChapter. */
    fun loadChapter(url: String, restorePosition: Boolean = false) {
        lastChapterUrl = url
        viewModelScope.launch {
            loading.value = true
            loadError.value = false
            val response = readerRepository.downloadChapterPages(url)
            val urls = response.toSuccessOrNull()?.data
            if (urls == null) {
                loading.value = false
                loadError.value = true
                return@launch
            }
            val title = chapters.value.firstOrNull { it.url == url }?.title ?: url
            val index = chapters.value.indexOfFirst { it.url == url }.takeIf { it >= 0 } ?: 0
            val savedPage = chapters.value.firstOrNull { it.url == url }?.lastReadPosition ?: 0
            val startPage = if (restorePosition) savedPage else 0
            chapter.value = MangaChapter(
                url = url,
                title = title,
                index = index,
                pages = urls.mapIndexed { i, pageUrl -> MangaPage(pageUrl, i) },
                startPage = startPage.coerceIn(0, (urls.size - 1).coerceAtLeast(0)),
            )
            currentChapterIndex.value = index
            currentPage.value = startPage.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
            chapterLoadId.value += 1
            loading.value = false
        }
    }

    /** Повторная попытка после ошибки загрузки главы. */
    fun retryChapter() {
        if (lastChapterUrl.isNotEmpty()) {
            loadChapter(lastChapterUrl, restorePosition = false)
        }
    }

    /** Смена главы (delta = ±1), без восстановления позиции. */
    fun moveChapter(delta: Int) {
        if (chapter.value == null) return
        val index = currentChapterIndex.value + delta
        val url = chapters.value.getOrNull(index)?.url ?: return
        loadChapter(url, restorePosition = false)
    }

    fun hasNextChapter(): Boolean = chapters.value.getOrNull(currentChapterIndex.value + 1) != null
    fun hasPrevChapter(): Boolean = chapters.value.getOrNull(currentChapterIndex.value - 1) != null

    /** Страница стала текущей: обновить индикатор + сохранить позицию. */
    fun setCurrentPage(page: Int) {
        if (page == currentPage.value) return
        val chapter = chapter.value ?: return
        if (page !in 0 until chapter.pageCount) return
        currentPage.value = page
        persistPosition(chapter.url, page)
    }

    private fun persistPosition(chapterUrl: String, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { bookChaptersRepository.updatePosition(chapterUrl, page, 0) }
        }
    }

    /** Глава дочитана до конца (последняя страница) — отметить прочитанной. */
    fun markChapterRead(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { bookChaptersRepository.setAsRead(url, true) }
        }
    }

    /** Сохранение при закрытии: last-read-chapter + позиция + история. */
    fun saveState() {
        val chapter = chapter.value ?: return
        readerRepository.saveBookLastReadPositionState(
            bookUrl = bookUrl,
            newChapter = ChapterState(chapter.url, currentPage.value, 0),
        )
    }

    private fun buildSettingsState(): MangaReaderSettingsState = MangaReaderSettingsState(
        readingMode = MangaReadingMode.fromPreference(appPreferences.MANGA_READER_READING_MODE.value),
        orientation = MangaReaderOrientation.fromPreference(appPreferences.MANGA_READER_ORIENTATION.value),
        transitionsPager = appPreferences.MANGA_READER_TRANSITIONS_PAGER.value,
        transitionsWebtoon = appPreferences.MANGA_READER_TRANSITIONS_WEBTOON.value,
        showPageNumber = appPreferences.MANGA_READER_SHOW_PAGE_NUMBER.value,
        keepScreenOn = appPreferences.MANGA_READER_KEEP_SCREEN_ON.value,
        fullscreen = appPreferences.MANGA_READER_FULLSCREEN.value,
        webtoonSidePadding = appPreferences.MANGA_READER_WEBTOON_SIDE_PADDING.value,
        zoomStart = MangaZoomStart.fromPreference(appPreferences.MANGA_READER_ZOOM_START.value),
        navModePager = MangaNavigationMode.fromPreference(appPreferences.MANGA_READER_NAV_MODE_PAGER.value),
        navModeWebtoon = MangaNavigationMode.fromPreference(appPreferences.MANGA_READER_NAV_MODE_WEBTOON.value),
        tappingInvertedPager = MangaTappingInvertMode.fromStorage(appPreferences.MANGA_READER_TAPPING_INVERTED_PAGER.value),
        tappingInvertedWebtoon = MangaTappingInvertMode.fromStorage(appPreferences.MANGA_READER_TAPPING_INVERTED_WEBTOON.value),
        longTap = appPreferences.MANGA_READER_LONG_TAP.value,
        colorFilterEnabled = appPreferences.MANGA_READER_COLOR_FILTER.value,
        colorFilterValue = appPreferences.MANGA_READER_COLOR_FILTER_VALUE.value,
        colorFilterMode = appPreferences.MANGA_READER_COLOR_FILTER_MODE.value,
        grayscale = appPreferences.MANGA_READER_GRAYSCALE.value,
        invertedColors = appPreferences.MANGA_READER_INVERTED_COLORS.value,
        autoscrollEnabled = appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value,
        autoscrollSpeed = appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value,
        autoscrollSmooth = appPreferences.MANGA_READER_AUTOSCROLL_SMOOTH.value,
    )

    private inner class MangaReaderSettingsActionsImpl : MangaReaderSettingsActions {
        override fun setReadingMode(mode: MangaReadingMode) {
            appPreferences.MANGA_READER_READING_MODE.value = mode.flagValue
            settings.value = buildSettingsState()
        }

        override fun setOrientation(orientation: MangaReaderOrientation) {
            appPreferences.MANGA_READER_ORIENTATION.value = orientation.flagValue
            settings.value = buildSettingsState()
        }

        override fun setTransitionsPager(enabled: Boolean) {
            appPreferences.MANGA_READER_TRANSITIONS_PAGER.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setTransitionsWebtoon(enabled: Boolean) {
            appPreferences.MANGA_READER_TRANSITIONS_WEBTOON.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setShowPageNumber(show: Boolean) {
            appPreferences.MANGA_READER_SHOW_PAGE_NUMBER.value = show
            settings.value = buildSettingsState()
        }

        override fun setKeepScreenOn(enabled: Boolean) {
            appPreferences.MANGA_READER_KEEP_SCREEN_ON.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setFullscreen(enabled: Boolean) {
            appPreferences.MANGA_READER_FULLSCREEN.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setWebtoonSidePadding(padding: Int) {
            appPreferences.MANGA_READER_WEBTOON_SIDE_PADDING.value = padding
            settings.value = buildSettingsState()
        }

        override fun setZoomStart(zoomStart: MangaZoomStart) {
            appPreferences.MANGA_READER_ZOOM_START.value = zoomStart.value
            settings.value = buildSettingsState()
        }

        override fun setNavModePager(mode: MangaNavigationMode) {
            appPreferences.MANGA_READER_NAV_MODE_PAGER.value = mode.value
            settings.value = buildSettingsState()
        }

        override fun setNavModeWebtoon(mode: MangaNavigationMode) {
            appPreferences.MANGA_READER_NAV_MODE_WEBTOON.value = mode.value
            settings.value = buildSettingsState()
        }

        override fun setTappingInvertedPager(mode: MangaTappingInvertMode) {
            appPreferences.MANGA_READER_TAPPING_INVERTED_PAGER.value = mode.storageKey
            settings.value = buildSettingsState()
        }

        override fun setTappingInvertedWebtoon(mode: MangaTappingInvertMode) {
            appPreferences.MANGA_READER_TAPPING_INVERTED_WEBTOON.value = mode.storageKey
            settings.value = buildSettingsState()
        }

        override fun setLongTap(enabled: Boolean) {
            appPreferences.MANGA_READER_LONG_TAP.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setColorFilterEnabled(enabled: Boolean) {
            appPreferences.MANGA_READER_COLOR_FILTER.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setColorFilterValue(value: Int) {
            appPreferences.MANGA_READER_COLOR_FILTER_VALUE.value = value
            settings.value = buildSettingsState()
        }

        override fun setColorFilterMode(mode: Int) {
            appPreferences.MANGA_READER_COLOR_FILTER_MODE.value = mode
            settings.value = buildSettingsState()
        }

        override fun setGrayscale(enabled: Boolean) {
            appPreferences.MANGA_READER_GRAYSCALE.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setInvertedColors(enabled: Boolean) {
            appPreferences.MANGA_READER_INVERTED_COLORS.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setAutoscrollEnabled(enabled: Boolean) {
            appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.value = enabled
            settings.value = buildSettingsState()
        }

        override fun setAutoscrollSpeed(speed: Int) {
            appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value = speed
            settings.value = buildSettingsState()
        }

        override fun setAutoscrollSmooth(enabled: Boolean) {
            appPreferences.MANGA_READER_AUTOSCROLL_SMOOTH.value = enabled
            settings.value = buildSettingsState()
        }

    }
}