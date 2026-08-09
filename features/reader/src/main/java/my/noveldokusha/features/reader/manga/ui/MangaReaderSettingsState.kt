package my.noveldokusha.features.reader.manga.ui

import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.features.reader.manga.setting.MangaReaderOrientation
import my.noveldokusha.features.reader.manga.setting.MangaTappingInvertMode
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart

/**
 * Состояние настроек манга/манхва-читалки для settings-шита.
 * Вся мутация идёт через [actions] (Activity пишет в AppPreferences).
 */
internal data class MangaReaderSettingsState(
    val readingMode: MangaReadingMode = MangaReadingMode.WEBTOON,
    val orientation: MangaReaderOrientation = MangaReaderOrientation.FREE,
    val transitionsPager: Boolean = true,
    val transitionsWebtoon: Boolean = true,
    val showPageNumber: Boolean = true,
    val keepScreenOn: Boolean = false,
    val fullscreen: Boolean = true,
    val readerTheme: Int = 1, // 0=WHITE, 1=BLACK, 2=GRAY, 3=AUTO
    val webtoonSidePadding: Int = 0,
    val webtoonDoubleTapZoom: Boolean = true,
    val webtoonDisableZoomOut: Boolean = false,
    val zoomStart: MangaZoomStart = MangaZoomStart.AUTOMATIC,
    val doubleTapAnimSpeed: Int = 500, // fast=300, medium=500, slow=750
    val navModePager: MangaNavigationMode = MangaNavigationMode.DEFAULT,
    val navModeWebtoon: MangaNavigationMode = MangaNavigationMode.DEFAULT,
    val tappingInvertedPager: MangaTappingInvertMode = MangaTappingInvertMode.NONE,
    val tappingInvertedWebtoon: MangaTappingInvertMode = MangaTappingInvertMode.NONE,
    val volumeKeys: Boolean = false,
    val volumeKeysInverted: Boolean = false,
    val longTap: Boolean = true,
    val colorFilterEnabled: Boolean = false,
    val colorFilterValue: Int = 0,
    val colorFilterMode: Int = 0, // 0..5 нормальный/multiply/screen/overlay/lighten/darken
    val grayscale: Boolean = false,
    val invertedColors: Boolean = false,
    val autoscrollEnabled: Boolean = false,
    val autoscrollInterval: Float = 3f,
    val autoscrollSmooth: Boolean = true,
    val pagePrefetchCount: Int = 8,
)

/** Сеттеры — Activity обновляет AppPreferences и пересобирает state. */
internal interface MangaReaderSettingsActions {
    fun setReadingMode(mode: MangaReadingMode)
    fun setOrientation(orientation: MangaReaderOrientation)
    fun setTransitionsPager(enabled: Boolean)
    fun setTransitionsWebtoon(enabled: Boolean)
    fun setShowPageNumber(show: Boolean)
    fun setKeepScreenOn(enabled: Boolean)
    fun setFullscreen(enabled: Boolean)
    fun setReaderTheme(theme: Int)
    fun setWebtoonSidePadding(padding: Int)
    fun setWebtoonDoubleTapZoom(enabled: Boolean)
    fun setWebtoonDisableZoomOut(enabled: Boolean)
    fun setZoomStart(zoomStart: MangaZoomStart)
    fun setDoubleTapAnimSpeed(speed: Int)
    fun setNavModePager(mode: MangaNavigationMode)
    fun setNavModeWebtoon(mode: MangaNavigationMode)
    fun setTappingInvertedPager(mode: MangaTappingInvertMode)
    fun setTappingInvertedWebtoon(mode: MangaTappingInvertMode)
    fun setVolumeKeys(enabled: Boolean)
    fun setVolumeKeysInverted(enabled: Boolean)
    fun setLongTap(enabled: Boolean)
    fun setColorFilterEnabled(enabled: Boolean)
    fun setColorFilterValue(value: Int)
    fun setColorFilterMode(mode: Int)
    fun setGrayscale(enabled: Boolean)
    fun setInvertedColors(enabled: Boolean)
    fun setAutoscrollEnabled(enabled: Boolean)
    fun setAutoscrollInterval(interval: Float)
    fun setAutoscrollSmooth(enabled: Boolean)
    fun setPagePrefetchCount(count: Int)
}