package my.noveldokusha.features.reader.manga.setting

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Дефолт ориентации манга-ридера — Портрет: невалидное/отсутствующее
 * значение префа даёт PORTRAIT, а явно сохранённый выбор пользователя
 * (FREE/PORTRAIT и т.д.) сохраняется.
 */
class MangaReaderOrientationTest {

    @Test
    fun fromPreferenceNullDefaultsToPortrait() {
        assertEquals(MangaReaderOrientation.PORTRAIT, MangaReaderOrientation.fromPreference(null))
    }

    @Test
    fun fromPreferenceFreeKeepsExplicitChoice() {
        assertEquals(MangaReaderOrientation.FREE, MangaReaderOrientation.fromPreference(MangaReaderOrientation.FREE.flagValue))
    }

    @Test
    fun fromPreferencePortraitKeepsExplicitChoice() {
        assertEquals(
            MangaReaderOrientation.PORTRAIT,
            MangaReaderOrientation.fromPreference(MangaReaderOrientation.PORTRAIT.flagValue),
        )
    }

    @Test
    fun fromPreferenceInvalidValueDefaultsToPortrait() {
        assertEquals(MangaReaderOrientation.PORTRAIT, MangaReaderOrientation.fromPreference(999))
    }
}