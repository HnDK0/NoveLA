package my.noveldokusha.core.appPreferences

import java.util.Locale

data class AppLanguage(
    val code: String,
    val locale: Locale,
) {
    fun getDisplayName(): String {
        return locale.getDisplayLanguage(locale)
            .replaceFirstChar { it.uppercaseChar() }
    }

    companion object {
        val ENGLISH = AppLanguage("en", Locale.ENGLISH)
        val RUSSIAN = AppLanguage("ru", Locale("ru"))
        val GERMAN = AppLanguage("de", Locale.GERMAN)
        val FRENCH = AppLanguage("fr", Locale.FRENCH)
        val SPANISH = AppLanguage("es", Locale("es"))
        val PORTUGUESE = AppLanguage("pt", Locale("pt"))
        val TURKISH = AppLanguage("tr", Locale("tr"))
        val CHINESE = AppLanguage("zh", Locale.CHINESE)
        val ARABIC = AppLanguage("ar", Locale("ar"))
        val INDONESIAN = AppLanguage("id", Locale("id"))
        val FILIPINO = AppLanguage("fil", Locale("fil"))
        val JAPANESE = AppLanguage("ja", Locale.JAPANESE)
        val MALAY = AppLanguage("ms", Locale("ms"))
        val VIETNAMESE = AppLanguage("vi", Locale("vi"))
        val BENGALI = AppLanguage("bn", Locale("bn"))
        val KOREAN = AppLanguage("ko", Locale.KOREAN)
        val THAI = AppLanguage("th", Locale("th"))
        val HINDI = AppLanguage("hi", Locale("hi"))
        val POLISH = AppLanguage("pl", Locale("pl"))
        val ITALIAN = AppLanguage("it", Locale.ITALIAN)
    }
}

object AppLanguageProvider {
    val supportedLanguages: List<AppLanguage> = listOf(
        AppLanguage.ENGLISH,
        AppLanguage.RUSSIAN,
        AppLanguage.GERMAN,
        AppLanguage.FRENCH,
        AppLanguage.SPANISH,
        AppLanguage.PORTUGUESE,
        AppLanguage.TURKISH,
        AppLanguage.CHINESE,
        AppLanguage.ARABIC,
        AppLanguage.INDONESIAN,
        AppLanguage.FILIPINO,
        AppLanguage.JAPANESE,
        AppLanguage.MALAY,
        AppLanguage.VIETNAMESE,
        AppLanguage.BENGALI,
        AppLanguage.KOREAN,
        AppLanguage.THAI,
        AppLanguage.HINDI,
        AppLanguage.POLISH,
        AppLanguage.ITALIAN,
    )

    private val codeToLanguage by lazy {
        supportedLanguages.associateBy { it.code }
    }

    private val oldEnumNameToCode = mapOf(
        "ENGLISH" to "en",
        "RUSSIAN" to "ru",
    )

    fun fromCode(code: String): AppLanguage? {
        val actualCode = oldEnumNameToCode[code] ?: code
        return codeToLanguage[actualCode]
    }

    fun fromLocale(locale: Locale): AppLanguage {
        return supportedLanguages.find { it.locale.language == locale.language }
            ?: supportedLanguages.first()
    }
}
