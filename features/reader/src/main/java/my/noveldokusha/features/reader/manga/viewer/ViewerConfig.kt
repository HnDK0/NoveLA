package my.noveldokusha.features.reader.manga.viewer

import kotlinx.coroutines.CoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.setting.MangaTappingInvertMode

/**
 * Общая конфигурация вьюеров — порт tachiyomisy ViewerConfig.
 * Настройки читаются в подклассах через AppPreferences.flow().
 */
internal abstract class ViewerConfig(
    protected val appPreferences: AppPreferences,
    protected val scope: CoroutineScope,
) {
    var imagePropertyChangedListener: (() -> Unit)? = null
    var navigationModeChangedListener: (() -> Unit)? = null

    var tappingInverted: MangaTappingInvertMode = MangaTappingInvertMode.NONE
    var longTapEnabled = true
    var doubleTapAnimDuration = 500
    var volumeKeysEnabled = false
    var volumeKeysInverted = false

    var navigationMode = 0
        protected set

    abstract var navigator: ViewerNavigation
        protected set

    protected abstract fun defaultNavigation(): ViewerNavigation

    abstract fun updateNavigation(navigationMode: Int)
}