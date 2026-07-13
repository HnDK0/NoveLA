package my.noveldokusha.text_translator

import kotlinx.serialization.Serializable

/**
 * Пресет системного промпта. Используется обоими LLM-провайдерами (Gemini и OpenAI-compatible).
 *
 * @param name    Отображаемое название пресета
 * @param prompt  Текст промпта. Поддерживает плейсхолдеры {source_language}, {target_language}
 */
@Serializable
data class PromptPreset(
    val name: String,
    val prompt: String,
)