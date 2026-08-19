package my.noveldokusha.features.reader.manga.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.DefaultOnImageEventListener
import com.davemorrissey.labs.subscaleview.decoder.CompatDecoderFactory
import com.davemorrissey.labs.subscaleview.decoder.SkiaPooledImageRegionDecoder
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import java.io.File

/**
 * Страница манхвы/манги: SSIV с тайловым декодером.
 *
 * Объединяет ReaderPageImageView (pager) и WebtoonSubsamplingImageView
 * из tachiyomisy: touchEnabled=false отдаёт все жесты RecyclerView'у
 * (webtoon-лента), как в tachiyomisy WebtoonSubsamplingImageView.
 *
 * Перелистывание vs пан/зум арбитрирует [MangaPager]: SSIV всегда
 * принимает события (touchEnabled=true), а пейджер перехватывает
 * перелистывание в onInterceptTouchEvent, когда изображение не увеличено
 * и пальцев меньше двух.
 *
 * Производительность (Bug6):
 *  - [SkiaPooledImageRegionDecoder] через фабрику — пул декодеров
 *    переиспользуется между страницами вместо создания нового
 *    BitmapRegionDecoder на каждое изображение;
 *  - RGB_565 для webtoon-ленты (в 2 раза меньше памяти на тайл,
 *    градации манги не страдают); пейджер сохраняет ARGB_8888;
 *  - декодирование тайлов выполняется на THREAD_POOL_EXECUTOR SSIV
 *    (не на main-потоке).
 */
internal class MangaPageImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private var config: Config = Config(),
) : SubsamplingScaleImageView(context, attrs) {

    data class Config(
        val doubleTapZoomEnabled: Boolean = true,
        val zoomAnimationDuration: Int = 500,
        val zoomStart: MangaZoomStart = MangaZoomStart.AUTOMATIC,
        val touchEnabled: Boolean = true, // false for webtoon pages
        val bitmapConfig: Bitmap.Config = Bitmap.Config.ARGB_8888, // RGB_565 for webtoon
    )

    /** Увеличено ли изображение (scale > minScale): тогда SSIV сам панует. */
    val isZoomed: Boolean
        get() = isReady && scale > minScale + 0.01f

    private var imageLoadedListener: (() -> Unit)? = null

    /** eventTime последнего одиночного UP — для детекта второго тапа двойного. */
    private var lastTapUpTime = 0L

    /** Идёт подавление жеста: второй DOWN двойного тапа уже съеден, до UP/CANCEL. */
    private var suppressGesture = false

    fun setOnImageLoadedListener(listener: () -> Unit) {
        imageLoadedListener = listener
        if (isReady) listener()
    }

    fun setPage(file: File) {
        applyConfig()
        setImage(ImageSource.uri(Uri.fromFile(file)))
    }

    /**
     * Колбэк на внутреннюю ошибку загрузки SSIV.
     *
     * Почему нужен: при переиспользовании холдера recycle() = reset(true)
     * обнуляет внутренний uri SSIV, а запоздавший BitmapLoadTask от старой
     * страницы дочитывает его как null (NPE «Failed to load bitmap») и падает
     * в onImageLoadError. Без слушателя SSIV 3.10 молча оставляет страницу
     * пустой — вечный спиннер вместо errorView с повтором.
     */
    fun setOnImageLoadErrorListener(listener: (Throwable) -> Unit) {
        // DefaultOnImageEventListener — базовая реализация-заглушка интерфейса
        // OnImageEventListener (сам интерфейс требует переопределить все методы);
        // переопределяем только интересующий нас onImageLoadError(Exception).
        setOnImageEventListener(object : DefaultOnImageEventListener() {
            override fun onImageLoadError(e: Exception) = listener(e)
        })
    }

    fun updateConfig(config: Config) {
        this.config = config
        applyConfig()
    }

    /**
     * Жесты: пан ограничен внутри изображения, пинч-зум до 5x, тайлы
     * декодируются в высоком dpi. Двойной тап зуму НЕ управляет: его
     * перехватывает GestureDetector пейджера (onDoubleTap → меню), поэтому
     * второй DOWN двойного тапа не доходит до SSIV — см. [onTouchEvent].
     */
    private fun applyConfig() {
        // Пул декодеров + формат битмапов в одном конструкторе фабрики.
        setRegionDecoderFactory(
            CompatDecoderFactory(SkiaPooledImageRegionDecoder::class.java, config.bitmapConfig),
        )
        setQuickScaleEnabled(config.doubleTapZoomEnabled)
        setDoubleTapZoomDuration(config.zoomAnimationDuration)
        setDoubleTapZoomStyle(ZOOM_FOCUS_CENTER)
        setPanLimit(PAN_LIMIT_INSIDE)
        setMinimumTileDpi(180)
        // В официальном SSIV 3.10.0 нет SCALE_TYPE_CENTER/END —
        // доступны только CENTER_INSIDE/CENTER_CROP/CUSTOM/START.
        setMinimumScaleType(
            when (config.zoomStart) {
                MangaZoomStart.LEFT -> SCALE_TYPE_START
                MangaZoomStart.CENTER -> SCALE_TYPE_CENTER_INSIDE
                MangaZoomStart.RIGHT -> SCALE_TYPE_CENTER_INSIDE
                MangaZoomStart.AUTOMATIC -> SCALE_TYPE_CENTER_INSIDE // для пейджера
            },
        )
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        // Лимиты зума выставляем всегда: пинч-зум до 5x должен работать
        // независимо от жеста двойного тапа (он занят меню и подавлен в
        // onTouchEvent). doubleTapZoomScale неактивен — onDoubleTap SSIV
        // больше не получает второй DOWN.
        setDoubleTapZoomScale(scale * 2f)
        setMaxScale(scale * 5f) // MAX_ZOOM_SCALE = 5F
        applyZoomStart()
        imageLoadedListener?.invoke()
    }

    /**
     * Стартовая позиция зума — реальная, а не декоративная:
     * в официальном SSIV 3.10 нет SCALE_TYPE_CENTER/END, поэтому
     * CENTER/RIGHT выставляются вручную через setScaleAndCenter:
     * страница заполняет ширину, видно её начало — по центру или
     * у правого края. minScale не поднимается, поэтому зум-аут
     * до полной страницы остаётся доступным из любого старта.
     */
    private fun applyZoomStart() {
        val start = config.zoomStart
        if (start == MangaZoomStart.LEFT || sWidth <= 0) return
        val focalX = when (start) {
            MangaZoomStart.CENTER -> sWidth / 2f
            MangaZoomStart.RIGHT -> sWidth.toFloat()
            else -> return // AUTOMATIC — CENTER_INSIDE, весь лист виден
        }
        post {
            val fitWidthScale = if (width > 0) width.toFloat() / sWidth else scale
            // minScale не поднимаем: полный лист должен оставаться доступным зум-аутом.
            setScaleAndCenter(fitWidthScale, PointF(focalX, 0f))
        }
    }

    /**
     * Подавление зума SSIV по двойному тапу.
     *
     * SSIV 3.10.0 не умеет отключать зум двойного тапа: setQuickScaleEnabled(false)
     * лишь убирает быстрый-зум-жест, но onDoubleTap в таком случае вызывает
     * doubleTapZoom() сразу (мгновенный зум в 2x). Поэтому второй DOWN одиночного
     * тапа (в пределах окна двойного тапа) не отдаём SSIV — меню откроет
     * GestureDetector пейджера (onDoubleTap → doubleTapListener), а SSIV не увидит
     * второе касание и не запустит зум. Последующие события жеста до UP/CANCEL
     * тоже глотаем, чтобы не ломать стейт-машину жестов SSIV.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!config.touchEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isSecondTap = event.pointerCount == 1 &&
                    lastTapUpTime > 0 &&
                    event.eventTime - lastTapUpTime < ViewConfiguration.getDoubleTapTimeout()
                if (isSecondTap) {
                    lastTapUpTime = 0
                    suppressGesture = true
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // Пальцы добавились/убрались — это не двойной тап: снимаем
                // подавление и отдаём событие SSIV (пинч не должен ломаться).
                suppressGesture = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (suppressGesture) {
                    suppressGesture = false
                    lastTapUpTime = 0
                    return true
                }
                if (event.pointerCount == 1) lastTapUpTime = event.eventTime
            }
            else -> if (suppressGesture) return true
        }
        return super.onTouchEvent(event)
    }

    init {
        applyConfig()
    }
}
