package my.noveldokusha.features.reader.manga.viewer

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF

/**
 * Тап-зоны вьюера — 3 простых режима навигации (см. [MangaNavigationMode]).
 * Все координаты нормализованы 0..1.
 *
 * Зоны СЕМАНТИЧЕСКИЕ (PREV/NEXT), а не геометрические: левый край = назад
 * (PREV), правый = вперёд (NEXT) — стандарт для манги. Инверсия
 * ([TapEdgesNavigation]/[BothNavigation] с inverted=true) зеркалит их.
 */
internal abstract class ViewerNavigation {

    sealed class NavigationRegion(val color: Int) {
        data object MENU : NavigationRegion(Color.argb(0xCC, 0x95, 0x81, 0x8D))
        data object PREV : NavigationRegion(Color.argb(0xCC, 0xFF, 0x77, 0x33))
        data object NEXT : NavigationRegion(Color.argb(0xCC, 0x84, 0xE2, 0x96))
    }

    data class Region(
        val rectF: RectF,
        val type: NavigationRegion,
    )

    /** Верхняя полоса 5% экрана всегда открывает меню. */
    private var constantMenuRegion: RectF = RectF(0f, 0f, 1f, 0.05f)

    protected abstract var regionList: List<Region>

    fun getAction(pos: PointF): NavigationRegion {
        val x = pos.x
        val y = pos.y
        val region = regionList.find { it.rectF.contains(x, y) }
        return when {
            region != null -> region.type
            constantMenuRegion.contains(x, y) -> NavigationRegion.MENU
            else -> NavigationRegion.MENU
        }
    }
}

/** Только листание: тапы в любой точке открывают меню. */
internal class SwipeNavigation : ViewerNavigation() {
    override var regionList: List<Region> = emptyList()
}

/**
 * Тапы по краям: левый край — назад (PREV), правый — вперёд (NEXT);
 * центр — меню. [inverted] зеркалит зоны (левый = вперёд, правый = назад).
 */
internal class TapEdgesNavigation(
    inverted: Boolean = false,
) : ViewerNavigation() {
    override var regionList: List<Region> = listOf(
        Region(RectF(0f, 0f, 0.33f, 1f), if (inverted) NavigationRegion.NEXT else NavigationRegion.PREV),
        Region(RectF(0.66f, 0f, 1f, 1f), if (inverted) NavigationRegion.PREV else NavigationRegion.NEXT),
    )
}

/**
 * Листание + тапы: те же семантические зоны PREV/NEXT, что у
 * [TapEdgesNavigation] (отличие — в TAP_EDGES свайпы отключены конфигом).
 * [inverted] зеркалит зоны.
 */
internal class BothNavigation(
    inverted: Boolean = false,
) : ViewerNavigation() {
    override var regionList: List<Region> = listOf(
        Region(RectF(0f, 0f, 0.33f, 1f), if (inverted) NavigationRegion.NEXT else NavigationRegion.PREV),
        Region(RectF(0.66f, 0f, 1f, 1f), if (inverted) NavigationRegion.PREV else NavigationRegion.NEXT),
    )
}
