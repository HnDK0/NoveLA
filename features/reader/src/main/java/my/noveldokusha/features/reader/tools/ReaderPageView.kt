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
 * Здесь, пока жесты выключены (режим ленты — включить их можно позже для
 * зума), onTouchEvent отдаёт событие родителю: скролл идёт через ListView,
 * тап — через pageContainer. Тайловый декод и отрисовка в полном разрешении
 * не меняются.
 */
class ReaderPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SubsamplingScaleImageView(context, attrs) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (isPanEnabled || isZoomEnabled) {
            super.onTouchEvent(event)
        } else {
            false
        }
    }
}
