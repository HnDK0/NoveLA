package my.noveldokusha.features.reader.manga.setting

import androidx.annotation.StringRes
import my.noveldokusha.reader.R

/**
 * Режимы навигации манга-ридера — 3 простых варианта вместо зоопарка тап-зон:
 *  - SWIPE — только листание (тапы в любой точке открывают меню);
 *  - TAP_EDGES — тапы по краям (левый край = назад, правый = вперёд);
 *  - BOTH — листание + тапы (прежнее поведение по умолчанию).
 *
 * Инверсия тапов (левый край = вперёд, правый = назад) — отдельный тогл
 * [MANGA_READER_TAPPING_INVERTED], общий для пейджера и вебтуна.
 *
 * Значения 10..12 — новые. Легаси-значения 0..5 из старой схемы тап-зон
 * и 13 (старый «для левшей») маппятся в [fromPreference], чтобы не ломать
 * сохранённые настройки: 13 теряет инверсию, она теперь отдельным тоглом.
 */
internal enum class MangaNavigationMode(
    val value: Int,
    @StringRes val labelRes: Int,
) {
    SWIPE(10, R.string.manga_nav_swipe),
    TAP_EDGES(11, R.string.manga_nav_tap_edges),
    BOTH(12, R.string.manga_nav_both),
    ;

    companion object {
        fun fromPreference(preference: Int?): MangaNavigationMode = when (preference) {
            // Легаси старой схемы тап-зон: 0..4 (default/L/Kindlish/Edge/
            // RightAndLeft) → BOTH (прежнее поведение по умолчанию),
            // 5 (выключено) → SWIPE (только листание).
            in 0..4 -> BOTH
            5 -> SWIPE
            // Старый BOTH_INVERTED (13) → BOTH: инверсия теперь отдельным тоглом.
            13 -> BOTH
            else -> entries.find { it.value == preference } ?: BOTH
        }
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
