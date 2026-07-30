package my.noveldokusha.network.interceptors

import my.noveldokusha.core.appPreferences.AppPreferences
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

// Выносим константу ВНЕ класса.
// Теперь это топовая переменная уровня пакета.
//const val GLOBAL_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
//const val GLOBAL_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.164 Mobile Safari/537.36"
const val GLOBAL_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240205.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.6834.83 Mobile Safari/537.36"

/**
 * Возвращает эффективный User-Agent: кастомный из настроек, если задан и валиден,
 * иначе — [GLOBAL_USER_AGENT] по умолчанию.
 */
fun resolveUserAgent(appPreferences: AppPreferences): String {
    val custom = appPreferences.SCRAPER_USER_AGENT.value
    return if (custom.isNotBlank() && custom.isAscii()) custom else GLOBAL_USER_AGENT
}

private fun String.isAscii(): Boolean = all { it.code in 0..127 }

class UserAgentInterceptor(private val appPreferences: AppPreferences) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("User-Agent") != null) return chain.proceed(request)

        // 1. По тегу source:<id> (Lua http_get, bookChapter, etc.)
        val tag = request.tag(String::class.java)
        if (tag != null && tag.startsWith("source:")) {
            val sourceId = tag.removePrefix("source:")
            val presetName = PluginUARegistry.getPreset(sourceId)
            Timber.d("intercept: tag=$tag sourceId=$sourceId presetName=$presetName")
            if (presetName != null) {
                val presetUA = UAPresets.resolve(presetName)
                Timber.d("intercept: resolved presetUA=$presetUA")
                if (presetUA != null) {
                    return chain.proceed(
                        request.newBuilder().header("User-Agent", presetUA).build()
                    )
                }
            }
        }

        // 2. По хосту (Coil-картинки, нетэгированные запросы к picture.readfrom.net и т.п.)
        if (tag == null) {
            val presetName = PluginUARegistry.getPresetForHost(request.url.host)
            if (presetName != null) {
                val presetUA = UAPresets.resolve(presetName)
                Timber.d("intercept: host=${request.url.host} presetName=$presetName presetUA=$presetUA")
                if (presetUA != null) {
                    return chain.proceed(
                        request.newBuilder().header("User-Agent", presetUA).build()
                    )
                }
            }
        }

        Timber.d("intercept: no tag or no preset, fallback to default UA")
        val userAgent = resolveUserAgent(appPreferences)
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", userAgent)
                .build()
        )
    }
}