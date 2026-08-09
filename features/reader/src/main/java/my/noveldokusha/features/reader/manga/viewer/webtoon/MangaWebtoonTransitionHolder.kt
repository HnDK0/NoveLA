package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import my.noveldokusha.features.reader.manga.viewer.ReaderTransitionView

/**
 * Маркер межглавного перехода в ленте. Одна глава = переходы не
 * добавляются (getItemCount = pages + transitions, transitions = 0);
 * тип зарезервирован под будущий мультиглавный режим.
 */
internal data class MangaWebtoonTransition(val isNext: Boolean)

/**
 * Холдер перехода между главами — порт tachiyomisy WebtoonTransitionHolder:
 * вертикальный LinearLayout с [ReaderTransitionView] (карточка перехода:
 * заголовок, спиннер загрузки страниц целевой главы, ошибка + retry).
 */
internal class MangaWebtoonTransitionHolder(
    private val root: LinearLayout,
    viewer: MangaWebtoonViewer,
) : MangaWebtoonBaseHolder(root, viewer) {

    private val transitionView = ReaderTransitionView(context)

    private var onRetry: (() -> Unit)? = null

    init {
        root.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER

        val density = context.resources.displayMetrics.density
        root.setPadding(
            (32 * density).toInt(),
            (128 * density).toInt(),
            (32 * density).toInt(),
            (128 * density).toInt(),
        )

        transitionView.onRetry = { onRetry?.invoke() }
        root.addView(transitionView)
    }

    /**
     * Биндит переход: [onRetry] — повторная загрузка целевой главы
     * (Activity); карточка переходит в состояние загрузки.
     */
    fun bind(transition: MangaWebtoonTransition, onRetry: () -> Unit) {
        this.onRetry = onRetry
        transitionView.bind(
            title = null, // одна глава: заголовка целевой главы нет
            direction = if (transition.isNext) {
                ReaderTransitionView.Direction.BOTTOM
            } else {
                ReaderTransitionView.Direction.LEFT
            },
        )
        transitionView.setLoading()
    }

    /** Загрузка целевой главы упала: показать ошибку и повтор. */
    fun showError(message: String? = null) {
        transitionView.setError(message)
    }

    override fun recycle() {
        onRetry = null
    }
}