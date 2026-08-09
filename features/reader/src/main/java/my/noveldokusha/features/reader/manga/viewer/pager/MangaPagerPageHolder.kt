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
import kotlinx.coroutines.launch
import my.noveldokusha.features.reader.manga.MangaPage
import my.noveldokusha.features.reader.manga.viewer.MangaPageImageView

/**
 * Холдер страницы пейджера — порт tachiyomisy PagerPageHolder без dual-page:
 * SSIV + центрированный спиннер загрузки + состояние ошибки ('!', тап = ретрай).
 *
 * Загрузка: suspend [my.noveldokusha.features.reader.tools.PageImageLoader.load]
 * в локальном scope (Main.immediate + SupervisorJob); job отменяется при
 * ресайкле — дальше пейджера не префетчим (RecyclerView сам решает, что
 * держать в живых).
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

    init {
        errorView.setOnClickListener { page?.let(::load) }
    }

    /** Стартует загрузку [page]. Вызывается из onBindViewHolder. */
    fun bind(page: MangaPage) {
        this.page = page
        load(page)
    }

    private fun load(page: MangaPage) {
        loadJob?.cancel()
        showLoading()
        loadJob = loadScope.launch {
            val chapterUrl = viewer.currentChapter?.url ?: return@launch
            val image = viewer.pageImageLoader.load(chapterUrl, page.url)
            if (image != null) {
                imageView.setPage(image.file)
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
        page = null
    }

    private fun showLoading() {
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