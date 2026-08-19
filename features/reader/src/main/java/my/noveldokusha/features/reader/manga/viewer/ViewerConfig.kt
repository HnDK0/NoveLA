package my.noveldokusha.features.reader.manga.viewer

import kotlinx.coroutines.CoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences

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

    var doubleTapAnimDuration = 500

    /** Меню по одному тапу (иначе — по двойному через onDoubleTap вьюера). */
    var singleTapToOpenSettings = false

    /**
     * Тап в зоне MENU: при singleTapToOpenSettings открывает меню сразу.
     * Иначе меню открывает двойной тап (нативный onDoubleTap вьюера).
     */
    fun handleMenuTap(openMenu: () -> Unit) {
        if (singleTapToOpenSettings) openMenu()
    }

    var navigationMode = 0
        protected set

    abstract var navigator: ViewerNavigation
        protected set

    protected abstract fun defaultNavigation(): ViewerNavigation

    abstract fun updateNavigation(navigationMode: Int)
}
