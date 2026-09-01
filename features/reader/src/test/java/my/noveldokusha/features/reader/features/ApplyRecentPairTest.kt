package my.noveldokusha.features.reader.features

import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.NovelPromptData
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
        ): TranslatorState = TranslatorState(source = source, target = target) { it }

        override fun downloadModel(language: String) {}
        override fun removeModel(language: String) {}

        override suspend fun translateBatch(
            texts: List<String>,
            sourceLanguage: String,
            targetLanguage: String,
            systemPromptOverride: String?,
        ): Map<String, String> = emptyMap()
    }

    private val models = listOf(
        TranslationModelState(language = "en", available = true, downloading = false, downloadingFailed = false),
        TranslationModelState(language = "ru", available = true, downloading = false, downloadingFailed = false),
    )

    private lateinit var prefs: AppPreferences

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
        prefs = mock()
        whenever(prefs.TRANSLATION_NOVEL_PROMPTS).thenReturn(novelPrompts)
        whenever(prefs.TRANSLATION_PROVIDER).thenReturn(provider)
        whenever(prefs.TRANSLATION_PARALLEL_ENABLED).thenReturn(parallelEnabled)
        whenever(prefs.TRANSLATION_PARALLEL_ORDER).thenReturn(parallelOrder)
        whenever(prefs.TRANSLATION_GLOBAL_MODE).thenReturn(globalMode)
        whenever(prefs.translationEnabledForBook("book1")).thenReturn(false)
        whenever(prefs.favoriteLanguages()).thenReturn(emptyList())
        whenever(prefs.recentTranslationPairs()).thenReturn(emptyList())
    }

    private fun <T> pref(value: T): AppPreferences.Preference<T> =
        mock<AppPreferences.Preference<T>>().also { whenever(it.value).thenReturn(value) }
    @Test
    fun `apply recent pair sets source and target in prefs and enables translation`() = runTest {
        val reader = ReaderLiveTranslation(
            translationManager = FakeTranslationManager(models),
            appPreferences = prefs,
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
}
