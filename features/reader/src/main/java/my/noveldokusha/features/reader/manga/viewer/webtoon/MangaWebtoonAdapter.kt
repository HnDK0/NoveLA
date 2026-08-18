package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import my.noveldokusha.features.reader.manga.MangaPage

/**
 * Адаптер вебтун-ленты — мультиглавный порт tachiyomisy WebtoonAdapter.
 *
 * items = плоский список [MangaWebtoonWindow.Item]: страницы глав,
 * разделённые [MangaWebtoonTransition] на границах. Список приходит из
 * модели окна ([MangaWebtoonWindow.buildItems]) — главы накапливаются
 * вперёд/назад, а не пересобираются с нуля при каждой установке.
 *
 * Каждая страница несёт URL СВОЕЙ главы ([WebtoonPage.chapterUrl]):
 * мультиглавный режим не может полагаться на единый chapterUrl вьюера.
 */
internal class MangaWebtoonAdapter(
    private val viewer: MangaWebtoonViewer,
) : RecyclerView.Adapter<MangaWebtoonBaseHolder>() {

    private var items: List<Any> = emptyList()

    /** Счётчик поколений установки списка — растёт при каждом setItems. */
    private var generation = 0

    /** Элемент ленты: страница + URL главы + токен поколения. */
    private data class WebtoonPage(
        val page: MangaPage,
        /**
         * URL главы страницы. Не viewer.chapterUrl: в мультиглавной ленте
         * соседние главы имеют разные URL, и ключ загрузки обязан быть
         * страничным, а не глобальным.
         */
        val chapterUrl: String,
        /**
         * Поколение установки списка. Повторная установка той же главы
         * (идентичные url) даёт новый токен → DiffUtil выдаёт не-пустой
         * diff, элементы пересоздаются и устаревшие изображения/состояние
         * холдеров сбрасываются.
         */
        val generation: Int,
        /**
         * Состояние загрузки страницы; сравнивается в areContentsTheSame
         * (пока фиксировано true — прогресс загрузки управляется холдером).
         */
        val isLoading: Boolean = true,
    )

    /**
     * Устанавливает плоский список ленты из модели окна (delta-обновление).
     * [MangaWebtoonWindow.Item] транслируется во внутренние элементы.
     */
    fun setItems(newItems: List<MangaWebtoonWindow.Item>) {
        // Новое поколение на каждую установку: даже повторный вход в ту же
        // главу обязан пересоздать элементы (иначе — пустой diff и старые
        // картинки).
        generation++
        updateItems(newItems.map { item ->
            when (item) {
                is MangaWebtoonWindow.Item.Page -> WebtoonPage(
                    page = MangaPage(item.pageUrl, item.index),
                    chapterUrl = item.chapterUrl,
                    generation = generation,
                )
                is MangaWebtoonWindow.Item.Transition -> MangaWebtoonTransition(item.isNext)
            }
        })
    }

    private fun updateItems(newItems: List<Any>) {
        val result = DiffUtil.calculateDiff(Callback(items, newItems))
        items = newItems
        result.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is WebtoonPage -> PAGE_VIEW
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
            is WebtoonPage -> (holder as MangaWebtoonPageHolder).bind(item.page, item.chapterUrl)
            is MangaWebtoonTransition -> {
                // Переход — просто разделитель (см. MangaWebtoonTransitionHolder):
                // загрузка страниц целевой главы управляется её холдерами.
                (holder as MangaWebtoonTransitionHolder).bind(item)
            }
            else -> error("Unknown item ${item.javaClass}")
        }
    }

    override fun onViewRecycled(holder: MangaWebtoonBaseHolder) {
        holder.recycle()
    }

    /** DiffUtil: идентичность = страница в пределах одного поколения. */
    private class Callback(
        private val oldItems: List<Any>,
        private val newItems: List<Any>,
    ) : DiffUtil.Callback() {

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldItems[oldItemPosition]
            val new = newItems[newItemPosition]
            // Идентичность = страница, БЕЗ поколения: prepend/append новой главы
            // обязаны сохранять позицию ленты, а generation в идентичности
            // превращает весь diff в remove+insert (лента «прыгала» на соседнюю
            // главу при каждом расширении окна).
            if (old is WebtoonPage && new is WebtoonPage) {
                return old.page == new.page && old.chapterUrl == new.chapterUrl
            }
            return old == new
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldItems[oldItemPosition]
            val new = newItems[newItemPosition]
            // Содержимое различается состоянием загрузки и поколением: повторная
            // установка той же главы (новое поколение, те же url) даёт
            // notifyItemRangeChanged → холдеры перебиндиваются и картинки
            // перезагружаются без потери позиции ленты.
            if (old is WebtoonPage && new is WebtoonPage) {
                return old.generation == new.generation && old.isLoading == new.isLoading
            }
            return true
        }

        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size
    }
}

/** View type страницы. */
private const val PAGE_VIEW = 0

/** View type межглавного перехода. */
private const val TRANSITION_VIEW = 1
