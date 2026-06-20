package my.noveldokusha.network.interceptors

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.selects.select
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.domain.CloudfareVerificationBypassFailedException
import my.noveldokusha.core.domain.WebViewCookieManagerInitializationFailedException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

private val ERROR_CODES = listOf(HttpsURLConnection.HTTP_FORBIDDEN, HttpsURLConnection.HTTP_UNAVAILABLE)
private const val TAG = "CloudflareInterceptor"
private const val MAX_MANUAL_ATTEMPTS = 3

private val CLOUDFLARE_WHITELIST = listOf(
    "github.com",
    "raw.githubusercontent.com"
)

/**
 * Настройки CF-байпаса для конкретного домена.
 * Устанавливается плагином через LuaCfOptionsRegistry.
 *
 * @param whitelist полностью отключить CF-детект для домена
 * @param ignoreMarkers конкретные маркеры которые игнорировать (например listOf("turnstile"))
 */
data class CfDomainOptions(
    val whitelist: Boolean = false,
    val ignoreMarkers: Set<String> = emptySet()
)

/**
 * Реестр CF-настроек от Lua плагинов.
 * Плагин при загрузке регистрирует свой домен и опции.
 */
object LuaCfOptionsRegistry {
    private val options = ConcurrentHashMap<String, CfDomainOptions>()

    fun register(domain: String, cfOptions: CfDomainOptions) {
        // Нормализуем домен — убираем www. и слеши
        val key = domain.removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").trimEnd('/')
        options[key] = cfOptions
        Log.d(TAG, "CF options registered for $key: $cfOptions")
    }

    fun getForHost(host: String): CfDomainOptions? {
        val key = host.removePrefix("www.")
        return options[key]
    }

    fun clear(domain: String) {
        val key = domain.removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").trimEnd('/')
        options.remove(key)
    }
}

object CloudflareBypassSignal {
    val channel = Channel<Unit>(Channel.CONFLATED)

    private val _bypassCompleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val bypassCompleted: SharedFlow<String> = _bypassCompleted

    fun notifyBypassCompleted(host: String) {
        _bypassCompleted.tryEmit(host)
    }
}

internal class CloudFareVerificationInterceptor(
    @ApplicationContext private val appContext: Context,
    private val appPreferences: AppPreferences
) : Interceptor {

    private val lock = ReentrantLock()
    private val resolvedDomains = mutableSetOf<String>()
    private val manualAttempts = ConcurrentHashMap<String, Int>()

    // Все возможные маркеры CF
    private val ALL_CF_MARKERS = listOf(
        "cf-challenge",
        "turnstile",
        "requireTurnstile",
        "__cf_chl_",
        "but-captcha"
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val bufferedRequest = if (originalRequest.body != null) {
            val buffer = Buffer()
            originalRequest.body!!.writeTo(buffer)
            val bodyBytes = buffer.readByteArray()
            val replayableBody = object : RequestBody() {
                override fun contentType() = originalRequest.body!!.contentType()
                override fun contentLength() = bodyBytes.size.toLong()
                override fun writeTo(sink: BufferedSink) { sink.write(bodyBytes) }
            }
            originalRequest.newBuilder()
                .method(originalRequest.method, replayableBody)
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(bufferedRequest)
        val bodyPreview = peekBodySafe(response)

        if (isNotCloudflare(response, bodyPreview)) {
            return response
        }

        Log.d(TAG, "CF: Challenge detected. URL: ${bufferedRequest.url}")

        return lock.withLock {
            response.close()

            val siteUrl = bufferedRequest.url.toString()
            val host = bufferedRequest.url.host
            val cookieManager = CookieManager.getInstance()
                ?: throw WebViewCookieManagerInitializationFailedException()
            val userAgent = resolveUserAgent(appPreferences)

            val existingCookie = cookieManager.getCookie(siteUrl) ?: ""
            if (resolvedDomains.contains(host) || existingCookie.contains("cf_clearance")) {
                Log.d(TAG, "CF: cf_clearance cached for $host, trying direct retry")
                val retryRequest = bufferedRequest.newBuilder()
                    .header("Cookie", formatCookies(existingCookie))
                    .header("User-Agent", userAgent)
                    .build()
                val retryResponse = chain.proceed(retryRequest)
                if (isNotCloudflare(retryResponse, peekBodySafe(retryResponse))) {
                    return@withLock retryResponse
                }
                retryResponse.close()
                resolvedDomains.remove(host)
                clearCookiesForDomain(siteUrl, cookieManager)
            }

            proceedWithBypass(chain, bufferedRequest, siteUrl, host, cookieManager, userAgent)
        }
    }

    private fun proceedWithBypass(
        chain: Interceptor.Chain,
        originalRequest: okhttp3.Request,
        siteUrl: String,
        host: String,
        cookieManager: CookieManager,
        userAgent: String
    ): Response {
        val referer = originalRequest.header("Referer")
        val webViewUrl = when {
            siteUrl.contains("/api/") && !referer.isNullOrEmpty() -> referer
            else -> siteUrl
        }

        runBlocking(Dispatchers.Main) {
            withTimeoutOrNull(15_000) {
                resolveWithWebViewAutomatic(webViewUrl, cookieManager)
            }
        }

        val firstCookies = cookieManager.getCookie(siteUrl) ?: ""
        val firstRetryRequest = originalRequest.newBuilder()
            .header("Cookie", formatCookies(firstCookies))
            .header("User-Agent", userAgent)
            .build()

        val firstRetryResponse = chain.proceed(firstRetryRequest)

        if (isNotCloudflare(firstRetryResponse, peekBodySafe(firstRetryResponse))) {
            resolvedDomains.add(host)
            manualAttempts.remove(host)
            CloudflareBypassSignal.notifyBypassCompleted(host)
            return firstRetryResponse
        }

        firstRetryResponse.close()

        val attempts = manualAttempts.getOrDefault(host, 0)
        if (attempts >= MAX_MANUAL_ATTEMPTS) {
            Log.e(TAG, "CF: Max manual attempts ($MAX_MANUAL_ATTEMPTS) reached for $host, giving up")
            manualAttempts.remove(host)
            throw CloudfareVerificationBypassFailedException()
        }
        manualAttempts[host] = attempts + 1
        Log.d(TAG, "CF: Step 2 - manual attempt ${attempts + 1}/$MAX_MANUAL_ATTEMPTS for $host, webViewUrl=$webViewUrl")

        clearCookiesForDomain(siteUrl, cookieManager)

        runBlocking(Dispatchers.IO) {
            resolveWithWebViewManual(webViewUrl, siteUrl, cookieManager)
        }

        cookieManager.flush()
        val finalCookies = cookieManager.getCookie(siteUrl) ?: ""

        val finalRetryRequest = originalRequest.newBuilder()
            .header("Cookie", formatCookies(finalCookies))
            .header("User-Agent", userAgent)
            .build()

        val finalResponse = chain.proceed(finalRetryRequest)

        if (!isNotCloudflare(finalResponse, peekBodySafe(finalResponse))) {
            finalResponse.close()
            throw CloudfareVerificationBypassFailedException()
        }

        resolvedDomains.add(host)
        manualAttempts.remove(host)
        CloudflareBypassSignal.notifyBypassCompleted(host)
        return finalResponse
    }

    private val STATIC_EXTENSIONS = setOf("js", "css", "png", "jpg", "svg", "woff", "woff2", "ttf", "ico", "webp", "json", "txt", "lua")

    private fun isNotCloudflare(response: Response, body: String): Boolean {
        val host = response.request.url.host

        // Статические файлы — никогда не CF-челлендж
        val pathExt = response.request.url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', "")?.lowercase()
        if (pathExt != null && pathExt in STATIC_EXTENSIONS) return true

        // Глобальный whitelist
        if (CLOUDFLARE_WHITELIST.any { host.contains(it) }) return true

        // Настройки от Lua плагина для этого домена
        val domainOptions = LuaCfOptionsRegistry.getForHost(host)

        // Плагин полностью отключил CF-детект для домена
        if (domainOptions?.whitelist == true) return true

        // Маркеры которые игнорируем для этого домена
        val ignoredMarkers = domainOptions?.ignoreMarkers ?: emptySet()

        val activeMarkers = ALL_CF_MARKERS.filter { it !in ignoredMarkers }

        val hasMarkers = activeMarkers.any { body.contains(it, ignoreCase = true) }
        val isError = response.code in ERROR_CODES || (response.code == 200 && hasMarkers)
        val isCfServer = response.header("Server")?.contains("cloudflare", true) == true

        val result = !(isError && (isCfServer || hasMarkers))
        if (!result) {
            val foundMarkers = activeMarkers.filter { body.contains(it, ignoreCase = true) }
            Log.e(TAG, "CF triggered: code=${response.code} isCfServer=$isCfServer foundMarkers=$foundMarkers")
        }
        return result
    }

    private fun clearCookiesForDomain(url: String, cm: CookieManager) {
        val httpUrl = url.toHttpUrlOrNull() ?: return
        val host = httpUrl.host

        cm.setCookie(url, "cf_clearance=; Max-Age=0; Path=/")

        val parts = host.split('.')
        for (i in 0 until parts.size - 1) {
            val domain = parts.subList(i, parts.size).joinToString(".")
            if (domain.contains('.')) {
                cm.setCookie("${httpUrl.scheme}://$domain", "cf_clearance=; Max-Age=0; Domain=.$domain; Path=/")
                cm.setCookie("${httpUrl.scheme}://$domain", "cf_clearance=; Max-Age=0; Domain=$domain; Path=/")
            }
        }
        cm.flush()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveWithWebViewAutomatic(webViewUrl: String, cm: CookieManager) {
        withContext(Dispatchers.Main) {
            val webView = WebView(appContext)
            // Cloudflare challenge cookies must be accepted by the bypass WebView too.
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = resolveUserAgent(appPreferences)
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { cm.flush() }
            }
            webView.loadUrl(webViewUrl)
            for (i in 1..30) {
                delay(500)
                if (cm.getCookie(webViewUrl)?.contains("cf_clearance") == true) {
                    Log.d(TAG, "CF: Auto WebView success on iteration $i")
                    break
                }
            }
            webView.stopLoading()
            cm.flush()
            delay(200)
            webView.destroy()
        }
    }

    private suspend fun resolveWithWebViewManual(
        webViewUrl: String,
        siteUrl: String,
        cm: CookieManager
    ) {
        while (CloudflareBypassSignal.channel.tryReceive().isSuccess) {}

        val oldCfClearance = extractCfClearance(cm.getCookie(siteUrl))

        withContext(Dispatchers.Main) {
            val intent = Intent().apply {
                setClassName(appContext, "my.noveldokusha.webview.WebViewActivity")
                putExtra("url", webViewUrl)
                putExtra("isBypassMode", true)
                putExtra("oldCfClearance", oldCfClearance)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            appContext.startActivity(intent)
        }

        withTimeoutOrNull(180.seconds) {
            coroutineScope {
                val signalJob = launch { CloudflareBypassSignal.channel.receive() }
                val cookieJob = launch {
                    while (isActive) {
                        delay(1500)
                        cm.flush()
                        if (cm.getCookie(siteUrl)?.contains("cf_clearance") == true) break
                    }
                }
                select<Unit> {
                    signalJob.onJoin {}
                    cookieJob.onJoin {}
                }
                signalJob.cancel()
                cookieJob.cancel()
            }
        }
    }

    private fun extractCfClearance(cookies: String?): String {
        if (cookies.isNullOrEmpty()) return ""
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("cf_clearance=") }
            ?.removePrefix("cf_clearance=")
            ?: ""
    }

    private fun formatCookies(cookies: String?): String {
        if (cookies.isNullOrEmpty()) return ""
        return cookies.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")
    }

    private fun peekBodySafe(response: Response): String {
        return try { response.peekBody(65536).string() } catch (e: Exception) { "" }
    }
}
