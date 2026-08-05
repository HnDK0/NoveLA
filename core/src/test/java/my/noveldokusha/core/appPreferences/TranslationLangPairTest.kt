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
    fun `per-novel mode off by default without a full pair`() {
        val empty = emptyMap<String, TranslationLangPair>()

        assertFalse(resolveTranslationEnabled(false, globalEnabled = true, map = empty, bookUrl = "a"))
        assertEquals(TranslationLangPair(), resolveTranslationPair(false, "en", "ru", empty, "a"))
    }

    @Test
    fun `partial pair is not enabled in per-novel mode`() {
        val map = mapOf("a" to TranslationLangPair(source = "en"))

        assertFalse(resolveTranslationEnabled(false, globalEnabled = true, map = map, bookUrl = "a"))
    }

    @Test
    fun `full pair enables the novel in per-novel mode`() {
        val map = mapOf("a" to TranslationLangPair(source = "en", target = "ru"))

        assertTrue(resolveTranslationEnabled(false, globalEnabled = false, map = map, bookUrl = "a"))
        assertEquals(TranslationLangPair("en", "ru"), resolveTranslationPair(false, "en", "ru", map, "a"))
    }

    @Test
    fun `global mode ignores per-novel map`() {
        val map = mapOf("a" to TranslationLangPair(source = "en", target = "ru"))

        assertTrue(resolveTranslationEnabled(true, globalEnabled = true, map = map, bookUrl = "other"))
        assertFalse(resolveTranslationEnabled(true, globalEnabled = false, map = map, bookUrl = "a"))
        assertEquals(
            TranslationLangPair(source = "fr", target = "de"),
            resolveTranslationPair(true, "fr", "de", map, "a"),
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
    fun `writing partial pair removes entry (unpin)`() {
        val initial = mapOf("a" to TranslationLangPair("en", "ru"))

        val updated = updateTranslationPairMap(initial, bookUrl = "a", source = "", target = "ru")
        val updated2 = updateTranslationPairMap(initial, bookUrl = "a", source = "en", target = "")

        assertTrue(updated.isEmpty())
        assertTrue(updated2.isEmpty())
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
