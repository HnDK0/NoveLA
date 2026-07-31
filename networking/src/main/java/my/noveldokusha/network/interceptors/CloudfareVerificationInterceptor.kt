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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import timber.log.Timber
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

private val ERROR_CODES = listOf(HttpsURLConnection.HTTP_FORBIDDEN, HttpsURLConnection.HTTP_UNAVAILABLE, 429)
private const val TAG = "CloudflareInterceptor"
private const val MAX_MANUAL_ATTEMPTS = 2
private const val COOLDOWN_MS = 120_000L
private val MANUAL_TIMEOUT = 35.seconds

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

    // Хосты, где WebView-обход аварийно прерван фатальной ошибкой челленджа.
    // Интерцептор извлекает запись (remove) после мануального флоу и фейлит запрос
    // без бесполезного finalRetry.
    private val abortedHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun abort(host: String) {
        abortedHosts.add(host)
        channel.trySend(Unit)
    }

    fun clearAbort(host: String) {
        abortedHosts.remove(host)
    }

    // Извлекает запись об abort: true — хост был аварийно прерван.
    fun consumeAbort(host: String): Boolean = abortedHosts.remove(host)

    private val _bypassCompleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val bypassCompleted: SharedFlow<String> = _bypassCompleted

    fun notifyBypassCompleted(host: String) {
        _bypassCompleted.tryEmit(host)
    }

    // Эмитится на любом терминальном исходе попытки обхода (успех, give-up, таймаут)
    // и служит только для авто-закрытия WebViewActivity. replay=0 специально:
    // реплей-кэш отравил бы будущую сессию обхода того же хоста устаревшей эмиссией,
    // а Activity в мануальном флоу всегда подписана раньше терминального события.
    private val _bypassFinished = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val bypassFinished: SharedFlow<String> = _bypassFinished

    fun notifyBypassFinished(host: String) {
        _bypassFinished.tryEmit(host)
    }
}

internal class CloudFareVerificationInterceptor(
    @ApplicationContext private val appContext: Context,
    private val appPreferences: AppPreferences,
    private val connectionPool: ConnectionPool
) : Interceptor {

    private val hostLocks = ConcurrentHashMap<String, ReentrantLock>()
    private val resolvedDomains = ConcurrentHashMap<String, Boolean>()
    private val manualAttempts = ConcurrentHashMap<String, Int>()
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    // Сериализует видимые ручные обходы: один WebViewActivity за раз.
    // Общий channel CONFLATED будит только один receive(), поэтому при параллельных
    // флоу разных хостов (GlobalSourceSearch) сигнал Done/abort хоста A мог
    // разбудить флоу B. Очередь оставляет в канале единственного вейтера.
    private val manualBypassLock = ReentrantLock()

    private fun isOnCooldown(host: String): Boolean =
        cooldownUntil[host]?.let { System.currentTimeMillis() < it } ?: false

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
        Timber.e(
            "CF: challenge resp code=${response.code} cf-mitigated=${response.header("cf-mitigated")} " +
                "server=${response.header("Server")} bodyHead=${bodyPreview.take(160).replace("\n", " ")}"
        )

        response.close()

        val siteUrl = bufferedRequest.url.toString()
        val host = bufferedRequest.url.host

        val hostLock = hostLocks.getOrPut(host) { ReentrantLock() }
        return hostLock.withLock {
            val cookieManager = CookieManager.getInstance()
                ?: throw WebViewCookieManagerInitializationFailedException()
            val userAgent = originalRequest.header("User-Agent") ?: resolveUserAgent(appPreferences)

            val existingCookie = cookieManager.getCookie(siteUrl) ?: ""
            if (resolvedDomains.containsKey(host) || existingCookie.contains("cf_clearance")) {
                Timber.d( "CF: cf_clearance cached for $host, trying direct retry")
                val retryRequest = bufferedRequest.newBuilder()
                    .header("Cookie", formatCookies(existingCookie))
                    .header("User-Agent", userAgent)
                    .header("Cache-Control", "no-store")
                    .build()
                // HTTP/2-соединение, отдавшее челлендж, CF помечает отравленным и вешает
                // на нём потоки (Http2Stream$StreamTimeout на readTimeout). evictAll закрывает
                // idle-соединения пула, чтобы ретрай ушёл на свежем соединении.
                connectionPool.evictAll()
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

        // Cooldown после «решено в WebView, но приложению кука не помогла»:
        // повторный WebView в ближайшее время бесполезен — фейлимся быстро.
        if (isOnCooldown(host)) {
            Timber.d( "CF: $host on cooldown, failing fast without WebView")
            throw CloudfareVerificationBypassFailedException()
        }

        val autoConfirmed = runBlocking {
            withTimeoutOrNull(15_000) {
                resolveWithWebViewAutomatic(webViewUrl, cookieManager, userAgent)
            } ?: false
        }

        // Ретрай после скрытого авто-флоу оправдан, только если он подтвердил куку
        // probe-ом: иначе это гарантированный челлендж ещё на 30с readTimeout вхолостую.
        if (autoConfirmed) {
            val firstCookies = cookieManager.getCookie(siteUrl) ?: ""
            val firstRetryRequest = originalRequest.newBuilder()
                .header("Cookie", formatCookies(firstCookies))
                .header("User-Agent", userAgent)
                .header("Cache-Control", "no-store")
                .build()

            connectionPool.evictAll()
            val firstRetryResponse = chain.proceed(firstRetryRequest)

            if (isNotCloudflare(firstRetryResponse, peekBodySafe(firstRetryResponse))) {
                resolvedDomains[host] = true
                manualAttempts.remove(host)
                cooldownUntil.remove(host)
                CloudflareBypassSignal.notifyBypassCompleted(host)
                CloudflareBypassSignal.notifyBypassFinished(host)
                return firstRetryResponse
            }

            firstRetryResponse.close()
        }

        val attempts = manualAttempts.getOrDefault(host, 0)
        if (attempts >= MAX_MANUAL_ATTEMPTS) {
            Timber.e( "CF: Max manual attempts ($MAX_MANUAL_ATTEMPTS) reached for $host, giving up")
            manualAttempts.remove(host)
            cooldownUntil.remove(host)
            CloudflareBypassSignal.notifyBypassFinished(host)
            throw CloudfareVerificationBypassFailedException()
        }
        manualAttempts[host] = attempts + 1
        Timber.d( "CF: Step 2 - manual attempt ${attempts + 1}/$MAX_MANUAL_ATTEMPTS for $host, webViewUrl=$webViewUrl")

        manualBypassLock.withLock {
            clearCookiesForDomain(siteUrl, cookieManager)

            runBlocking(Dispatchers.IO) {
                resolveWithWebViewManual(webViewUrl, siteUrl, cookieManager, userAgent)
            }

            val manualHost = webViewUrl.toHttpUrlOrNull()?.host ?: host
            if (CloudflareBypassSignal.consumeAbort(manualHost)) {
                Timber.e("CF: Bypass aborted by WebView fatal error for $manualHost")
                throw CloudfareVerificationBypassFailedException()
            }
        }

        cookieManager.flush()
        val finalCookies = cookieManager.getCookie(siteUrl) ?: ""

        val finalRetryRequest = originalRequest.newBuilder()
            .header("Cookie", formatCookies(finalCookies))
            .header("User-Agent", userAgent)
            .header("Cache-Control", "no-store")
            .build()

        connectionPool.evictAll()
        val finalResponse = chain.proceed(finalRetryRequest)

        if (!isNotCloudflare(finalResponse, peekBodySafe(finalResponse))) {
            finalResponse.close()
            // Кука есть (WebView решил челлендж), но приложению не помогла:
            // ставим cooldown, чтобы не открывать бесполезный WebView повторно.
            if (finalCookies.contains("cf_clearance")) {
                cooldownUntil[host] = System.currentTimeMillis() + COOLDOWN_MS
                Timber.d( "CF: $host cooldown ${COOLDOWN_MS / 1000}s (cookie present but retry still challenged)")
            }
            CloudflareBypassSignal.notifyBypassFinished(host)
            throw CloudfareVerificationBypassFailedException()
        }

        resolvedDomains[host] = true
        manualAttempts.remove(host)
        cooldownUntil.remove(host)
        CloudflareBypassSignal.notifyBypassCompleted(host)
        CloudflareBypassSignal.notifyBypassFinished(host)
        return finalResponse
    }

    private val STATIC_EXTENSIONS = setOf("js", "css", "png", "jpg", "svg", "woff", "woff2", "ttf", "ico", "webp", "json", "txt", "lua")

    private fun isNotCloudflare(response: Response, body: String, skipStatic: Boolean = true): Boolean {
        val host = response.request.url.host

        // Статический shortcut честен только для обычных вызовов. В probe-режиме
        // (skipStatic=false) челлендж может выдаваться и на пути с расширением
        // (.json/.txt/.lua) — там решение принимаем только по маркерам.
        val pathExt = response.request.url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', "")?.lowercase()
        if (skipStatic && pathExt != null && pathExt in STATIC_EXTENSIONS) return true

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
    private suspend fun resolveWithWebViewAutomatic(
        webViewUrl: String, cm: CookieManager, userAgent: String
    ): Boolean = withContext(Dispatchers.Main) {
        val webView = WebView(appContext)
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgent
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        var probeConfirmed = false
        try {
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
                val cookies = cm.getCookie(webViewUrl) ?: ""
                if (cookies.contains("cf_clearance")) {
                    val probeOk = probeIsClear(webViewUrl, cookies, userAgent)
                    if (probeOk) {
                        Timber.d("CF: Auto WebView success on iteration $i")
                        probeConfirmed = true
                        break
                    }
                    Timber.d("CF: cf_clearance detected but probe failed, continuing")
                }
            }
        } finally {
            // destroy обязателен и при отмене корутины (withTimeoutOrNull), поэтому без
            // delay — suspend в finally не переживёт отмену.
            webView.stopLoading()
            cm.flush()
            webView.destroy()
        }
        probeConfirmed
    }

    private suspend fun resolveWithWebViewManual(
        webViewUrl: String,
        siteUrl: String,
        cm: CookieManager,
        userAgent: String
    ) {
        while (CloudflareBypassSignal.channel.tryReceive().isSuccess) {}
        webViewUrl.toHttpUrlOrNull()?.host?.let(CloudflareBypassSignal::clearAbort)

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

        val probeConfirmed = withTimeoutOrNull(MANUAL_TIMEOUT) {
            coroutineScope {
                val signalJob = launch { CloudflareBypassSignal.channel.receive() }
                val cookieJob = launch {
                    while (isActive) {
                        delay(1500)
                        cm.flush()
                        val cookies = cm.getCookie(siteUrl) ?: ""
                        if (cookies.contains("cf_clearance") &&
                            probeIsClear(siteUrl, cookies, userAgent)
                        ) {
                            Timber.d("CF: Manual bypass confirmed by probe")
                            break
                        }
                    }
                }
                select<Unit> {
                    signalJob.onJoin {}
                    cookieJob.onJoin {}
                }
                signalJob.cancel()
                cookieJob.cancel()
            }
        } != null

        if (!probeConfirmed) {
            Timber.d( "CF: Manual bypass timed out after ${MANUAL_TIMEOUT.inWholeSeconds}s")
            webViewUrl.toHttpUrlOrNull()?.host
                ?.let { CloudflareBypassSignal.notifyBypassFinished(it) }
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

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Валидирует cf_clearance: только если прямой запрос с кукой и UA
     * возвращает не-челлендж ответ — обход считается успешным.
     * Наличие куки само по себе не гарантирует прохождение Turnstile.
     */
    private suspend fun probeIsClear(url: String, cookies: String, userAgent: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
            .build()
        return suspendCancellableCoroutine { cont ->
            val call = probeClient.newCall(request)
            // Канселим активный probe при отмене корутины (withTimeoutOrNull/select),
            // иначе blocking-execute затягивал удержание hostLock до ~30с.
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.d( "CF: probe failed for $url: ${e.message}")
                    cont.resume(false) { _, _, _ -> }
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        // skipStatic=false: probe обязан проверять маркеры и на .json/.txt
                        cont.resume(isNotCloudflare(response, peekBodySafe(response), skipStatic = false)) { _, _, _ -> }
                    }
                }
            })
        }
    }
}