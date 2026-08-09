package my.noveldokusha.features.reader.manga.setting

import androidx.annotation.StringRes
import my.noveldokusha.reader.R

/**
 * Зоны-навигация (тап-зоны) — порт tachiyomisy ReaderPreferences.TapZones.
 * Порядок значений сохранён как в tachiyomisy (0 = default).
 */
internal enum class MangaNavigationMode(
    val value: Int,
    @StringRes val labelRes: Int,
) {
    DEFAULT(0, R.string.manga_nav_default),
    L(1, R.string.manga_nav_l),
    KINDLISH(2, R.string.manga_nav_kindlish),
    EDGE(3, R.string.manga_nav_edge),
    RIGHT_AND_LEFT(4, R.string.manga_nav_right_left),
    DISABLED(5, R.string.manga_nav_disabled),
    ;

    companion object {
        fun fromPreference(preference: Int?): MangaNavigationMode =
            entries.find { it.value == preference } ?: DEFAULT
    }
}

/** Инверсия тап-зон — порт tachiyomisy TappingInvertMode. */
internal enum class MangaTappingInvertMode(
    val storageKey: String,
    val shouldInvertHorizontal: Boolean = false,
    val shouldInvertVertical: Boolean = false,
) {
    NONE("NONE"),
    HORIZONTAL("HORIZONTAL", shouldInvertHorizontal = true),
    VERTICAL("VERTICAL", shouldInvertVertical = true),
    BOTH("BOTH", shouldInvertHorizontal = true, shouldInvertVertical = true),
    ;

    companion object {
        fun fromStorage(value: String?): MangaTappingInvertMode =
            entries.find { it.storageKey == value } ?: NONE
    }
}

/** Начальная позиция зума — порт tachiyomisy ZoomStartPosition + automatic. */
internal enum class MangaZoomStart(
    val value: Int,
    @StringRes val labelRes: Int,
) {
    AUTOMATIC(0, R.string.manga_zoom_start_automatic),
    LEFT(1, R.string.manga_zoom_start_left),
    CENTER(2, R.string.manga_zoom_start_center),
    RIGHT(3, R.string.manga_zoom_start_right),
    ;

    companion object {
        fun fromPreference(preference: Int?): MangaZoomStart =
            entries.find { it.value == preference } ?: AUTOMATIC
    }
}
