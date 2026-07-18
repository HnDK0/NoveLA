package my.noveldokusha.core

/**
 * Extension functions for LanguageCode
 */

/**
 * Find LanguageCode by ISO 639-1 code
 */
fun fromIso639_1(isoCode: String): LanguageCode {
    return LanguageCode.values().find { it.iso639_1.equals(isoCode, ignoreCase = true) }
        ?: LanguageCode.ENGLISH // Default to English if not found
}
