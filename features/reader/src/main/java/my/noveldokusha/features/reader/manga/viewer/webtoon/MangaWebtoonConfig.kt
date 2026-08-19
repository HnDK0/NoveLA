package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.util.TypedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import my.noveldokusha.features.reader.manga.viewer.BothNavigation
import my.noveldokusha.features.reader.manga.viewer.SwipeNavigation
import my.noveldokusha.features.reader.manga.viewer.TapEdgesNavigation
import my.noveldokusha.features.reader.manga.viewer.ViewerConfig
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation

/**
 * Конфигурация вебтун-вьюера — порт tachiyomisy WebtoonConfig.
 * Все настройки подписаны на AppPreferences (flow в [scope]).
 * Зум всей ленты (strip-zoom) в NoveLA не портирован — страницы
 * не принимают тачи (touchEnabled=false), как WebtoonSubsamplingImageView
 * в tachiyomisy (onTouchEvent всегда false).
 */
internal class MangaWebtoonConfig(
    appPreferences: AppPreferences,
    scope: CoroutineScope,
) : ViewerConfig(appPreferences, scope) {

    /** Боковые отступы ленты в px (в настройках — dp). */
    var sidePaddingPx = 0
        private set

    /** Плавный скролл по тапу/клавишам (иначе — мгновенный). */
    var transitionsEnabled = true
        private set

    /** Стартовая позиция зума (пейджера; у ленты — fit-width). */
    var zoomStart = MangaZoomStart.AUTOMATIC
        private set

    /** Инверсия тап-зон (левый край = вперёд, правый = назад). */
    private var tappingInverted = false

    init {
        appPreferences.MANGA_READER_NAV_MODE_WEBTOON.flow()
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

        appPreferences.MANGA_READER_WEBTOON_SIDE_PADDING.flow()
            .onEach {
                sidePaddingPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    it.toFloat(),
                    android.content.res.Resources.getSystem().displayMetrics,
                ).toInt()
                imagePropertyChangedListener?.invoke()
            }
            .launchIn(scope)

        appPreferences.MANGA_READER_TRANSITIONS_WEBTOON.flow()
            .onEach { transitionsEnabled = it }
            .launchIn(scope)

        appPreferences.MANGA_READER_ZOOM_START.flow()
            .onEach { zoomStart = MangaZoomStart.fromPreference(it) }
            .launchIn(scope)
    }

    override var navigator: ViewerNavigation = defaultNavigation()

    /** По умолчанию — листание + тапы (семантические зоны PREV/NEXT). */
    override fun defaultNavigation(): ViewerNavigation = BothNavigation()

    override fun updateNavigation(navigationMode: Int) {
        navigator = when (MangaNavigationMode.fromPreference(navigationMode)) {
            MangaNavigationMode.SWIPE -> SwipeNavigation()
            MangaNavigationMode.TAP_EDGES -> TapEdgesNavigation(tappingInverted)
            MangaNavigationMode.BOTH -> BothNavigation(tappingInverted)
        }
        navigationModeChangedListener?.invoke()
    }
}
