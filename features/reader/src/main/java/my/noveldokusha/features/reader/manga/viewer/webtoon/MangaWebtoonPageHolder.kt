package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.noveldokusha.features.reader.manga.MangaPage
import my.noveldokusha.features.reader.manga.viewer.MangaPageImageView
import my.noveldokusha.reader.R

/**
 * Холдер одной страницы в вебтун-ленте — порт tachiyomisy WebtoonPageHolder.
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

    /** URL главы, к которой привязан холдер (ключ для guard'а протухших результатов). */
    private var boundChapterUrl: String = ""

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

        // Внутренняя ошибка SSIV (гонка uri=null после recycle, NPE в
        // BitmapLoadTask) должна показать errorView с повтором, а не оставить
        // вечный спиннер: без слушателя SSIV 3.10 молча бросает NPE.
        pageImage.setOnImageLoadErrorListener { showError() }
        pageImage.setOnImageLoadedListener {
            // Точная высота по фактическим пропорциям декодированной страницы:
            // SSIV 3.10 не имеет SCALE_TYPE_FIT_WIDTH, поэтому ширина-фит
            // достигается точной высотой + CENTER_INSIDE/START. При первом
            // показе страницы dims ещё нет в кэше, и fallback-высота экрана
            // могла не совпасть с пропорциями — иначе SSIV вписал бы страницу
            // по высоте, а не по ширине.
            val width = pageImage.width.takeIf { it > 0 } ?: availableWidth()
            pageImage.setHeightIfChanged((width * pageImage.sHeight) / pageImage.sWidth)
            hideProgress()
        }
        updateImageConfig()
        // refreshLayoutParams здесь не нужен: до attach layoutParams ещё null
        // (RecyclerView создаст его при прикреплении), margins применяются в bind.
    }

    /** Биндит [page] и перезапускает загрузку (повтор — тоже сюда).
     *  [chapterUrl] — URL главы страницы (из адаптера): в мультиглавной
     *  ленте соседние главы имеют разные URL, глобальный viewer.chapterUrl
     *  здесь не годится. */
    fun bind(page: MangaPage, chapterUrl: String) {
        // Ключ главы захватывается СИНХРОННО: внутри корутины он мог бы устареть
        // к моменту чтения (холдер перепривязан к другой главе).
        this.page = page
        this.boundChapterUrl = chapterUrl
        loadJob?.cancel()
        // Ресайкл битмапа ДО показа прогресса: иначе при in-place rebind
        // под спиннером мелькает картинка предыдущей страницы/главы.
        pageImage.recycle()
        refreshLayoutParams()
        updateImageConfig()
        // Высота ряда ставится СИНХРОННО из кэша размеров (память) или
        // fallback-высоты экрана: асинхронная смена высоты ПОСЛЕ layout
        // (getDimensions в корутине) дёргает ленту и ЗАДЕРЖИВАЕТ загрузку
        // следующей картинки (suspend прелюдия перед load). Точные
        // пропорции подтянутся по onImageLoaded — единственное
        // асинхронное изменение высоты.
        val fallbackHeight = viewer.recycler.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val reservedHeight = viewer.pageImageLoader.getCachedDimensions(page.url)
            ?.let { (availableWidth() * it.second) / it.first }
            ?: fallbackHeight
        pageImage.setHeightIfChanged(reservedHeight)
        progressContainer.setHeightIfChanged(reservedHeight)
        // Прогресс показываем ТОЛЬКО если страница ещё не в кэше: если она
        // уже загружена (page_images/скачана), load() вернёт мгновенно, и мелькание
        // загрузки не должно быть видно.
        if (viewer.pageImageLoader.getCachedDimensions(page.url) == null) {
            showProgress()
        }
        loadJob = scope.launch {
            val image = viewer.pageImageLoader.load(chapterUrl, page.url)
            // Протухший результат: холдер уже перепривязан к другой главе —
            // setPage/showError применять нельзя (показали бы чужую картинку).
            if (chapterUrl != boundChapterUrl) return@launch
            // Guard: задача загрузки отменена (recycle/новый bind) — результат
            // больше не нужен, setPage после отмены не вызываем.
            if (!isActive) return@launch
            if (image == null) {
                showError()
            } else {
                val file = image.file
                // Guard SSIV NPE («Failed to load bitmap», SSIV:1737 — Uri.toString()
                // на null): setImage переживает recycle — SSIV.reset(true) обнуляет
                // внутренний uri, а TilesInitTask/BitmapLoadTask из очереди дочитывают
                // его как null. Пустой/удалённый файл отсекаем до setPage.
                if (!file.exists() || file.length() == 0L) {
                    showError()
                    return@launch
                }
                // Fit-width ДО первого кадра: для свежей главы (dims не в кэше)
                // reservedHeight = высота экрана, и SSIV вписал бы страницу по
                // высоте с боковыми полями. getDimensions читает размеры файла,
                // который load() только что положил в кэш (без сети) — ставим
                // точную высоту ряда ДО setPage, пока под спиннером: лента не
                // дёргается и картинка сразу растянута по ширине.
                viewer.pageImageLoader.getDimensions(chapterUrl, page.url)?.let { (w, h) ->
                    val width = pageImage.width.takeIf { it > 0 } ?: availableWidth()
                    val exactHeight = (width * h) / w
                    pageImage.setHeightIfChanged(exactHeight)
                    progressContainer.setHeightIfChanged(exactHeight)
                }
                // Fit-width: SSIV сам декодирует и позиционирует по ширине;
                // горизонтальная позиция — из конфига вьюера (zoomStart).
                pageImage.setPage(file)
            }
        }
    }

    /** Присваивает высоту layoutParams ТОЛЬКО при изменении: присвоение
     *  layoutParams безусловно вызывает requestLayout даже при той же высоте,
     *  а каждый layout-проход ленты дёргает onScrolled (обработчики,
     *  префетч) — лишние проходы на каждую загрузку картинки. */
    private fun View.setHeightIfChanged(height: Int) {
        val lp = layoutParams
        if (lp == null) {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, height)
        } else if (lp.height != height) {
            layoutParams = lp.apply { this.height = height }
        }
    }

    /**
     * Обновляет отступы (side padding). Мутируем поля существующего
     * layoutParams: замена объекта после attach холдера (root.layoutParams =
     * ...) ломает каст RecyclerView.getChildViewHolderInt — краш при повторе
     * и перескакивании при скролле. До attach layoutParams ещё null —
     * RecyclerView сам создаст его при прикреплении, margins применяются в bind.
     */
    private fun refreshLayoutParams() {
        val padding = viewer.config.sidePaddingPx
        (root.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.leftMargin = padding
            it.rightMargin = padding
            root.requestLayout()
        }
    }

    /** Ширина, доступная картинке: ширина ленты минус боковые отступы. */
    private fun availableWidth(): Int =
        (viewer.recycler.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) -
            viewer.config.sidePaddingPx * 2

    /** Применяет текущие настройки к картинке (при rebind'е). */
    private fun updateImageConfig() {
        // Лента: страницы не принимают жесты вообще (как WebtoonSubsamplingImageView
        // в tachiyomisy — onTouchEvent всегда false), скролл/тапы ведёт RecyclerView.
        // zoomStart — из конфига вьюера, а не захардкоженный LEFT: настройка
        // MANGA_READER_ZOOM_START общая с пейджером и должна применяться к ленте.
        // RGB_565 (вместо ARGB_8888): вебтун-страницы полноцветные, но декодер
        // региона работает на 16-bit — втрое меньше памяти на битмап-кэш при
        // незаметной визуальной разнице (основной выигрыш — OOM-защита).
        pageImage.updateConfig(
            MangaPageImageView.Config(
                doubleTapZoomEnabled = false,
                zoomStart = viewer.config.zoomStart,
                touchEnabled = false,
                bitmapConfig = Bitmap.Config.RGB_565,
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
                setOnClickListener {
                    val p = page ?: return@setOnClickListener
                    val url = boundChapterUrl
                    if (url.isNotEmpty()) bind(p, url)
                }
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
