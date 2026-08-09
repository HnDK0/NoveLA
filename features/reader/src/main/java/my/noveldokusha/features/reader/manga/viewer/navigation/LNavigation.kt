package my.noveldokusha.features.reader.manga.viewer.navigation

import android.graphics.RectF
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation

/** Порт tachiyomisy LNavigation. */
internal open class LNavigation : ViewerNavigation() {
    override var regionList: List<Region> = listOf(
        Region(RectF(0f, 0.33f, 0.33f, 0.66f), NavigationRegion.PREV),
        Region(RectF(0f, 0f, 1f, 0.33f), NavigationRegion.PREV),
        Region(RectF(0.66f, 0.33f, 1f, 0.66f), NavigationRegion.NEXT),
        Region(RectF(0f, 0.66f, 1f, 1f), NavigationRegion.NEXT),
    )
}