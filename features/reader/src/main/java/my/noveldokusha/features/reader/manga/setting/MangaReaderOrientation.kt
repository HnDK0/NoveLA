package my.noveldokusha.features.reader.manga.setting

import android.content.pm.ActivityInfo
import androidx.annotation.StringRes
import my.noveldokusha.reader.R

/**
 * Ориентация читалки — порт tachiyomisy ReaderOrientation
 * (те же flag-значения для совместимости сохранённых настроек).
 */
internal enum class MangaReaderOrientation(
    val flag: Int,
    val flagValue: Int,
    @StringRes val labelRes: Int,
) {
    DEFAULT(
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        0x00000000,
        R.string.manga_orientation_default,
    ),
    FREE(
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        0x00000008,
        R.string.manga_orientation_free,
    ),
    PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        0x00000010,
        R.string.manga_orientation_portrait,
    ),
    LANDSCAPE(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        0x00000018,
        R.string.manga_orientation_landscape,
    ),
    LOCKED_PORTRAIT(
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        0x00000020,
        R.string.manga_orientation_locked_portrait,
    ),
    LOCKED_LANDSCAPE(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        0x00000028,
        R.string.manga_orientation_locked_landscape,
    ),
    ;

    companion object {
        fun fromPreference(preference: Int?): MangaReaderOrientation =
            entries.find { it.flagValue == preference } ?: FREE
    }
}
