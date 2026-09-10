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
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import my.noveldokusha.core.LocaleManager
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.coreui.AppThemeProvider
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.interceptors.CloudflareBypassSignal
import my.noveldokusha.network.interceptors.PluginUARegistry
import my.noveldokusha.network.interceptors.resolveUserAgent
import my.noveldokusha.text_translator.domain.TranslationManager
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return super.attachBaseContext(null)
        super.attachBaseContext(LocaleManager.createAppLocaleContext(base))
    }

    @Inject lateinit var toasty: Toasty
    @Inject lateinit var themeProvider: AppThemeProvider
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var translationManager: TranslationManager

    private var currentTargetUrl: String = ""
    private var isBypassMode: Boolean = false
    private var oldCfClearance: String = ""
    private var hasAutoClosed: Boolean = false  // ponytail: dedup guard for auto-close paths
    private lateinit var webView: WebView
    private lateinit var translateBridge: NovelaTranslateBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntentExtras(intent)

        val host = Uri.parse(currentTargetUrl).host ?: ""
        val presetUA = PluginUARegistry.getPresetForHost(host)
            ?.let { PluginUARegistry.resolveUAString(it) }
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = presetUA ?: resolveUserAgent(appPreferences)
                setAllowFileAccess(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }

        // Cloudflare commonly relies on cookies set during challenge flows, so we must
        // explicitly accept them in this WebView instance.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        setContent {
            var isReady by remember { mutableStateOf(isBypassMode) }
            var currentUrl by remember { mutableStateOf(currentTargetUrl) }
            var isTranslated by remember { mutableStateOf(false) }
            val translationEnabled = !isBypassMode

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
                    // Инъекция JS-обвязки перевода только вне bypass-режима и только на http(s)-документы.
                    if (!isBypassMode && url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                        webView.evaluateJavascript(PAGE_TRANSLATOR_JS, null)
                        isTranslated = false
                        translateBridge.setActive(false)
                    }
                    // ponytail: auto-close on cf_clearance in bypass mode — server-set cookies
                    // are already in CookieManager by onPageFinished; Turnstile JS cookies
                    // are caught by the polling fallback below.
                    if (isBypassMode) autoCloseOnClearance()
                }

                // ✅ ИСПРАВЛЕНИЕ: логируем HTTP ошибки для диагностики
                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    Timber.w("HTTP error ${errorResponse?.statusCode} for ${request?.url}")
                    abortBypassIfFatal(
                        url = request?.url?.toString(),
                        isMainFrame = request?.isForMainFrame == true,
                        errorCode = null
                    )
                }

                // ✅ ИСПРАВЛЕНИЕ: логируем сетевые ошибки для диагностики
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Timber.e("Network error: code=${error?.errorCode}, desc=${error?.description}, url=${request?.url}")
                    abortBypassIfFatal(
                        url = request?.url?.toString(),
                        isMainFrame = request?.isForMainFrame == true,
                        errorCode = error?.errorCode
                    )
                }
            }

            // ponytail: fallback — close on interceptor's terminal signal (success/give-up/timeout).
            // This catches cases where autoCloseOnClearance didn't fire (e.g., signal arrived
            // before cf_clearance was set, or oldCfClearance comparison was stale).
            LaunchedEffect(Unit) {
                if (isBypassMode) {
                    val host = Uri.parse(currentTargetUrl).host ?: ""
                    CloudflareBypassSignal.bypassFinished
                        .filter { it == host }
                        .collect {
                            if (!isFinishing && !hasAutoClosed) {
                                CookieManager.getInstance().flush()
                                hasAutoClosed = true
                                finish()
                            }
                        }
                }
            }

            // ponytail: polling fallback for Turnstile JS cookies (set AFTER onPageFinished)
            // + update currentUrl for toolbar display
            LaunchedEffect(Unit) {
                while (true) {
                    // In bypass mode, auto-close on new clearance (catches JS-set cookies)
                    if (isBypassMode) autoCloseOnClearance()
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
                    onBackClicked = { if (!isFinishing) finish() },
                    onDoneClicked = {
                        if (!isFinishing) {
                            CookieManager.getInstance().flush()
                            CloudflareBypassSignal.channel.trySend(Unit)
                            hasAutoClosed = true
                            finish()
                        }
                    },
                    onReloadClicked = { webView.reload() },
                    onClearCookiesClicked = { hardResetSession() },
                    onCopyUrlClicked = { copyToClipboard(webView.url ?: currentUrl) },
                    translationEnabled = translationEnabled,
                    isTranslated = isTranslated,
                    onTranslateClicked = {
                        if (isTranslated) {
                            // Закрываем гейт моста до restore(): отказ летящего батча
                            // (устаревшей генерации) не должен показывать toast.
                            translateBridge.setActive(false)
                            webView.evaluateJavascript("window.__novelaPageTranslator.restore()", null)
                        } else {
                            // Целевой язык всегда из белого списка GOOGLE_TRANSLATE_LANGUAGES
                            // (или "en") — резолвер гарантирует отсутствие кавычек/небезопасных
                            // символов, поэтому интерполяция в JS-литерал в одинарных кавычках безопасна.
                            val target = TargetLanguageResolver.resolve(
                                appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value,
                                Locale.getDefault().language
                            )
                            // Микроокно (мс) между setActive(true) и исполнением start() в JS:
                            // гейт уже открыт, но перевод ещё не начался — вызов translateAsync
                            // в этом окне пропустится (ограничение проектирования, см. NovelaTranslateBridge).
                            translateBridge.setActive(true)
                            webView.evaluateJavascript("window.__novelaPageTranslator.start('$target')", null)
                        }
                        isTranslated = !isTranslated
                    }
                )
            }
        }

        // Мост JS→Kotlin→JS регистрируется безусловно (в bypass-режиме он просто
        // не используется — JS-обвязка в onPageFinished не инъецируется, кнопка скрыта).
        translateBridge = NovelaTranslateBridge(
            webView,
            translationManager,
            lifecycleScope,
            onError = { toasty.show("Translation failed") }
        )
        webView.addJavascriptInterface(translateBridge, "NovelaTranslate")
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

    // ponytail: extract cf_clearance value from cookie string, return empty if absent
    private fun extractCfClearance(cookies: String): String {
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("cf_clearance=") }
            ?.removePrefix("cf_clearance=")
            ?: ""
    }

    // ponytail: safe auto-close — correct order (flush → signal → finish), dedup, guard
    private fun autoCloseOnClearance() {
        if (isFinishing || hasAutoClosed || !isBypassMode) return
        val cookies = CookieManager.getInstance().getCookie(currentTargetUrl) ?: ""
        val newCf = extractCfClearance(cookies)
        if (newCf.isEmpty() || newCf == oldCfClearance) return
        hasAutoClosed = true
        val host = Uri.parse(currentTargetUrl).host ?: ""
        Timber.d("CF: cf_clearance detected in bypass WebView, auto-closing (host=$host)")
        CookieManager.getInstance().flush()
        CloudflareBypassSignal.notifyBypassFinished(host)
        CloudflareBypassSignal.channel.trySend(Unit)
        finish()
    }

    // Неустранимые сетевые ошибки главного фрейма: перезагрузка не поможет.
    private companion object {
        val FATAL_MAIN_FRAME_ERROR_CODES = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
        )
    }

    private fun isChallengePlatformUrl(url: String?): Boolean {
        val u = url ?: return false
        return u.contains("challenge-platform", ignoreCase = true) ||
            u.contains("challenges.cloudflare.com", ignoreCase = true)
    }

    // В bypass-режиме фатальная ошибка главного фрейма означает, что обход заведомо
    // не решается: аварийно закрываем WebView и фейлим запрос без ожидания таймаута.
    // Ошибки на сабресурсах challenge-platform (DNS, 401/403) — штатная часть Turnstile:
    // они игнорируются, иначе капча закрывалась бы до появления.
    private fun abortBypassIfFatal(url: String?, isMainFrame: Boolean, errorCode: Int?) {
        if (!isBypassMode || isFinishing) return
        val fatal = isMainFrame && (
            isChallengePlatformUrl(url) || errorCode in FATAL_MAIN_FRAME_ERROR_CODES
        )
        if (!fatal) return
        val host = Uri.parse(currentTargetUrl).host ?: return
        Timber.e("CF: Fatal challenge error, aborting bypass for $host (url=$url, code=$errorCode)")
        hasAutoClosed = true
        CloudflareBypassSignal.abort(host)
        finish()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
