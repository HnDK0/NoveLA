package my.noveldokusha.text_translator

import java.util.Locale

// ─── Separator used to split batch paragraphs ─────────────────────────────────
// A line containing only "---" — rare in fiction, easy to split on, zero numbering overhead.
const val BATCH_SEPARATOR = "---"

// ─── Prompt presets ───────────────────────────────────────────────────────────
// Each preset is a BASE template (no format instructions).
// buildSystemPrompt() appends the correct FORMAT block depending on single vs batch mode.

private const val BASE_MINIMAL = """You are a professional literary translator. Translate from {source_language} to {target_language} with complete fidelity. Never summarize, omit, or soften anything.
- Keep character names as-is. Remove ads/site plugs."""

private const val BASE_BALANCED = """You are a professional literary translator (20 yrs, Asian web novels: Xianxia, Wuxia, Light Novels). Translate from {source_language} to {target_language} with complete fidelity.
- Never shorten. Keep names as-is. Translate ranks/techniques into natural {target_language}. Unique terms: "Term (Meaning)" first use only.
- Rich vocabulary. Professionally published tone. Distinguish formal/informal speech."""

private const val BASE_DETAILED = """You are a professional literary translator (20 yrs, Asian web novels: Xianxia, Wuxia, Light Novels, Manhwa). Translate from {source_language} to {target_language} — any alteration of tone, intensity, or detail is an error.
- Never shorten. Keep names as-is. Translate ranks/techniques/sects/artifacts into natural {target_language}. Unique terms: "Term (Meaning)" first use only.
- Rich vocabulary. Professionally published tone. Distinguish formal/informal speech. Inner monologues with emotional depth. Match source tone exactly."""

private const val BASE_ADULT = """You are a professional literary translator (20 yrs, major international publishing house). Translate from {source_language} to {target_language}. Absolute fidelity required — omitting or softening any scene violates the author's rights.
- Never shorten. Keep names as-is. No euphemisms — render all scenes with full intensity. Rich vocabulary. Professionally published tone."""

private const val BASE_DIRECT_ASIAN = """You are a professional literary translator (20 yrs, Asian web novels: Xianxia, Wuxia, Light Novels, Manhwa). Translate directly from {source_language} to {target_language} with complete fidelity.
- Never shorten. Transliterate names DIRECTLY into {target_language} phonetics — NOT via English (e.g. Chinese→Russian: 小燕 → Сяо Янь). Translate ranks/techniques into natural {target_language}.
- Rich vocabulary. Professionally published tone. Distinguish formal/informal speech."""

// Format suffixes — appended at prompt-build time
private const val FORMAT_SINGLE = "\nOutput the translation only. No preamble, no notes."

private const val FORMAT_BATCH = """
Paragraphs are delimited by "$BATCH_SEPARATOR" on its own line. Translate each paragraph and output them in the same order, delimited by "$BATCH_SEPARATOR". No preamble, no notes."""

// Public prompt constants (base + single format, for display/user editing)
const val PROMPT_MINIMAL       = BASE_MINIMAL       + FORMAT_SINGLE
const val PROMPT_BALANCED      = BASE_BALANCED      + FORMAT_SINGLE
const val PROMPT_DETAILED      = BASE_DETAILED      + FORMAT_SINGLE
const val PROMPT_ADULT         = BASE_ADULT         + FORMAT_SINGLE
const val PROMPT_DIRECT_ASIAN  = BASE_DIRECT_ASIAN  + FORMAT_SINGLE

const val DEFAULT_TRANSLATION_PROMPT = PROMPT_BALANCED

val BUILT_IN_PROMPTS = listOf(
    "Minimal"            to PROMPT_MINIMAL,
    "Balanced (Default)" to PROMPT_BALANCED,
    "Detailed"           to PROMPT_DETAILED,
    "Adult (18+)"        to PROMPT_ADULT,
    "Direct Asian"       to PROMPT_DIRECT_ASIAN,
)

// ─── Prompt building ──────────────────────────────────────────────────────────

fun resolveLanguageName(langCode: String, useEnglish: Boolean): String {
    val locale = Locale(langCode)
    return if (useEnglish) locale.getDisplayLanguage(Locale.ENGLISH) else locale.displayLanguage
}

/**
 * Builds the final system prompt.
 * The stored template may use FORMAT_SINGLE ending (from user-edited presets).
 * For batch calls, we strip that suffix and append FORMAT_BATCH instead.
 */
fun buildSystemPrompt(
    template: String,
    sourceLanguage: String,
    targetLanguage: String,
    useEnglishLocale: Boolean,
    isBatch: Boolean = false,
): String {
    val src = resolveLanguageName(sourceLanguage, useEnglishLocale)
    val tgt = resolveLanguageName(targetLanguage, useEnglishLocale)
    // Strip any existing format suffix, then append the correct one
    val base = template.removeSuffix(FORMAT_BATCH).removeSuffix(FORMAT_SINGLE)
    val format = if (isBatch) FORMAT_BATCH else FORMAT_SINGLE
    return (base + format)
        .replace("{source_language}", src)
        .replace("{target_language}", tgt)
}

// ─── Batch response parser (shared by all LLM managers) ───────────────────────

/**
 * Splits a separator-delimited response back into a map of original → translated.
 * Falls back to original text for any missing paragraph.
 */
fun parseSeparatedTranslations(response: String, originals: List<String>): Map<String, String> {
    val parts = response.split(Regex("\\n$BATCH_SEPARATOR\\n|^$BATCH_SEPARATOR\\n|\\n$BATCH_SEPARATOR$"))
        .map { it.trim() }
    return buildMap {
        originals.forEachIndexed { i, orig ->
            put(orig, parts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: orig)
        }
    }
}
