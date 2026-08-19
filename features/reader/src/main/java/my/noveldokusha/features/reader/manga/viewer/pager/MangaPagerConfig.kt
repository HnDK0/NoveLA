package my.noveldokusha.features.reader.manga.viewer.pager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import my.noveldokusha.features.reader.manga.viewer.BothNavigation
import my.noveldokusha.features.reader.manga.viewer.MangaPageImageView
import my.noveldokusha.features.reader.manga.viewer.SwipeNavigation
import my.noveldokusha.features.reader.manga.viewer.TapEdgesNavigation
import my.noveldokusha.features.reader.manga.viewer.ViewerConfig
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation

/**
 * Конфигурация пейджера — порт tachiyomisy PagerConfig (без dual-page и
 * crop borders). Настройки подписаны на AppPreferences.
 */
internal class MangaPagerConfig(
    private val viewer: MangaPagerViewer,
    appPreferences: AppPreferences,
    scope: CoroutineScope,
) : ViewerConfig(appPreferences, scope) {

    var usePageTransitions = true
        private set

    /**
     * Решённая zoom-start позиция (AUTOMATIC → по направлению чтения).
     * 0=AUTOMATIC, 1=LEFT, 2=CENTER, 3=RIGHT (MangaZoomStart.value).
     */
    var imageZoomStart: MangaZoomStart = MangaZoomStart.CENTER
        private set

    /** Сколько страниц вперёд грузить заранее. */
    var prefetchCount = 8
        private set

    // Должен быть объявлен ДО init-блока: подписки на AppPreferences ниже
    // могут синхронно эмитить первое значение (StateFlow + Main.immediate),
    // и обращение к ещё не инициализированному полю дало бы NPE.
    override var navigator: ViewerNavigation = defaultNavigation()

    /** Инверсия тап-зон (левый край = вперёд, правый = назад). */
    private var tappingInverted = false

    override fun defaultNavigation(): ViewerNavigation = BothNavigation()

    init {
        appPreferences.MANGA_READER_TRANSITIONS_PAGER.flow()
            .onEach { usePageTransitions = it }
            .launchIn(scope)

        appPreferences.READER_PAGE_PREFETCH_COUNT.flow()
            .onEach { prefetchCount = it }
            .launchIn(scope)

        appPreferences.MANGA_READER_ZOOM_START.flow()
            .onEach { imageZoomStart = zoomStartFromPreference(it) }
            .launchIn(scope)

        appPreferences.MANGA_READER_NAV_MODE_PAGER.flow()
            .onEach {
                navigationMode = it
                updateNavigation(it)
            }
            .launchIn(scope)

        appPreferences.MANGA_READER_TAPPING_INVERTED.flow()
            .onEach {
                tappingInverted = it
                updateNavigation(navigationMode)
            }
            .launchIn(scope)

        appPreferences.READER_SINGLE_TAP_TO_OPEN_SETTINGS.flow()
            .onEach { singleTapToOpenSettings = it }
            .launchIn(scope)
    }

    override fun updateNavigation(navigationMode: Int) {
        val mode = MangaNavigationMode.fromPreference(navigationMode)
        navigator = when (mode) {
            MangaNavigationMode.SWIPE -> SwipeNavigation()
            MangaNavigationMode.TAP_EDGES -> TapEdgesNavigation(tappingInverted)
            MangaNavigationMode.BOTH -> BothNavigation(tappingInverted)
        }
        // TAP_EDGES: только тапы по краям — свайп-драги пейджера отключены.
        viewer.pager.dragEnabled = mode != MangaNavigationMode.TAP_EDGES
        navigationModeChangedListener?.invoke()
    }

    /** 0=AUTOMATIC (по направлению чтения), 1=LEFT, 2=CENTER, 3=RIGHT. */
    private fun zoomStartFromPreference(value: Int): MangaZoomStart {
        return when (value) {
            0 -> when (viewer) {
                is MangaR2LPagerViewer -> MangaZoomStart.RIGHT
                else -> MangaZoomStart.CENTER
            }
            1 -> MangaZoomStart.LEFT
            2 -> MangaZoomStart.CENTER
            3 -> MangaZoomStart.RIGHT
            else -> MangaZoomStart.CENTER
        }
    }

    /**
     * SSIV-конфиг страницы пейджера. Двойной тап занят открытием меню
     * (onDoubleTap вьюера), поэтому double-tap-зум SSIV отключён — масштаб
     * по-прежнему доступен пинчем.
     */
    fun imageConfig(): MangaPageImageView.Config = MangaPageImageView.Config(
        zoomStart = imageZoomStart,
        zoomAnimationDuration = doubleTapAnimDuration,
        doubleTapZoomEnabled = false,
    )
}
