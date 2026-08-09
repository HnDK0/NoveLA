package my.noveldokusha.features.reader.manga.viewer

import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import my.noveldokusha.features.reader.manga.setting.MangaTappingInvertMode

/**
 * Тап-зоны вьюера — порт tachiyomisy ViewerNavigation.
 * Все координаты нормализованы 0..1.
 */
internal abstract class ViewerNavigation {

    sealed class NavigationRegion(val color: Int) {
        data object MENU : NavigationRegion(Color.argb(0xCC, 0x95, 0x81, 0x8D))
        data object PREV : NavigationRegion(Color.argb(0xCC, 0xFF, 0x77, 0x33))
        data object NEXT : NavigationRegion(Color.argb(0xCC, 0x84, 0xE2, 0x96))
        data object LEFT : NavigationRegion(Color.argb(0xCC, 0x7D, 0x11, 0x28))
        data object RIGHT : NavigationRegion(Color.argb(0xCC, 0xA6, 0xCF, 0xD5))
    }

    data class Region(
        val rectF: RectF,
        val type: NavigationRegion,
    ) {
        fun invert(invertMode: MangaTappingInvertMode): Region {
            if (invertMode == MangaTappingInvertMode.NONE) return this
            var left = rectF.left
            var top = rectF.top
            var right = rectF.right
            var bottom = rectF.bottom
            if (invertMode.shouldInvertHorizontal) {
                val newLeft = 1f - right
                right = 1f - left
                left = newLeft
            }
            if (invertMode.shouldInvertVertical) {
                val newTop = 1f - bottom
                bottom = 1f - top
                top = newTop
            }
            return copy(rectF = RectF(left, top, right, bottom))
        }
    }

    /** Верхняя полоса 5% экрана всегда открывает меню. */
    private var constantMenuRegion: RectF = RectF(0f, 0f, 1f, 0.05f)

    var invertMode: MangaTappingInvertMode = MangaTappingInvertMode.NONE

    protected abstract var regionList: List<Region>

    fun getRegions(): List<Region> = regionList.map { it.invert(invertMode) }

    fun getAction(pos: PointF): NavigationRegion {
        val x = pos.x
        val y = pos.y
        val region = getRegions().find { it.rectF.contains(x, y) }
        return when {
            region != null -> region.type
            constantMenuRegion.contains(x, y) -> NavigationRegion.MENU
            else -> NavigationRegion.MENU
        }
    }
}