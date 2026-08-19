package my.noveldokusha.features.reader.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.ChapterBodyRepository
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.features.reader.ReaderRepository
import my.noveldokusha.features.reader.domain.ChapterState
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.features.reader.manga.ui.MangaReaderSettingsActions
import my.noveldokusha.features.reader.manga.ui.MangaReaderSettingsState
import timber.log.Timber
import javax.inject.Inject

/** Состояние экрана манга-читалки (MVI). */
internal sealed interface MangaReaderUiState {
    data object Loading : MangaReaderUiState
    data object Error : MangaReaderUiState

    data class Ready(
        val bookUrl: String,
        val bookTitle: String?,
        /** Упорядоченный список глав книги (для навигации и заголовков). */
        val chapters: List<Chapter>,
        /** Текущая глава со страницами. */
        val chapter: MangaChapter,
        val currentChapterIndex: Int,
        /** Страница внутри текущей главы. */
        val currentPage: Int,
        /** Инкремент при каждой успешной загрузке главы — ключ рекомпозиции вьюера. */
        val chapterLoadId: Int,
        val isEndOfBook: Boolean,
    ) : MangaReaderUiState
}

/** Одноразовые события экрана. */
internal sealed interface MangaReaderEvent {
    data object EndOfBook : MangaReaderEvent
    data object InvalidChapter : MangaReaderEvent
}

/**
 * MVI-логика манга/манхва-читалки (порт tachiyomisy, адаптация под NoveLA).
 *
 * Гонки init (Bug1a): `init` фиксирует `bookUrl` и целевой URL главы СИНХРОННО,
 * до запуска корутины загрузки. Повторный init игнорируется (идемпотентен).
 *
 * Latest-wins: каждый вызов [loadChapter] отменяет предыдущий in-flight job и
 * перезаписывает [lastChapterUrl]; устаревшие корутины, не успевшие отмениться
 * кооперативно, сами отбрасывают свои записи сверкой `url == lastChapterUrl`
 * перед каждой записью состояния.
 *
 * Retry (6.5): сетевая ошибка не роняет последнее успешное состояние —
 * `Error` выставляется только если успешного состояния ещё не было.
 */
@HiltViewModel
internal class MangaReaderViewModel @Inject constructor(
    private val readerRepository: ReaderRepository,
    private val bookChaptersRepository: BookChaptersRepository,
    private val appRepository: AppRepository,
    private val appPreferences: AppPreferences,
    private val downloadedPageChaptersStore: DownloadedPageChaptersStore,
) : ViewModel() {

    private lateinit var bookUrl: String

    /** URL главы, загруженной ПОСЛЕДНЕЙ (latest-wins; сверка в корутинах). */
    private var lastChapterUrl: String = ""

    /** In-flight job загрузки главы: новый вызов отменяет предыдущий. */
    private var loadJob: Job? = null

    /**
     * Кэш глав, построенных [buildChapter] для мультиглавного окна вебтуна.
     * Используется [syncChapter]: при переходе окна на соседнюю главу страницы
     * берутся из кэша, чтобы не перезагружать их и не сбрасывать вьюер.
     * Устаревшие записи всегда перезаписываются при следующем buildChapter
     * (тот же URL), поэтому очистка не требуется.
     */
    private val windowChapterCache = mutableMapOf<String, MangaChapter>()

    private val _uiState = MutableStateFlow<MangaReaderUiState>(MangaReaderUiState.Loading)
    val uiState: StateFlow<MangaReaderUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(buildSettingsState())
    val settings: StateFlow<MangaReaderSettingsState> = _settings.asStateFlow()

    private val _events = MutableSharedFlow<MangaReaderEvent>(replay = 1, extraBufferCapacity = 8)
    val events: SharedFlow<MangaReaderEvent> = _events.asSharedFlow()

    val actions: MangaReaderSettingsActions = MangaReaderSettingsActionsImpl()

    /**
     * Первичная инициализация: главы книги → идентичность (заголовок) →
     * целевая глава → восстановление позиции. Идемпотентна — повторный вызов
     * игнорируется, чтобы вьюер не перезапускал загрузку при рекомпозиции.
     */
    fun init(bookUrl: String, chapterUrl: String) {
        if (::bookUrl.isInitialized) return
        this.bookUrl = bookUrl
        Timber.d("MangaReaderLoad: init bookUrl=%s chapterUrl=%s", bookUrl, chapterUrl)
        loadChapter(chapterUrl, restorePosition = true)
    }

    /**
     * Загрузка главы: getPageList → MangaChapter. Latest-wins: предыдущий
     * job отменяется, а устаревшие корутины отбрасывают записи сверкой с
     * [lastChapterUrl] (отмена кооперативна, одной её недостаточно).
     *
     * [startPageOverride] — явная стартовая страница (например, переход
     * назад в pager открывает ПОСЛЕДНЮЮ страницу предыдущей главы);
     * coerceIn в [loadChapterInternal] срежет её в границы главы.
     */
    fun loadChapter(url: String, restorePosition: Boolean = false, startPageOverride: Int? = null) {
        Timber.d(
            "MangaReaderLoad: loadChapter url=%s restorePosition=%b startPageOverride=%s",
            url, restorePosition, startPageOverride,
        )
        lastChapterUrl = url
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadChapterInternal(url, restorePosition, startPageOverride)
        }
    }

    private suspend fun loadChapterInternal(
        url: String,
        restorePosition: Boolean,
        startPageOverride: Int? = null,
    ) {
        if (url != lastChapterUrl) return
        val bookUrl = this.bookUrl
        val bookTitle = runCatching { appRepository.libraryBooks.get(bookUrl)?.title }.getOrNull()
        val chapters = runCatching { bookChaptersRepository.chapters(bookUrl) }.getOrNull() ?: emptyList()
        if (url != lastChapterUrl) return

        val meta = chapters.firstOrNull { it.url == url }
        Timber.d(
            "MangaReaderLoad: loadChapterInternal url=%s bookUrl=%s chapters.size=%d metaFound=%b",
            url, bookUrl, chapters.size, meta != null,
        )
        if (meta == null) {
            // Главы нет в списке книги — такой главы не существует (текстовая глава
            // в манга-ридере или битая ссылка). Ничего не открываем, без фолбэка на главу 1.
            Timber.w("MangaReaderLoad: InvalidChapter — url not in chapters list: %s", url)
            _events.tryEmit(MangaReaderEvent.InvalidChapter)
            return
        }
        val index = chapters.indexOfFirst { it.url == url }

        val response = appRepository.chapterBody.fetchPages(url)
        if (url != lastChapterUrl) return
        when (response) {
            is Response.Success -> {
                val pages = response.data
                Timber.d("MangaReaderLoad: fetchPages OK url=%s pages=%d", url, pages.size)
                if (pages.isEmpty()) {
                    // getPageList вернул пусто — текстовая глава попала в манга-ридер.
                    Timber.w("MangaReaderLoad: InvalidChapter — empty pages: %s", url)
                    _events.tryEmit(MangaReaderEvent.InvalidChapter)
                    return
                }
                val savedPage = meta.lastReadPosition
                val startPage = when {
                    // Явная стартовая страница (переход назад в pager — последняя
                    // страница предыдущей главы); Int.MAX_VALUE срезается в последнюю.
                    startPageOverride != null ->
                        startPageOverride.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    restorePosition -> savedPage
                    else -> 0
                }
                val chapter = MangaChapter(
                    url = url,
                    title = meta.title,
                    index = index,
                    pages = pages.mapIndexed { i, pageUrl -> MangaPage(pageUrl, i) },
                    startPage = startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                )
                val previous = _uiState.value as? MangaReaderUiState.Ready
                _uiState.value = MangaReaderUiState.Ready(
                    bookUrl = bookUrl,
                    bookTitle = bookTitle,
                    chapters = chapters,
                    chapter = chapter,
                    currentChapterIndex = index,
                    currentPage = chapter.startPage,
                    chapterLoadId = (previous?.chapterLoadId ?: 0) + 1,
                    isEndOfBook = index == chapters.lastIndex,
                )
                Timber.d(
                    "MangaReaderLoad: Ready url=%s index=%d/%d startPage=%d pages=%d",
                    url, index, chapters.lastIndex, chapter.startPage, pages.size,
                )

                // Промоушен открытой главы в постоянное хранилище (download on open).
                if (appPreferences.MANGA_READER_DOWNLOAD_ON_OPEN.value) {
                    viewModelScope.launch {
                        runCatching { downloadedPageChaptersStore.downloadChapter(url, pages) }
                    }
                }
            }
            is Response.Error -> {
                // 6.5: сетевая ошибка не роняет последнее успешное состояние.
                Timber.w("MangaReaderLoad: Error url=%s exception=%s", url, response.exception)
                if (_uiState.value !is MangaReaderUiState.Ready) {
                    _uiState.value = MangaReaderUiState.Error
                }
            }
        }
    }

    /** Повторная попытка после ошибки загрузки главы. */
    fun retryChapter() {
        if (lastChapterUrl.isNotEmpty()) {
            loadChapter(lastChapterUrl, restorePosition = false)
        }
    }

    /**
     * Смена главы по смещению (delta = ±1), без восстановления позиции.
     * За границами книги — явное событие «конец книги»; без Ready-состояния
     * событие не шлётся (защита от ложного конца при ещё не загруженной главе).
     */
    fun moveChapter(delta: Int) {
        val state = _uiState.value
        if (state !is MangaReaderUiState.Ready) return
        if (state.chapters.isEmpty()) return
        val index = state.currentChapterIndex + delta
        val url = state.chapters.getOrNull(index)?.url
        Timber.d(
            "MangaReaderLoad: moveChapter delta=%d currentIndex=%d targetIndex=%d url=%s",
            delta, state.currentChapterIndex, index, url,
        )
        if (url == null) {
            _events.tryEmit(MangaReaderEvent.EndOfBook)
            return
        }
        loadChapter(url, restorePosition = false)
    }

    /** Обновление текущей страницы; события устаревшего вьюера игнорируются. */
    fun setCurrentPage(page: Int, chapterUrl: String) {
        val state = _uiState.value
        if (state !is MangaReaderUiState.Ready) return
        if (state.chapter.url != chapterUrl) return
        _uiState.value = state.copy(currentPage = page)
        viewModelScope.launch {
            runCatching { bookChaptersRepository.updatePosition(chapterUrl, page, 0) }
        }
    }

    /** Сохранение позиции чтения (onPause). */
    fun saveState() {
        val state = _uiState.value
        if (state !is MangaReaderUiState.Ready) return
        readerRepository.saveBookLastReadPositionState(
            bookUrl = state.bookUrl,
            newChapter = ChapterState(state.chapter.url, state.currentPage, 0),
        )
    }

    /** Глава дочитана (достигнут её конец). */
    fun markChapterRead(chapterUrl: String) {
        viewModelScope.launch {
            runCatching { bookChaptersRepository.setAsRead(chapterUrl, true) }
        }
    }

    /**
     * Строит [MangaChapter] для СОСЕДНЕЙ главы (мультиглавное окно вебтуна)
     * БЕЗ мутации состояния: не трогает loadJob/lastChapterUrl/currentPage.
     * Страницы грузятся как у prefetch (вне активной загрузки).
     */
    suspend fun buildChapter(url: String): MangaChapter? {
        // Уже построенная глава (окно расширялось ранее): без повторной сети.
        windowChapterCache[url]?.let { return it }
        // Книгу определяем по записи самой главы: buildChapter вызывается и до init
        // (окно вебтуна расширяется независимо от основной загрузки).
        val meta = bookChaptersRepository.get(url) ?: run {
            Timber.w("MangaReaderLoad: buildChapter meta not found url=%s", url)
            return null
        }
        val chapters = runCatching { bookChaptersRepository.chapters(meta.bookUrl) }.getOrNull() ?: return null
        val index = chapters.indexOfFirst { it.url == url }.takeIf { it >= 0 } ?: return null
        val urls = runCatching { appRepository.chapterBody.fetchPages(url) }
            .getOrNull()?.toSuccessOrNull()?.data ?: run {
            Timber.w("MangaReaderLoad: buildChapter fetchPages failed url=%s", url)
            return null
        }
        Timber.d("MangaReaderLoad: buildChapter url=%s index=%d pages=%d", url, index, urls.size)
        return MangaChapter(
            url = url,
            title = meta.title,
            index = index,
            pages = urls.mapIndexed { i, pageUrl -> MangaPage(pageUrl, i) },
            startPage = 0,
        ).also { windowChapterCache[url] = it }
    }

    /**
     * Синхронизация состояния с мультиглавным окном вебтуна: окно перешло на
     * соседнюю главу ([chapterUrl], [chapterIndex]) и показывает страницу [page].
     * Обновляет Ready-состояние НА МЕСТЕ, без загрузки и без инкремента
     * [MangaReaderUiState.Ready.chapterLoadId] — вьюер не пересоздаётся и не
     * «выбрасывает» читателя из окна. Страницы берутся из [windowChapterCache]
     * (окно строит главу заранее), иначе — из [pages], переданных вьюером
     * (окно всегда знает страницы главы, даже если кэш VM не пополнялся).
     */
    fun syncChapter(chapterUrl: String, chapterIndex: Int, page: Int, pages: List<MangaPage>) {
        val state = _uiState.value
        if (state !is MangaReaderUiState.Ready) return
        val meta = state.chapters.firstOrNull { it.url == chapterUrl }
        Timber.d(
            "MangaReaderLoad: syncChapter url=%s index=%d page=%d pages=%d metaFound=%b cacheHit=%b",
            chapterUrl, chapterIndex, page, pages.size, meta != null, windowChapterCache.containsKey(chapterUrl),
        )
        if (meta == null) return
        val chapter = when {
            windowChapterCache.containsKey(chapterUrl) ->
                windowChapterCache.getValue(chapterUrl).copy(startPage = page)
            state.chapter.url == chapterUrl -> state.chapter.copy(startPage = page)
            // Запасной путь: страницы из окна вьюера. Без них счётчик показывал
            // бы «X из 0», а onPageChanged(0) дедуплицировался (page == currentIndex).
            else -> MangaChapter(chapterUrl, meta.title, chapterIndex, pages, startPage = page)
        }
        _uiState.value = state.copy(
            chapter = chapter,
            currentChapterIndex = chapterIndex,
            currentPage = page,
            isEndOfBook = chapterIndex == state.chapters.lastIndex,
        )
        viewModelScope.launch {
            runCatching { bookChaptersRepository.updatePosition(chapterUrl, page, 0) }
        }
    }

    fun hasNextChapter(): Boolean {
        val state = _uiState.value
        return state is MangaReaderUiState.Ready && state.currentChapterIndex < state.chapters.lastIndex
    }

    fun hasPrevChapter(): Boolean {
        val state = _uiState.value
        return state is MangaReaderUiState.Ready && state.currentChapterIndex > 0
    }

    // ---- settings ----

    private fun buildSettingsState(): MangaReaderSettingsState = MangaReaderSettingsState(
        readingMode = MangaReadingMode.fromPreference(appPreferences.MANGA_READER_READING_MODE.value),
        transitionsPager = appPreferences.MANGA_READER_TRANSITIONS_PAGER.value,
        transitionsWebtoon = appPreferences.MANGA_READER_TRANSITIONS_WEBTOON.value,
        showPageNumber = appPreferences.MANGA_READER_SHOW_PAGE_NUMBER.value,
        keepScreenOn = appPreferences.MANGA_READER_KEEP_SCREEN_ON.value,
        fullscreen = appPreferences.MANGA_READER_FULLSCREEN.value,
        webtoonSidePadding = appPreferences.MANGA_READER_WEBTOON_SIDE_PADDING.value,
        navModePager = MangaNavigationMode.fromPreference(appPreferences.MANGA_READER_NAV_MODE_PAGER.value),
        navModeWebtoon = MangaNavigationMode.fromPreference(appPreferences.MANGA_READER_NAV_MODE_WEBTOON.value),
        tappingInverted = appPreferences.MANGA_READER_TAPPING_INVERTED.value,
        singleTapToOpenSettings = appPreferences.READER_SINGLE_TAP_TO_OPEN_SETTINGS.value,
        downloadOnOpen = appPreferences.MANGA_READER_DOWNLOAD_ON_OPEN.value,
        colorFilterEnabled = appPreferences.MANGA_READER_COLOR_FILTER.value,
        colorFilterValue = appPreferences.MANGA_READER_COLOR_FILTER_VALUE.value,
        colorFilterMode = appPreferences.MANGA_READER_COLOR_FILTER_MODE.value,
        customBrightness = appPreferences.MANGA_READER_CUSTOM_BRIGHTNESS.value,
        customBrightnessValue = appPreferences.MANGA_READER_CUSTOM_BRIGHTNESS_VALUE.value,
        grayscale = appPreferences.MANGA_READER_GRAYSCALE.value,
        invertedColors = appPreferences.MANGA_READER_INVERTED_COLORS.value,
    )

    /** Сеттеры настроек: пишут в AppPreferences и пересобирают state. */
    private inner class MangaReaderSettingsActionsImpl : MangaReaderSettingsActions {
        private fun <T> set(pref: AppPreferences.Preference<T>, value: T) {
            pref.value = value
            _settings.value = buildSettingsState()
        }

        override fun setReadingMode(mode: MangaReadingMode) =
            set(appPreferences.MANGA_READER_READING_MODE, mode.flagValue)

        override fun setTransitionsPager(enabled: Boolean) =
            set(appPreferences.MANGA_READER_TRANSITIONS_PAGER, enabled)

        override fun setTransitionsWebtoon(enabled: Boolean) =
            set(appPreferences.MANGA_READER_TRANSITIONS_WEBTOON, enabled)

        override fun setShowPageNumber(show: Boolean) =
            set(appPreferences.MANGA_READER_SHOW_PAGE_NUMBER, show)

        override fun setKeepScreenOn(enabled: Boolean) =
            set(appPreferences.MANGA_READER_KEEP_SCREEN_ON, enabled)

        override fun setFullscreen(enabled: Boolean) =
            set(appPreferences.MANGA_READER_FULLSCREEN, enabled)

        override fun setWebtoonSidePadding(padding: Int) =
            set(appPreferences.MANGA_READER_WEBTOON_SIDE_PADDING, padding)

        override fun setNavModePager(mode: MangaNavigationMode) =
            set(appPreferences.MANGA_READER_NAV_MODE_PAGER, mode.value)

        override fun setNavModeWebtoon(mode: MangaNavigationMode) =
            set(appPreferences.MANGA_READER_NAV_MODE_WEBTOON, mode.value)

        override fun setTappingInverted(enabled: Boolean) =
            set(appPreferences.MANGA_READER_TAPPING_INVERTED, enabled)

        override fun setSingleTapToOpenSettings(enabled: Boolean) =
            set(appPreferences.READER_SINGLE_TAP_TO_OPEN_SETTINGS, enabled)

        override fun setDownloadOnOpen(enabled: Boolean) =
            set(appPreferences.MANGA_READER_DOWNLOAD_ON_OPEN, enabled)

        override fun setColorFilterEnabled(enabled: Boolean) =
            set(appPreferences.MANGA_READER_COLOR_FILTER, enabled)

        override fun setColorFilterValue(value: Int) =
            set(appPreferences.MANGA_READER_COLOR_FILTER_VALUE, value)

        override fun setColorFilterMode(mode: Int) =
            set(appPreferences.MANGA_READER_COLOR_FILTER_MODE, mode)

        override fun setCustomBrightness(enabled: Boolean) =
            set(appPreferences.MANGA_READER_CUSTOM_BRIGHTNESS, enabled)

        override fun setCustomBrightnessValue(value: Int) =
            set(appPreferences.MANGA_READER_CUSTOM_BRIGHTNESS_VALUE, value)

        override fun setGrayscale(enabled: Boolean) =
            set(appPreferences.MANGA_READER_GRAYSCALE, enabled)

        override fun setInvertedColors(enabled: Boolean) =
            set(appPreferences.MANGA_READER_INVERTED_COLORS, enabled)
    }
}
