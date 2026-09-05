package my.noveldokusha.core.appPreferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты трёхуровневой резолюции перевода:
 *   per-novel → per-plugin → global.
 *
 * ВКЛЮЧЕНИЕ перевода — три независимых уровня (OR): перевод идёт, если согласен
 * хотя бы один (perNovel || включённый плагин || глобальный режим И глобальный
 * включён). Выключение уровня НЕ блокирует остальные: выключенная книга не гасит
 * включённый плагин или глобальный перевод, и наоборот.
 *
 * ПАРА/провайдер — приоритет сверху вниз (самая специфичная настройка побеждает):
 *   1. пер-новел пара (book-карта);
 *   2. включённый плагин (pluginMap[sourceId] == true) — выше глобала;
 *   3. глобальная пара (в глобальном режиме при включённом глобальном переводе);
 *   4. иначе — пустая пара / STANDARD scope / null provider.
 *
 * sourceId=null обязан давать ровно прежнее поведение (plugin-уровень пропускается).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranslationPluginCascadeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        prefs = AppPreferences(context)
        // Чистый старт: сбрасываем все префы перевода перед каждым тестом.
        prefs.TRANSLATION_GLOBAL_MODE.value = false
        prefs.GLOBAL_TRANSLATION_ENABLED.value = false
        prefs.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = "en"
        prefs.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = "ru"
        prefs.TRANSLATION_BOOK_ENABLED_MAP.value = emptyMap()
        prefs.TRANSLATION_BOOK_LANG_PAIR.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_ENABLED_MAP.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_LANG_PAIR.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_PROVIDER.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_SCOPE.value = emptyMap()
        prefs.TRANSLATION_PLUGIN_PROMPTS.value = emptyMap()
    }

    // ─── resolveTranslationEnabled: truth table ─────────────────────────────

    @Test
    fun `enabled - book key wins over global mode`() {
        // book-ключ = true включает перевод независимо от выключенного глобала.
        assertTrue(
            resolveTranslationEnabled(
                globalMode = false, globalEnabled = false,
                enabledMap = mapOf("book" to true), pluginMap = mapOf("lua_demo" to false),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
        // book-ключ = false (выключение книги) НЕ гасит другие уровни (OR):
        // включённые глобал и плагин продолжают переводить.
        assertTrue(
            resolveTranslationEnabled(
                globalMode = true, globalEnabled = true,
                enabledMap = mapOf("book" to false), pluginMap = mapOf("lua_demo" to true),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `enabled - book key present wins over plugin`() {
        val enabledMap = mapOf("book" to true)
        val pluginMap = mapOf("lua_demo" to false)

        assertTrue(
            resolveTranslationEnabled(
                globalMode = false, globalEnabled = false,
                enabledMap = enabledMap, pluginMap = pluginMap,
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `enabled - book key disabled does not cancel plugin enabled`() {
        // Выключенная книга (ключ false) НЕ отменяет включённый плагин (OR):
        // перевод идёт за счёт плагина.
        val enabledMap = mapOf("book" to false)
        val pluginMap = mapOf("lua_demo" to true)

        assertTrue(
            resolveTranslationEnabled(
                globalMode = false, globalEnabled = false,
                enabledMap = enabledMap, pluginMap = pluginMap,
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `enabled - no book key falls back to plugin`() {
        val enabledMap = emptyMap<String, Boolean>()
        val pluginMap = mapOf("lua_demo" to true)

        assertTrue(
            resolveTranslationEnabled(
                globalMode = false, globalEnabled = false,
                enabledMap = enabledMap, pluginMap = pluginMap,
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `enabled - global mode ignores disabled plugin`() {
        // Вариант A: выключенный переводчик плагина НЕ гасит глобальный перевод его книг.
        assertTrue(
            resolveTranslationEnabled(
                globalMode = true, globalEnabled = true,
                enabledMap = emptyMap(), pluginMap = mapOf("lua_demo" to false),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `enabled - outside global mode disabled plugin still wins`() {
        // Глобальный режим выключен, книга не задана, плагин выключен —
        // ни один уровень не согласен (OR): перевод выключен.
        assertFalse(
            resolveTranslationEnabled(
                globalMode = false, globalEnabled = true,
                enabledMap = emptyMap(), pluginMap = mapOf("lua_demo" to false),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `enabled - no keys yields false`() {
        assertFalse(
            resolveTranslationEnabled(
                globalMode = false, globalEnabled = false,
                enabledMap = emptyMap(), pluginMap = emptyMap(),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `enabled - sourceId null skips plugin tier (old behavior)`() {
        val pluginMap = mapOf("lua_demo" to true)

        // Плагин включён, но sourceId=null — плагин-уровень пропускается.
        assertFalse(
            resolveTranslationEnabled(
                globalMode = false, globalEnabled = false,
                enabledMap = emptyMap(), pluginMap = pluginMap,
                bookUrl = "book", sourceId = null,
            )
        )
    }

    // ─── resolveTranslationPair: truth table ────────────────────────────────

    @Test
    fun `pair - book key wins over global mode`() {
        val map = mapOf("book" to TranslationLangPair("fr", "de"))
        val pluginMap = mapOf("lua_demo" to TranslationLangPair("ja", "ko"))

        // Пара конкретной книги побеждает даже глобальную пару (пер-новел включён).
        assertEquals(
            TranslationLangPair("fr", "de"),
            resolveTranslationPair(
                globalMode = true, globalEnabled = true, globalSource = "en", globalTarget = "ru",
                map = map, bookEnabledMap = mapOf("book" to true), pluginMap = pluginMap, pluginEnabledMap = emptyMap(),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `pair - book key present wins over plugin`() {
        val map = mapOf("book" to TranslationLangPair("fr", "de"))
        val pluginMap = mapOf("lua_demo" to TranslationLangPair("ja", "ko"))

        assertEquals(
            TranslationLangPair("fr", "de"),
            resolveTranslationPair(
                globalMode = false, globalEnabled = true, globalSource = "en", globalTarget = "ru",
                map = map, bookEnabledMap = mapOf("book" to true), pluginMap = pluginMap, pluginEnabledMap = emptyMap(),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `pair - no book key falls back to plugin`() {
        val map = emptyMap<String, TranslationLangPair>()
        val pluginMap = mapOf("lua_demo" to TranslationLangPair("ja", "ko"))

        // ponytail: плагин должен быть включён (rule 5), иначе pair/scope/provider не пробрасываются в outside-global
        assertEquals(
            TranslationLangPair("ja", "ko"),
            resolveTranslationPair(
                globalMode = false, globalEnabled = true, globalSource = "en", globalTarget = "ru",
                map = map, bookEnabledMap = emptyMap(), pluginMap = pluginMap, pluginEnabledMap = mapOf("lua_demo" to true),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `pair - disabled novel does not leak its pair to plugin`() {
        val map = mapOf("book" to TranslationLangPair("fr", "de"))
        val pluginMap = mapOf("lua_demo" to TranslationLangPair("ja", "ko"))

        // Пер-новел ВЫКЛЮЧЕН (bookEnabledMap пуст), но пара сохранена: она не протекает
        // к плагину — действует пара включённого плагина.
        assertEquals(
            TranslationLangPair("ja", "ko"),
            resolveTranslationPair(
                globalMode = false, globalEnabled = true, globalSource = "en", globalTarget = "ru",
                map = map, bookEnabledMap = emptyMap(), pluginMap = pluginMap, pluginEnabledMap = mapOf("lua_demo" to true),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `pair - disabled novel does not leak its pair to global`() {
        val map = mapOf("book" to TranslationLangPair("fr", "de"))

        // Пер-новел ВЫКЛЮЧЕН: сохранённая пара не перебивает глобальную.
        assertEquals(
            TranslationLangPair("en", "ru"),
            resolveTranslationPair(
                globalMode = true, globalEnabled = true, globalSource = "en", globalTarget = "ru",
                map = map, bookEnabledMap = emptyMap(), pluginMap = emptyMap(), pluginEnabledMap = emptyMap(),
                bookUrl = "book", sourceId = null,
            )
        )
    }

    @Test
    fun `pair - no keys yields empty pair`() {
        assertEquals(
            TranslationLangPair(),
            resolveTranslationPair(
                globalMode = false, globalEnabled = true, globalSource = "en", globalTarget = "ru",
                map = emptyMap(), bookEnabledMap = emptyMap(), pluginMap = emptyMap(), pluginEnabledMap = emptyMap(),
                bookUrl = "book", sourceId = "lua_demo",
            )
        )
    }

    @Test
    fun `pair - sourceId null skips plugin tier (old behavior)`() {
        val pluginMap = mapOf("lua_demo" to TranslationLangPair("ja", "ko"))

        assertEquals(
            TranslationLangPair(),
            resolveTranslationPair(
                globalMode = false, globalEnabled = true, globalSource = "en", globalTarget = "ru",
                map = emptyMap(), bookEnabledMap = emptyMap(), pluginMap = pluginMap, pluginEnabledMap = emptyMap(),
                bookUrl = "book", sourceId = null,
            )
        )
    }

    // ─── Convenience methods: enabled + pair through the cascade ────────────

    @Test
    fun `happy - plugin pair and enabled resolve through cascade`() {
        prefs.setTranslationPairForPlugin("lua_demo", "ru", "en")
        prefs.setTranslationEnabledForPlugin("lua_demo", true)

        // Книга под этим источником, без book-ключа и без глобального режима.
        assertTrue(prefs.translationEnabledForBook("https://lua_demo/book/1", "lua_demo"))
        assertEquals(
            TranslationLangPair("ru", "en"),
            prefs.translationPairForBook("https://lua_demo/book/1", "lua_demo"),
        )
    }

    @Test
    fun `plugin - global mode still uses plugin pair when enabled`() {
        prefs.setTranslationPairForPlugin("lua_demo", "ru", "en")
        prefs.setTranslationEnabledForPlugin("lua_demo", true)
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_ENABLED.value = true
        prefs.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = "en"
        prefs.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = "ru"

        // Плагин выше глобала: в глобальном режиме включённый плагин действует сам
        // (enabled=true от плагина), а не глобальные настройки.
        assertTrue(prefs.translationEnabledForBook("https://lua_demo/book/1", "lua_demo"))
        assertEquals(
            TranslationLangPair("ru", "en"),
            prefs.translationPairForBook("https://lua_demo/book/1", "lua_demo"),
        )
    }

    @Test
    fun `plugin enabled + global on - scope comes from plugin`() {
        prefs.setTranslationEnabledForPlugin("lua_demo", true)
        prefs.setTranslationScopeForPlugin("lua_demo", "FULL")
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_ENABLED.value = true

        // Плагин выше глобала: в глобальном режиме включённый плагин отдаёт свой scope.
        assertEquals("FULL", prefs.translationScopeForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `plugin enabled + global on - provider comes from plugin`() {
        prefs.setTranslationEnabledForPlugin("lua_demo", true)
        prefs.setTranslationProviderForPlugin("lua_demo", "GEMINI")
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_ENABLED.value = true

        // Плагин выше глобала: в глобальном режиме включённый плагин отдаёт свой провайдер.
        assertEquals("GEMINI", prefs.translationProviderForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `failure - book key disabled does not cancel plugin enabled`() {
        prefs.setTranslationEnabledForPlugin("lua_demo", true)
        // Явный false в book-карте (тот же результат даёт setTranslationEnabledForBook(false))
        // НЕ отменяет включённый плагин — уровни независимы (OR), книга переводится плагином.
        prefs.TRANSLATION_BOOK_ENABLED_MAP.value = mapOf("https://lua_demo/book/1" to false)

        assertTrue(prefs.translationEnabledForBook("https://lua_demo/book/1", "lua_demo"))
    }

    // ─── Global OFF + plugin ON (фикс «включил плагин — не работает») ──────

    @Test
    fun `global off + plugin on - enabled true and plugin pair used`() {
        // Пользовательский сценарий: глобальный режим вкл., глобальный перевод ВЫКЛ.,
        // переводчик плагина ВКЛ. Плагин должен переводить свои книги.
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_ENABLED.value = false
        prefs.setTranslationEnabledForPlugin("lua_demo", true)
        prefs.setTranslationPairForPlugin("lua_demo", "ja", "ko")

        assertTrue(prefs.translationEnabledForBook("https://lua_demo/book/1", "lua_demo"))
        assertEquals(
            TranslationLangPair("ja", "ko"),
            prefs.translationPairForBook("https://lua_demo/book/1", "lua_demo"),
        )
    }

    @Test
    fun `global off + plugin on - scope and provider come from plugin`() {
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_ENABLED.value = false
        prefs.setTranslationEnabledForPlugin("lua_demo", true)
        prefs.setTranslationScopeForPlugin("lua_demo", "FULL")
        prefs.setTranslationProviderForPlugin("lua_demo", "GEMINI")

        assertEquals("FULL", prefs.translationScopeForBook("https://lua_demo/book/1", "lua_demo"))
        assertEquals("GEMINI", prefs.translationProviderForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `global off + plugin off - stays off`() {
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_ENABLED.value = false
        prefs.setTranslationEnabledForPlugin("lua_demo", false)

        assertFalse(prefs.translationEnabledForBook("https://lua_demo/book/1", "lua_demo"))
        assertEquals("STANDARD", prefs.translationScopeForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `sourceId null on convenience methods keeps old behavior`() {
        prefs.setTranslationPairForPlugin("lua_demo", "ru", "en")
        prefs.setTranslationEnabledForPlugin("lua_demo", true)

        // sourceId=null — плагин-уровень пропускается, поведение как раньше.
        assertFalse(prefs.translationEnabledForBook("https://lua_demo/book/1"))
        assertEquals(
            TranslationLangPair(),
            prefs.translationPairForBook("https://lua_demo/book/1"),
        )
    }

    // ─── Scope ──────────────────────────────────────────────────────────────

    @Test
    fun `scope - default STANDARD when no plugin scope`() {
        assertEquals("STANDARD", prefs.translationScopeForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `scope - plugin FULL when set`() {
        prefs.setTranslationScopeForPlugin("lua_demo", "FULL")
        // ponytail: плагин должен быть включён (rule 5), иначе scope/provider не пробрасываются в outside-global
        prefs.setTranslationEnabledForPlugin("lua_demo", true)

        assertEquals("FULL", prefs.translationScopeForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `scope - global mode drops plugin scope to STANDARD`() {
        prefs.setTranslationScopeForPlugin("lua_demo", "FULL")
        prefs.TRANSLATION_GLOBAL_MODE.value = true

        // Вариант A: глобальный режим игнорирует per-plugin scope → STANDARD.
        assertEquals("STANDARD", prefs.translationScopeForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `scope - sourceId null yields STANDARD`() {
        prefs.setTranslationScopeForPlugin("lua_demo", "FULL")

        assertEquals("STANDARD", prefs.translationScopeForBook("https://lua_demo/book/1"))
    }

    // ─── Provider ───────────────────────────────────────────────────────────

    @Test
    fun `provider - default null when no plugin provider`() {
        assertNull(prefs.translationProviderForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `provider - plugin provider when set`() {
        prefs.setTranslationProviderForPlugin("lua_demo", "GEMINI")
        // ponytail: плагин должен быть включён (rule 5), иначе scope/provider не пробрасываются в outside-global
        prefs.setTranslationEnabledForPlugin("lua_demo", true)

        assertEquals("GEMINI", prefs.translationProviderForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `provider - global mode drops plugin provider to null`() {
        prefs.setTranslationProviderForPlugin("lua_demo", "GEMINI")
        prefs.TRANSLATION_GLOBAL_MODE.value = true

        // Вариант A: глобальный режим игнорирует per-plugin provider → null (глобальный возьмётся).
        assertNull(prefs.translationProviderForBook("https://lua_demo/book/1", "lua_demo"))
    }

    @Test
    fun `provider - sourceId null yields null`() {
        prefs.setTranslationProviderForPlugin("lua_demo", "GEMINI")

        assertNull(prefs.translationProviderForBook("https://lua_demo/book/1"))
    }

    // ─── Plugin getters/setters semantics ───────────────────────────────────

    @Test
    fun `plugin enabled setter stores false on disable`() {
        prefs.setTranslationEnabledForPlugin("lua_demo", true)
        prefs.setTranslationEnabledForPlugin("lua_demo", false)

        assertFalse(prefs.translationEnabledForPlugin("lua_demo"))
        // Выключение хранится как явный ключ false — отличимо от «не настроен».
        assertEquals(false, prefs.TRANSLATION_PLUGIN_ENABLED_MAP.value["lua_demo"])
    }

    @Test
    fun `plugin pair setter removes key when both blank`() {
        prefs.setTranslationPairForPlugin("lua_demo", "ru", "en")
        prefs.setTranslationPairForPlugin("lua_demo", "", "")

        assertEquals(TranslationLangPair(), prefs.translationPairForPlugin("lua_demo"))
        assertTrue(prefs.TRANSLATION_PLUGIN_LANG_PAIR.value.isEmpty())
    }

    @Test
    fun `plugin pair setter keeps partial pair`() {
        prefs.setTranslationPairForPlugin("lua_demo", "ru", "")

        assertEquals(TranslationLangPair("ru", ""), prefs.translationPairForPlugin("lua_demo"))
    }

    @Test
    fun `plugin provider setter stores and clears`() {
        prefs.setTranslationProviderForPlugin("lua_demo", "GEMINI")
        assertEquals("GEMINI", prefs.translationProviderForPlugin("lua_demo"))

        prefs.setTranslationProviderForPlugin("lua_demo", "")
        assertNull(prefs.translationProviderForPlugin("lua_demo"))
    }

    @Test
    fun `plugin scope setter stores and defaults`() {
        prefs.setTranslationScopeForPlugin("lua_demo", "FULL")
        assertEquals("FULL", prefs.translationScopeForPlugin("lua_demo"))

        prefs.setTranslationScopeForPlugin("lua_demo", "STANDARD")
        assertEquals("STANDARD", prefs.translationScopeForPlugin("lua_demo"))
    }

    @Test
    fun `plugin prompt setter stores and clears`() {
        prefs.setTranslationPromptForPlugin("lua_demo", "Переведи в стиле классики")
        assertEquals("Переведи в стиле классики", prefs.translationPromptForPlugin("lua_demo"))

        prefs.setTranslationPromptForPlugin("lua_demo", "")
        assertNull(prefs.translationPromptForPlugin("lua_demo"))
    }

    // ─── FIX-A: per-novel setter must NOT touch global prefs ──────────────

    @Test
    fun `setter pair in global mode writes per-novel not global`() {
        // Режим глобальный, глобальная пара установлена.
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = "en"
        prefs.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = "ru"

        // Пер-новел включён: пара читается из каскада только при включённом уровне
        // (карты enable и pair независимы; сеттер enabled адаптивен — в глобальном
        // режиме он писал бы в глобальный флаг, поэтому enable ставим в карту явно).
        prefs.TRANSLATION_BOOK_ENABLED_MAP.value = mapOf("https://example.com/book/1" to true)

        // Запись per-novel пары: bookUrl="https://example.com/book/1", ko→en.
        prefs.setTranslationPairForBook("https://example.com/book/1", "ko", "en")

        // (a) Пер-новел пара читается корректно (без передачи sourceId — каскад пропускает плагин).
        assertEquals(
            TranslationLangPair("ko", "en"),
            prefs.translationPairForBook("https://example.com/book/1"),
        )
        // (b) Глобальные префы НЕ мутированы.
        assertEquals("en", prefs.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value)
        assertEquals("ru", prefs.GLOBAL_TRANSLATION_PREFERRED_TARGET.value)
    }

    @Test
    fun `setter enabled in global mode writes global not per-novel`() {
        prefs.TRANSLATION_GLOBAL_MODE.value = true
        prefs.GLOBAL_TRANSLATION_ENABLED.value = false

        // В глобальном режиме сеттер адаптивен: пишет в глобальный включённый флаг.
        prefs.setTranslationEnabledForBook("https://example.com/book/1", true)

        // Глобальный enabled включён, book-карта НЕ тронута.
        assertTrue(prefs.GLOBAL_TRANSLATION_ENABLED.value)
        assertFalse(prefs.TRANSLATION_BOOK_ENABLED_MAP.value.containsKey("https://example.com/book/1"))

        // Выключение в глобальном режиме также пишет в глобальный флаг.
        prefs.setTranslationEnabledForBook("https://example.com/book/1", false)
        assertFalse(prefs.GLOBAL_TRANSLATION_ENABLED.value)
        assertFalse(prefs.TRANSLATION_BOOK_ENABLED_MAP.value.containsKey("https://example.com/book/1"))
    }
}
