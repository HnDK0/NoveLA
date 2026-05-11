package my.noveldokusha.text_translator

import androidx.compose.runtime.mutableStateListOf
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState

/**
 * No-op [TranslationManager] used when no translation backend is available
 * (e.g. flavor builds that exclude all translation modules).
 *
 * Every operation is a safe, empty default — no exceptions are thrown.
 */
class TranslationManagerEmpty : TranslationManager {

    override val available = false
    override val models = mutableStateListOf<TranslationModelState>()

    override suspend fun hasModelDownloaded(language: String): TranslationModelState? = null

    override fun getTranslator(source: String, target: String) = TranslatorState(
        source = source,
        target = target,
        translate = { "" },
    )

    override fun downloadModel(language: String) = Unit
    override fun removeModel(language: String) = Unit

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<String, String> = emptyMap()
}
