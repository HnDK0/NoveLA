package my.noveldokusha.network.interceptors

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import timber.log.Timber
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

private val ERROR_CODES = listOf(HttpsURLConnection.HTTP_FORBIDDEN, HttpsURLConnection.HTTP_UNAVAILABLE, 429)
private const val TAG = "CloudflareInterceptor"
private const val MAX_MANUAL_ATTEMPTS = 3

private val CLOUDFLARE_WHITELIST = listOf(
    "github.com",
    "raw.githubusercontent.com"
)

data class CfDomainOptions(
    val whitelist: Boolean = false,
    val ignoreMarkers: Set<String> = emptySet()
)

object LuaCfOptionsRegistry {
    private val options = ConcurrentHashMap<String, CfDomainOptions>()

    fun register(domain: String, cfOptions: CfDomainOptions) {
        val key = domain.removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").trimEnd('/')
        options[key] = cfOptions
        Timber.d( "CF options registered for $key: $cfOptions")
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

    private val ALL_CF_MARKERS = listOf(
        "cf-challenge",
        "requireTurnstile",
        "action=\"/cdn-cgi/challenge-platform/",
        "onloadTurnstileCallback",
        "__cf_chl_",
        "but-captcha",
        "recaptcha-accessible-status",
        "cf-browser-verification",
        "cf-challenge-running",
        "cf-please-wait",
        "id=\"challenge-running\"",
        "id=\"cf-challenge-running\"",
        "ddos-guard.net",
        ".ddos-guard.net",
    )

    /**
     * URL-маркеры жёсткой блокировки IP Cloudflare (ошибки 1020/1015).
     * Когда авто-WebView уходит на такой URL, дальнейшая выпечка
     * cf_clearance бессмысленна — прерываем опрос сразу, не дожидаясь
     * полного таймаута в 15 с.
     */
    private val IP_BLOCKED_URL_MARKERS = listOf(
        "/cdn-cgi/error/",
        "error=1020",
        "error=1015",
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

        Timber.d( "CF: Challenge detected. URL: ${bufferedRequest.url}")

        return lock.withLock {
            response.close()

            val siteUrl = bufferedRequest.url.toString()
            val host = bufferedRequest.url.host
            val cookieManager = CookieManager.getInstance()
                ?: throw WebViewCookieManagerInitializationFailedException()
            val userAgent = resolveUserAgent(appPreferences)

            val existingCookie = cookieManager.getCookie(siteUrl) ?: ""
            if (resolvedDomains.contains(host) || existingCookie.contains("cf_clearance")) {
                Timber.d( "CF: cf_clearance cached for $host, trying direct retry")
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

        runBlocking {
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
            Timber.e( "CF: Max manual attempts ($MAX_MANUAL_ATTEMPTS) reached for $host, giving up")
            manualAttempts.remove(host)
            throw CloudfareVerificationBypassFailedException()
        }
        manualAttempts[host] = attempts + 1
        Timber.d( "CF: Step 2 - manual attempt ${attempts + 1}/$MAX_MANUAL_ATTEMPTS for $host, webViewUrl=$webViewUrl")

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

        val pathExt = response.request.url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', "")?.lowercase()
        if (pathExt != null && pathExt in STATIC_EXTENSIONS) return true

        if (CLOUDFLARE_WHITELIST.any { host.contains(it) }) return true

        val domainOptions = LuaCfOptionsRegistry.getForHost(host)
        if (domainOptions?.whitelist == true) return true

        if (response.header("cf-mitigated") == "challenge") return false

        val ignoredMarkers = domainOptions?.ignoreMarkers ?: emptySet()
        val activeMarkers = ALL_CF_MARKERS.filter { it !in ignoredMarkers }

        val hasMarkers = activeMarkers.any { body.contains(it, ignoreCase = true) }
        val isCfServer = response.header("Server")?.let {
            it.contains("cloudflare", true) || it.contains("ddos-guard", true)
        } == true

        val isCf = when {
            response.code == 200 -> hasMarkers && isCfServer
            response.code in ERROR_CODES -> hasMarkers && isCfServer
            else -> false
        }
        if (isCf) {
            val foundMarkers = activeMarkers.filter { body.contains(it, ignoreCase = true) }
            Timber.e( "CF triggered: code=${response.code} isCfServer=$isCfServer foundMarkers=$foundMarkers")
        }
        return !isCf
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
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = resolveUserAgent(appPreferences)
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(
                    view: WebView?, handler: SslErrorHandler?, error: SslError?
                ) {
                    Timber.e("CF WebView SSL error: ${error?.primaryError}")
                    handler?.cancel()
                }
                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    Timber.e("CF WebView error: code=${error?.errorCode}, url=${request?.url}")
                }
                override fun onPageFinished(view: WebView?, url: String?) { cm.flush() }
            }
            webView.loadUrl(webViewUrl)
            for (i in 1..30) {
                delay(500)
                val currentUrl = webView.url
                if (currentUrl != null && IP_BLOCKED_URL_MARKERS.any {
                        currentUrl.contains(it, ignoreCase = true)
                    }) {
                    Timber.d("CF: IP blocked ($currentUrl), aborting auto attempt")
                    break
                }
                if (cm.getCookie(webViewUrl)?.contains("cf_clearance") == true) {
                    delay(500)
                    val urlAfterCookie = webView.url
                    val stillOnChallenge = urlAfterCookie != null && (
                        urlAfterCookie.contains("__cf_chl", ignoreCase = true) ||
                        urlAfterCookie.contains("/cdn-cgi/challenge-platform", ignoreCase = true)
                    )
                    if (!stillOnChallenge) {
                        Timber.d( "CF: Auto WebView success on iteration $i")
                        break
                    }
                    Timber.d("CF: cf_clearance detected but still on challenge page, continuing")
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