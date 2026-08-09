package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.util.TypedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaTappingInvertMode
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import my.noveldokusha.features.reader.manga.viewer.ViewerConfig
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.DisabledNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.EdgeNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.KindlishNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.LNavigation
import my.noveldokusha.features.reader.manga.viewer.navigation.RightAndLeftNavigation

/**
 * Конфигурация вебтун-вьюера — порт tachiyomisy WebtoonConfig.
 * Все настройки подписаны на AppPreferences (flow в [scope]).
 * Зум всей ленты (strip-zoom) в NoveLA не портирован — страницы
 * не принимают тачи (touchEnabled=false), поэтому doubleTapZoom/
 * disableZoomOut хранятся как поля конфигурации для API-паритета.
 */
internal class MangaWebtoonConfig(
    appPreferences: AppPreferences,
    scope: CoroutineScope,
) : ViewerConfig(appPreferences, scope) {

    /** Боковые отступы ленты в px (в настройках — dp). */
    var sidePaddingPx = 0
        private set

    var doubleTapZoomEnabled = true
        private set

    var disableZoomOut = false
        private set

    /** Плавный скролл по тапу/клавишам (иначе — мгновенный). */
    var transitionsEnabled = true
        private set

    /** Стартовая позиция зума (пейджера; у ленты — fit-width). */
    var zoomStart = MangaZoomStart.AUTOMATIC
        private set

    init {
        appPreferences.MANGA_READER_TAPPING_INVERTED_WEBTOON.flow()
            .onEach {
                tappingInverted = MangaTappingInvertMode.fromStorage(it)
                navigator.invertMode = tappingInverted
            }
            .launchIn(scope)

        appPreferences.MANGA_READER_NAV_MODE_WEBTOON.flow()
            .onEach {
                navigationMode = it
                updateNavigation(it)
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

        appPreferences.MANGA_READER_DOUBLE_TAP_ANIM_SPEED.flow()
            .onEach { doubleTapAnimDuration = it }
            .launchIn(scope)

        appPreferences.MANGA_READER_WEBTOON_DOUBLE_TAP_ZOOM.flow()
            .onEach { doubleTapZoomEnabled = it }
            .launchIn(scope)

        appPreferences.MANGA_READER_WEBTOON_DISABLE_ZOOM_OUT.flow()
            .onEach { disableZoomOut = it }
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
        set(value) {
            field = value.also { it.invertMode = tappingInverted }
        }

    /** По умолчанию вебтун читается тапом «право = вперёд, лево = назад». */
    override fun defaultNavigation(): ViewerNavigation = RightAndLeftNavigation()

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
}