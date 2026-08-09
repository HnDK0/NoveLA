package my.noveldokusha.features.reader.manga.viewer.navigation

import android.graphics.RectF
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation

/** Порт tachiyomisy RightAndLeftNavigation. */
internal class RightAndLeftNavigation : ViewerNavigation() {
    override var regionList: List<Region> = listOf(
        Region(RectF(0f, 0f, 0.33f, 1f), NavigationRegion.LEFT),
        Region(RectF(0.66f, 0f, 1f, 1f), NavigationRegion.RIGHT),
    )
}