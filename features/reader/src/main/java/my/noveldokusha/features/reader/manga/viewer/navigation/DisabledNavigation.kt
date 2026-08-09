package my.noveldokusha.features.reader.manga.viewer.navigation

import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation

/** Порт tachiyomisy DisabledNavigation: все тапы идут в меню. */
internal class DisabledNavigation : ViewerNavigation() {
    override var regionList: List<Region> = emptyList()
}