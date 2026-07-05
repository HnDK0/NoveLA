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
 * Composite translation manager.
 * Active backend is determined by appPreferences.TRANSLATION_PROVIDER:
 *   "GOOGLE_PA"   — translate-pa.googleapis.com (HTML chunks, best quality, default)
 *   "GOOGLE_FREE" — translate.googleapis.com/translate_a/single (plain text)
 *   "GEMINI"      — Google Gemini API (requires user API key; throws if key absent)
 *   "OPENAI"      — Any OpenAI-compatible API (OpenAI, OpenRouter, Mistral, DeepSeek, etc.)
 *
 * No silent fallback for any provider — errors are thrown with descriptive messages
 * so the user always knows what went wrong.
 *
 * Exception: translateTitle() always uses Google PA → Free fallback,
 * regardless of the active provider, to avoid spending Gemini/OpenAI tokens on titles.
 */
class TranslationManagerComposite(
    private val coroutineScope: AppCoroutineScope,
    private val geminiManager: TranslationManagerGemini,
    private val googleFreeManager: TranslationManagerGoogleFree,
    private val googlePAManager: TranslationManagerGooglePA,
    private val openAiManager: TranslationManagerOpenAI,
    private val appPreferences: AppPreferences
) : TranslationManager {

    override val available: Boolean = true
    override val isUsingOnlineTranslation: Boolean = true

    override val models = mutableStateListOf<TranslationModelState>()

    init {
        val allLanguages = mutableSetOf<String>()
        allLanguages.addAll(geminiManager.models.map { it.language })
        allLanguages.addAll(googleFreeManager.models.map { it.language })
        allLanguages.addAll(googlePAManager.models.map { it.language })
        allLanguages.addAll(openAiManager.models.map { it.language })
        models.addAll(allLanguages.map { lang ->
            TranslationModelState(
                language = lang,
                available = true,
                downloading = false,
                downloadingFailed = false
            )
        })
    }

    override suspend fun hasModelDownloaded(language: String): TranslationModelState? =
        models.firstOrNull { it.language == language }

    private fun activeProvider(): String = appPreferences.TRANSLATION_PROVIDER.value

    fun getActiveTranslatorName(): String = when (activeProvider()) {
        "GEMINI"      -> "Google Gemini API"
        "GOOGLE_FREE" -> "Google Translate (Free)"
        "OPENAI"      -> "OpenAI-compatible API"
        else          -> "Google Translate (Enhanced)"
    }

    override fun getTranslator(source: String, target: String, systemPromptOverride: String?): TranslatorState {
        val provider = activeProvider()
        Log.d(TAG, "getTranslator: source=$source, target=$target, provider=$provider, override=${systemPromptOverride != null}")
        return when {
            provider == "OPENAI"      -> openAiManager.getTranslator(source, target, systemPromptOverride)
            provider == "GEMINI"      -> buildGeminiTranslator(source, target, systemPromptOverride)
            provider == "GOOGLE_FREE" -> googleFreeManager.getTranslator(source, target)
            else                      -> googlePAManager.getTranslator(source, target)
        }
    }

    /**
     * Gemini translator with retry — no fallback to other providers.
     * If Gemini fails (no key, rate limit, API error), the exception propagates
     * to ReaderChaptersLoader which shows it as a ReaderItem.Error.
     */
    private fun buildGeminiTranslator(source: String, target: String, systemPromptOverride: String? = null): TranslatorState {
        val geminiTranslator = geminiManager.getTranslator(source, target, systemPromptOverride)
        return TranslatorState(
            source = source,
            target = target,
            translate = { input -> geminiTranslator.translate(input) }
        )
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        systemPromptOverride: String?,
    ): Map<String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "translateBatch: $sourceLanguage → $targetLanguage, override='${systemPromptOverride?.take(200)}', texts=${texts.size}")
        if (texts.isEmpty()) return@withContext emptyMap()

        val resolvedSource = if (sourceLanguage == "auto") {
            val sample = texts.firstOrNull { it.isNotBlank() }?.take(200) ?: ""
            val detected = googleFreeManager.detectLanguage(sample)
            Log.d(TAG, "translateBatch: detected language=$detected")
            detected ?: sourceLanguage
        } else {
            sourceLanguage
        }

        when (activeProvider()) {
            "OPENAI" -> {
                Log.d(TAG, "translateBatch: using OpenAI-compatible API")
                openAiManager.translateBatch(texts, resolvedSource, targetLanguage, systemPromptOverride)
            }
            "GEMINI" -> {
                Log.d(TAG, "translateBatch: using Gemini, passing override='${systemPromptOverride?.take(200)}'")
                // No fallback — let exception propagate with descriptive message
                geminiManager.translateBatch(texts, resolvedSource, targetLanguage, systemPromptOverride)
            }
            "GOOGLE_FREE" -> {
                Log.d(TAG, "translateBatch: using Google Free")
                googleFreeManager.translateBatch(texts, resolvedSource, targetLanguage)
            }
            else -> {
                Log.d(TAG, "translateBatch: using Google PA")
                googlePAManager.translateBatch(texts, resolvedSource, targetLanguage)
            }
        }
    }

    /**
     * Translates a single chapter title.
     * Uses the active provider if it is GOOGLE_FREE or GOOGLE_PA.
     * For GEMINI or OPENAI falls back to Google PA then Google Free
     * to avoid spending API tokens on short titles.
     */
    override suspend fun translateTitle(
        title: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null

        val resolvedSource = if (sourceLanguage == "auto") {
            googleFreeManager.detectLanguage(title.take(200)) ?: sourceLanguage
        } else {
            sourceLanguage
        }

        when (activeProvider()) {
            "GOOGLE_FREE" -> {
                Log.d(TAG, "translateTitle: using Google Free")
                try {
                    return@withContext googleFreeManager.translateBatch(
                        listOf(title), resolvedSource, targetLanguage
                    )[title]?.takeIf { !it.isNullOrBlank() && it != title }
                } catch (e: Exception) {
                    Log.w(TAG, "translateTitle: Free failed (${e.message})")
                    null
                }
            }
            "GOOGLE_PA" -> {
                Log.d(TAG, "translateTitle: using Google PA")
                try {
                    return@withContext googlePAManager.translateBatch(
                        listOf(title), resolvedSource, targetLanguage
                    )[title]?.takeIf { !it.isNullOrBlank() && it != title }
                } catch (e: Exception) {
                    Log.w(TAG, "translateTitle: PA failed (${e.message})")
                    null
                }
            }
            else -> {
                // GEMINI or OPENAI — PA → Free fallback
                try {
                    val result = googlePAManager.translateBatch(
                        listOf(title), resolvedSource, targetLanguage
                    )[title]
                    if (!result.isNullOrBlank() && result != title) {
                        Log.d(TAG, "translateTitle: PA succeeded")
                        return@withContext result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "translateTitle: PA failed (${e.message}), trying Free")
                }

                try {
                    val result = googleFreeManager.translateBatch(
                        listOf(title), resolvedSource, targetLanguage
                    )[title]
                    if (!result.isNullOrBlank() && result != title) {
                        Log.d(TAG, "translateTitle: Free succeeded")
                        return@withContext result
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "translateTitle: Free also failed (${e.message})")
                }

                null
            }
        }
    }

    override suspend fun detectLanguage(text: String): String? {
        return googleFreeManager.detectLanguage(text)
    }

    override fun downloadModel(language: String) {}
    override fun removeModel(language: String) {}

    companion object {
        private const val TAG = "TranslationComposite"
    }
}