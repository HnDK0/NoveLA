package my.noveldokusha.webview

import my.noveldokusha.text_translator.domain.GOOGLE_TRANSLATE_LANGUAGES

/**
 * Резолвер целевого языка перевода страниц WebView.
 *
 * Порядок разрешения строго по приоритету:
 * 1. Глобальная настройка [globalTargetPref] (после trim) непустая И содержится
 *    в [GOOGLE_TRANSLATE_LANGUAGES] → возвращается она;
 * 2. Язык устройства [deviceLocaleLanguage] содержится в [GOOGLE_TRANSLATE_LANGUAGES]
 *    → возвращается он;
 * 3. Иначе → "en".
 *
 * Значения вроде "auto" / "" не входят в список поддерживаемых кодов и корректно
 * отбрасываются на шаге 1.
 *
 * Осознанное ограничение: базовый код "zh" (значение Locale.getDefault().language
 * для китайского) НЕ матчится с региональными "zh-CN"/"zh-TW" из списка
 * (SupportedLanguages.kt) → китайские устройства получат "en". Поведение
 * соответствует приоритету плана (белый список → fallback "en") и выбрано осознанно.
 */
object TargetLanguageResolver {

    fun resolve(globalTargetPref: String?, deviceLocaleLanguage: String): String {
        val target = globalTargetPref?.trim()
            ?.takeIf { it in GOOGLE_TRANSLATE_LANGUAGES }
            ?: deviceLocaleLanguage.takeIf { it in GOOGLE_TRANSLATE_LANGUAGES }
        return target ?: "en"
    }
}
