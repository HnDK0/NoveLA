package my.noveldokusha.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import timber.log.Timber

class ScraperCookieJar : CookieJar {

    // Ленивая инициализация: CookieManager.getInstance() тянет за собой WebViewFactory
    // (загрузка webviewchromium ~сотни мс). Если делать это в field-инициализаторе,
    // WebView грузится на main при создании ScraperNetworkClient на старте приложения.
    // Лениво — инициализация уходит на первый куки-запрос (IO-поток, вне критического пути).
    // Best-effort: на устройстве со сломанным/отсутствующим WebView не роняем сетевые
    // запросы (раньше ошибка инициализации глоталась в App.onCreate).
    private val manager: CookieManager? by lazy {
        try {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }
        } catch (e: Exception) {
            Timber.e(e, "CookieManager init failed")
            null
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Полный URL нужен чтобы OkHttp проверил path при парсинге куки
        val cookieString = manager?.getCookie(url.toString()) ?: return emptyList()

        return cookieString
            .split(";")
            .mapNotNull { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                Cookie.parse(url, trimmed)
            }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = manager ?: return
        cookies.forEach { cookie ->
            // cookie.toString() возвращает только "name=value" без атрибутов.
            // Строим Set-Cookie строку вручную чтобы сохранить expires и domain,
            // иначе cf_clearance протухнет сразу после закрытия приложения.
            val setCookieString = buildString {
                append("${cookie.name}=${cookie.value}")

                if (cookie.domain.isNotEmpty()) {
                    append("; Domain=${cookie.domain}")
                }
                append("; Path=${cookie.path}")

                if (cookie.expiresAt != Long.MIN_VALUE && cookie.expiresAt != Long.MAX_VALUE) {
                    val date = java.util.Date(cookie.expiresAt)
                    val fmt = java.text.SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss zzz",
                        java.util.Locale.US
                    ).apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }
                    append("; Expires=${fmt.format(date)}")
                }

                if (cookie.secure) append("; Secure")
                if (cookie.httpOnly) append("; HttpOnly")
            }

            // Сохраняем на домен самой куки (может быть .example.com),
            // а не просто на host запроса — критично для cf_clearance
            val saveUrl = "${url.scheme}://${cookie.domain.trimStart('.')}"
            cm.setCookie(saveUrl, setCookieString)
        }
        // flush() один раз после батча
        cm.flush()
    }
}