package my.noveldokusha.features.reader.manga.ui

import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode

/**
 * Состояние настроек манга/манхва-читалки для settings-шита.
 * Вся мутация идёт через [actions] (Activity пишет в AppPreferences).
 *
 * Набор соответствует tachiyomisy ReaderSettingsDialog: три вкладки
 * (Reading mode / General / Custom filters); фон читалки следует теме
 * приложения (night-aware), без отдельной настройки.
 */
internal data class MangaReaderSettingsState(
    val readingMode: MangaReadingMode = MangaReadingMode.WEBTOON,
    val transitionsPager: Boolean = true,
    val transitionsWebtoon: Boolean = true,
    val showPageNumber: Boolean = true,
    val keepScreenOn: Boolean = false,
    val fullscreen: Boolean = true,
    val webtoonSidePadding: Int = 0,
    val navModePager: MangaNavigationMode = MangaNavigationMode.BOTH,
    val navModeWebtoon: MangaNavigationMode = MangaNavigationMode.BOTH,
    val tappingInverted: Boolean = false,
    val longTap: Boolean = true,
    val downloadOnOpen: Boolean = false,
    val colorFilterEnabled: Boolean = false,
    val colorFilterValue: Int = 0,
    val colorFilterMode: Int = 0, // 0..5 нормальный/multiply/screen/overlay/lighten/darken
    val customBrightness: Boolean = false,
    val customBrightnessValue: Int = 0, // -75..100, как в tachiyomisy
    val grayscale: Boolean = false,
    val invertedColors: Boolean = false,
)

/** Сеттеры — Activity обновляет AppPreferences и пересобирает state. */
internal interface MangaReaderSettingsActions {
    fun setReadingMode(mode: MangaReadingMode)
    fun setTransitionsPager(enabled: Boolean)
    fun setTransitionsWebtoon(enabled: Boolean)
    fun setShowPageNumber(show: Boolean)
    fun setKeepScreenOn(enabled: Boolean)
    fun setFullscreen(enabled: Boolean)
    fun setWebtoonSidePadding(padding: Int)
    fun setNavModePager(mode: MangaNavigationMode)
    fun setNavModeWebtoon(mode: MangaNavigationMode)
    fun setTappingInverted(enabled: Boolean)
    fun setLongTap(enabled: Boolean)
    fun setDownloadOnOpen(enabled: Boolean)
    fun setColorFilterEnabled(enabled: Boolean)
    fun setColorFilterValue(value: Int)
    fun setColorFilterMode(mode: Int)
    fun setCustomBrightness(enabled: Boolean)
    fun setCustomBrightnessValue(value: Int)
    fun setGrayscale(enabled: Boolean)
    fun setInvertedColors(enabled: Boolean)
}
