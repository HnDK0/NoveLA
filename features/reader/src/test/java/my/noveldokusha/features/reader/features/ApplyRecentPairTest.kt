package my.noveldokusha.features.reader.features

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.NovelPromptData
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Тесты обработчика onApplyRecentPair: применение недавней пары должно
 * выставить source+target в префах, включить перевод и записать пару в список последних.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplyRecentPairTest {

    // Фейковый менеджер: модели «en» и «ru» считаются скачанными.
    private class FakeTranslationManager(
        private val modelStates: List<TranslationModelState>,
    ) : TranslationManager {
        override val available: Boolean = true
        override val models: SnapshotStateList<TranslationModelState> =
            SnapshotStateList<TranslationModelState>().apply { addAll(modelStates) }

        override suspend fun hasModelDownloaded(language: String): TranslationModelState? =
            modelStates.firstOrNull { it.language == language }

        override fun getTranslator(
            source: String,
            target: String,
            systemPromptOverride: String?,
            provider: String?,
        ): TranslatorState = TranslatorState(source = source, target = target) { it }

        override suspend fun translateBatch(
            texts: List<String>,
            sourceLanguage: String,
            targetLanguage: String,
            systemPromptOverride: String?,
            provider: String?,
        ): Map<String, String> = emptyMap()
    }

    private val models = listOf(
        TranslationModelState(language = "en", available = true, downloading = false, downloadingFailed = false),
        TranslationModelState(language = "ru", available = true, downloading = false, downloadingFailed = false),
    )

    private lateinit var prefs: AppPreferences
    private lateinit var resolver: TranslationSettingsResolver

    @Before
    fun setUp() {
        // Мок-стиббинг преф-свойств через whenever (как в MangaReaderViewModelTest):
        // геттер свойства на моке по умолчанию возвращает null, поэтому стабим сам Preference.
        // ВАЖНО: pref(...) создаёт мок ДО whenever(...), иначе вложенный whenever внутри
        // аргумента thenReturn оставляет предыдущую стабировку незавершённой (UnfinishedStubbing).
        val novelPrompts = pref(emptyMap<String, NovelPromptData>())
        val provider = pref("")
        val parallelEnabled = pref(false)
        val parallelOrder = pref("")
        val globalMode = pref(false)
        val bookEnabledMap = pref(emptyMap<String, Boolean>())
        val globalEnabled = pref(false)
        prefs = mock()
        whenever(prefs.TRANSLATION_NOVEL_PROMPTS).thenReturn(novelPrompts)
        whenever(prefs.TRANSLATION_PROVIDER).thenReturn(provider)
        whenever(prefs.TRANSLATION_PARALLEL_ENABLED).thenReturn(parallelEnabled)
        whenever(prefs.TRANSLATION_PARALLEL_ORDER).thenReturn(parallelOrder)
        whenever(prefs.TRANSLATION_GLOBAL_MODE).thenReturn(globalMode)
        whenever(prefs.TRANSLATION_BOOK_ENABLED_MAP).thenReturn(bookEnabledMap)
        whenever(prefs.GLOBAL_TRANSLATION_ENABLED).thenReturn(globalEnabled)
        whenever(prefs.translationEnabledForBook("book1")).thenReturn(false)
        whenever(prefs.favoriteLanguages()).thenReturn(emptyList())
        whenever(prefs.recentTranslationPairs()).thenReturn(emptyList())
        resolver = mock()
        whenever(resolver.translationEnabledForBook("book1")).thenReturn(false)
    }

    private fun <T> pref(value: T): AppPreferences.Preference<T> =
        mock<AppPreferences.Preference<T>>().also { whenever(it.value).thenReturn(value) }
    @Test
    fun `apply recent pair sets source and target in prefs and enables translation`() = runTest {
        val reader = ReaderLiveTranslation(
            translationManager = FakeTranslationManager(models),
            appPreferences = prefs,
            translationSettingsResolver = resolver,
            bookUrl = "book1",
            scope = this,
        )

        // Подписка на сигнал изменения, чтобы emit не блокировался без получателя.
        val collector = launch { reader.onTranslatorChanged.collect {} }

        reader.state.onApplyRecentPair("en", "ru")

        // Синхронная часть: пара и включение записываются сразу.
        verify(prefs).setTranslationPairForBook("book1", "en", "ru")
        verify(prefs).setTranslationEnabledForBook("book1", true)
        assertTrue(reader.state.enable.value)

        // Дожидаемся корутины: пара перезаписывается в список последних.
        advanceUntilIdle()
        verify(prefs).recordRecentTranslationPair("en", "ru")

        collector.cancel()
    }

    @Test
    fun `apply recent pair refreshes source and target state`() = runTest {
        val reader = ReaderLiveTranslation(
            translationManager = FakeTranslationManager(models),
            appPreferences = prefs,
            translationSettingsResolver = resolver,
            bookUrl = "book1",
            scope = this,
        )
        val collector = launch { reader.onTranslatorChanged.collect {} }

        reader.state.onApplyRecentPair("en", "ru")
        advanceUntilIdle()

        assertEquals("en", reader.state.source.value?.language)
        assertEquals("ru", reader.state.target.value?.language)

        collector.cancel()
    }

    // ── FIX-C: Guard 1 — onEnable блокирует включение без языковой пары ──

    @Test
    fun `onEnable blocks when no language pair is set for book`() = runTest {
        // Резолвер отдаёт пустую пару — guard должен заблокировать включение.
        whenever(resolver.translationPairForBook("book1"))
            .thenReturn(TranslationLangPair(source = "", target = ""))

        val reader = ReaderLiveTranslation(
            translationManager = FakeTranslationManager(models),
            appPreferences = prefs,
            translationSettingsResolver = resolver,
            bookUrl = "book1",
            scope = this,
        )

        reader.state.onEnable(true)

        // enable остаётся false — guard отклонил включение.
        assertEquals(false, reader.state.enable.value)
    }

    @Test
    fun `onEnable allows disabling even without language pair`() = runTest {
        whenever(resolver.translationPairForBook("book1"))
            .thenReturn(TranslationLangPair(source = "", target = ""))

        val bookEnabledMap = pref(mapOf("book1" to true))
        whenever(prefs.TRANSLATION_BOOK_ENABLED_MAP).thenReturn(bookEnabledMap)

        val reader = ReaderLiveTranslation(
            translationManager = FakeTranslationManager(models),
            appPreferences = prefs,
            translationSettingsResolver = resolver,
            bookUrl = "book1",
            scope = this,
        )
        // Принудительно ставим enabled=true, чтобы проверить что disable() проходит.
        reader.state.enable.value = true

        reader.state.onEnable(false)

        // Выключение всегда разрешено — guard не блокирует enabled=false.
        assertEquals(false, reader.state.enable.value)
    }

    // ── FIX-C: Guard 2 — onTranslationGlobalModeChange блокирует без глобальной пары ──

    @Test
    fun `onTranslationGlobalModeChange blocks when no global pair is set`() = runTest {
        val emptyGlobalSource = pref("")
        val emptyGlobalTarget = pref("")
        whenever(prefs.GLOBAL_TRANSLATION_PREFERRED_SOURCE).thenReturn(emptyGlobalSource)
        whenever(prefs.GLOBAL_TRANSLATION_PREFERRED_TARGET).thenReturn(emptyGlobalTarget)

        val reader = ReaderLiveTranslation(
            translationManager = FakeTranslationManager(models),
            appPreferences = prefs,
            translationSettingsResolver = resolver,
            bookUrl = "book1",
            scope = this,
        )

        reader.state.onTranslationGlobalModeChange(true)

        // translationGlobalMode остаётся false — guard отклонил включение глобала.
        assertEquals(false, reader.state.translationGlobalMode.value)
    }

    @Test
    fun `onTranslationGlobalModeChange allows disabling even without global pair`() = runTest {
        val emptyGlobalSource = pref("")
        val emptyGlobalTarget = pref("")
        whenever(prefs.GLOBAL_TRANSLATION_PREFERRED_SOURCE).thenReturn(emptyGlobalSource)
        whenever(prefs.GLOBAL_TRANSLATION_PREFERRED_TARGET).thenReturn(emptyGlobalTarget)
        whenever(resolver.translationPairForBook("book1"))
            .thenReturn(TranslationLangPair(source = "", target = ""))

        val reader = ReaderLiveTranslation(
            translationManager = FakeTranslationManager(models),
            appPreferences = prefs,
            translationSettingsResolver = resolver,
            bookUrl = "book1",
            scope = this,
        )
        // Принудительно ставим globalMode=true.
        reader.state.translationGlobalMode.value = true

        reader.state.onTranslationGlobalModeChange(false)

        // Выключение глобального режима всегда разрешено.
        assertEquals(false, reader.state.translationGlobalMode.value)
    }
}
