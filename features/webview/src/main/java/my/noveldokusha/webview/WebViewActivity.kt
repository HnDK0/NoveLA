package my.noveldokusha.webview

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.net.http.SslError
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.coreui.AppThemeProvider
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.interceptors.CloudflareBypassSignal
import my.noveldokusha.network.interceptors.resolveUserAgent
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    @Inject lateinit var toasty: Toasty
    @Inject lateinit var themeProvider: AppThemeProvider
    @Inject lateinit var appPreferences: AppPreferences

    private var currentTargetUrl: String = ""
    private var isBypassMode: Boolean = false
    private var oldCfClearance: String = ""
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntentExtras(intent)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = resolveUserAgent(appPreferences)
                setAllowFileAccess(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }

        // Cloudflare commonly relies on cookies set during challenge flows, so we must
        // explicitly accept them in this WebView instance.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        setContent {
            var isReady by remember { mutableStateOf(false) }
            var currentUrl by remember { mutableStateOf(currentTargetUrl) }
            var pageLoadedOnce by remember { mutableStateOf(false) }

            webView.webViewClient = object : WebViewClient() {

                // ✅ ИСПРАВЛЕНИЕ: обработка SSL-ошибок (handshake failed, net_error -100)
                // Без этого WebView молча отменяет запрос при любой проблеме с сертификатом,
                // что особенно актуально при обходе Cloudflare.
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    Timber.e("SSL error: ${error?.primaryError}, cancelling request")
                    handler?.cancel()
                    toasty.show("Secure connection failed")
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    val scheme = request.url.scheme?.lowercase()
                    return when (scheme) {
                        "http", "https" -> false
                        else -> {
                            Timber.d("Ignoring unsupported scheme: $url")
                            true
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                    if (cookies.contains("cf_clearance")) {
                        Timber.d("CF Cookie detected!")
                        pageLoadedOnce = true
                    }
                }

                // ✅ ИСПРАВЛЕНИЕ: логируем HTTP ошибки для диагностики
                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    Timber.w("HTTP error ${errorResponse?.statusCode} for ${request?.url}")
                }

                // ✅ ИСПРАВЛЕНИЕ: логируем сетевые ошибки для диагностики
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Timber.e("Network error: code=${error?.errorCode}, desc=${error?.description}, url=${request?.url}")
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    val cookies = CookieManager.getInstance().getCookie(currentTargetUrl) ?: ""
                    val currentCfClearance = cookies.split(";")
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("cf_clearance=") }
                        ?.removePrefix("cf_clearance=")
                        ?: ""
                    if (currentCfClearance.isNotEmpty() && currentCfClearance != oldCfClearance) {
                        isReady = true
                        if (isBypassMode && pageLoadedOnce) {
                            delay(500)
                            CookieManager.getInstance().flush()
                            CloudflareBypassSignal.channel.trySend(Unit)
                            val host = Uri.parse(currentTargetUrl).host ?: ""
                            if (host.isNotEmpty()) {
                                CloudflareBypassSignal.notifyBypassCompleted(host)
                            }
                            finish()
                            return@LaunchedEffect
                        }
                    }
                    webView.url?.let { currentUrl = it }
                    delay(500)
                }
            }

            Theme(themeProvider = themeProvider) {
                WebViewScreen(
                    toolbarTitle = currentUrl,
                    isReady = isReady,
                    webViewFactory = { webView },
                    onNavigateToUrl = { url -> webView.loadUrl(url) },
                    onBackClicked = { finish() },
                    onDoneClicked = {
                        CookieManager.getInstance().flush()
                        CloudflareBypassSignal.channel.trySend(Unit)
                        val host = Uri.parse(currentTargetUrl).host ?: ""
                        if (host.isNotEmpty()) {
                            CloudflareBypassSignal.notifyBypassCompleted(host)
                        }
                        finish()
                    },
                    onReloadClicked = { webView.reload() },
                    onClearCookiesClicked = { hardResetSession() },
                    onCopyUrlClicked = { copyToClipboard(webView.url ?: currentUrl) }
                )
            }
        }

        webView.loadUrl(currentTargetUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntentExtras(intent)
        val url = intent.data?.toString()
            ?: intent.getStringExtra("url")
            ?: return
        Timber.d("onNewIntent: loading $url")
        currentTargetUrl = url
        webView.loadUrl(url)
    }

    private fun readIntentExtras(intent: Intent) {
        currentTargetUrl = intent.getStringExtra("url") ?: intent.data?.toString().orEmpty()
        isBypassMode = intent.getBooleanExtra("isBypassMode", false)
        oldCfClearance = intent.getStringExtra("oldCfClearance") ?: ""
    }

    private fun hardResetSession() {
        CookieManager.getInstance().removeAllCookies {
            webView.clearCache(true)
            WebStorage.getInstance().deleteAllData()
            webView.loadUrl(currentTargetUrl)
            toasty.show("Session cleared")
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("URL", text)
        clipboard.setPrimaryClip(clip)
        toasty.show("Link copied")
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
