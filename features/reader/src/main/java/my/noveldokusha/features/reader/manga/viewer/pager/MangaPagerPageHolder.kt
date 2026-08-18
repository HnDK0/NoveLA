package my.noveldokusha.features.reader.manga.viewer.pager

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.noveldokusha.features.reader.manga.MangaPage
import my.noveldokusha.features.reader.manga.viewer.MangaPageImageView

/**
 * Холдер страницы пейджера — порт tachiyomisy PagerPageHolder без dual-page:
 * SSIV + центрированный спиннер загрузки + состояние ошибки ('!', тап = ретрай).
 *
 * Загрузка: suspend PageImageLoader.load в локальном scope
 * (Main.immediate + SupervisorJob); job отменяется при ресайкле —
 * дальше пейджера не префектим (RecyclerView сам решает, что держать в живых).
 */
internal class MangaPagerPageHolder private constructor(
    root: FrameLayout,
    val imageView: MangaPageImageView,
    private val progressBar: ProgressBar,
    private val errorView: TextView,
    private val viewer: MangaPagerViewer,
) : RecyclerView.ViewHolder(root) {

    companion object {
        fun create(parent: ViewGroup, viewer: MangaPagerViewer): MangaPagerPageHolder {
            val context = parent.context
            val root = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            val imageView = MangaPageImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            val progressBar = ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleLarge,
            ).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
            }
            val errorView = TextView(context).apply {
                text = "!"
                textSize = 40f
                gravity = Gravity.CENTER
                isClickable = true
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
            }
            root.addView(imageView)
            root.addView(progressBar)
            root.addView(errorView)
            return MangaPagerPageHolder(root, imageView, progressBar, errorView, viewer)
        }
    }

    private var loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var page: MangaPage? = null
    /** Ключ главы, зафиксированный синхронно в [bind]: ретрай и guard
     *  используют именно его, а не currentChapter на момент завершения
     *  загрузки (глава могла смениться за время асинхронной работы). */
    private var chapterUrl: String? = null

    init {
        errorView.setOnClickListener { page?.let { load(it, chapterUrl) } }
        // Внутренняя ошибка SSIV (гонка uri=null после recycle, NPE в
        // BitmapLoadTask) должна показать errorView (тап = ретрай), а не
        // оставить вечный спиннер: без слушателя SSIV 3.10 молча бросает NPE.
        imageView.setOnImageLoadErrorListener { showError() }
    }

    /** Стартует загрузку [page]. Вызывается из onBindViewHolder. */
    fun bind(page: MangaPage) {
        this.page = page
        // Ключ главы фиксируем ДО запуска корутины: страница привязана к
        // конкретной главе, и результат загрузки чужой главы не должен
        // попасть на холдер после rebind/recycle.
        chapterUrl = viewer.currentChapter?.url
        load(page, chapterUrl)
    }

    private fun load(page: MangaPage, chapterUrl: String?) {
        loadJob?.cancel()
        // Спиннер показываем ТОЛЬКО если страница ещё не в кэше: если она уже
        // загружена (page_images/скачана), load() вернёт мгновенно, и мелькание
        // загрузки не должно быть видно.
        if (viewer.pageImageLoader.getCachedDimensions(page.url) == null) {
            showLoading()
        }
        if (chapterUrl == null) return // главы нет
        loadJob = loadScope.launch {
            val image = viewer.pageImageLoader.load(chapterUrl, page.url)
            // Guard: холдер мог быть отресаклен/переиспользован под другую
            // страницу, пока шла загрузка. Результат (и успех, и ошибку)
            // применяем только если привязанная страница всё ещё наша.
            if (this@MangaPagerPageHolder.page !== page) return@launch
            // Guard: задача загрузки отменена (recycle/новый bind) — результат
            // больше не нужен, setPage после отмены не вызываем.
            if (!isActive) return@launch
            if (image != null) {
                // Guard SSIV NPE («Failed to load bitmap», SSIV:1737 — Uri.toString()
                // на null): setImage переживает recycle — SSIV.reset(true) обнуляет
                // внутренний uri, а TilesInitTask/BitmapLoadTask из очереди дочитывают
                // его как null. Пустой/удалённый файл отсекаем до setPage.
                val file = image.file
                if (!file.exists() || file.length() == 0L) {
                    showError()
                    return@launch
                }
                imageView.setPage(file)
                imageView.updateConfig(viewer.config.imageConfig())
                showImage()
            } else {
                showError()
            }
        }
    }

    /** Отменяет загрузку при ресайкле холдера (готов к новому bind). */
    fun recycle() {
        loadJob?.cancel()
        loadJob = null
        loadScope.cancel()
        loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // Чистим битмап и сбрасываем зум: на отресакленном холдере не
        // должно оставаться картинки прошлой главы (иначе она мелькнёт
        // под спиннером при следующем bind).
        imageView.recycle()
        page = null
        chapterUrl = null
    }

    private fun showLoading() {
        // Старый битмап убираем до показа спиннера: при rebind под другую
        // главу картинка предыдущей страницы не должна быть видна.
        imageView.recycle()
        progressBar.visibility = View.VISIBLE
        errorView.visibility = View.GONE
    }

    private fun showImage() {
        progressBar.visibility = View.GONE
        errorView.visibility = View.GONE
    }

    private fun showError() {
        progressBar.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }
}
