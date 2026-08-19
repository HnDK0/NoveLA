package my.noveldokusha.core

import android.content.Context
import android.content.res.Configuration
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.core.appPreferences.AppLanguageProvider
import java.util.Locale

object LocaleManager {

    /**
     * Контекст с локалью, выбранной в настройках приложения
     * (APP_LANGUAGE_CODE). Используется в attachBaseContext Application
     * и каждой Activity: без этого экран падает на системный язык.
     */
    fun createAppLocaleContext(context: Context): Context {
        val prefs = context.getSharedPreferences(
            context.packageName + "_preferences", Context.MODE_PRIVATE
        )
        val code = prefs.getString("APP_LANGUAGE_CODE", "en") ?: "en"
        val language = AppLanguageProvider.fromCode(code)
            ?: AppLanguageProvider.supportedLanguages.first()
        return createLocaleContext(context, language)
    }

    fun createLocaleContext(context: Context, language: AppLanguage): Context {
        val locale = language.locale
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    fun applyLocale(context: Context, language: AppLanguage) {
        val locale = language.locale
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    fun applyLocale(context: Context, locale: Locale) {
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    fun getCurrentLocale(context: Context): Locale {
        return context.resources.configuration.locales[0]
    }
}
