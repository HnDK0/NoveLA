package my.noveldokusha.core

import androidx.annotation.StringRes

/**
 * ISO 639-1 codes
 * https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
 */
enum class LanguageCode(
    @Suppress("PropertyName") val iso639_1: String,
    @StringRes val nameResId: Int
) {
    MULTILANGUAGE(iso639_1 = "MULTILANGUAGE", nameResId = R.string.language_multilanguage),
    MTL(iso639_1 = "MTL", nameResId = R.string.language_MTL),
    ENGLISH(iso639_1 = "en", nameResId = R.string.language_english),
    INDONESIAN(iso639_1 = "id", nameResId = R.string.language_indonesian),
    CHINESE(iso639_1 = "zh", nameResId = R.string.language_chinese),
    RUSSIAN(iso639_1 = "ru", nameResId = R.string.language_russian),

}
