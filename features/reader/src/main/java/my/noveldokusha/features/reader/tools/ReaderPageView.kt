package my.noveldokusha.features.reader.tools

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

/**
 * SSIV для «ленты» страниц манхвы/манги.
 *
 * Базовая SubsamplingScaleImageView в onTouchEvent ВСЕГДА возвращает true
 * (даже когда pan/zoom выключены) — ListView не получает ни драга, ни тапа:
 * лента не скроллится, меню не открывается.
 *
 * Маршрутизация жестов:
 *  - один палец при немасштабированном виде → false: скролл через ListView,
 *    тап — через pageContainer (открыть меню);
 *  - два пальца (пинч) → SSIV: зум страницы (тайловый декод, детали в
 *    полном разрешении);
 *  - один палец при увеличенном виде (scale > minScale) → SSIV: пан внутри
 *    страницы (ListView не скроллится, пока страница увеличена).
 */
class ReaderPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SubsamplingScaleImageView(context, attrs) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when {
            event.pointerCount >= 2 && isZoomEnabled -> super.onTouchEvent(event)
            isZoomEnabled && scale > minScale + 0.01f -> super.onTouchEvent(event)
            else -> false
        }
    }
}
