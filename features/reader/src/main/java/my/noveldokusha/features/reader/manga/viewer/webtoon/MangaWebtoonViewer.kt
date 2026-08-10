package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.content.Context
import android.graphics.PointF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.MangaChapter
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation.NavigationRegion
import my.noveldokusha.features.reader.tools.PageImageLoader

/**
 * Вебтун-вьюер — леан-порт tachiyomisy WebtoonViewer (одна глава).
 *
 * Скролл-хелперы (scrollUp/scrollDown на 3/4 экрана) и тап-зоны
 * (config.navigator) портированы как в tachiyomisy; убраны
 * verticalNavigator, chapter sets, preload и crop borders.
 */
internal class MangaWebtoonViewer(
    private val context: Context,
    internal val pageImageLoader: PageImageLoader,
    private val appPreferences: AppPreferences,
    /** false — «непрерывность» выключена (нижний margin у страниц). */
    internal val isContinuous: Boolean = true,
) : Viewer {

    private val scope = MainScope()

    /** Конфигурация вьюера (настройки подписаны на AppPreferences). */
    val config = MangaWebtoonConfig(appPreferences, scope)

    /** Лента. */
    internal val recycler = MangaWebtoonRecyclerView(context)

    /** Обёртка ленты (трансляция тачей). */
    private val frame = MangaWebtoonFrame(context)

    /** Дистанция скролла при тапе на край: 3/4 экрана (как в tachiyomisy). */
    private val scrollDistance = context.resources.displayMetrics.heightPixels * 3 / 4

    /** Extra layout space: лента создаёт холдеры на 2 экрана вперёд. */
    private val layoutManager =
        MangaWebtoonLayoutManager(context, context.resources.displayMetrics.heightPixels * 2)

    private val adapter = MangaWebtoonAdapter(this)

    /** URL текущей главы (ключ для PageImageLoader). */
    internal var chapterUrl: String = ""

    /** Индекс текущей страницы (первая полностью видимая). */
    private var currentIndex = -1

    init {
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateCurrentPage()
                }
            },
        )
        recycler.tapListener = { event -> handleTap(event) }
        recycler.longTapListener = { event -> handleLongTap(event) }

        // Отступы/переходы изменились — перепривязать видимые холдеры.
        config.imagePropertyChangedListener = {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)

        setupAutoScroll()
    }

    // ── Автопрокрутка (tachiyomisy-стиль: непрерывный плавный скролл) ──

    private var autoScrollJob: Job? = null

    /** Пауза по касанию: любой тач по ленте останавливает скролл. */
    private var autoScrollPaused = false

    private fun setupAutoScroll() {
        // Тач по ленте — пауза (пользователь читает/листает сам).
        recycler.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                autoScrollPaused = true
            }
            false // не перехватываем событие
        }
        appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.flow()
            .onEach { enabled ->
                if (enabled && autoScrollJob == null) {
                    autoScrollPaused = false
                    autoScrollJob = scope.launch { runAutoScroll() }
                } else if (!enabled) {
                    autoScrollJob?.cancel()
                    autoScrollJob = null
                }
            }
            .launchIn(scope)
    }

    /** Цикл: тик каждые 16 мс, дельта = скорость (dp/с) * dt. */
    private suspend fun CoroutineScope.runAutoScroll() {
        val density = context.resources.displayMetrics.density
        while (isActive) {
            if (!autoScrollPaused && recycler.width > 0 && recycler.height > 0) {
                // Читаем префы каждый тик — смена скорости/плавности в настройках
                // применяется на лету.
                val speedPxPerSec = appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value * density
                val smooth = appPreferences.MANGA_READER_AUTOSCROLL_SMOOTH.value
                val step = (speedPxPerSec * 0.016f).toInt().coerceAtLeast(1)
                if (smooth) {
                    recycler.smoothScrollBy(0, step)
                } else {
                    recycler.scrollBy(0, step)
                }
            }
            delay(16)
        }
    }

    /** Корневой view вьюера. */
    override val view: View get() = frame

    override var onPageChanged: ((Int) -> Unit)? = null
    override var onLastPageReached: (() -> Unit)? = null
    override var onFirstPageReached: (() -> Unit)? = null
    override var pageClickListener: (() -> Unit)? = null

    override fun setChapter(chapter: MangaChapter, startPage: Int) {
        adapter.setChapter(chapter.pages)
        chapterUrl = chapter.url
        currentIndex = -1
        moveToPage(startPage)
    }

    override fun currentPage(): Int = currentIndex

    override fun moveToPage(index: Int) {
        if (index !in 0 until adapter.itemCount) return
        recycler.scrollToPage(index)
        setCurrentPage(index)
    }

    /** Скролл на страницу вверх (назад). */
    override fun prevPage(): Boolean {
        val current = currentPage()
        return if (current > 0) {
            recycler.smoothScrollToPage(current - 1)
            true
        } else {
            onFirstPageReached?.invoke()
            true
        }
    }

    /** Скролл на страницу вниз (вперёд). */
    override fun nextPage(): Boolean {
        val current = currentPage()
        return if (current < adapter.itemCount - 1) {
            recycler.smoothScrollToPage(current + 1)
            true
        } else {
            onLastPageReached?.invoke()
            true
        }
    }

    override fun destroy() {
        scope.cancel()
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> if (isUp) pageClickListener?.invoke()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) scrollUp()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) scrollDown()

            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean = false

    /** Текущая страница из первого полностью видимого холдера. */
    private fun updateCurrentPage() {
        val position = recycler.currentPagePosition()
        if (position == RecyclerView.NO_POSITION) return
        setCurrentPage(position)
    }

    private fun setCurrentPage(position: Int) {
        if (position == currentIndex) return
        currentIndex = position
        onPageChanged?.invoke(position)
    }

    /**
     * Тап: зоны из config.navigator (координаты нормализованы 0..1).
     * MENU — переключение тулбара, NEXT/RIGHT — вниз, PREV/LEFT — вверх.
     */
    private fun handleTap(event: MotionEvent) {
        val width = recycler.width
        val height = recycler.height
        if (width <= 0 || height <= 0) {
            pageClickListener?.invoke()
            return
        }
        val pos = PointF(event.x / width, event.y / height)
        when (config.navigator.getAction(pos)) {
            NavigationRegion.MENU -> pageClickListener?.invoke()
            NavigationRegion.NEXT, NavigationRegion.RIGHT -> scrollDown()
            NavigationRegion.PREV, NavigationRegion.LEFT -> scrollUp()
        }
    }

    /** Долгий тап: открыть меню (gated config.longTapEnabled). */
    private fun handleLongTap(event: MotionEvent): Boolean {
        if (!config.longTapEnabled) return false
        val child = recycler.findChildViewUnder(event.x, event.y) ?: return false
        if (recycler.getChildAdapterPosition(child) == RecyclerView.NO_POSITION) return false
        pageClickListener?.invoke()
        return true
    }

    /** Скролл вверх на 3/4 экрана (плавно при transitionsEnabled). */
    private fun scrollUp() {
        if (config.transitionsEnabled) {
            recycler.smoothScrollBy(0, -scrollDistance)
        } else {
            recycler.scrollBy(0, -scrollDistance)
        }
    }

    /** Скролл вниз на 3/4 экрана (плавно при transitionsEnabled). */
    private fun scrollDown() {
        if (config.transitionsEnabled) {
            recycler.smoothScrollBy(0, scrollDistance)
        } else {
            recycler.scrollBy(0, scrollDistance)
        }
    }
}