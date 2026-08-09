package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import my.noveldokusha.features.reader.manga.MangaPage

/**
 * Адаптер вебтун-ленты — порт tachiyomisy WebtoonAdapter (одна глава).
 * items = страницы главы; viewType: page / transition. Межглавные
 * переходы в одной главе не добавляются (будущий мультиглавный режим:
 * getItemCount = pages + transitions).
 */
internal class MangaWebtoonAdapter(
    private val viewer: MangaWebtoonViewer,
) : RecyclerView.Adapter<MangaWebtoonBaseHolder>() {

    private var items: List<Any> = emptyList()

    /** Устанавливает страницы текущей главы (delta-обновление). */
    fun setChapter(pages: List<MangaPage>) {
        updateItems(pages)
    }

    private fun updateItems(newItems: List<Any>) {
        val result = DiffUtil.calculateDiff(Callback(items, newItems))
        items = newItems
        result.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is MangaPage -> PAGE_VIEW
        is MangaWebtoonTransition -> TRANSITION_VIEW
        else -> error("Unknown view type for ${items[position].javaClass}")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaWebtoonBaseHolder {
        return when (viewType) {
            PAGE_VIEW -> MangaWebtoonPageHolder(FrameLayout(parent.context), viewer)
            TRANSITION_VIEW -> MangaWebtoonTransitionHolder(LinearLayout(parent.context), viewer)
            else -> error("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: MangaWebtoonBaseHolder, position: Int) {
        when (val item = items[position]) {
            is MangaPage -> (holder as MangaWebtoonPageHolder).bind(item)
            is MangaWebtoonTransition -> {
                // В одной главе переходов нет; при мультиглавии здесь
                // будет ретрай загрузки целевой главы через Activity.
                (holder as MangaWebtoonTransitionHolder).bind(item) { }
            }
            else -> error("Unknown item ${item.javaClass}")
        }
    }

    override fun onViewRecycled(holder: MangaWebtoonBaseHolder) {
        holder.recycle()
    }

    /** DiffUtil: страницы — data class, сравниваются по значению. */
    private class Callback(
        private val oldItems: List<Any>,
        private val newItems: List<Any>,
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldItems[oldItemPosition] == newItems[newItemPosition]

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = true

        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size
    }
}

/** View type страницы. */
private const val PAGE_VIEW = 0

/** View type межглавного перехода. */
private const val TRANSITION_VIEW = 1