package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import my.noveldokusha.features.reader.manga.MangaPage
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import my.noveldokusha.features.reader.manga.viewer.MangaPageImageView
import my.noveldokusha.reader.R

/**
 * Холдер одной страницы вебтун-ленты — порт tachiyomisy WebtoonPageHolder.
 *
 * Корень — FrameLayout, внутри: [MangaPageImageView] (touchEnabled=false,
 * жесты у ленты), прогресс-контейнер (держит минимальную высоту холдера,
 * пока нет картинки) и error-контейнер с кнопкой повтора. Загрузка через
 * [viewer]'s PageImageLoader на per-holder CoroutineScope (Main.immediate),
 * отменяется при ресайклинге.
 */
internal class MangaWebtoonPageHolder(
    private val root: FrameLayout,
    viewer: MangaWebtoonViewer,
) : MangaWebtoonBaseHolder(root, viewer) {

    /** Страница, привязанная к холдеру (для повтора). */
    private var page: MangaPage? = null

    /** Картинка страницы: fit-width, без тачей (лента скроллит сама). */
    private val pageImage = MangaPageImageView(context)

    /** Минимальная высота холдера до загрузки картинки. */
    private val progressContainer = FrameLayout(context)

    /** Error-контейнер (создаётся при первой ошибке). */
    private var errorContainer: FrameLayout? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    init {
        root.addView(
            pageImage,
            FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )

        val containerHeight =
            viewer.recycler.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        progressContainer.addView(
            ProgressBar(context),
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER),
        )
        root.addView(
            progressContainer,
            FrameLayout.LayoutParams(MATCH_PARENT, containerHeight),
        )

        pageImage.setOnImageLoadedListener { hideProgress() }
        updateImageConfig()
        refreshLayoutParams()
    }

    /** Биндит [page] и перезапускает загрузку (повтор — тоже сюда). */
    fun bind(page: MangaPage) {
        this.page = page
        loadJob?.cancel()
        refreshLayoutParams()
        updateImageConfig()
        showProgress()
        loadJob = scope.launch {
            val image = viewer.pageImageLoader.load(viewer.chapterUrl, page.url)
            if (image == null) {
                showError()
            } else {
                // Fit-width: SSIV сам декодирует и позиционирует по ширине;
                // zoom-start для ленты зафиксирован LEFT (SCALE_TYPE_START).
                pageImage.setPage(image.file)
            }
        }
    }

    /** Обновляет отступы (side padding) и нижний margin для !isContinuous. */
    private fun refreshLayoutParams() {
        val padding = viewer.config.sidePaddingPx
        root.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            if (!viewer.isContinuous) {
                bottomMargin = (15 * context.resources.displayMetrics.density).toInt()
            }
            leftMargin = padding
            rightMargin = padding
        }
    }

    /** Применяет текущие настройки к картинке (при rebind'е). */
    private fun updateImageConfig() {
        pageImage.updateConfig(
            MangaPageImageView.Config(
                doubleTapZoomEnabled = false,
                zoomAnimationDuration = viewer.config.doubleTapAnimDuration,
                zoomStart = MangaZoomStart.LEFT, // fit по ширине (SCALE_TYPE_START)
                touchEnabled = false,
            ),
        )
    }

    private fun showProgress() {
        removeError()
        progressContainer.visibility = View.VISIBLE
    }

    private fun hideProgress() {
        progressContainer.visibility = View.GONE
        removeError()
    }

    private fun showError() {
        progressContainer.visibility = View.GONE
        val error = errorContainer ?: createErrorContainer().also { errorContainer = it }
        error.visibility = View.VISIBLE
    }

    private fun removeError() {
        errorContainer?.let {
            root.removeView(it)
            errorContainer = null
        }
    }

    /** Контейнер «не удалось загрузить» с кнопкой повтора. */
    private fun createErrorContainer(): FrameLayout {
        val containerHeight =
            viewer.recycler.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        content.addView(
            TextView(context).apply {
                text = context.getString(R.string.manga_reader_chapter_failed)
                gravity = Gravity.CENTER
            },
        )
        content.addView(
            Button(context).apply {
                text = context.getString(R.string.manga_reader_retry)
                setOnClickListener { page?.let(::bind) }
            },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = (8 * context.resources.displayMetrics.density).toInt()
            },
        )

        return FrameLayout(context).apply {
            addView(
                content,
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER),
            )
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, (containerHeight * 0.8).toInt())
            root.addView(this)
        }
    }

    override fun recycle() {
        loadJob?.cancel()
        loadJob = null
        removeError()
        pageImage.recycle()
        page = null
        progressContainer.visibility = View.VISIBLE
    }
}