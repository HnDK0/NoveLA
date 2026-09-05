package my.noveldokusha.text_translator.domain

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.Locale

@Immutable
data class TranslationModelState(
    val language: String,
    val available: Boolean,
    val downloading: Boolean,
    val downloadingFailed: Boolean,
) {
    val locale: Locale = try {
        Locale.forLanguageTag(language)
    } catch (_: Exception) {
        try { Locale(language) } catch (_: Exception) { Locale("en") }
    }

    val displayName: String
        get() {
            // Локализованное полное название (язык + регион/скрипт)
            val full = locale.getDisplayName()
                .takeIf { it.isNotBlank() && it != language }
            if (full != null) return full
            // Fallback на английское из карты
            return LANGUAGE_DISPLAY_NAMES[language] ?: language.uppercase()
        }
}

data class TranslatorState(
    val source: String,
    val target: String,
    val translate: suspend (input: String) -> String,
) {
    val sourceLocale = Locale(source)
    val targetLocale = Locale(target)
}

interface TranslationManager {

    val available: Boolean

    val isUsingOnlineTranslation: Boolean get() = false

    val models: SnapshotStateList<TranslationModelState>

    suspend fun hasModelDownloaded(language: String): TranslationModelState?

    /**
     * Doesn't check if the model has been downloaded. Must be externally guaranteed.
     * @param source language locale
     * @param target language locale
     * @param systemPromptOverride optional per-novel prompt override (Gemini/OpenAI only)
     * @param provider optional backend override; null → active provider from settings
     */
    fun getTranslator(
        source: String,
        target: String,
        systemPromptOverride: String? = null,
        provider: String? = null
    ): TranslatorState

    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        systemPromptOverride: String? = null,
        provider: String? = null,
    ): Map<String, String>

    /**
     * Detect the language of the given text.
     * Returns a BCP-47 language tag (e.g. "zh", "en", "ru") or null if detection failed.
     * Default implementation returns null — override in online managers.
     */
    suspend fun detectLanguage(text: String): String? = null

    /**
     * Translate a single chapter title.
     * GOOGLE_FREE / GOOGLE_PA — direct translation.
     * GEMINI / OPENAI — falls back to Google PA → Free to avoid wasting LLM tokens on short titles.
     * Returns null if translation is not supported or all endpoints fail.
     * @param provider optional backend override; null → active provider from settings
     */
    suspend fun translateTitle(
        title: String,
        sourceLanguage: String,
        targetLanguage: String,
        provider: String? = null
    ): String? = null
}