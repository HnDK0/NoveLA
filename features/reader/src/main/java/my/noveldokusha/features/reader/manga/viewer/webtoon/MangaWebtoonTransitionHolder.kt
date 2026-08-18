package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout

/**
 * Маркер межглавного перехода в ленте. Одна глава = переходы не
 * добавляются (getItemCount = pages + transitions, transitions = 0);
 * тип зарезервирован под будущий мультиглавный режим.
 */
internal data class MangaWebtoonTransition(val isNext: Boolean)

/**
 * Холдер перехода между главами — тонкий разделитель вместо карточки
 * со спиннером: спиннер читатель видел как «что-то пытается загрузить
 * между главами». Страницы соседней главы и так префетчатся заранее
 * (prefetchChapter при расширении окна) и появляются бесшовно.
 */
internal class MangaWebtoonTransitionHolder(
    private val root: LinearLayout,
    viewer: MangaWebtoonViewer,
) : MangaWebtoonBaseHolder(root, viewer) {

    init {
        root.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        root.orientation = LinearLayout.VERTICAL

        val density = context.resources.displayMetrics.density
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                MATCH_PARENT,
                (1 * density).toInt(),
            ).apply {
                marginStart = (32 * density).toInt()
                marginEnd = (32 * density).toInt()
            }
            setBackgroundColor(0x33FFFFFF.toInt())
        }
        root.addView(divider)
    }

    fun bind(transition: MangaWebtoonTransition) = Unit
}