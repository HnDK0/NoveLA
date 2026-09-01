package my.noveldokusha.core.appPreferences

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты избранных языков перевода и списка последних пар.
 * Проверяют чистую логику AppPreferences поверх реального SharedPreferences (Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranslationFavoritesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        prefs = AppPreferences(context)
        // Чистый старт: сбрасываем оба префа перед каждым тестом.
        prefs.TRANSLATION_FAVORITE_LANGUAGES.value = emptyList()
        prefs.TRANSLATION_RECENT_PAIRS.value = emptyList()
    }

    // ─── toggleFavoriteLanguage ─────────────────────────────────────────────

    @Test
    fun `toggle adds a code`() {
        prefs.toggleFavoriteLanguage("en")

        assertTrue(prefs.isFavoriteLanguage("en"))
        assertEquals(listOf("en"), prefs.favoriteLanguages())
    }

    @Test
    fun `toggle removes a code when re-toggled`() {
        prefs.toggleFavoriteLanguage("en")
        prefs.toggleFavoriteLanguage("ru")

        prefs.toggleFavoriteLanguage("en")

        assertFalse(prefs.isFavoriteLanguage("en"))
        assertEquals(listOf("ru"), prefs.favoriteLanguages())
    }

    // ─── isFavoriteLanguage ─────────────────────────────────────────────────

    @Test
    fun `isFavorite true only for pinned codes`() {
        prefs.toggleFavoriteLanguage("en")

        assertTrue(prefs.isFavoriteLanguage("en"))
        assertFalse(prefs.isFavoriteLanguage("ru"))
        assertFalse(prefs.isFavoriteLanguage(""))
    }

    // ─── favoriteLanguages ──────────────────────────────────────────────────

    @Test
    fun `favoriteLanguages ordered most-recently-pinned first`() {
        prefs.toggleFavoriteLanguage("en")
        prefs.toggleFavoriteLanguage("ru")
        prefs.toggleFavoriteLanguage("fr")

        assertEquals(listOf("fr", "ru", "en"), prefs.favoriteLanguages())
    }

    @Test
    fun `favoriteLanguages is unbounded`() {
        val codes = listOf("en", "ru", "fr", "de", "es", "it", "ja", "ko")
        codes.forEach { prefs.toggleFavoriteLanguage(it) }

        // Все 8 кодов сохранены — верхней границы нет.
        assertEquals(codes.reversed(), prefs.favoriteLanguages())
    }

    // ─── recordRecentTranslationPair ────────────────────────────────────────

    @Test
    fun `record inserts at the front`() {
        prefs.recordRecentTranslationPair("en", "ru")
        prefs.recordRecentTranslationPair("fr", "de")

        assertEquals(
            listOf(
                TranslationLangPair("fr", "de"),
                TranslationLangPair("en", "ru"),
            ),
            prefs.recentTranslationPairs(),
        )
    }

    @Test
    fun `record dedupes by exact pair`() {
        prefs.recordRecentTranslationPair("en", "ru")
        prefs.recordRecentTranslationPair("fr", "de")
        prefs.recordRecentTranslationPair("en", "ru")

        assertEquals(
            listOf(
                TranslationLangPair("en", "ru"),
                TranslationLangPair("fr", "de"),
            ),
            prefs.recentTranslationPairs(),
        )
    }

    @Test
    fun `record caps the list at five`() {
        val pairs = listOf(
            "en" to "ru",
            "fr" to "de",
            "es" to "it",
            "ja" to "ko",
            "zh" to "en",
            "pt" to "pl",
        )
        pairs.forEach { (s, t) -> prefs.recordRecentTranslationPair(s, t) }

        // Остаются только 5 самых свежих пар.
        assertEquals(
            listOf(
                TranslationLangPair("pt", "pl"),
                TranslationLangPair("zh", "en"),
                TranslationLangPair("ja", "ko"),
                TranslationLangPair("es", "it"),
                TranslationLangPair("fr", "de"),
            ),
            prefs.recentTranslationPairs(),
        )
    }

    // ─── recentTranslationPairs ─────────────────────────────────────────────

    @Test
    fun `recentPairs returns most-recent-first`() {
        prefs.recordRecentTranslationPair("en", "ru")
        prefs.recordRecentTranslationPair("fr", "de")
        prefs.recordRecentTranslationPair("ja", "ko")

        assertEquals(
            listOf(
                TranslationLangPair("ja", "ko"),
                TranslationLangPair("fr", "de"),
                TranslationLangPair("en", "ru"),
            ),
            prefs.recentTranslationPairs(),
        )
    }

    // ─── Codec roundtrip ────────────────────────────────────────────────────

    @Test
    fun `recent pairs codec roundtrip preserves order and values`() {
        val pairs = listOf(
            TranslationLangPair("en", "ru"),
            TranslationLangPair("fr", "de"),
        )

        val encoded = encodeTranslationPairs(pairs)
        val decoded = decodeTranslationPairs(encoded)

        assertEquals(pairs, decoded)
    }

    @Test
    fun `recent pairs codec roundtrip of empty list`() {
        val decoded = decodeTranslationPairs(encodeTranslationPairs(emptyList()))

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `favorite languages codec roundtrip preserves order`() {
        val codes = listOf("en", "ru", "fr")

        val encoded = kotlinx.serialization.json.Json.encodeToString(codes)
        val decoded = kotlinx.serialization.json.Json.decodeFromString<List<String>>(encoded)

        assertEquals(codes, decoded)
    }

    // ─── Failure: malformed value yields empty default ──────────────────────

    @Test
    fun `malformed recent pairs value yields empty list`() {
        // Кладём мусорную строку напрямую в тот же SharedPreferences, что читает AppPreferences.
        // Декодер оборачивается в runCatching и должен вернуть пустой список по умолчанию.
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString("TRANSLATION_RECENT_PAIRS", "not json at all {")
            .apply()

        assertTrue(prefs.recentTranslationPairs().isEmpty())
    }
}
