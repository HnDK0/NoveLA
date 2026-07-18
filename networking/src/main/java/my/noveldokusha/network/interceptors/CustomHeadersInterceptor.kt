package my.noveldokusha.network.interceptors

import my.noveldokusha.core.appPreferences.AppPreferences
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

internal class CustomHeadersInterceptor @Inject constructor(
    private val appPreferences: AppPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val customHeaders = appPreferences.SCRAPER_CUSTOM_HEADERS.value

        if (customHeaders.isEmpty()) {
            return chain.proceed(originalRequest)
        }

        val requestBuilder = originalRequest.newBuilder()
        customHeaders.forEach { (key, value) ->
            if (value.isNotBlank()) {
                requestBuilder.addHeader(key, value)
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}
