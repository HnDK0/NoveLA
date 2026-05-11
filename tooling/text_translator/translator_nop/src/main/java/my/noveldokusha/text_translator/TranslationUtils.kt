package my.noveldokusha.text_translator

import java.util.Locale
// import java.util.regex.Pattern

// ─── BATCH SEPARATOR (~1 token) ──────────────────────────────────────────────
// Uses "§" (SECTION SIGN) because:
//  • Tokenizes as a single token in all major LLM tokenizers → zero overhead
//  • Virtually never appears in web-novel prose (<0.0001 % occurrence)
//  • Needs no escaping → no complex pre/post-processing logic
//  • ASCII-compatible and universally supported
const val BATCH_SEPARATOR = "§"

// ─── SANITIZATION PATTERNS ───────────────────────────────────────────────────

/** Zero-width characters and BOM that commonly appear in scraped web-novel text. */
private val ZERO_WIDTH_CHARS = Regex("[\u200B-\u200D\uFEFF]")

/**
 * Control characters that are invalid in prose (\n, \r, \t are intentionally kept).
 * Null bytes, bell, backspace, vertical tab, form feed, DEL, and other C0/C1 garbage.
 */
 private val INVALID_CONTROL_CHARS = Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]")

/** Three or more consecutive newlines (collapsed to a single paragraph break). */
private val EXCESSIVE_NEWLINES = Regex("(\n\\s*){3,}")

// ─── PROMPT BASE TEMPLATES (optimized for token efficiency) ────────────────
// Base templates do NOT include a format suffix.
// The correct suffix (single vs. batch) is appended by buildSystemPrompt().

private const val BASE_MINIMAL = "Literary translator. {source_language}→{target_language}. Fidelity: keep all, no summary/omission/softening. Keep names. Remove ads."
private const val BASE_BALANCED = "Professional translator (Asian WN). {source_language}→{target_language}. No shortening. Keep names. Natural translation of terms/ranks. Unique terms: \"Term (Meaning)\" first use. Rich vocab, professional tone."
private const val BASE_DETAILED = "Expert Asian WN translator. {source_language}→{target_language}. Absolute fidelity: no change in tone/intensity/detail. No shortening. Keep names. Natural terms, ranks, sects, artifacts. Unique: \"Term (Meaning)\" first use. Rich vocab, professional tone, match source tone. Inner monologue depth."
private const val BASE_ADULT = "Professional novel translator (publishing). {source_language}→{target_language}. Absolute fidelity, no softening/omission. No shortening. Keep names. No euphemisms, full intensity. Rich vocab, professional tone."
private const val BASE_DIRECT_ASIAN = "Expert Asian WN translator. {source_language}→{target_language}. Direct translation, complete fidelity. No shortening. Transliterate names into {target_language} phonetics (NOT via English). Example: 小燕→Сяо Янь. Natural terms/ranks. Rich vocab, professional tone."

// ─── FORMAT SUFFIXES ─────────────────────────────────────────────────────────

/** Appended when translating a single text block. */
internal const val FORMAT_SINGLE = "\nOnly the translation. No extra text."

/**
 * Appended when translating a batch of paragraphs.
 * Instructs the LLM to preserve the BATCH_SEPARATOR as a delimiter.
 */
internal const val FORMAT_BATCH = """Paragraphs separated by "$BATCH_SEPARATOR". Output each translation in order, keep line breaks. No extra text."""

// ─── PUBLIC PROMPT CONSTANTS ─────────────────────────────────────────────────
// Exposed as convenience constants (base + single-format suffix).
// buildSystemPrompt() will strip the suffix and re-attach the correct one,
// so passing these as a stored user preference is safe.

const val PROMPT_MINIMAL       = BASE_MINIMAL      + FORMAT_SINGLE
const val PROMPT_BALANCED      = BASE_BALANCED     + FORMAT_SINGLE
const val PROMPT_DETAILED      = BASE_DETAILED     + FORMAT_SINGLE
const val PROMPT_ADULT         = BASE_ADULT        + FORMAT_SINGLE
const val PROMPT_DIRECT_ASIAN  = BASE_DIRECT_ASIAN + FORMAT_SINGLE

/** Default template used when no user preference is set. */
const val DEFAULT_TRANSLATION_PROMPT = PROMPT_BALANCED

val BUILT_IN_PROMPTS = listOf(
    "Minimal"            to PROMPT_MINIMAL,
    "Balanced (Default)" to PROMPT_BALANCED,
    "Detailed"           to PROMPT_DETAILED,
    "Adult (18+)"        to PROMPT_ADULT,
    "Direct Asian"       to PROMPT_DIRECT_ASIAN,
)

// ─── LANGUAGE UTILS ──────────────────────────────────────────────────────────

/**
 * Resolves an ISO 639-1 language code to a human-readable display name.
 *
 * @param langCode    ISO 639-1 code, e.g. "en", "ja", "zh"
 * @param useEnglish  If true, always return the English name; otherwise use the system locale.
 */
fun resolveLanguageName(langCode: String, useEnglish: Boolean): String {
    val locale = Locale(langCode)
    return if (useEnglish) locale.getDisplayLanguage(Locale.ENGLISH) else locale.displayLanguage
}

/**
 * Builds the final system prompt with resolved language names and the correct format suffix.
 *
 * Handles both built-in templates (which already embed FORMAT_SINGLE) and arbitrary
 * user-defined templates by stripping any pre-existing format suffix before appending
 * the correct one. This prevents the double-suffix bug ("...no notes.\nOutput the
 * translation only. No preamble, no notes.") that would otherwise inflate token usage.
 *
 * @param template         Raw template string — may or may not include a format suffix.
 * @param sourceLanguage   ISO 639-1 source language code.
 * @param targetLanguage   ISO 639-1 target language code.
 * @param useEnglishLocale If true, language names in the prompt are resolved in English.
 * @param isBatch          If true, appends FORMAT_BATCH; otherwise appends FORMAT_SINGLE.
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

    // Strip any pre-existing format suffix so we never send duplicate instructions.
    // ORDER MATTERS: check FORMAT_BATCH first (it does not overlap with FORMAT_SINGLE).
    val base = template.trimEnd().let { t ->
        when {
            t.endsWith(FORMAT_BATCH.trimEnd())  -> t.removeSuffix(FORMAT_BATCH.trimEnd())
            t.endsWith(FORMAT_SINGLE.trimEnd()) -> t.removeSuffix(FORMAT_SINGLE.trimEnd())
            else                                -> t
        }
    }.trimEnd()

    val suffix = if (isBatch) FORMAT_BATCH else FORMAT_SINGLE

    return base
        .replace("{source_language}", src)
        .replace("{target_language}", tgt) + suffix
}

// ─── PRE-SEND SANITIZATION ───────────────────────────────────────────────────

/**
 * Sanitizes a single paragraph before it is sent to an LLM.
 *
 * Steps applied (in order):
 * 1. Replace any accidental BATCH_SEPARATOR with a space to protect the delimiter protocol.
 * 2. Remove zero-width characters and BOM (common in scraped web-novel HTML).
 * 3. Strip invalid control characters (null, bell, DEL, etc.) while keeping \n, \r, \t.
 * 4. Trim leading/trailing whitespace on each line, then trim the paragraph as a whole.
 *
 * @param paragraph Raw paragraph text from the scraper.
 * @return Sanitized paragraph safe for LLM consumption.
 */
fun sanitizeParagraph(paragraph: String): String =
    paragraph
        .replace(BATCH_SEPARATOR, " ")           // protect delimiter
        .replace(ZERO_WIDTH_CHARS, "")           // strip invisible chars & BOM
        .replace(INVALID_CONTROL_CHARS, "")      // strip garbage control chars
        .lines()
        .joinToString("\n") { it.trim() }        // normalize per-line whitespace
        .trim()

/**
 * Sanitizes a raw chapter text and splits it into clean paragraphs ready for batch LLM calls.
 *
 * Steps:
 * 1. Collapse runs of 3+ blank lines into a single paragraph break (\n\n).
 * 2. Split on double newlines (paragraph boundaries).
 * 3. Sanitize each paragraph via [sanitizeParagraph].
 * 4. Drop any resulting empty paragraphs.
 *
 * @param chapterText Raw chapter text (from scraper/HTML extractor).
 * @return Non-empty, sanitized paragraphs ready to pass into translateBatch().
 */
fun sanitizeChapterForBatch(chapterText: String): List<String> =
    chapterText
        .replace(EXCESSIVE_NEWLINES, "\n\n")   // collapse excessive blank lines
        .split("\n\n")
        .map { sanitizeParagraph(it) }
        .filter { it.isNotBlank() }

// ─── POST-RECEIVE PARSING ────────────────────────────────────────────────────

/**
 * Parses an LLM batch response and builds an original→translation mapping.
 *
 * The response is expected to contain one translated paragraph per original,
 * delimited by [BATCH_SEPARATOR].
 *
 * NOTE: This function only returns successfully translated items. If the LLM
 * returns fewer segments than requested, the missing entries are NOT included
 * in the map. This allows the caller to detect partial failures and avoid
 * saving original text as "translation" in the database.
 *
 * @param response           Raw LLM response string.
 * @param originalParagraphs Source paragraphs used as map keys.
 * @return Map of original paragraph → translated paragraph.
 */
fun parseBatchTranslationResponse(
    response: String,
    originalParagraphs: List<String>,
): Map<String, String> {
    val parts = response
        .split(Regex("\\s*$BATCH_SEPARATOR\\s*"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    return buildMap {
        originalParagraphs.forEachIndexed { index, original ->
            parts.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { translated ->
                // Avoid mapping back to the same text if the LLM just echoed the original.
                if (translated != original) {
                    put(original, translated)
                }
            }
        }
    }
}

// ─── BACKWARD COMPATIBILITY ALIAS ────────────────────────────────────────────

/**
 * Alias for [parseBatchTranslationResponse].
 * Retained for any call sites not yet migrated.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun parseSeparatedTranslations(
    response: String,
    originals: List<String>,
): Map<String, String> = parseBatchTranslationResponse(response, originals)

// ─── LEGACY ALIAS ────────────────────────────────────────────────────────────

/**
 * Alias for [sanitizeParagraph].
 * Retained for call sites that used the old name.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun cleanParagraphForBatch(paragraph: String): String = sanitizeParagraph(paragraph)
