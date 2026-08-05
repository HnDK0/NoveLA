package my.noveldokusha.core.appPreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLangPairTest {

    // ─── isComplete ─────────────────────────────────────────────────────────

    @Test
    fun `pair complete only when both languages selected`() {
        assertFalse(TranslationLangPair().isComplete)
        assertFalse(TranslationLangPair(source = "en").isComplete)
        assertFalse(TranslationLangPair(target = "ru").isComplete)
        assertFalse(TranslationLangPair(source = " ", target = "ru").isComplete)
        assertTrue(TranslationLangPair(source = "en", target = "ru").isComplete)
    }

    // ─── JSON codec ─────────────────────────────────────────────────────────

    @Test
    fun `codec roundtrip preserves map`() {
        val map = mapOf(
            "https://example.com/a" to TranslationLangPair(source = "en", target = "ru"),
            "local://Книга" to TranslationLangPair(source = "fr", target = "en"),
        )

        val decoded = decodeTranslationPairMap(encodeTranslationPairMap(map))

        assertEquals(map, decoded)
    }

    @Test
    fun `codec decode of corrupt json returns empty map`() {
        assertTrue(decodeTranslationPairMap("not json at all {").isEmpty())
        assertTrue(decodeTranslationPairMap("").isEmpty())
        assertTrue(decodeTranslationPairMap("[]").isEmpty())
    }

    @Test
    fun `codec decode ignores non-object values`() {
        val decoded = decodeTranslationPairMap(
            """{"url": "not an object", "b": {"source": "en", "target": "ru"}}"""
        )
        assertEquals(1, decoded.size)
        assertEquals(TranslationLangPair(source = "en", target = "ru"), decoded["b"])
    }

    // ─── Per-novel mode semantics ───────────────────────────────────────────

    @Test
    fun `per-novel mode off by default`() {
        val enabledMap = emptyMap<String, Boolean>()

        assertFalse(resolveTranslationEnabled(false, globalEnabled = true, enabledMap = enabledMap, bookUrl = "a"))
        assertEquals(TranslationLangPair(), resolveTranslationPair(false, "en", "ru", emptyMap(), "a"))
    }

    @Test
    fun `per-novel mode enabled only when flag present`() {
        val enabledMap = mapOf("a" to true)

        assertTrue(resolveTranslationEnabled(false, globalEnabled = false, enabledMap = enabledMap, bookUrl = "a"))
        assertFalse(resolveTranslationEnabled(false, globalEnabled = false, enabledMap = enabledMap, bookUrl = "b"))
    }

    @Test
    fun `full pair does not enable - toggle decides`() {
        val pairs = mapOf("a" to TranslationLangPair(source = "en", target = "ru"))
        val enabledMap = emptyMap<String, Boolean>()

        assertFalse(resolveTranslationEnabled(false, globalEnabled = true, enabledMap = enabledMap, bookUrl = "a"))
        assertEquals(TranslationLangPair("en", "ru"), resolveTranslationPair(false, "en", "ru", pairs, "a"))
    }

    @Test
    fun `global mode ignores per-novel enabled map`() {
        val enabledMap = mapOf("a" to false)

        assertTrue(resolveTranslationEnabled(true, globalEnabled = true, enabledMap = enabledMap, bookUrl = "a"))
        assertFalse(resolveTranslationEnabled(true, globalEnabled = false, enabledMap = enabledMap, bookUrl = "a"))
        assertEquals(
            TranslationLangPair(source = "fr", target = "de"),
            resolveTranslationPair(true, "fr", "de", emptyMap(), "a"),
        )
    }

    @Test
    fun `writing full pair stores it`() {
        val updated = updateTranslationPairMap(
            map = emptyMap(), bookUrl = "a", source = "en", target = "ru"
        )

        assertEquals(mapOf("a" to TranslationLangPair("en", "ru")), updated)
    }

    @Test
    fun `writing partial pair keeps entry`() {
        val initial = mapOf("a" to TranslationLangPair("en", "ru"))

        val updated = updateTranslationPairMap(initial, bookUrl = "a", source = "", target = "ru")
        val updated2 = updateTranslationPairMap(initial, bookUrl = "a", source = "en", target = "")

        assertEquals(TranslationLangPair("", "ru"), updated["a"])
        assertEquals(TranslationLangPair("en", ""), updated2["a"])
    }

    @Test
    fun `writing empty pair removes entry`() {
        val initial = mapOf("a" to TranslationLangPair("en", "ru"))

        val updated = updateTranslationPairMap(initial, bookUrl = "a", source = "", target = "")

        assertTrue(updated.isEmpty())
    }

    // ─── Enabled map codec ──────────────────────────────────────────────────

    @Test
    fun `enabled map codec roundtrip preserves map`() {
        val map = mapOf(
            "https://example.com/a" to true,
            "local://Книга" to false,
        )

        val decoded = decodeEnabledMap(encodeEnabledMap(map))

        assertEquals(map, decoded)
    }

    @Test
    fun `enabled map decode of corrupt json returns empty map`() {
        assertTrue(decodeEnabledMap("not json at all {").isEmpty())
        assertTrue(decodeEnabledMap("").isEmpty())
        assertTrue(decodeEnabledMap("[]").isEmpty())
    }

    @Test
    fun `enabled map decode treats missing or non-bool values as false`() {
        val decoded = decodeEnabledMap("""{"a": true, "b": "yes", "c": 1}""")

        assertEquals(true, decoded["a"])
        assertEquals(false, decoded["b"])
        assertEquals(false, decoded["c"])
    }

    // ─── Enabled state migration (pairs -> toggle map) ──────────────────────

    @Test
    fun `migration derives enabled from complete pairs only`() {
        val pairs = mapOf(
            "a" to TranslationLangPair("en", "ru"),
            "b" to TranslationLangPair("en"),
            "c" to TranslationLangPair(),
        )

        assertEquals(mapOf("a" to true), deriveEnabledMapFromPairs(pairs))
    }

    @Test
    fun `migration from empty pairs gives empty enabled map`() {
        assertTrue(deriveEnabledMapFromPairs(emptyMap()).isEmpty())
    }

    // ─── Legacy migration (TRANSLATION_BOOK_ENABLED) ─────────────────────────

    @Test
    fun `migration copies global pair to novels enabled without own pair`() {
        val legacy = """{"a": true, "b": false}"""

        val migrated = migrateLegacyEnabledToPairs(
            legacyJson = legacy,
            pairs = emptyMap(),
            globalSource = "en",
            globalTarget = "ru",
        )

        assertEquals(mapOf("a" to TranslationLangPair("en", "ru")), migrated)
    }

    @Test
    fun `migration does not overwrite existing pair`() {
        val existing = mapOf("a" to TranslationLangPair("fr", "de"))

        val migrated = migrateLegacyEnabledToPairs(
            legacyJson = """{"a": true}""",
            pairs = existing,
            globalSource = "en",
            globalTarget = "ru",
        )

        assertEquals(existing, migrated)
    }

    @Test
    fun `migration skips disabled novels`() {
        val migrated = migrateLegacyEnabledToPairs(
            legacyJson = """{"a": false}""",
            pairs = emptyMap(),
            globalSource = "en",
            globalTarget = "ru",
        )

        assertTrue(migrated.isEmpty())
    }

    @Test
    fun `migration removes pair for explicitly disabled novel`() {
        val existing = mapOf("a" to TranslationLangPair("en", "ru"))

        val migrated = migrateLegacyEnabledToPairs(
            legacyJson = """{"a": false}""",
            pairs = existing,
            globalSource = "en",
            globalTarget = "ru",
        )

        assertTrue(migrated.isEmpty())
    }

    @Test
    fun `migration removes disabled pairs even without global pair`() {
        val existing = mapOf("a" to TranslationLangPair("en", "ru"))

        val migrated = migrateLegacyEnabledToPairs(
            legacyJson = """{"a": false}""",
            pairs = existing,
            globalSource = "en",
            globalTarget = "",
        )

        assertTrue(migrated.isEmpty())
    }

    @Test
    fun `migration keeps own pair of enabled novel`() {
        val existing = mapOf("a" to TranslationLangPair("fr", "de"), "b" to TranslationLangPair("en", "ru"))

        val migrated = migrateLegacyEnabledToPairs(
            legacyJson = """{"a": true, "b": false}""",
            pairs = existing,
            globalSource = "en",
            globalTarget = "ru",
        )

        assertEquals(mapOf("a" to TranslationLangPair("fr", "de")), migrated)
    }

    @Test
    fun `migration needs complete global pair`() {
        val migrated = migrateLegacyEnabledToPairs(
            legacyJson = """{"a": true}""",
            pairs = emptyMap(),
            globalSource = "en",
            globalTarget = "",
        )

        assertTrue(migrated.isEmpty())
    }

    @Test
    fun `migration ignores missing or corrupt legacy json`() {
        val empty = emptyMap<String, TranslationLangPair>()

        assertEquals(empty, migrateLegacyEnabledToPairs(null, empty, "en", "ru"))
        assertEquals(empty, migrateLegacyEnabledToPairs("", empty, "en", "ru"))
        assertEquals(empty, migrateLegacyEnabledToPairs("not json {", empty, "en", "ru"))
    }
}
