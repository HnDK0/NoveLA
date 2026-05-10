package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState

/**
 * Routes translation to the active backend selected by [AppPreferences.TRANSLATION_PROVIDER]:
 *   GOOGLE_PA   — translate-pa.googleapis.com (HTML chunks, highest quality, default)
 *   GOOGLE_FREE — translate.googleapis.com/translate_a/single (plain text)
 *   GEMINI      — Google Gemini API (requires API key)
 *   OPENAI      — Any OpenAI-compatible API
 *
 * No silent fallback — errors propagate so the user sees what went wrong.
 */
class TranslationManagerComposite(
    private val coroutineScope: AppCoroutineScope,
    private val geminiManager: TranslationManagerGemini,
    private val googleFreeManager: TranslationManagerGoogleFree,
    private val googlePAManager: TranslationManagerGooglePA,
    private val openAiManager: TranslationManagerOpenAI,
    private val appPreferences: AppPreferences
) : TranslationManager {

    override val available = true
    override val isUsingOnlineTranslation = true

    override val models = mutableStateListOf<TranslationModelState>().also { list ->
        val langs = buildSet {
            addAll(geminiManager.models.map { it.language })
            addAll(googleFreeManager.models.map { it.language })
            addAll(googlePAManager.models.map { it.language })
            addAll(openAiManager.models.map { it.language })
        }
        list.addAll(langs.map { TranslationModelState(it, available = true, downloading = false, downloadingFailed = false) })
    }

    override suspend fun hasModelDownloaded(language: String) = models.firstOrNull { it.language == language }

    private fun provider(): String = appPreferences.TRANSLATION_PROVIDER.value

    fun getActiveTranslatorName() = when (provider()) {
        "GEMINI"      -> "Google Gemini API"
        "GOOGLE_FREE" -> "Google Translate (Free)"
        "OPENAI"      -> "OpenAI-compatible API"
        else          -> "Google Translate (Enhanced)"
    }

    override fun getTranslator(source: String, target: String): TranslatorState {
        Log.d(TAG, "getTranslator: source=$source target=$target provider=${provider()}")
        return when {
            provider() == "OPENAI"      -> openAiManager.getTranslator(source, target)
            provider() == "GEMINI"      -> geminiWithRetry(source, target)
            provider() == "GOOGLE_FREE" -> googleFreeManager.getTranslator(source, target)
            source == "auto"            -> googleFreeManager.getTranslator(source, target)
            else                        -> googlePAManager.getTranslator(source, target)
        }
    }

    /** Gemini translator with 2 retries, no silent fallback. */
    private fun geminiWithRetry(source: String, target: String): TranslatorState {
        val delegate = geminiManager.getTranslator(source, target)
        return TranslatorState(source, target) { input ->
            var lastEx: Exception? = null
            repeat(2) { attempt ->
                try {
                    return@TranslatorState delegate.translate(input)
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini attempt ${attempt + 1}/2 failed: ${e.message}")
                    lastEx = e
                    if (attempt == 0) kotlinx.coroutines.delay(1000L)
                }
            }
            throw lastEx ?: IllegalStateException("Gemini: Translation failed.")
        }
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        val source = if (sourceLanguage == "auto") {
            val sample = texts.firstOrNull { it.isNotBlank() }?.take(200) ?: ""
            googleFreeManager.detectLanguage(sample).also { Log.d(TAG, "detected language=$it") }
                ?: sourceLanguage
        } else sourceLanguage

        when (provider()) {
            "OPENAI"      -> openAiManager.translateBatch(texts, source, targetLanguage)
            "GEMINI"      -> geminiManager.translateBatch(texts, source, targetLanguage)
            "GOOGLE_FREE" -> googleFreeManager.translateBatch(texts, source, targetLanguage)
            else          -> googlePAManager.translateBatch(texts, source, targetLanguage)
        }
    }

    override suspend fun detectLanguage(text: String) = googleFreeManager.detectLanguage(text)

    override fun downloadModel(language: String) {}
    override fun removeModel(language: String) {}

    companion object { private const val TAG = "TranslationComposite" }
}
