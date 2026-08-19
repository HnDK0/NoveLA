package my.noveldokusha.features.reader.manga.viewer.pager

import android.content.Context
import android.graphics.PointF
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.MangaChapter
import my.noveldokusha.features.reader.manga.MangaPage
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation.NavigationRegion
import my.noveldokusha.features.reader.tools.PageImageLoader
import my.noveldokusha.reader.R

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

    override val view: View get() = root

    /** Горизонтальный (L2R/R2L) или вертикальный пейджер. */
    protected abstract val isHorizontal: Boolean

    /** R2L — reverseLayout: первая страница справа, листание влево. */
    protected open val reverseLayout: Boolean = false

    val pager: MangaPager = MangaPager(
        context,
        isHorizontal = isHorizontal,
        reverseLayout = reverseLayout,
    )

    /** Оверлей для пустой главы: адаптер не создаёт холдеры на 0 страниц. */
    private val emptyView: TextView = TextView(context).apply {
        text = context.getString(R.string.manga_reader_no_pages)
        gravity = Gravity.CENTER
        visibility = View.GONE
    }

    /** Корневой контейнер: пейджер + оверлей «нет страниц». */
    private val root: FrameLayout = FrameLayout(context).apply {
        // FrameLayout.LayoutParams обязательны: onMeasure кастует layoutParams
        // детей к MarginLayoutParams, ViewGroup.LayoutParams → ClassCastException.
        addView(
            pager,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            emptyView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

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

    /** Последний жест был пользовательским свайпом (а не программным скроллом). */
    private var lastGestureWasUser = false

    /** Позиция, с которой начался драг (lastSnappedIndex до его обновления). */
    private var dragStartPosition = RecyclerView.NO_POSITION

    /**
     * Накопленное движение пальца за драг: >0 = «назад» (свайп вправо/вниз,
     * к началу главы), <0 = «вперёд» (влево/вверх). Направление одинаково
     * для L2R/R2L/вертикали: физически начало главы всегда «справа/снизу».
     */
    private var accumulatedDrag = 0f

    private val pagerListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                // Жест начался: фиксируем стартовую страницу, пока она ещё
                // не обновлена в IDLE, и сбрасываем накопленное направление.
                lastGestureWasUser = true
                dragStartPosition = lastSnappedIndex
                accumulatedDrag = 0f
            }
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                val position = pager.snapPosition()
                if (position != RecyclerView.NO_POSITION && position != lastSnappedIndex) {
                    lastSnappedIndex = position
                    currentIndex = position
                    pager.currentHolder = pager.findPageHolder(position)
                    onPageChanged?.invoke(position)
                }
                if (lastGestureWasUser) {
                    lastGestureWasUser = false
                    checkEdgeReached(position)
                }
                prefetchNearbyPages()
            }
        }
    }

    /**
     * Свайп на границе главы: RecyclerView упирается в первый/последний
     * элемент и «отпружинивает», колбэки границы от жеста не вызываются
     * (в отличие от тап-зон и кнопок). Если драг начался И закончился на
     * границе и направление совпадает с «выходом за край» — читатель
     * пытался листать дальше → переключаем главу.
     */
    private fun checkEdgeReached(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val last = chapterLastIndex ?: return
        if (position == 0 && dragStartPosition == 0 && accumulatedDrag > 0f) {
            onFirstPageReached?.invoke()
        }
        if (position == last && dragStartPosition == last && accumulatedDrag < 0f) {
            onLastPageReached?.invoke()
        }
    }

    init {
        // Тот же тип, что задан в root.addView: перезапись на ViewGroup.LayoutParams
        // после attach ломает каст FrameLayout.onMeasure → краш при показе главы.
        pager.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        pager.isFocusable = false
        pager.adapter = adapter
        pager.addOnScrollListener(pagerListener)
        pager.tapListener = { event -> onTap(event) }
        pager.longTapListener = { event -> onLongTap(event) }
        pager.dragDeltaListener = { dx, dy ->
            // Накопление направления жеста для edge-детекции (checkEdgeReached):
            // знак зависит только от оси (горизонталь/вертикаль), не от R2L.
            accumulatedDrag += if (isHorizontal) dx else dy
        }

        config.imagePropertyChangedListener = {
            pager.post { adapter.refresh() }
        }
    }

    override fun setChapter(chapter: MangaChapter, startPage: Int) {
        currentChapter = chapter
        adapter.setPages(chapter.pages)
        val target = safePageIndex(chapter.pages, startPage)
        currentIndex = target
        lastSnappedIndex = target
        if (chapter.pages.isEmpty()) {
            // Пустая глава: скролл не нужен, коллбеки не дёргаем — иначе
            // onLastPageReached/onFirstPageReached устроят цикл смены глав.
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE
        pager.removeOnScrollListener(pagerListener)
        pager.scrollToPosition(target)
        pager.addOnScrollListener(pagerListener)
        pager.post { pager.currentHolder = pager.findPageHolder(target) }
        onPageChanged?.invoke(target)
        // Сразу прогреваем окно страниц после установки главы: пока холдер
        // первой страницы грузится, следующие уже в кэше — нет «затупа»
        // при быстром листании.
        prefetchNearbyPages()
        prefetchChapter(chapter)
    }

    override fun currentPage(): Int = currentIndex

    /** Прогрев начала главы при открытии: только [READER_PAGE_PREFETCH_COUNT]
     * первых страниц, а не вся глава разом — иначе десятки параллельных
     * загрузок на CDN дают 429/бан. 0 = префетч выключен. */
    private fun prefetchChapter(chapter: MangaChapter) {
        if (chapter.pages.isEmpty()) return
        val count = appPreferences.READER_PAGE_PREFETCH_COUNT.value
        if (count <= 0) return
        pageImageLoader.prefetchPages(chapter.url, chapter.pages.take(count))
    }

    /**
     * Префетч соседних страниц после оседания пейджера: грузим окно
     * [READER_PAGE_PREFETCH_COUNT] страниц вперёд через PageImageLoader,
     * чтобы при быстром листании картинки не «моргали». load()
     * дедуплицирует одновременные запросы, лишние запросы не идут в сеть.
     */
    private fun prefetchNearbyPages() {
        val chapter = currentChapter ?: return
        val current = currentIndex
        if (chapter.pages.isEmpty()) return
        val count = appPreferences.READER_PAGE_PREFETCH_COUNT.value
        if (count <= 0) return
        val start = (current + 1).coerceAtMost(chapter.pages.size)
        val end = (current + 1 + count).coerceAtMost(chapter.pages.size)
        if (start < end) {
            pageImageLoader.prefetchPages(chapter.url, chapter.pages.subList(start, end))
        }
    }

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

    /**
     * Последний индекс страницы; null, если главы нет или она пуста —
     * навигационные методы на этом раннем выходе не трогают пейджер.
     */
    private val chapterLastIndex: Int?
        get() = currentChapter?.pages?.takeIf { it.isNotEmpty() }?.lastIndex

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
            NavigationRegion.MENU -> config.handleMenuTap { pageClickListener?.invoke() }
            NavigationRegion.NEXT -> moveToNext()
            NavigationRegion.PREV -> moveToPrevious()
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

/**
 * Индекс стартовой страницы с защитой от пустой главы: для пустого
 * списка возвращает 0 — иначе `coerceIn(0, -1)` бросает IllegalArgumentException.
 */
internal fun safePageIndex(pages: List<MangaPage>, startPage: Int): Int =
    if (pages.isEmpty()) 0 else startPage.coerceIn(0, pages.lastIndex)
