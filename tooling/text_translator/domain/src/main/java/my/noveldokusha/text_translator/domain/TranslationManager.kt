package my.noveldokusha.text_translator.domain

import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.Locale

data class TranslationModelState(
    val language: String,
    val available: Boolean,
    val downloading: Boolean,
    val downloadingFailed: Boolean,
) {
    val locale = Locale(language)
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
     */
    fun getTranslator(source: String, target: String, systemPromptOverride: String? = null): TranslatorState

    fun downloadModel(language: String)

    fun removeModel(language: String)

    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        systemPromptOverride: String? = null,
    ): Map<String, String>

    /**
     * Detect the language of the given text.
     * Returns a BCP-47 language tag (e.g. "zh", "en", "ru") or null if detection failed.
     * Default implementation returns null — override in online managers.
     */
    suspend fun detectLanguage(text: String): String? = null

    /**
     * Translate a single chapter title using free Google endpoints (PA → Free fallback).
     * Never uses token-based providers (Gemini/OpenAI) to avoid wasting quota.
     * Returns null if translation is not supported or both endpoints fail.
     */
    suspend fun translateTitle(
        title: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? = null
}