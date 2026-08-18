package my.noveldokusha.features.reader.manga.viewer.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import my.noveldokusha.features.reader.manga.MangaPage

/**
 * Адаптер пейджера — порт tachiyomisy PagerViewerAdapter без chapter-сетов:
 * одна глава = список [MangaPage], никаких crossfade-пар.
 */
internal class MangaPagerAdapter(
    private val viewer: MangaPagerViewer,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val pages = mutableListOf<MangaPage>()

    fun setPages(newPages: List<MangaPage>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }

    /** Пересоздаёт холдеры (после изменения конфига изображения). */
    fun refresh() {
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return MangaPagerPageHolder.create(parent, viewer)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as MangaPagerPageHolder).bind(pages[position])
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? MangaPagerPageHolder)?.recycle()
    }
}
