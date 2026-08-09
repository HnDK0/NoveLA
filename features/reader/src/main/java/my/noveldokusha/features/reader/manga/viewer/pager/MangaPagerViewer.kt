package my.noveldokusha.features.reader.manga.viewer.pager

import android.content.Context
import android.graphics.PointF
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.MangaChapter
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation.NavigationRegion
import my.noveldokusha.features.reader.tools.PageImageLoader

/**
 * Пейджер-вьюер на RecyclerView — порт tachiyomisy PagerViewer для одной
 * главы. Направления: L2R/R2L — горизонталь (R2L через reverseLayout),
 * VERTICAL — вертикаль. Тап по зонам навигации, long-press → меню,
 * клавиши громкости (с инверсией) и колесо мыши.
 */
internal abstract class MangaPagerViewer(
    private val context: Context,
    internal val pageImageLoader: PageImageLoader,
    protected val appPreferences: AppPreferences,
    protected val scope: CoroutineScope,
) : Viewer {

    override val view: View get() = pager

    val pager: MangaPager = MangaPager(
        context,
        isHorizontal = isHorizontal,
        reverseLayout = reverseLayout,
    )

    val config: MangaPagerConfig = MangaPagerConfig(this, appPreferences, scope)

    private val adapter = MangaPagerAdapter(this)

    /** Текущая глава — холдерам нужен её url как ключ кэша загрузок. */
    internal var currentChapter: MangaChapter? = null
        private set

    override var onPageChanged: ((Int) -> Unit)? = null
    override var onLastPageReached: (() -> Unit)? = null
    override var onFirstPageReached: (() -> Unit)? = null
    override var pageClickListener: (() -> Unit)? = null

    private var currentIndex = 0
    private var lastSnappedIndex = RecyclerView.NO_POSITION

    private val pagerListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                val position = pager.snapPosition()
                if (position != RecyclerView.NO_POSITION && position != lastSnappedIndex) {
                    lastSnappedIndex = position
                    currentIndex = position
                    pager.currentHolder = pager.findPageHolder(position)
                    onPageChanged?.invoke(position)
                }
            }
        }
    }

    init {
        pager.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        pager.isFocusable = false
        pager.adapter = adapter
        pager.addOnScrollListener(pagerListener)
        pager.tapListener = { event -> onTap(event) }
        pager.longTapListener = { event -> onLongTap(event) }

        config.imagePropertyChangedListener = {
            pager.post { adapter.refresh() }
        }
    }

    /** Горизонтальный (L2R/R2L) или вертикальный пейджер. */
    protected abstract val isHorizontal: Boolean

    /** R2L — reverseLayout: первая страница справа, листание влево. */
    protected open val reverseLayout: Boolean = false

    override fun setChapter(chapter: MangaChapter, startPage: Int) {
        currentChapter = chapter
        adapter.setPages(chapter.pages)
        val target = startPage.coerceIn(0, chapter.pages.lastIndex)
        currentIndex = target
        lastSnappedIndex = target
        pager.removeOnScrollListener(pagerListener)
        pager.scrollToPosition(target)
        pager.addOnScrollListener(pagerListener)
        pager.post { pager.currentHolder = pager.findPageHolder(target) }
        onPageChanged?.invoke(target)
    }

    override fun currentPage(): Int = currentIndex

    override fun moveToPage(index: Int) {
        val last = chapterLastIndex ?: return
        val clamped = index.coerceIn(0, last)
        if (config.usePageTransitions) {
            pager.smoothScrollToPosition(clamped)
        } else {
            pager.scrollToPosition(clamped)
        }
        if (clamped != currentIndex) {
            currentIndex = clamped
            lastSnappedIndex = clamped
            onPageChanged?.invoke(clamped)
        }
    }

    override fun prevPage(): Boolean {
        if (chapterLastIndex == null) return false
        return if (currentIndex > 0) {
            moveToPage(currentIndex - 1)
            true
        } else {
            onFirstPageReached?.invoke()
            true
        }
    }

    override fun nextPage(): Boolean {
        val last = chapterLastIndex ?: return false
        return if (currentIndex < last) {
            moveToPage(currentIndex + 1)
            true
        } else {
            onLastPageReached?.invoke()
            true
        }
    }

    private val chapterLastIndex: Int?
        get() = currentChapter?.pages?.lastIndex ?: -1

    override fun destroy() {
        pager.removeOnScrollListener(pagerListener)
        pager.adapter = null
    }

    // ── Жесты ──

    private fun onTap(event: MotionEvent) {
        val pos = PointF(
            if (pager.width > 0) event.x / pager.width else 0f,
            if (pager.height > 0) event.y / pager.height else 0f,
        )
        when (config.navigator.getAction(pos)) {
            NavigationRegion.MENU -> pageClickListener?.invoke()
            NavigationRegion.NEXT -> moveToNext()
            NavigationRegion.PREV -> moveToPrevious()
            NavigationRegion.RIGHT -> moveRight()
            NavigationRegion.LEFT -> moveLeft()
        }
    }

    private fun onLongTap(event: MotionEvent): Boolean {
        return if (config.longTapEnabled) {
            pageClickListener?.invoke()
            true
        } else {
            false
        }
    }

    // ── Навигация (семантика направлений tachiyomisy) ──

    /** Следующая страница по направлению чтения. */
    open fun moveToNext(): Boolean = nextPage()

    /** Предыдущая страница по направлению чтения. */
    open fun moveToPrevious(): Boolean = prevPage()

    /** Тап в правую зону (L2R — следующая страница). */
    protected open fun moveRight(): Boolean = nextPage()

    /** Тап в левую зону (L2R — предыдущая страница). */
    protected open fun moveLeft(): Boolean = prevPage()

    protected open fun moveUp(): Boolean = moveToPrevious()

    protected open fun moveDown(): Boolean = moveToNext()

    // ── Клавиатура / колесо ──

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState and KeyEvent.META_CTRL_ON > 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled) return false
                if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled) return false
                if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isUp) {
                    if (ctrlPressed) moveToNext() else moveRight()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) moveToPrevious() else moveLeft()
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) pageClickListener?.invoke()
            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            if (event.action == MotionEvent.ACTION_SCROLL) {
                if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                    moveDown()
                } else {
                    moveUp()
                }
                return true
            }
        }
        return false
    }
}