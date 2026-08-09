package my.noveldokusha.features.reader.manga.viewer.navigation

import android.graphics.RectF
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation

/** Порт tachiyomisy KindlishNavigation. */
internal class KindlishNavigation : ViewerNavigation() {
    override var regionList: List<Region> = listOf(
        Region(RectF(0.33f, 0.33f, 1f, 1f), NavigationRegion.NEXT),
        Region(RectF(0f, 0.33f, 0.33f, 1f), NavigationRegion.PREV),
    )
}