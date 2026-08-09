package my.noveldokusha.features.reader.manga.viewer.pager

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Пейджер на RecyclerView + PagerSnapHelper — замена DirectionalViewPager
 * из tachiyomisy. L2R/R2L задаются orientation + reverseLayout.
 *
 * Арбитраж жестов (аналог логики ViewPager):
 *  - пинч (>= 2 пальца) и пан увеличенного образа остаются SSIV;
 *  - один палец без зума — RecyclerView перелистывает страницы.
 * SSIV на каждый DOWN ставит requestDisallowInterceptTouchEvent(true),
 * поэтому мы его проглатываем и решаем перехват сами (как делает
 * DirectionalViewPager, когда может перетаскивать).
 */
internal class MangaPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    isHorizontal: Boolean = true,
    reverseLayout: Boolean = false,
) : RecyclerView(context, attrs) {

    /** Snap-хелпер, которым прилипают страницы. */
    val snapHelper = PagerSnapHelper()

    /** Тап по пейджеру (координаты в системе пейджера). */
    var tapListener: ((MotionEvent) -> Unit)? = null

    /** Долгий тап; true = жест обработан (даёт haptic-фидбек). */
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    /** Холдер текущей (привязанной к снапу) страницы — для арбитража. */
    var currentHolder: MangaPagerPageHolder? = null

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                tapListener?.invoke(e)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val listener = longTapListener
                if (listener != null && listener.invoke(e)) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        },
    )

    private var isGestureDetectorEnabled = true

    init {
        layoutManager = LinearLayoutManager(
            context,
            if (isHorizontal) LinearLayoutManager.HORIZONTAL else LinearLayoutManager.VERTICAL,
            reverseLayout,
        )
        snapHelper.attachToRecyclerView(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (isGestureDetectorEnabled) {
            gestureDetector.onTouchEvent(ev)
        }
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
            val holder = currentHolder
            if (ev.pointerCount >= 2 || (holder != null && holder.imageView.isZoomed)) {
                // Пинч / пан увеличенного образа — не перелистываем.
                return false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // SSIV ставит disallow на каждый DOWN; перелистывание решает
        // onInterceptTouchEvent — как DirectionalViewPager игнорирует его.
    }

    /** Позиция привязанной к снапу страницы (или NO_POSITION). */
    fun snapPosition(): Int {
        val layoutManager = layoutManager ?: return RecyclerView.NO_POSITION
        val snap = snapHelper.findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
        return getChildAdapterPosition(snap)
    }

    /** Холдер страницы на [position], если он сейчас в layout. */
    fun findPageHolder(position: Int): MangaPagerPageHolder? {
        val view = layoutManager?.findViewByPosition(position) ?: return null
        return getChildViewHolder(view) as? MangaPagerPageHolder
    }

    fun setGestureDetectorEnabled(enabled: Boolean) {
        isGestureDetectorEnabled = enabled
    }
}