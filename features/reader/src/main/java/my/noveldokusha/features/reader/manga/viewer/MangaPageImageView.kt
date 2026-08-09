package my.noveldokusha.features.reader.manga.viewer

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import java.io.File

/**
 * Страница манхвы/манги: SSIV с тайловым декодом оригинала.
 *
 * Объединяет ReaderPageImageView (pager) и WebtoonSubsamplingImageView
 * из tachiyomisy: touchEnabled=false отдаёт все жесты RecyclerView'у
 * (webtoon-лента), как в tachiyomisy WebtoonSubsamplingImageView.
 *
 * Перелистывание vs пан/зум арбитрирует [MangaPager]: SSIV всегда
 * принимает события (touchEnabled=true), а пейджер перехватывает
 * перелистывание в onInterceptTouchEvent, когда изображение не увеличено
 * и пальцев меньше двух.
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
    )

    /** Увеличено ли изображение (scale > minScale): тогда SSIV сам панует. */
    val isZoomed: Boolean
        get() = isReady && scale > minScale + 0.01f

    private var imageLoadedListener: (() -> Unit)? = null

    fun setOnImageLoadedListener(listener: () -> Unit) {
        imageLoadedListener = listener
        if (isReady) listener()
    }

    fun setPage(file: File) {
        applyConfig()
        setImage(ImageSource.uri(Uri.fromFile(file)))
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        imageLoadedListener?.invoke()
    }

    fun updateConfig(config: Config) {
        this.config = config
        applyConfig()
    }

    private fun applyConfig() {
        setQuickScaleEnabled(config.doubleTapZoomEnabled)
        setDoubleTapZoomDuration(config.zoomAnimationDuration)
        // В официальном SSIV 3.10.0 нет SCALE_TYPE_CENTER/END (это форк
        // tachiyomisy) — доступны только CENTER_INSIDE/CENTER_CROP/CUSTOM/START.
        setMinimumScaleType(
            when (config.zoomStart) {
                MangaZoomStart.LEFT -> SCALE_TYPE_START
                MangaZoomStart.CENTER -> SCALE_TYPE_CENTER_INSIDE
                MangaZoomStart.RIGHT -> SCALE_TYPE_CENTER_INSIDE
                MangaZoomStart.AUTOMATIC -> SCALE_TYPE_CENTER_INSIDE // для пейджера
            },
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return config.touchEnabled && super.onTouchEvent(event)
    }

    init {
        applyConfig()
    }
}