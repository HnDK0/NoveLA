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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.domain.CloudfareVerificationBypassFailedException
import my.noveldokusha.core.domain.WebViewCookieManagerInitializationFailedException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ponytail: Cloudflare detection constants ported from the Paras fork. The
// Paras implementation is more thorough than the legacy NoveLA markers — it
// catches 202 bot-gates, 502 transient CF errors, DDoS-Guard challenge pages,
// the `cf-mitigated` header, and the modern Turnstile / "managed challenge"
// HTML markers that older code missed entirely.
private val ERROR_CODES = listOf(
    202 /*bot-gate*/,
    HttpsURLConnection.HTTP_FORBIDDEN /*403*/,
    429 /*Too Many Requests*/,
    HttpsURLConnection.HTTP_BAD_GATEWAY /*502 — transient CF edge errors*/,
    HttpsURLConnection.HTTP_UNAVAILABLE /*503*/,
)

/**
 * Header values for the `Server` response header that indicate the response
 * came from a Cloudflare edge node (not the origin server). Includes DDoS-Guard
 * which uses a similar challenge template and is bypassed by the same WebView
 * flow.
 */
private val SERVER_HEADER_VALUES = arrayOf(
    "cloudflare-nginx",
    "cloudflare",
    "cloudflare-iad",
    "ddos-guard",
    "ddos-guard.net",
)

/**
 * Response-header names whose mere presence indicates a Cloudflare
 * challenge / block. The `cf-mitigated` header in particular is set by
 * Cloudflare when a request is challenged or blocked, even on responses
 * that don't include the typical challenge HTML body.
 */
private val CHALLENGE_HEADER_NAMES = arrayOf(
    "cf-mitigated",
)

/**
 * Substrings that identify a Cloudflare / DDoS-Guard / similar challenge or
 * interstitial page in the response body. Cloudflare's "Just a moment…"
 * page always contains at least one of these markers. We fall back to body
 * inspection when the server header is missing or stripped, and to catch
 * "managed challenge" pages that return HTTP 200 (which the old code missed
 * entirely).
 *
 * The markers are a superset of the trawl project's `isCloudflarePage()`
 * detector, expanded with the legacy markers the previous NoveLA
 * implementation already handled so we don't regress on older CF challenge
 * templates.
 */
private val CHALLENGE_BODY_MARKERS = arrayOf(
    // Legacy NoveLA CF markers (kept for backwards compat with Lua plugins
    // that registered ignore_markers against these names).
    "cf-challenge",
    "turnstile",
    "requireTurnstile",
    "__cf_chl_",
    "but-captcha",
    "recaptcha-accessible-status",
    // Paras / trawl-derived markers — catch Turnstile, DDoS-Guard, and the
    // newer "managed challenge" templates that return HTTP 200.
    "cf-browser-verification",
    "cf-challenge-running",
    "/cdn-cgi/challenge-platform/",
    "cf-please-wait",
    "Just a moment",
    "Checking your browser",
    "cf_chl_opt",
    "cf-mitigated",
    "Attention Required! | Cloudflare",
    "challenge-platform",
    "ray id",
    "verify you are human",
    "enable javascript and cookies to continue",
    "one more step",
    "id=\"challenge-running\"",
    "id=\"cf-challenge-running\"",
    "id=\"turnstile-wrapper\"",
    "cf-turnstile",
    "challenges.cloudflare.com/turnstile",
    "ddos-guard.net",
    ".ddos-guard.net",
)

/**
 * Page-title substrings that indicate an active Cloudflare challenge.
 * Mirrors trawl's `CF_CHALLENGE_TITLE` regex. We check the WebView's title
 * against these (case-insensitive) during the polling loop — when the title
 * changes to something else, the challenge is done.
 */
private val CHALLENGE_TITLE_MARKERS = arrayOf(
    "just a moment",
    "verify you are human",
    "please wait",
    "one more step",
    "attention required",
    "checking your browser",
)

/**
 * URL substrings that indicate the IP itself has been blocked by Cloudflare
 * (error 1020) or rate-limited (error 1015). In these cases no amount of
 * cookie-baking will help — the challenge can't be solved from this IP.
 * We detect this and bail out fast instead of spinning through all retries.
 */
private val IP_BLOCKED_URL_MARKERS = arrayOf(
    "/cdn-cgi/error/",
    "error=1020",
    "error=1015",
)

/**
 * Maximum number of WebView-based bypass attempts per intercepted request.
 * Each attempt waits up to [MAX_CHALLENGE_WAIT] for the cf_clearance cookie
 * to appear before giving up and retrying.
 */
private const val MAX_BYPASS_ATTEMPTS = 3

/**
 * Hard cap on how long a single WebView challenge attempt may run.
 * Real challenges usually resolve in 2-6 seconds; 20 s is a generous ceiling
 * that also covers Turnstile interactive widgets on slow connections and
 * the "force re-navigation" fallback (which itself can take up to 8 s).
 */
private val MAX_CHALLENGE_WAIT = 20.seconds

/**
 * Polling interval used while waiting for the cf_clearance cookie to appear
 * inside the WebView. Tight enough to feel snappy on a fast site, loose
 * enough not to burn CPU on a slow one.
 */
private val CHALLENGE_POLL_INTERVAL = 300.milliseconds

/**
 * Once `cf_clearance` has been obtained, Cloudflare normally auto-redirects
 * to the original URL within 2-3 seconds. If it hasn't redirected after this
 * many milliseconds, we manually navigate the WebView to the original URL —
 * this is the same trick trawl's `challengeWait.ts` uses to break out of
 * "cookie set but page stuck on challenge" loops, which are common with
 * Turnstile's non-interactive mode.
 */
private val FORCE_NAVIGATION_DELAY = 5.seconds

/**
 * Legacy NoveLA limit on manual bypass attempts (via WebViewActivity).
 * Kept here so the manual fallback path stays bounded when the automatic
 * WebView bypass exhausts [MAX_BYPASS_ATTEMPTS].
 */
private const val MAX_MANUAL_ATTEMPTS = 3

// ponytail: global whitelist of hosts that never serve CF challenges. The
// legacy NoveLA list (github.com / raw.githubusercontent.com) is preserved
// because some Lua plugins fetch JSON catalogs from these hosts.
private val CLOUDFLARE_WHITELIST = listOf(
    "github.com",
    "raw.githubusercontent.com"
)

private const val TAG = "CloudflareInterceptor"

/**
 * JavaScript snippet injected into the WebView to attempt clicking the
 * Cloudflare Turnstile checkbox. Turnstile is the successor to the classic
 * "I'm not a robot" reCAPTCHA and is what most modern CF-protected sites
 * use. The checkbox is inside a cross-origin iframe from
 * `challenges.cloudflare.com`, so direct DOM access is blocked — but we
 * can still dispatch a click event at the iframe's on-page coordinates,
 * which Chrome will forward to the iframe as a user gesture.
 *
 * This is a best-effort approach: if the challenge is in "managed" mode
 * (non-interactive), it will resolve on its own and this JS is a no-op.
 * If it's in "interactive" mode, this click is what makes it pass.
 *
 * The snippet is idempotent and safe to evaluate repeatedly.
 */
private val TURNSTILE_CLICK_JS = """
    (function() {
        try {
            // Find any iframe whose src points at Cloudflare's challenge platform.
            var frames = document.querySelectorAll('iframe[src*="challenges.cloudflare.com"], iframe[src*="cdn-cgi/challenge-platform"]');
            for (var i = 0; i < frames.length; i++) {
                var f = frames[i];
                var rect = f.getBoundingClientRect();
                if (rect.width < 20 || rect.height < 20) continue;
                // Dispatch a real click at the centre of the iframe.
                var cx = rect.left + rect.width / 2;
                var cy = rect.top + rect.height / 2;
                var ev = new MouseEvent('click', {
                    bubbles: true, cancelable: true, view: window,
                    clientX: cx, clientY: cy
                });
                f.dispatchEvent(ev);
                // Also try a synthetic pointer event for newer Turnstile.
                var pe = new PointerEvent('pointerdown', {
                    bubbles: true, cancelable: true, view: window,
                    clientX: cx, clientY: cy, pointerId: 1
                });
                f.dispatchEvent(pe);
                return true;
            }
            // Fallback: look for an in-page Turnstile widget (no iframe).
            var widget = document.querySelector('.cf-turnstile, [data-sitekey]');
            if (widget) {
                var r = widget.getBoundingClientRect();
                widget.dispatchEvent(new MouseEvent('click', {
                    bubbles: true, cancelable: true, view: window,
                    clientX: r.left + r.width / 2, clientY: r.top + r.height / 2
                }));
                return true;
            }
        } catch (e) { /* swallow — best-effort */ }
        return false;
    })();
""".trimIndent()

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

/**
 * If a CloudFare security verification redirection is detected, execute a
 * WebView, wait for the challenge to resolve, harvest the cf_clearance cookie
 * (plus any other cookies Cloudflare set), then retry the original request.
 *
 * This file merges three lineages:
 *   1. The original NoveLA implementation (manual bypass via WebViewActivity,
 *      Lua plugin CfDomainOptions registry, the WebView singleton reuse
 *      "ponytail" optimization, and the Conflated channel for the
 *      WebViewActivity → interceptor handshake).
 *   2. The Paras fork's comprehensive automatic WebView bypass — better
 *      challenge detection (202/502/ddos-guard/cf-mitigated/managed-challenge),
 *      Turnstile click JS, title-based completion, force re-navigation when
 *      stuck, IP-block fast-fail, and up to MAX_BYPASS_ATTEMPTS retries.
 *   3. The trawl project's challengeWait.ts technique (cookie-set-but-stuck
 *      loop breaker, Turnstile iframe click, IP-block markers).
 *
 * Flow:
 *   1. intercept() peeks the response and classifies it via classifyResponse().
 *   2. If classified as Cloudflare, lock and run resolveWithWebViewAutomatic()
 *      up to MAX_BYPASS_ATTEMPTS times, re-issuing the original OkHttp request
 *      after each attempt. On success, cache the host and notify listeners.
 *   3. If all automatic attempts fail, fall back to the legacy manual path:
 *      resolveWithWebViewManual() launches WebViewActivity in bypass mode and
 *      waits for either the user to solve an interactive challenge or the
 *      cf_clearance cookie to appear.
 *   4. If the manual path also fails MAX_MANUAL_ATTEMPTS times, throw
 *      CloudfareVerificationBypassFailedException.
 */
internal class CloudFareVerificationInterceptor(
    @ApplicationContext private val appContext: Context,
    private val appPreferences: AppPreferences
) : Interceptor {

    private val lock = ReentrantLock()
    private val resolvedDomains = mutableSetOf<String>()
    private val manualAttempts = ConcurrentHashMap<String, Int>()

    // ponytail: reuse a single lazily-initialized WebView across Cloudflare challenges
    // instead of constructing a fresh WebView(appContext) on every challenge. Building a
    // WebView costs ~50–100ms plus ~10MB resident memory; on a single chapter download
    // with 5+ CF challenges this adds up quickly. The WebView is created on the Main thread
    // (callers already wrap resolveWithWebViewAutomatic in withContext(Dispatchers.Main))
    // and never destroyed — the interceptor is a singleton that lives for the app lifetime.
    // The CookieManager is shared globally, so cookies persist across loads automatically.
    @Volatile
    private var cfWebView: WebView? = null

    private fun getOrCreateCfWebView(cm: CookieManager): WebView {
        cfWebView?.let { return it }
        // Must be called on the Main thread — callers guarantee this via
        // withContext(Dispatchers.Main). Creating a WebView off-Main throws.
        val webView = WebView(appContext)
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // ponytail: ported from Paras fork — these settings make the
            // headless WebView behave more like a real browser during CF
            // challenges: enable database storage, block images (we don't
            // need them for a challenge), and force the UA to match the
            // OkHttp request so CF doesn't see two different browsers.
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            blockNetworkImage = true
            setSupportZoom(false)
            userAgentString = resolveUserAgent(appPreferences)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { cm.flush() }
        }
        cfWebView = webView
        return webView
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // ponytail: preserve the legacy NoveLA body-buffering behaviour —
        // if the request has a body, materialise it so we can replay the
        // request after a CF challenge. Without this, OkHttp would throw
        // when we tried to retry a POST whose InputStream was already
        // consumed.
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
        val challengeInfo = classifyResponse(response, peekBodySafe(response))
        if (!challengeInfo.isCloudflare) {
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

            // ponytail: legacy NoveLA fast-path — if we've already solved CF
            // for this host (or the cf_clearance cookie is already present),
            // retry the original request directly before spinning up a
            // WebView. This skips the WebView entirely for repeat requests
            // in the same session.
            val existingCookie = cookieManager.getCookie(siteUrl) ?: ""
            if (resolvedDomains.contains(host) || existingCookie.contains("cf_clearance")) {
                Log.d(TAG, "CF: cf_clearance cached for $host, trying direct retry")
                val retryRequest = bufferedRequest.newBuilder()
                    .header("Cookie", formatCookies(existingCookie))
                    .header("User-Agent", userAgent)
                    .build()
                val retryResponse = chain.proceed(retryRequest)
                val retryInfo = classifyResponse(retryResponse, peekBodySafe(retryResponse))
                if (!retryInfo.isCloudflare) {
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
        originalRequest: Request,
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

        // ponytail: ported from Paras fork — try the automatic WebView bypass
        // up to MAX_BYPASS_ATTEMPTS times. Each attempt primes the WebView
        // with cookies, polls for cf_clearance / title change / URL change,
        // and re-issues the OkHttp request. A single transient CF hiccup
        // no longer kills the entire fetch.
        var lastFailure: Exception? = null
        repeat(MAX_BYPASS_ATTEMPTS) { attempt ->
            try {
                runBlocking(Dispatchers.Main) {
                    withTimeoutOrNull(MAX_CHALLENGE_WAIT.inWholeMilliseconds) {
                        resolveWithWebViewAutomatic(webViewUrl, cookieManager, originalRequest)
                    }
                }

                val retryRequest = originalRequest.newBuilder()
                    .header("Cookie", formatCookies(cookieManager.getCookie(siteUrl) ?: ""))
                    .header("User-Agent", userAgent)
                    .build()
                val retryResponse = chain.proceed(retryRequest)
                val retryInfo = classifyResponse(retryResponse, peekBodySafe(retryResponse))
                if (!retryInfo.isCloudflare) {
                    resolvedDomains.add(host)
                    manualAttempts.remove(host)
                    CloudflareBypassSignal.notifyBypassCompleted(host)
                    return retryResponse
                }
                retryResponse.close()
                lastFailure = CloudfareVerificationBypassFailedException(
                    "Attempt ${attempt + 1}/$MAX_BYPASS_ATTEMPTS: " +
                        "still challenged after WebView bypass"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastFailure = e
            }
        }

        // ponytail: legacy NoveLA manual fallback — launch WebViewActivity so
        // the user can solve interactive challenges (e.g. CF Turnstile
        // checkbox, hCaptcha). The activity signals back via
        // CloudflareBypassSignal.channel; we also poll the cookie jar in
        // case the activity closes without an explicit signal but the
        // cookie has already been set.
        Log.d(TAG, "CF: Automatic bypass exhausted ($MAX_BYPASS_ATTEMPTS attempts), falling back to manual WebViewActivity")

        val attempts = manualAttempts.getOrDefault(host, 0)
        if (attempts >= MAX_MANUAL_ATTEMPTS) {
            Log.e(TAG, "CF: Max manual attempts ($MAX_MANUAL_ATTEMPTS) reached for $host, giving up")
            manualAttempts.remove(host)
            throw lastFailure ?: CloudfareVerificationBypassFailedException(
                "Cloudflare bypass failed after $MAX_BYPASS_ATTEMPTS automatic + " +
                    "$MAX_MANUAL_ATTEMPTS manual attempts"
            )
        }
        manualAttempts[host] = attempts + 1
        Log.d(TAG, "CF: Manual attempt ${attempts + 1}/$MAX_MANUAL_ATTEMPTS for $host, webViewUrl=$webViewUrl")

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
        val finalInfo = classifyResponse(finalResponse, peekBodySafe(finalResponse))

        if (finalInfo.isCloudflare) {
            finalResponse.close()
            throw lastFailure ?: CloudfareVerificationBypassFailedException()
        }

        resolvedDomains.add(host)
        manualAttempts.remove(host)
        CloudflareBypassSignal.notifyBypassCompleted(host)
        return finalResponse
    }

    // ponytail: ported from Paras fork — static file extensions that never
    // participate in CF challenges. Peeking their bodies is wasteful and
    // occasionally breaks streaming responses.
    private val STATIC_EXTENSIONS = setOf(
        "js", "css", "png", "jpg", "svg", "woff", "woff2", "ttf", "ico", "webp", "json", "txt", "lua"
    )

    /**
     * A response is "Cloudflare" (or a similar bot-protection interstitial)
     * if ANY of the following is true:
     *
     *   1. The response carries a header named in [CHALLENGE_HEADER_NAMES]
     *      (e.g. `cf-mitigated`). This is the strongest signal — CF sets
     *      it even on non-HTML responses.
     *   2. The HTTP status code is in [ERROR_CODES] AND the `Server` header
     *      looks like Cloudflare (or DDoS-Guard).
     *   3. The HTTP status code is in [ERROR_CODES] OR 200, AND the body
     *      contains one of the [CHALLENGE_BODY_MARKERS] substrings. This
     *      catches the 200-status "managed challenge" pages that newer
     *      Cloudflare configs serve, as well as 403/503 pages where the
     *      Server header was stripped.
     *
     * NoveLA additions over the Paras fork:
     *   - Honours [LuaCfOptionsRegistry] so a Lua plugin can whitelist a
     *     host entirely or selectively ignore certain markers (e.g. a
     *     plugin that legitimately contains the word "turnstile" in its
     *     HTML body can ask the interceptor to skip the "turnstile" marker).
     *   - Skips static file extensions entirely.
     *   - Keeps the legacy [CLOUDFLARE_WHITELIST] for github.com etc.
     */
    private fun classifyResponse(response: Response, bodyPreview: String): ChallengeInfo {
        val host = response.request.url.host

        // Статические файлы — никогда не CF-челлендж
        val pathExt = response.request.url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', "")?.lowercase()
        if (pathExt != null && pathExt in STATIC_EXTENSIONS) {
            return ChallengeInfo(isCloudflare = false)
        }

        // Глобальный whitelist
        if (CLOUDFLARE_WHITELIST.any { host.contains(it) }) {
            return ChallengeInfo(isCloudflare = false)
        }

        // Настройки от Lua плагина для этого домена
        val domainOptions = LuaCfOptionsRegistry.getForHost(host)
        if (domainOptions?.whitelist == true) {
            return ChallengeInfo(isCloudflare = false)
        }
        val ignoredMarkers = domainOptions?.ignoreMarkers ?: emptySet()
        val activeBodyMarkers = CHALLENGE_BODY_MARKERS.filter { it !in ignoredMarkers }

        val code = response.code

        // (1) Header presence check — strongest signal, works on any body.
        for (headerName in CHALLENGE_HEADER_NAMES) {
            if (response.header(headerName) != null) {
                return ChallengeInfo(isCloudflare = true)
            }
        }

        val serverHeader = response.header("Server")
        val serverLooksLikeCloudflare = serverHeader != null &&
            SERVER_HEADER_VALUES.any { serverHeader.equals(it, ignoreCase = true) }

        // (2) Status code + Server header check.
        if (code in ERROR_CODES && serverLooksLikeCloudflare) {
            return ChallengeInfo(isCloudflare = true)
        }

        // (3) Body-based fallback — catches 200-status managed challenge
        // pages and 403/503 pages where the Server header was stripped.
        val hasBodyMarker = bodyPreview.isNotBlank() &&
            activeBodyMarkers.any { bodyPreview.contains(it, ignoreCase = true) }
        val isErrorOrManaged = code in ERROR_CODES || (code == 200 && hasBodyMarker)
        val isCfServer = serverLooksLikeCloudflare
        if (isErrorOrManaged && (isCfServer || hasBodyMarker)) {
            val foundMarkers = activeBodyMarkers.filter { bodyPreview.contains(it, ignoreCase = true) }
            Log.e(TAG, "CF triggered: code=$code isCfServer=$isCfServer foundMarkers=$foundMarkers")
            return ChallengeInfo(isCloudflare = true, bodyText = bodyPreview)
        }
        return ChallengeInfo(isCloudflare = false)
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

    /**
     * Automatic WebView bypass — ported from Paras fork with the NoveLA
     * WebView singleton reuse optimisation layered on top.
     *
     * Polls for challenge completion via three signals (whichever fires first):
     *   (a) the `cf_clearance` cookie appears in the CookieManager, OR
     *   (b) the page title changes away from a known challenge title
     *       ("just a moment", "verify you are human", …), OR
     *   (c) the page URL changes away from the challenge URL.
     *
     * If `cf_clearance` has been set but the page is still on the challenge
     * URL after [FORCE_NAVIGATION_DELAY], manually re-navigate — this is
     * the trawl trick that breaks "cookie set but page stuck" loops common
     * with Turnstile non-interactive mode.
     *
     * If the page navigates to a URL matching [IP_BLOCKED_URL_MARKERS],
     * abort immediately with a helpful error message — no amount of
     * cookie-baking will fix a hard IP block.
     *
     * Every 3 seconds, inject [TURNSTILE_CLICK_JS] to dispatch a click at
     * any Turnstile iframe. For interactive Turnstile widgets this is what
     * actually solves the challenge; for non-interactive ("managed")
     * widgets it's a harmless no-op.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveWithWebViewAutomatic(
        webViewUrl: String,
        cm: CookieManager,
        originalRequest: Request,
    ) {
        withContext(Dispatchers.Main) {
            // ponytail: reuse the lazily-created singleton WebView instead of constructing
            // and destroying one per challenge. Only loadUrl() and the polling loop run here.
            val webView = getOrCreateCfWebView(cm)
            // Clear any leftover page state from a previous challenge before navigating
            // to the new URL — keeps the JS context clean without recreating the WebView.
            webView.stopLoading()

            // ponytail: ported from Paras fork — forward all relevant headers
            // from the OkHttp request to the WebView so its fingerprint matches
            // the request that triggered the challenge. Mismatched Accept-Language
            // or Sec-CH-UA is a common cause of "infinite challenge loops".
            val headersMap = mutableMapOf<String, String>()
            originalRequest.header("Accept")?.let { headersMap["Accept"] = it }
            originalRequest.header("Accept-Language")?.let { headersMap["Accept-Language"] = it }
            originalRequest.header("Accept-Encoding")?.let { headersMap["Accept-Encoding"] = it }
            originalRequest.header("Referer")?.let { headersMap["Referer"] = it }
            originalRequest.header("Sec-CH-UA")?.let { headersMap["Sec-CH-UA"] = it }
            originalRequest.header("Sec-CH-UA-Mobile")?.let { headersMap["Sec-CH-UA-Mobile"] = it }
            originalRequest.header("Sec-CH-UA-Platform")?.let { headersMap["Sec-CH-UA-Platform"] = it }
            originalRequest.header("Sec-Fetch-Dest")?.let { headersMap["Sec-Fetch-Dest"] = it }
            originalRequest.header("Sec-Fetch-Mode")?.let { headersMap["Sec-Fetch-Mode"] = it }
            originalRequest.header("Sec-Fetch-Site")?.let { headersMap["Sec-Fetch-Site"] = it }

            webView.loadUrl(webViewUrl, headersMap)

            val deadline = System.currentTimeMillis() + MAX_CHALLENGE_WAIT.inWholeMilliseconds
            var cfClearanceAt: Long? = null
            var lastClickAttempt = 0L
            var lastUrl: String? = webViewUrl

            try {
                while (System.currentTimeMillis() < deadline) {
                    delay(CHALLENGE_POLL_INTERVAL)

                    // (c) IP-block fast-fail.
                    val currentUrl = runCatching { webView.url }.getOrNull()
                    if (currentUrl != null && IP_BLOCKED_URL_MARKERS.any {
                            currentUrl.contains(it, ignoreCase = true)
                        }) {
                        throw CloudfareVerificationBypassFailedException(
                            "IP blocked by Cloudflare (error 1020/1015). " +
                                "Try a different network."
                        )
                    }

                    // (a) Check if cf_clearance cookie is now present.
                    val currentCookies = cm.getCookie(webViewUrl) ?: ""
                    if (currentCookies.contains("cf_clearance")) {
                        if (cfClearanceAt == null) {
                            cfClearanceAt = System.currentTimeMillis()
                        }
                        // Give CF 500ms to fire its auto-redirect after the
                        // cookie lands. If it doesn't, fall through to the
                        // force-navigation check below.
                        delay(500)
                        val urlAfterCookie = runCatching { webView.url }.getOrNull()
                        if (urlAfterCookie == null ||
                            (!urlAfterCookie.contains("challenge", ignoreCase = true) &&
                                !urlAfterCookie.contains("__cf_chl", ignoreCase = true) &&
                                !urlAfterCookie.contains("cdn-cgi/challenge-platform", ignoreCase = true))
                        ) {
                            break
                        }
                    }

                    // Force re-navigation: cf_clearance has been set but
                    // the page is still on the challenge URL. Manually
                    // load the original URL — this is the trawl trick that
                    // breaks "cookie set but page stuck" loops.
                    if (cfClearanceAt != null &&
                        System.currentTimeMillis() - cfClearanceAt!! >=
                        FORCE_NAVIGATION_DELAY.inWholeMilliseconds
                    ) {
                        runCatching { webView.loadUrl(webViewUrl, headersMap) }
                        delay(1_000)  // give the navigation a moment to land
                        break
                    }

                    // (b) Title-based completion: if the title is no
                    // longer a challenge title, we're done. This catches
                    // challenges that set the cookie via a redirect that
                    // doesn't change the visible URL.
                    val title = runCatching { webView.title }.getOrNull() ?: ""
                    if (title.isNotBlank() &&
                        CHALLENGE_TITLE_MARKERS.none {
                            title.contains(it, ignoreCase = true)
                        } &&
                        !currentUrl.isNullOrBlank() &&
                        currentUrl != webViewUrl &&
                        !currentUrl.contains("challenge", ignoreCase = true) &&
                        !currentUrl.contains("__cf_chl", ignoreCase = true) &&
                        !currentUrl.contains("cdn-cgi/challenge-platform", ignoreCase = true)
                    ) {
                        // Title looks legit and URL has changed — give the
                        // cookie a moment to settle, then break.
                        delay(300)
                        break
                    }

                    // (c) URL changed away from challenge URL (legacy
                    // signal — kept for compatibility with CF configs that
                    // don't change the title).
                    if (currentUrl != null && currentUrl != lastUrl &&
                        !currentUrl.contains("challenge", ignoreCase = true) &&
                        !currentUrl.contains("__cf_chl", ignoreCase = true) &&
                        !currentUrl.contains("cdn-cgi/challenge-platform", ignoreCase = true) &&
                        IP_BLOCKED_URL_MARKERS.none {
                            currentUrl.contains(it, ignoreCase = true)
                        }
                    ) {
                        lastUrl = currentUrl
                        // Give the cookie a brief moment to be set after
                        // the redirect lands.
                        delay(300)
                        break
                    }

                    // Attempt to click the Turnstile checkbox every 3 s.
                    val now = System.currentTimeMillis()
                    if (now - lastClickAttempt >= 3_000) {
                        lastClickAttempt = now
                        runCatching { webView.evaluateJavascript(TURNSTILE_CLICK_JS, null) }
                    }
                }
            } finally {
                runCatching { webView.stopLoading() }
                cm.flush()
                delay(200)
                // Intentionally do NOT destroy the WebView — it is reused on the next challenge.
            }
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

    private data class ChallengeInfo(
        val isCloudflare: Boolean,
        val bodyText: String? = null,
    )
}
