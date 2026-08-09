package my.noveldokusha.features.reader.manga.viewer.pager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaTappingInvertMode
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import my.noveldokusha.features.reader.manga.viewer.MangaPageImageView
import my.noveldokusha.features.reader.manga.viewer.ViewerConfig
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.DisabledNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.EdgeNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.KindlishNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.LNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.RightAndLeftNavigation

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

        appPreferences.MANGA_READER_DOUBLE_TAP_ANIM_SPEED.flow()
            .onEach { doubleTapAnimDuration = it }
            .launchIn(scope)

        appPreferences.MANGA_READER_NAV_MODE_PAGER.flow()
            .onEach {
                navigationMode = it
                updateNavigation(it)
            }
            .launchIn(scope)

        appPreferences.MANGA_READER_TAPPING_INVERTED_PAGER.flow()
            .onEach {
                tappingInverted = MangaTappingInvertMode.fromStorage(it)
                navigator.invertMode = tappingInverted
            }
            .launchIn(scope)

        appPreferences.MANGA_READER_VOLUME_KEYS.flow()
            .onEach { volumeKeysEnabled = it }
            .launchIn(scope)

        appPreferences.MANGA_READER_VOLUME_KEYS_INVERTED.flow()
            .onEach { volumeKeysInverted = it }
            .launchIn(scope)

        appPreferences.MANGA_READER_LONG_TAP.flow()
            .onEach { longTapEnabled = it }
            .launchIn(scope)
    }

    override var navigator: ViewerNavigation = defaultNavigation()
        set(value) {
            field = value.also { it.invertMode = this.tappingInverted }
        }

    override fun defaultNavigation(): ViewerNavigation {
        return when (viewer) {
            is MangaVerticalPagerViewer -> LNavigation()
            else -> RightAndLeftNavigation()
        }
    }

    override fun updateNavigation(navigationMode: Int) {
        navigator = when (MangaNavigationMode.fromPreference(navigationMode)) {
            MangaNavigationMode.DEFAULT -> defaultNavigation()
            MangaNavigationMode.L -> LNavigation()
            MangaNavigationMode.KINDLISH -> KindlishNavigation()
            MangaNavigationMode.EDGE -> EdgeNavigation()
            MangaNavigationMode.RIGHT_AND_LEFT -> RightAndLeftNavigation()
            MangaNavigationMode.DISABLED -> DisabledNavigation()
        }
        navigationModeChangedListener?.invoke()
    }

    /** 0=AUTOMATIC (по направлению чтения), 1=LEFT, 2=CENTER, 3=RIGHT. */
    private fun zoomStartFromPreference(value: Int): MangaZoomStart {
        return when (value) {
            0 -> when (viewer) {
                is MangaL2RPagerViewer -> MangaZoomStart.LEFT
                is MangaR2LPagerViewer -> MangaZoomStart.RIGHT
                else -> MangaZoomStart.CENTER
            }
            1 -> MangaZoomStart.LEFT
            2 -> MangaZoomStart.CENTER
            3 -> MangaZoomStart.RIGHT
            else -> MangaZoomStart.CENTER
        }
    }

    /** SSIV-конфиг страницы пейджера (zoom-start + длительность double-tap). */
    fun imageConfig(): MangaPageImageView.Config = MangaPageImageView.Config(
        zoomStart = imageZoomStart,
        zoomAnimationDuration = doubleTapAnimDuration,
    )
}