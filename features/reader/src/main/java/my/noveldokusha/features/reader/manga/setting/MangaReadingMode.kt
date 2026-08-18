package my.noveldokusha.features.reader.manga.setting

import androidx.annotation.StringRes
import my.noveldokusha.reader.R

/**
 * Режимы чтения — порт tachiyomisy ReadingMode, адаптированный под NoveLA
 * (только страничные главы; labels вместо moko-ресурсов).
 *
 * Два режима: PAGED (постраничный, направление листания фиксировано R2L)
 * и WEBTOON (непрерывная лента).
 */
internal enum class MangaReadingMode(
    val flagValue: Int,
    @StringRes val labelRes: Int,
) {
    PAGED(0x00000001, R.string.manga_mode_paged),
    WEBTOON(0x00000004, R.string.manga_mode_webtoon),
    ;

    companion object {
        /** 0x05 — legacy CONTINUOUS_VERTICAL (тождественен WEBTOON после удаления зазоров). */
        private const val LEGACY_CONTINUOUS_VERTICAL = 0x00000005

        /**
         * Маппинг старых значений: 0x01 (LTR), 0x02 (RTL) и 0x03 (VERTICAL)
         * были режимами пейджера и теперь сворачиваются в PAGED; 0x04 — WEBTOON.
         */
        fun fromPreference(preference: Int?): MangaReadingMode = when (preference) {
            LEGACY_CONTINUOUS_VERTICAL -> WEBTOON
            0x00000001, 0x00000002, 0x00000003 -> PAGED
            0x00000004 -> WEBTOON
            else -> DEFAULT
        }

        /** Режим по умолчанию: WEBTOON (манхва-ориентированный дефолт). */
        val DEFAULT: MangaReadingMode = WEBTOON
    }
}