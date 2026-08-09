package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * RecyclerView вебтун-ленты — леан-порт tachiyomisy WebtoonRecyclerView.
 *
 * Убрано: зум всей ленты (scaleX/scaleY, zoomFling, onScale...) — страницы
 * в NoveLA не принимают тачи, жесты целиком достаются ленте; двойной тап
 * поэтому не зуммирует ленту. Оставлено: single tap -> [tapListener],
 * long press -> [longTapListener] (+haptic), флик -> перескок на соседнюю
 * страницу ([flingToPage]) при резком свайпе, мягкие флики оставляют
 * стандартный momentum. try/catch в onTouchEvent/onInterceptTouchEvent —
 * защита от крашей, когда дочерние вью дёргают
 * requestDisallowInterceptTouchEvent (паттерн tachiyomisy Pager.kt).
 */
internal class MangaWebtoonRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    /** Одиночный тап: координаты локальные (обрабатывает viewer). */
    var tapListener: ((MotionEvent) -> Unit)? = null

    /** Долгий тап: вернуть true, если обработан (для haptic). */
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    /** Порог флика для перескока на страницу (мягкий флик = momentum). */
    private val flingPageThreshold = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 2

    /** Скорость флика, ожидающая обработки после ACTION_UP. */
    private var pendingFlingVelocity: Int? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            tapListener?.invoke(e)
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            val listener = longTapListener
            if (listener != null && listener.invoke(e)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (abs(velocityY) > flingPageThreshold) {
                // Сам скролл запустим после super.onTouchEvent(ACTION_UP),
                // иначе momentum-флинг RecyclerView перебьёт плавный скролл.
                pendingFlingVelocity = velocityY.toInt()
            }
            return false
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            pendingFlingVelocity = null
        }
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
        val velocity = pendingFlingVelocity
        if (velocity != null && e.actionMasked == MotionEvent.ACTION_UP) {
            pendingFlingVelocity = null
            flingToPage(velocity)
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

    /** Мгновенный скролл: верх страницы [position] к верху экрана. */
    internal fun scrollToPage(position: Int) {
        (layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
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

    /** Перескок на соседнюю страницу по направлению вертикального флика. */
    internal fun flingToPage(velocityY: Int) {
        val current = currentPagePosition()
        if (current == RecyclerView.NO_POSITION) return
        smoothScrollToPage(if (velocityY < 0) current + 1 else current - 1)
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