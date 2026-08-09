package my.noveldokusha.features.reader.manga.viewer.pager

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import my.noveldokusha.features.reader.manga.viewer.ReaderTransitionView

/**
 * Холдер перехода главы — порт tachiyomisy PagerTransitionHolder.
 *
 * В single-chapter пейджере в потоке страниц не участвует (границы главы
 * обрабатывает Activity через onFirstPageReached/onLastPageReached), но
 * класс реализован полностью для «подсматривания» соседних глав и для
 * webtoon-слайса (там MangaWebtoonTransitionHolder использует
 * ReaderTransitionView напрямую).
 */
internal class MangaPagerTransitionHolder(
    context: Context,
    direction: ReaderTransitionView.Direction,
    title: String?,
) : LinearLayout(context) {

    val transitionView = ReaderTransitionView(context)

    /** Тап по ошибке перехода — повторить. */
    var onRetry: (() -> Unit)? = null
        set(value) {
            field = value
            transitionView.onRetry = value
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val sidePadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            64f,
            resources.displayMetrics,
        ).toInt()
        setPadding(sidePadding, 0, sidePadding, 0)

        addView(
            transitionView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        transitionView.bind(title, direction)
    }

    fun setLoading() {
        transitionView.setLoading()
    }

    fun setError(message: String?) {
        transitionView.setError(message)
    }
}