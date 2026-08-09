package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams
import androidx.recyclerview.widget.RecyclerView

/**
 * Базовый холдер вебтун-ленты — порт tachiyomisy WebtoonBaseHolder.
 * Тап-зоны (config.navigator) обрабатываются самим вьюером через
 * recycler.tapListener, поэтому здесь только вспомогательные геттеры.
 */
internal abstract class MangaWebtoonBaseHolder(
    view: View,
    protected val viewer: MangaWebtoonViewer,
) : RecyclerView.ViewHolder(view) {

    /** Context геттер (используется часто). */
    val context: Context get() = itemView.context

    /** Вызывается при ресайклинге холдера (в пул). */
    open fun recycle() {}

    /** Расширение: layout params wrap_content. */
    protected fun View.wrapContent() {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
}