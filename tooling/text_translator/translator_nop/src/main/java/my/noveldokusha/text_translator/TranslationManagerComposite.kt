package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState

/**
 * Routes translation requests to the backend selected by [AppPreferences.TRANSLATION_PROVIDER]:
 *
 *  GOOGLE_PA   — translate-pa.googleapis.com  (HTML-wrapped paragraphs, best quality, default)
 *  GOOGLE_FREE — translate.googleapis.com     (plain text, no key required)
 *  GEMINI      — Google Gemini generative API (requires API key)
 *  OPENAI      — Any OpenAI-compatible API    (requires API key + base URL)
 *
 * Errors are not silently swallowed — they propagate to the UI so the user can see what failed.
 */
class TranslationManagerComposite(
    private val coroutineScope: AppCoroutineScope,
    private val geminiManager: TranslationManagerGemini,
    private val googleFreeManager: TranslationManagerGoogleFree,
    private val googlePAManager: TranslationManagerGooglePA,
    private val openAiManager: TranslationManagerOpenAI,
    private val appPreferences: AppPreferences,
) : TranslationManager {

    override val available = true
    override val isUsingOnlineTranslation = true

    /** Union of all languages supported by any sub-manager. */
    override val models = mutableStateListOf<TranslationModelState>().also { list ->
        val languages = buildSet {
            addAll(geminiManager.models.map { it.language })
            addAll(googleFreeManager.models.map { it.language })
            addAll(googlePAManager.models.map { it.language })
            addAll(openAiManager.models.map { it.language })
        }
        list.addAll(languages.map {
            TranslationModelState(it, available = true, downloading = false, downloadingFailed = false)
        })
    }

    override suspend fun hasModelDownloaded(language: String) =
        models.firstOrNull { it.language == language }

    private fun provider(): String = appPreferences.TRANSLATION_PROVIDER.value

    /** Human-readable name of the currently active backend (for UI display). */
    fun getActiveTranslatorName() = when (provider()) {
        "GEMINI"      -> "Google Gemini API"
        "GOOGLE_FREE" -> "Google Translate (Free)"
        "OPENAI"      -> "OpenAI-compatible API"
        else          -> "Google Translate (Enhanced)"
    }

    override fun getTranslator(source: String, target: String): TranslatorState {
        Log.d(TAG, "getTranslator: source=$source target=$target provider=${provider()}")
        return when (provider()) {
            "OPENAI"      -> openAiManager.getTranslator(source, target)
            "GEMINI"      -> geminiWithRetry(source, target)
            "GOOGLE_FREE" -> googleFreeManager.getTranslator(source, target)
            // Google PA does not support "auto" source detection; fall back to Free.
            else -> if (source == "auto") googleFreeManager.getTranslator(source, target)
                    else googlePAManager.getTranslator(source, target)
        }
    }

    /**
     * Wraps the Gemini translator with a single retry and a 1-second back-off.
     * Errors are not swallowed — the second failure propagates to the caller.
     */
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
                    if (attempt == 0) delay(1_000L)
                }
            }
            throw lastEx ?: IllegalStateException("Gemini: Translation failed.")
        }
    }

    /**
     * Translates a batch of paragraphs using the active backend.
     *
     * When the source language is "auto", Google Free is used to detect the language
     * from the first non-blank paragraph before delegating to the selected provider.
     */
    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        val resolvedSource = if (sourceLanguage == "auto") {
            val sample = texts.firstOrNull { it.isNotBlank() }?.take(200).orEmpty()
            googleFreeManager.detectLanguage(sample)
                .also { Log.d(TAG, "auto-detected language: $it") }
                ?: sourceLanguage
        } else {
            sourceLanguage
        }

        when (provider()) {
            "OPENAI"      -> openAiManager.translateBatch(texts, resolvedSource, targetLanguage)
            "GEMINI"      -> geminiManager.translateBatch(texts, resolvedSource, targetLanguage)
            "GOOGLE_FREE" -> googleFreeManager.translateBatch(texts, resolvedSource, targetLanguage)
            else          -> googlePAManager.translateBatch(texts, resolvedSource, targetLanguage)
        }
    }

    override suspend fun detectLanguage(text: String) = googleFreeManager.detectLanguage(text)

    override fun downloadModel(language: String) = Unit
    override fun removeModel(language: String) = Unit

    companion object {
        private const val TAG = "TranslationComposite"
    }
}
