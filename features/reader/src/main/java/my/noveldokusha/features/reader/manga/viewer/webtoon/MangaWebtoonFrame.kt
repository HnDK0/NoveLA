package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * FrameLayout-обёртка ленты — порт tachiyomisy WebtoonFrame.
 *
 * Переводит координаты тача внутрь границ ленты, чтобы жест можно было
 * начать за её краем. Детекторы scale/fling удалены: зум всей ленты не
 * портирован, лента всегда неклипнута (MATCH_PARENT).
 */
internal class MangaWebtoonFrame(context: Context) : FrameLayout(context) {

    /** Лента, добавленная в этот фрейм. */
    private val recycler: MangaWebtoonRecyclerView?
        get() = getChildAt(0) as? MangaWebtoonRecyclerView

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val recyclerRect = Rect()
        recycler?.getHitRect(recyclerRect) ?: return super.dispatchTouchEvent(ev)
        // Немного ужать прямоугольник от погрешностей округления.
        recyclerRect.inset(1, 1)

        if (recyclerRect.right < recyclerRect.left || recyclerRect.bottom < recyclerRect.top) {
            return super.dispatchTouchEvent(ev)
        }

        ev.setLocation(
            ev.x.coerceIn(recyclerRect.left.toFloat(), recyclerRect.right.toFloat()),
            ev.y.coerceIn(recyclerRect.top.toFloat(), recyclerRect.bottom.toFloat()),
        )
        return super.dispatchTouchEvent(ev)
    }
}