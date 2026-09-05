package my.noveldokusha.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TranslationSettingsResolverImpl: книга → загруженный источник (sourceId) → каскад
 * AppPreferences (per-book > per-plugin > global).
 *
 * Идентичность источника берётся из `scraper.getCompatibleSource(url)?.id` (только
 * ЗАГРУЖЕННЫЕ источники). Когда источник не загружен (getCompatibleSource == null) —
 * поведение ровно как у старых методов AppPreferences без sourceId (plugin-уровень
 * пропускается).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranslationSettingsResolverTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val appPreferences = AppPreferences(context)
    private val scraper = mock<Scraper>()
    private lateinit var resolver: TranslationSettingsResolverImpl

    private val luaBookUrl = "https://lua_x.example/book/1"
    private val localBookUrl = "https://local.domain/book/1"

    @Before
    fun setUp() {
        resolver = TranslationSettingsResolverImpl(appPreferences, scraper)
    }

    // ── Happy: загруженный источник "lua_x" + plugin пара (zh→ru) ──────────────
    @Test
    fun `book under loaded source uses plugin pair and enabled`() {
        val source = mock<SourceInterface.Catalog>()
        whenever(source.id).thenReturn("lua_x")
        whenever(scraper.getCompatibleSource(luaBookUrl)).thenReturn(source)

        appPreferences.setTranslationPairForPlugin("lua_x", "zh", "ru")
        appPreferences.setTranslationEnabledForPlugin("lua_x", true)

        assertEquals(
            TranslationLangPair("zh", "ru"),
            resolver.translationPairForBook(luaBookUrl)
        )
        assertEquals("ru", resolver.translationTargetForBook(luaBookUrl))
        assertTrue(resolver.translationEnabledForBook(luaBookUrl))
    }

    // ── Failure: URL не совпадает ни с одним загруженным источником → null sourceId,
    //    plugin-уровень пропускается → пустая пара / отключено (как старый путь) ──
    @Test
    fun `book with non-loaded source falls back to empty global path`() {
        val source = mock<SourceInterface.Catalog>()
        whenever(source.id).thenReturn("lua_x")
        // Этот URL не совпадает — getCompatibleSource возвращает null (источник не загружен)
        whenever(scraper.getCompatibleSource(luaBookUrl)).thenReturn(null)

        appPreferences.setTranslationPairForPlugin("lua_x", "zh", "ru")
        appPreferences.setTranslationEnabledForPlugin("lua_x", true)

        assertEquals(TranslationLangPair(), resolver.translationPairForBook(luaBookUrl))
        assertFalse(resolver.translationEnabledForBook(luaBookUrl))
        assertNull(resolver.translationProviderForBook(luaBookUrl))
    }

    // ── Failure: локальная книга (getCompatibleSource == null) → поведение идентично
    //    старым методам AppPreferences без sourceId (per-book/global) ────────────
    @Test
    fun `getCompatibleSource null behaves exactly like old book global path`() {
        whenever(scraper.getCompatibleSource(anyUrl())).thenReturn(null)

        // per-book пара включена — resolver должен вернуть её как старый метод
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "fr")
        appPreferences.setTranslationEnabledForBook(localBookUrl, true)

        assertEquals(
            appPreferences.translationPairForBook(localBookUrl),
            resolver.translationPairForBook(localBookUrl)
        )
        assertEquals(
            appPreferences.translationEnabledForBook(localBookUrl),
            resolver.translationEnabledForBook(localBookUrl)
        )
        assertEquals(
            appPreferences.translationScopeForBook(localBookUrl),
            resolver.translationScopeForBook(localBookUrl)
        )
        assertEquals(
            appPreferences.translationProviderForBook(localBookUrl),
            resolver.translationProviderForBook(localBookUrl)
        )
        assertEquals("fr", resolver.translationTargetForBook(localBookUrl))
    }

    // ── ActiveTranslatorLevel: уровень = ВКЛЮЧЁННЫЙ переводчик с полной парой, ──
    //    не наличие сохранённой пары (карты enable и pair независимы). ──────────
    @Test
    fun `level - everything off gives NONE even with saved pair`() {
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "fr")

        assertEquals(
            TranslationSettingsResolver.ActiveTranslatorLevel.NONE,
            resolver.activeTranslatorLevelForBook(localBookUrl)
        )
    }

    @Test
    fun `level - enabled per-novel pair wins over global`() {
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "fr")
        appPreferences.setTranslationEnabledForBook(localBookUrl, true)
        appPreferences.TRANSLATION_GLOBAL_MODE.value = true
        appPreferences.GLOBAL_TRANSLATION_ENABLED.value = true
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = "zh"
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = "ru"

        assertEquals(
            TranslationSettingsResolver.ActiveTranslatorLevel.PER_NOVEL,
            resolver.activeTranslatorLevelForBook(localBookUrl)
        )
    }

    @Test
    fun `level - disabled per-novel pair does not mask global`() {
        // Жалоба: «на глобал не реагирует, тоже пишет This novel» — пер-новел выключен,
        // пара сохранена, включён глобал → должен показываться GLOBAL.
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "fr")
        appPreferences.TRANSLATION_GLOBAL_MODE.value = true
        appPreferences.GLOBAL_TRANSLATION_ENABLED.value = true
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = "zh"
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = "ru"

        assertEquals(
            TranslationSettingsResolver.ActiveTranslatorLevel.GLOBAL,
            resolver.activeTranslatorLevelForBook(localBookUrl)
        )
    }

    @Test
    fun `level - enabled plugin shown when per-novel off`() {
        // Жалоба: «всё выключено, кроме перплагин» → полоска должна показывать плагин,
        // а не сохранённую пер-новел пару.
        val source = mock<SourceInterface.Catalog>()
        whenever(source.id).thenReturn("lua_x")
        whenever(scraper.getCompatibleSource(luaBookUrl)).thenReturn(source)

        appPreferences.setTranslationPairForBook(luaBookUrl, "en", "fr")
        appPreferences.setTranslationPairForPlugin("lua_x", "zh", "ru")
        appPreferences.setTranslationEnabledForPlugin("lua_x", true)

        assertEquals(
            TranslationSettingsResolver.ActiveTranslatorLevel.PLUGIN,
            resolver.activeTranslatorLevelForBook(luaBookUrl)
        )
    }

    @Test
    fun `level - enabled per-novel pair beats enabled plugin`() {
        val source = mock<SourceInterface.Catalog>()
        whenever(source.id).thenReturn("lua_x")
        whenever(scraper.getCompatibleSource(luaBookUrl)).thenReturn(source)

        appPreferences.setTranslationPairForBook(luaBookUrl, "en", "fr")
        appPreferences.setTranslationEnabledForBook(luaBookUrl, true)
        appPreferences.setTranslationPairForPlugin("lua_x", "zh", "ru")
        appPreferences.setTranslationEnabledForPlugin("lua_x", true)

        assertEquals(
            TranslationSettingsResolver.ActiveTranslatorLevel.PER_NOVEL,
            resolver.activeTranslatorLevelForBook(luaBookUrl)
        )
    }

    // ── hasStoredTranslationPairForBook: проверка наличия хранимой пары БЕЗ гейта enable ──

    @Test
    fun `hasStoredPair - per-novel mode, pair stored returns true`() {
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "fr")
        assertTrue(appPreferences.hasStoredTranslationPairForBook(localBookUrl))
    }

    @Test
    fun `hasStoredPair - per-novel mode, no pair returns false`() {
        assertFalse(appPreferences.hasStoredTranslationPairForBook(localBookUrl))
    }

    @Test
    fun `hasStoredPair - per-novel mode, partial pair returns false`() {
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "")
        assertFalse(appPreferences.hasStoredTranslationPairForBook(localBookUrl))
    }

    @Test
    fun `hasStoredPair - global mode, global pair stored returns true`() {
        appPreferences.TRANSLATION_GLOBAL_MODE.value = true
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = "en"
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = "ru"
        assertTrue(appPreferences.hasStoredTranslationPairForBook(localBookUrl))
    }

    @Test
    fun `hasStoredPair - global mode, no global pair returns false`() {
        appPreferences.TRANSLATION_GLOBAL_MODE.value = true
        // Дефолтный target пустой — hasStoredPair должен вернуть false
        assertFalse(appPreferences.hasStoredTranslationPairForBook(localBookUrl))
    }

    // ── storedTranslationPairForBook: хранимая пара ДЛЯ ОТОБРАЖЕНИЯ, без гейта enable ──
    //    (карты enable и pair независимы — выключение уровня не скрывает пару в UI).

    @Test
    fun `storedPair - per-novel mode, stored pair returned even when disabled`() {
        // Пара сохранена, но включение НЕ ставили — отображение всё равно отдаёт пару.
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "fr")
        assertEquals(
            TranslationLangPair("en", "fr"),
            appPreferences.storedTranslationPairForBook(localBookUrl)
        )
    }

    @Test
    fun `storedPair - per-novel mode, no pair returns empty`() {
        assertEquals(
            TranslationLangPair(),
            appPreferences.storedTranslationPairForBook(localBookUrl)
        )
    }

    @Test
    fun `storedPair - per-novel mode, partial pair returned as-is`() {
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "")
        assertEquals(
            TranslationLangPair("en", ""),
            appPreferences.storedTranslationPairForBook(localBookUrl)
        )
    }

    @Test
    fun `storedPair - global mode, global pair returned even when global disabled`() {
        appPreferences.TRANSLATION_GLOBAL_MODE.value = true
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = "en"
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = "ru"
        assertEquals(
            TranslationLangPair("en", "ru"),
            appPreferences.storedTranslationPairForBook(localBookUrl)
        )
    }

    @Test
    fun `storedPair - global mode, no global pair returns empty`() {
        appPreferences.TRANSLATION_GLOBAL_MODE.value = true
        // Дефолтный source непустой ("en") — зануляем явно, чтобы хранимой пары не было.
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = ""
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = ""
        // Отображение не должно изобретать пару.
        assertEquals(
            TranslationLangPair(),
            appPreferences.storedTranslationPairForBook(localBookUrl)
        )
    }

    @Test
    fun `storedPair - global mode ignores per-novel pair`() {
        appPreferences.setTranslationPairForBook(localBookUrl, "en", "fr")
        appPreferences.TRANSLATION_GLOBAL_MODE.value = true
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = ""
        appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = ""
        assertEquals(
            TranslationLangPair(),
            appPreferences.storedTranslationPairForBook(localBookUrl)
        )
    }

    // Сброс prefs между тестами — избегаем залипания состояний в SharedPreferences.
    @org.junit.After
    fun tearDown() {
        appPreferences.context.getSharedPreferences("default", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun anyUrl(): String = "https://any.example/book"
}
