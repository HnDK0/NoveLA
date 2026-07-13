package my.noveldokusha.network

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import my.noveldokusha.core.AppInternalState
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.interceptors.CloudFareVerificationInterceptor
import my.noveldokusha.network.interceptors.DecodeResponseInterceptor
import my.noveldokusha.network.interceptors.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkClient {
    val cookieJar: okhttp3.CookieJar
    suspend fun call(request: Request.Builder, followRedirects: Boolean = false): Response
    suspend fun get(url: String): Response
    suspend fun getWithHeaders(url: String, headers: Map<String, String>): Response
    suspend fun get(url: Uri.Builder): Response
}

@Singleton
class ScraperNetworkClient @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appInternalState: AppInternalState,
    private val appPreferences: AppPreferences
) : NetworkClient {

    private val cacheDir = File(appContext.cacheDir, "network_cache")
    private val cacheSize = 50L * 1024 * 1024

    override val cookieJar = ScraperCookieJar()

    private val okhttpLoggingInterceptor = createLoggingInterceptor()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply {
                if (appInternalState.isDebugMode) okhttpLoggingInterceptor?.let { addInterceptor(it) }
                addInterceptor(UserAgentInterceptor(appPreferences))
                addInterceptor(DecodeResponseInterceptor())
                if (appPreferences.CLOUDFLARE_BYPASS_ENABLED.value) {
                    addInterceptor(CloudFareVerificationInterceptor(appContext, appPreferences))
                }
                connectionPool(ConnectionPool(15, 5, TimeUnit.MINUTES))
                dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
                cookieJar(cookieJar)
                cache(Cache(cacheDir, cacheSize))
                connectTimeout(30, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                // Кастомный DNS resolver через DoH — работает когда системный DNS блокируется Doze mode
                dns(DnsOverHttps())
            }
            .build()
    }

    private val clientWithRedirects: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override suspend fun call(request: Request.Builder, followRedirects: Boolean): Response {
        return if (followRedirects) clientWithRedirects.call(request) else client.call(request)
    }

    override suspend fun get(url: String): Response = call(getRequest(url))
    override suspend fun getWithHeaders(url: String, headers: Map<String, String>): Response {
        val builder = getRequest(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return call(builder)
    }
    override suspend fun get(url: Uri.Builder): Response = call(getRequest(url.toString()))

    private fun getRequest(url: String): Request.Builder {
        return Request.Builder().url(url).get()
    }
}
