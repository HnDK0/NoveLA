package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView вебтун-ленты — лин-порт tachiyomisy WebtoonRecyclerView.
 *
 * Убрано: зум всей ленты (scaleX/scaleY, zoomFling, onScale...) — страницы
 * в NoveLA не принимают тачи, жесты целиком достаются ленте; двойной тап
 * поэтому не зумит ленту. Перехват флинга с перескоком на соседнюю
 * страницу тоже убран: при резком свайпе он «дёргал» ленту на следующую
 * картинку вместо инерции — скролл теперь штатный momentum RecyclerView.
 * Оставлено: single tap -> [tapListener], double tap -> [doubleTapListener].
 * try/catch в onTouchEvent/onInterceptTouchEvent — защита от
 * крашей, когда дочерние вью дёргают requestDisallowInterceptTouchEvent
 * (паттерн tachiyomisy Pager.kt).
 */
internal class MangaWebtoonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    /** Одиночный тап: координаты локальные (обрабатывает viewer). */
    var tapListener: ((MotionEvent) -> Unit)? = null

    /** Двойной тап: вернуть true, если обработан. Меню по двойному тапу. */
    var doubleTapListener: ((MotionEvent) -> Boolean)? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            tapListener?.invoke(e)
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            return doubleTapListener?.invoke(e) ?: false
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(e)
        val result = try {
            super.onTouchEvent(e)
        } catch (ex: NullPointerException) {
            false
        } catch (ex: IndexOutOfBoundsException) {
            false
        } catch (ex: IllegalArgumentException) {
            false
        }
        return result
    }

    /**
     * Защита от крашей, когда дочерние вью манипулируют
     * requestDisallowInterceptTouchEvent (см. tachiyomisy Pager.kt).
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    // ---- API навигации по страницам ----

    /** Позиция страницы, полностью занимающей верх экрана (текущая). */
    internal fun currentPagePosition(): Int {
        val lm = layoutManager as? LinearLayoutManager ?: return RecyclerView.NO_POSITION
        val first = lm.findFirstCompletelyVisibleItemPosition()
        return if (first != RecyclerView.NO_POSITION) first else lm.findFirstVisibleItemPosition()
    }

    /** true, если [position] — первая полностью видимая страница. */
    internal fun isOnPage(position: Int): Boolean =
        (layoutManager as? LinearLayoutManager)?.findFirstCompletelyVisibleItemPosition() == position

    /**
     * Позиция, запрошенная [scrollToPage], но ещё не применённая layout-проходом.
     * DiffUtil-вставка слева (prepend главы) сдвигает плоские координаты:
     * без корректировки pending-скролла лента оседает на странице 0
     * ПРЕДЫДУЩЕЙ главы (откат moveChapter). Сбрасывается в [onScrolled] —
     * как только layout применил скролл, сдвигать больше нечего.
     */
    private var pendingScrollPosition = RecyclerView.NO_POSITION

    /** Мгновенный скролл: верх страницы [position] к верху экрана. */
    internal fun scrollToPage(position: Int) {
        pendingScrollPosition = position
        (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
    }

    /**
     * Сдвиг pending-скролла на [delta] (prepend вставил [delta] страниц слева).
     * Возвращает true, если скролл ещё не применён layout-проходом и был
     * скорректирован; false — лента уже осела, позицию восстанавливает
     * вызывающий по фактическому состоянию.
     */
    internal fun adjustPendingScroll(delta: Int): Boolean {
        if (pendingScrollPosition == RecyclerView.NO_POSITION) return false
        pendingScrollPosition += delta
        scrollToPage(pendingScrollPosition)
        return true
    }

    override fun onScrolled(dx: Int, dy: Int) {
        pendingScrollPosition = RecyclerView.NO_POSITION
        super.onScrolled(dx, dy)
    }

    /**
     * Плавный скролл к странице [position]. При [duration] != null —
     * анимация заданной длительности (используется оценочная дистанция,
     * если целевой холдер ещё не приаттачен).
     */
    internal fun smoothScrollToPage(position: Int, duration: Long? = null) {
        val lm = layoutManager as? LinearLayoutManager
        if (lm == null) {
            scrollToPage(position)
            return
        }
        if (duration == null) {
            smoothScrollToPosition(position)
            return
        }
        val dy = verticalDistanceTo(position)
        if (dy != 0) {
            smoothScrollBy(0, dy, DecelerateInterpolator(), duration.toInt())
        }
    }

    /** Дистанция по вертикали до верха страницы [position] (для scrollBy). */
    private fun verticalDistanceTo(position: Int): Int {
        val holder = findViewHolderForAdapterPosition(position)
        val itemView = holder?.itemView
        if (itemView != null) {
            return itemView.top - paddingTop
        }
        // Холдер ещё не создан: оценка по числу страниц и высоте экрана.
        val current = currentPagePosition()
        return (position - current) * height + computeVerticalScrollOffset()
    }
}
