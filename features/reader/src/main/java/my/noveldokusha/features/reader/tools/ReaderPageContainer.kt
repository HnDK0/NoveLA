package my.noveldokusha.features.reader.tools

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Контейнер ряда страницы. Маршрутизирует пинч (второй палец) в
 * [ReaderPageView], а первый палец оставляет ListView (скролл) и себе
 * (тап → меню). После начала зума все события идут в картинку до
 * поднятия всех пальцев — иначе ListView, уже забравший жест первого
 * пальца, не отдал бы зум обратно.
 */
class ReaderPageContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var imageView: ReaderPageView? = null
    private var zoomActive = false

    fun attachImage(view: ReaderPageView) {
        imageView = view
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) zoomActive = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> zoomActive = false
        }
        return if (zoomActive) {
            imageView?.dispatchTouchEvent(event) ?: true
        } else {
            super.dispatchTouchEvent(event)
        }
    }
}
