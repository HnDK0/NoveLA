package my.noveldokusha.text_translator

import kotlinx.serialization.Serializable

/**
 * Represents a named system-prompt preset.
 *
 * Used by both LLM-backed translation managers (Gemini and OpenAI-compatible).
 * The [prompt] field may include `{source_language}` and `{target_language}` placeholders,
 * which are resolved at call time by [buildSystemPrompt].
 *
 * @property name   Display name shown in the Settings UI (e.g. "Balanced (Default)").
 * @property prompt Raw prompt template. May or may not include a format suffix;
 *                  [buildSystemPrompt] normalises it before use.
 */
@Serializable
data class PromptPreset(
    val name: String,
    val prompt: String,
)
