package my.noveldokusha.core

import java.util.Locale

/**
 * Единственный источник истины для отображения названия языка по ISO 639-1 коду.
 * Язык отображается на своём языке (системный подход, без хардкода):
 *   "en" → "English", "de" → "Deutsch", "ru" → "Русский"
 */
fun getLanguageDisplayName(code: String): String {
    if (code.isBlank()) return ""
    if (code == "multi" || code == "Mtl") return "Mtl"
    return try {
        val locale = Locale.forLanguageTag(code)
        locale.getDisplayLanguage(locale)
            .replaceFirstChar { it.uppercaseChar() }
            .takeIf { it.isNotBlank() && it != code }
            ?: code.uppercase()
    } catch (e: Exception) {
        code.uppercase()
    }
}