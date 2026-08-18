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

    var longTapEnabled = true
    var doubleTapAnimDuration = 500

    /** Меню по одному тапу (иначе — по двойному, как в текстовом ридере). */
    var singleTapToOpenSettings = false

    private var lastMenuTapTime = 0L

    /**
     * Тап в зоне MENU. При singleTapToOpenSettings — сразу; иначе ждёт
     * второй тап в пределах [MENU_TAP_THRESHOLD_MS] (поведение текстового
     * ридера, READER_SINGLE_TAP_TO_OPEN_SETTINGS).
     */
    fun handleMenuTap(openMenu: () -> Unit) {
        if (singleTapToOpenSettings) {
            openMenu()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastMenuTapTime < MENU_TAP_THRESHOLD_MS) {
            lastMenuTapTime = 0L
            openMenu()
        } else {
            lastMenuTapTime = now
        }
    }

    var navigationMode = 0
        protected set

    abstract var navigator: ViewerNavigation
        protected set

    protected abstract fun defaultNavigation(): ViewerNavigation

    abstract fun updateNavigation(navigationMode: Int)

    companion object {
        private const val MENU_TAP_THRESHOLD_MS = 350L
    }
}
