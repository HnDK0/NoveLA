package my.noveldokusha.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class ScraperCookieJar : CookieJar {

    private val manager = CookieManager.getInstance().apply {
        setAcceptCookie(true)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // Полный URL нужен чтобы OkHttp проверил path при парсинге куки
        val cookieString = manager.getCookie(url.toString()) ?: return emptyList()

        return cookieString
            .split(";")
            .mapNotNull { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                Cookie.parse(url, trimmed)
            }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
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
            manager.setCookie(saveUrl, setCookieString)
        }
        // flush() один раз после батча
        manager.flush()
    }
}