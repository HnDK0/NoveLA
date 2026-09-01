package my.noveldokusha.features.reader.features

import my.noveldokusha.core.appPreferences.TranslationLangPair
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты предиката shouldRecordRecentPair: решает, нужно ли записывать новую пару
 * в список последних. Защищает от частичных пар и повторной записи без изменений.
 */
class ShouldRecordRecentPairTest {

    // ─── Частичный выбор / удаление ─────────────────────────────────────────

    @Test
    fun `false when source is blank`() {
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), "", "ru"))
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), " ", "ru"))
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), null, "ru"))
    }

    @Test
    fun `false when target is blank`() {
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), "en", ""))
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), "en", " "))
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), "en", null))
    }

    @Test
    fun `false when both blank`() {
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), "", ""))
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), null, null))
    }

    // ─── Повторная запись без изменений ─────────────────────────────────────

    @Test
    fun `false when resulting pair equals previous pair`() {
        assertFalse(shouldRecordRecentPair(TranslationLangPair("en", "ru"), "en", "ru"))
    }

    // ─── Новая полная пара ──────────────────────────────────────────────────

    @Test
    fun `true for a genuinely new complete pair`() {
        assertTrue(shouldRecordRecentPair(TranslationLangPair("en", "ru"), "fr", "de"))
        assertTrue(shouldRecordRecentPair(null, "en", "ru"))
    }

    // ─── Критическая регрессия: применение недавнего чипа ───────────────────

    @Test
    fun `applying a recent chip records the full pair`() {
        // Предыдущая пара (en, ru). Пользователь применяет недавний чип (ja, ko).
        // Применение чипа выставляет source и target одновременно, поэтому предикат
        // вызывается только с полной парой (ja, ko) и должен вернуть true — записывается
        // именно она, а не частичная (ja, ru).
        val previousPair = TranslationLangPair("en", "ru")

        assertTrue(shouldRecordRecentPair(previousPair, "ja", "ko"))
    }
}
