package my.noveldokusha.features.reader.manga.setting

import androidx.annotation.StringRes
import my.noveldokusha.reader.R

/**
 * Режимы чтения — порт tachiyomisy ReadingMode, адаптированный под NoveLA
 * (только страничные главы; labels вместо moko-ресурсов).
 */
internal enum class MangaReadingMode(
    val flagValue: Int,
    @StringRes val labelRes: Int,
) {
    LEFT_TO_RIGHT(0x00000001, R.string.manga_mode_ltr),
    RIGHT_TO_LEFT(0x00000002, R.string.manga_mode_rtl),
    VERTICAL(0x00000003, R.string.manga_mode_vertical),
    WEBTOON(0x00000004, R.string.manga_mode_webtoon),
    CONTINUOUS_VERTICAL(0x00000005, R.string.manga_mode_continuous_vertical),
    ;

    companion object {
        fun fromPreference(preference: Int?): MangaReadingMode =
            entries.find { it.flagValue == preference } ?: DEFAULT

        /** Режим по умолчанию: WEBTOON (манхва-ориентированный дефолт). */
        val DEFAULT: MangaReadingMode = WEBTOON
    }
}
