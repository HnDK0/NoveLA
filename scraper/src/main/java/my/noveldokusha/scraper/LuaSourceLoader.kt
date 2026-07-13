package my.noveldokusha.scraper

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import my.noveldokusha.core.ExtensionManager
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.postRequest
import my.noveldokusha.scraper.configs.SourceMetadata
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.luaj.vm2.*
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.ThreeArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JsePlatform
import org.yaml.snakeyaml.Yaml
import androidx.core.os.ConfigurationCompat
import timber.log.Timber
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import android.util.LruCache


// =============================================================================
// LuaEngine
// =============================================================================

@Singleton
class LuaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkClient: NetworkClient
) {
    private val gson = Gson()

    /**
     * Создаёт globals с минимально необходимым набором библиотек.
     * Удаляет глобалы, дающие RCE / доступ к файловой системе / загрузку произвольного кода,
     * чтобы внешние Lua-плагины не могли выйти из песочницы.
     */
    private fun createSandboxGlobals(): Globals {
        val globals = JsePlatform.standardGlobals()
        // Полностью удаляем опасные библиотеки/функции
        globals.set("luajava", LuaValue.NIL)
        globals.set("io", LuaValue.NIL)
        globals.set("load", LuaValue.NIL)
        globals.set("loadfile", LuaValue.NIL)
        globals.set("loadstring", LuaValue.NIL)
        globals.set("dofile", LuaValue.NIL)
        globals.set("require", LuaValue.NIL)
        globals.set("package", LuaValue.NIL)
        globals.set("debug", LuaValue.NIL)
        // Из os оставляем только безопасные хелперы (os.time, os.date, ...),
        // убираем всё, что даёт доступ к ОС/файлам.
        (globals.get("os") as? LuaTable)?.apply {
            set("execute", LuaValue.NIL)
            set("getenv", LuaValue.NIL)
            set("rename", LuaValue.NIL)
            set("remove", LuaValue.NIL)
            set("tmpname", LuaValue.NIL)
        }
        return globals
    }

    suspend fun loadScript(luaCode: String): LuaValue = withContext(Dispatchers.IO) {
        val globals = createSandboxGlobals()
        registerApi(globals)
        val result = withTimeout(10_000) { globals.load(luaCode).call() }
        // Если скрипт вернул таблицу (return { ... }) — используем её,
        // иначе — функции объявлены как глобальные (function name() ... end)
        if (result.istable()) result else globals
    }

    suspend fun loadFromScript(scriptContent: String, iconUrl: String? = null): SourceInterface.Catalog {
        val globals = createSandboxGlobals()
        registerApi(globals)
        val result = withTimeout(10_000) { globals.load(scriptContent).call() }
        val luaScript = if (result.istable()) result else globals
        return createLuaSourceAdapter(context, luaScript, this, iconUrl, null)
    }

    suspend fun loadFromScriptWithFileName(scriptContent: String, fileName: String, iconUrl: String? = null): SourceInterface.Catalog {
        val globals = createSandboxGlobals()
        registerApi(globals)
        val result = withTimeout(10_000) { globals.load(scriptContent).call() }
        val luaScript = if (result.istable()) result else globals
        return createLuaSourceAdapter(context, luaScript, this, iconUrl, fileName)
    }

    private fun registerApi(g: Globals) {
        // HTTP
        g.set("http_get",               HttpGetFunction()              as LuaValue)
        g.set("http_post",              HttpPostFunction()             as LuaValue)
        // Cookies & Prefs
        g.set("get_cookies",            GetCookiesFunction()           as LuaValue)
        g.set("set_cookies",            SetCookiesFunction()           as LuaValue)
        g.set("get_preference",         GetPreferenceFunction()        as LuaValue)
        g.set("set_preference",         SetPreferenceFunction()        as LuaValue)
        // Crypto
        g.set("aes_decrypt",            AesDecryptFunction()           as LuaValue)
        g.set("base64_decode",          Base64DecodeFunction()         as LuaValue)
        g.set("base64_encode",          Base64EncodeFunction()         as LuaValue)
        // HTML
        g.set("html_parse",             HtmlParseFunction()            as LuaValue)
        g.set("html_select",            HtmlSelectFunction()           as LuaValue)
        g.set("html_select_first",      HtmlSelectFirstFunction()      as LuaValue)
        g.set("html_attr",              HtmlAttrFunction()             as LuaValue)
        g.set("html_text",              HtmlTextFunction()             as LuaValue)
        g.set("html_remove",            HtmlRemoveFunction()           as LuaValue)
        g.set("http_get_batch",         HttpGetBatchFunction()         as LuaValue)
        // URL
        g.set("url_encode",             UrlEncodeFunction()            as LuaValue)
        g.set("url_encode_charset",     UrlEncodeCharsetFunction()     as LuaValue)
        g.set("url_resolve",            UrlResolveFunction()           as LuaValue)
        // String utils
        g.set("regex_match",            RegexMatchFunction()           as LuaValue)
        g.set("regex_replace",          RegexReplaceFunction()         as LuaValue)
        g.set("string_normalize",       StringNormalizeFunction()      as LuaValue)
        g.set("string_split",           StringSplitFunction()          as LuaValue)
        g.set("string_trim",            StringTrimFunction()           as LuaValue)
        g.set("string_starts_with",     StringStartsWithFunction()     as LuaValue)
        g.set("string_ends_with",       StringEndsWithFunction()       as LuaValue)
        g.set("string_clean",           StringCleanFunction()          as LuaValue)
        g.set("unescape_unicode",       UnescapeUnicodeFunction()      as LuaValue)
        // JSON
        g.set("json_parse",             JsonParseFunction()            as LuaValue)
        g.set("json_stringify",         JsonStringifyFunction()        as LuaValue)
        // Misc
        g.set("detect_pagination",      DetectPaginationFunction()     as LuaValue)
        g.set("sleep",                  SleepFunction()                as LuaValue)
        g.set("log_info",               LogInfoFunction()              as LuaValue)
        g.set("log_error",              LogErrorFunction()             as LuaValue)
        g.set("base64_encode",          Base64EncodeFunction()         as LuaValue)
        g.set("os_time",                OsTimeFunction()               as LuaValue)
    }


    // ── HTTP ──────────────────────────────────────────────────────────────────

    /**
     * http_get(url [, config])
     * config = { headers = {}, charset = "UTF-8" }
     * returns { success, body, code }
     */
    private inner class HttpGetFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = runBlocking {
            val url           = a1.checkjstring()
            if (!isSsrfSafe(url)) return@runBlocking ssrfErrorTable(url)
            val config        = if (a2.istable()) a2.checktable() else LuaTable()
            val pluginHeaders = convertHeaders(config.get("headers").opttable(LuaTable()))
            val charset       = config.get("charset").optjstring("UTF-8")
            val headers       = defaultHeaders(url) + pluginHeaders
            try {
                withContext(Dispatchers.IO) {
                    networkClient.getWithHeaders(url, headers).use { r ->
                        val bytes = r.body.bytes()
                        val body  = String(bytes, java.nio.charset.Charset.forName(charset))
                        responseTable(r.isSuccessful, body, r.code)
                    }
                }
            } catch (e: CancellationException) {
                Timber.e(e, "http_get cancelled: $url")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "http_get failed: $url")
                errorTable(e)
            }
        }
    }

    /**
     * http_post(url, body [, config])
     * config = { headers = {}, charset = "UTF-8" }
     * returns { success, body, code }
     */
    private inner class HttpPostFunction : ThreeArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue, a3: LuaValue): LuaValue = runBlocking {
            val url           = a1.checkjstring()
            if (!isSsrfSafe(url)) return@runBlocking ssrfErrorTable(url)
            val bodyStr       = a2.checkjstring()
            val config        = if (a3.istable()) a3.checktable() else LuaTable()
            val pluginHeaders = convertHeaders(config.get("headers").opttable(LuaTable()))
            val charset       = config.get("charset").optjstring("UTF-8")
            val headers       = defaultHeaders(url) + pluginHeaders
            try {
                withContext(Dispatchers.IO) {
                    val mediaType = (headers["Content-Type"] ?: detectContentType(bodyStr)).toMediaType()
                    val body = bodyStr.toRequestBody(mediaType)
                    networkClient.call(postRequest(url, body = body, headers = headers.toHeaders())).use { r ->
                        val bytes = r.body.bytes()
                        val s     = String(bytes, java.nio.charset.Charset.forName(charset))
                        responseTable(r.isSuccessful, s, r.code)
                    }
                }
            } catch (e: CancellationException) {
                Timber.e(e, "http_post cancelled: $url")
                throw e
            } catch (e: Exception) {
                Timber.e(e, "http_post failed: $url")
                errorTable(e)
            }
        }
    }

    private fun responseTable(success: Boolean, body: String, code: Int) = LuaTable().also { t ->
        t.set("success", LuaValue.valueOf(success))
        t.set("body",    LuaValue.valueOf(body))
        t.set("code",    LuaValue.valueOf(code))
    }

    private fun errorTable(e: Exception) = LuaTable().also { t ->
        t.set("success", LuaValue.FALSE)
        t.set("body",    LuaValue.valueOf(e.message ?: "Unknown error"))
        t.set("code",    LuaValue.valueOf(-1))
    }

    // ── Дефолтные заголовки для Lua HTTP-функций ─────────────────────────────

    /**
     * Referer = scheme://host/ — минимальный Referer который не раскрывает путь
     * но достаточен для обхода anti-scraping проверок большинства сайтов.
     */
    private fun refererFromUrl(url: String): String = try {
        val uri = URI(url)
        "${uri.scheme}://${uri.host}/"
    } catch (_: Exception) { url }

    /**
     * Accept-Language из системных локалей устройства.
     * Пример: "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"
     */
    private fun systemAcceptLanguage(): String {
        val locales = ConfigurationCompat.getLocales(context.resources.configuration)
        return buildString {
            for (i in 0 until locales.size()) {
                val locale = locales.get(i) ?: continue
                if (isNotEmpty()) append(',')
                append(locale.toLanguageTag())
                if (i > 0) {
                    val q = maxOf(0.1, 1.0 - i * 0.1)
                    append(";q=%.1f".format(q))
                }
            }
        }.ifEmpty { Locale.getDefault().toLanguageTag() }
    }

    /**
     * Дефолтные заголовки для всех Lua HTTP-запросов.
     * Плагин переопределяет нужные через свой config.headers — остальные берутся отсюда.
     */
    private fun defaultHeaders(url: String): Map<String, String> = mapOf(
        "Accept-Language" to systemAcceptLanguage(),
        "Referer"         to refererFromUrl(url),
    )

    /**
     * Блокирует SSRF: запрещает обращаться к loopback / приватным / link-local / any-local
     * адресам (localhost, 127.0.0.1, 10.0.2.2, внутренние сети и т.д.).
     * Резолвит имя хоста и проверяет ВСЕ полученные адреса.
     */
    private fun isSsrfSafe(url: String): Boolean {
        val host = try { URI(url).host } catch (_: Exception) { null } ?: return false
        if (host.isEmpty()) return false
        return try {
            InetAddress.getAllByName(host).all { addr ->
                !addr.isLoopbackAddress &&
                    !addr.isSiteLocalAddress &&
                    !addr.isLinkLocalAddress &&
                    !addr.isAnyLocalAddress
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun ssrfErrorTable(url: String) = LuaTable().also { t ->
        t.set("success", LuaValue.FALSE)
        t.set("body", LuaValue.valueOf("SSRF blocked: private/loopback address not allowed ($url)"))
        t.set("code", LuaValue.valueOf(-1))
    }

    /**
     * Определяет Content-Type по телу запроса если плагин его не указал.
     * JSON-тело (начинается с { или [) → application/json
     * Иначе → application/x-www-form-urlencoded
     */
    private fun detectContentType(body: String): String =
        if (body.trimStart().firstOrNull() in listOf('{', '[')) "application/json"
        else "application/x-www-form-urlencoded"

    private fun convertHeaders(table: LuaTable): Map<String, String> {
        val map = mutableMapOf<String, String>()
        table.keys().forEach { map[it.tojstring()] = table.get(it).tojstring() }
        return map
    }

    // http_get_batch(urls_table) → массив { success, body, code } в том же порядке
    private inner class HttpGetBatchFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            val urlTable = arg.checktable()
            val urls = (1..urlTable.length()).map { urlTable.get(it).checkjstring() }

            val results = runBlocking {
                urls.map { url ->
                    async(Dispatchers.IO) {
                        try {
                            if (!isSsrfSafe(url)) return@async Triple(false, "", 0)
                            networkClient.getWithHeaders(url, defaultHeaders(url)).use { r ->
                                val body = r.body.string()
                                Triple(r.isSuccessful, body, r.code)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "http_get_batch failed: $url")
                            Triple(false, "", 0)
                        }
                    }
                }.awaitAll()
            }

            return LuaTable().also { out ->
                results.forEachIndexed { i, (success, body, code) ->
                    out.set(i + 1, responseTable(success, body, code))
                }
            }
        }
    }

    // ── Preferences ───────────────────────────────────────────────────────────

    private inner class GetPreferenceFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            val prefs = context.getSharedPreferences("lua_preferences", Context.MODE_PRIVATE)
            return LuaValue.valueOf(prefs.getString(arg.checkjstring(), "") ?: "")
        }
    }

    private inner class SetPreferenceFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue {
            context.getSharedPreferences("lua_preferences", Context.MODE_PRIVATE)
                .edit().putString(a1.checkjstring(), a2.tojstring()).apply()
            return LuaValue.NIL
        }
    }

    // ── Cookies ───────────────────────────────────────────────────────────────

    private inner class GetCookiesFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            val httpUrl = arg.checkjstring().toHttpUrl()
            return LuaTable().also { t ->
                networkClient.cookieJar.loadForRequest(httpUrl)
                    .forEach { c -> t.set(c.name, c.value) }
            }
        }
    }

    private inner class SetCookiesFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue {
            val httpUrl      = a1.checkjstring().toHttpUrl()
            val cookiesTable = a2.checktable()
            val cookies = cookiesTable.keys().map { key ->
                okhttp3.Cookie.Builder()
                    .domain(httpUrl.host)
                    .name(key.tojstring())
                    .value(cookiesTable.get(key).tojstring())
                    .build()
            }
            networkClient.cookieJar.saveFromResponse(httpUrl, cookies)
            return LuaValue.NIL
        }
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    private inner class AesDecryptFunction : ThreeArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue, a3: LuaValue): LuaValue = try {
            val cipher  = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(a2.checkjstring().toByteArray(), "AES"),
                javax.crypto.spec.IvParameterSpec(a3.checkjstring().toByteArray())
            )
            LuaValue.valueOf(String(cipher.doFinal(
                android.util.Base64.decode(a1.checkjstring(), android.util.Base64.DEFAULT)
            ), Charsets.UTF_8))
        } catch (e: Exception) { Timber.e(e, "aes_decrypt failed"); LuaValue.NIL }
    }

    private inner class Base64DecodeFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            LuaValue.valueOf(String(
                android.util.Base64.decode(arg.checkjstring(), android.util.Base64.DEFAULT),
                Charsets.UTF_8
            ))
        } catch (_: Exception) { LuaValue.NIL }
    }

    private inner class Base64EncodeFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            LuaValue.valueOf(android.util.Base64.encodeToString(
                arg.checkjstring().toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            ))
        } catch (_: Exception) { LuaValue.NIL }
    }

    // ── HTML ──────────────────────────────────────────────────────────────────

    /**
     * html_parse(html) → { text, html, title, body }
     */
    private inner class HtmlParseFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            val doc = Jsoup.parse(arg.checkjstring())
            LuaTable().also { t ->
                t.set("text",  LuaValue.valueOf(doc.text()))
                t.set("html",  LuaValue.valueOf(doc.html()))
                t.set("title", LuaValue.valueOf(doc.title()))
                t.set("body", elementToTable(doc.body()))
            }
        } catch (e: Exception) { Timber.e(e, "html_parse"); LuaValue.NIL }
    }

    /**
     * html_select(html_or_element, css_selector) → array of element tables
     * Each element: { text, html, href, src, title, class, id, attr(name), remove() }
     */
    private inner class HtmlSelectFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            val html  = htmlFromValue(a1)
            val elems = Jsoup.parse(html).select(a2.checkjstring())
            LuaTable().also { t -> elems.forEachIndexed { i, el -> t.set(i + 1, elementToTable(el)) } }
        } catch (e: Exception) { Timber.e(e, "html_select"); LuaTable() }
    }

    /**
     * html_select_first(html_or_element, css_selector) → element table or nil
     */
    private inner class HtmlSelectFirstFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            val html = htmlFromValue(a1)
            val el = Jsoup.parse(html).selectFirst(a2.checkjstring())
            if (el != null) elementToTable(el) else LuaValue.NIL
        } catch (e: Exception) { Timber.e(e, "html_select_first"); LuaValue.NIL }
    }

    /**
     * html_attr(html_or_element, css_selector, attr_name) → string
     * Shorthand for: html_select(html, sel)[1].attr(name)
     */
    private inner class HtmlAttrFunction : ThreeArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue, a3: LuaValue): LuaValue = try {
            val html = htmlFromValue(a1)
            val el   = Jsoup.parse(html).selectFirst(a2.checkjstring())
            if (el != null) LuaValue.valueOf(el.attr(a3.checkjstring())) else LuaValue.valueOf("")
        } catch (_: Exception) { LuaValue.valueOf("") }
    }

    /**
     * html_text(html_or_element) → extracted text (respects <p>, <br>)
     */
    private inner class HtmlTextFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            val html = arg.tojstring()

            // ЛОГ: Проверяем первые 50 символов входящего HTML
            Timber.d("LuaEngine: html_text input (start): ${html.take(50)}")

            val doc = Jsoup.parseBodyFragment(html)
            val text = TextExtractor.get(doc.body())

            // ЛОГ: Проверяем результат экстракции
            Timber.d("LuaEngine: html_text output (start): ${text.take(50)}")

            LuaValue.valueOf(text)
        } catch (e: Exception) {
            Timber.e(e, "html_text failed")
            LuaValue.NIL
        }
    }

    /**
     * html_remove(html, selector1, selector2, ...) → cleaned html string
     * Аналог removeElementsDOM — удаляет элементы перед извлечением текста.
     */
    private inner class HtmlRemoveFunction : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs {
            return try {
                val html = htmlFromValue(args.arg(1))
                val doc  = Jsoup.parse(html)
                for (i in 2..args.narg()) {
                    val selector = args.arg(i).optjstring(null) ?: continue
                    if (selector.isNotBlank()) doc.select(selector).remove()
                }
                LuaValue.valueOf(doc.body().html())
            } catch (e: Exception) {
                Timber.e(e, "html_remove")
                args.arg(1)
            }
        }
    }

    // string_clean(str) — normalize Unicode + collapse whitespace + trim
// Эквивалент Kotlin: Clean() = normalizeUnicode().regexReplace("""\s+""", " ").trim()
    private inner class StringCleanFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            val s = arg.optjstring("") ?: return LuaValue.valueOf("")
            val normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC)
            val collapsed  = normalized.replace(Regex("""\s+"""), " ").trim()
            return LuaValue.valueOf(collapsed)
        }
    }

    private fun htmlFromValue(v: LuaValue): String =
        if (v.istable()) v.checktable().get("html").optjstring("") else v.checkjstring()

    private fun elementToTable(el: Element): LuaTable = LuaTable().also { t ->
        t.set("text",  LuaValue.valueOf(el.text()))
        t.set("html",  LuaValue.valueOf(el.html()))
        t.set("href",  LuaValue.valueOf(el.attr("abs:href").ifEmpty { el.attr("href") }))
        t.set("src",   LuaValue.valueOf(el.attr("abs:src").ifEmpty  { el.attr("src")  }))
        t.set("title", LuaValue.valueOf(el.attr("title")))
        t.set("class", LuaValue.valueOf(el.attr("class")))
        t.set("id",    LuaValue.valueOf(el.attr("id")))
        t.set("get_text", object : ZeroArgFunction() { override fun call() = LuaValue.valueOf(el.text()) })
        t.set("get_html", object : ZeroArgFunction() { override fun call() = LuaValue.valueOf(el.html()) })
        t.set("attr",  object : OneArgFunction() {
            override fun call(a: LuaValue) = try {
                LuaValue.valueOf(el.attr(a.checkjstring()))
            } catch (_: Exception) { LuaValue.valueOf("") }
        })
        t.set("remove", object : ZeroArgFunction() {
            override fun call(): LuaValue { el.remove(); return LuaValue.NIL }
        })
        t.set("select", object : OneArgFunction() {
            override fun call(a: LuaValue): LuaValue = try {
                val elems = el.select(a.checkjstring())
                LuaTable().also { t2 -> elems.forEachIndexed { i, e -> t2.set(i + 1, elementToTable(e)) } }
            } catch (_: Exception) { LuaTable() }
        })
    }

    // ── URL ───────────────────────────────────────────────────────────────────

    private inner class UrlEncodeFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            LuaValue.valueOf(java.net.URLEncoder.encode(arg.checkjstring(), "UTF-8"))
        } catch (_: Exception) { LuaValue.NIL }
    }

    /**
     * url_encode_charset(str, charset) → encoded string
     * Нужен для GBK-поиска (Shuba69, PiaoTia)
     */
    private inner class UrlEncodeCharsetFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            val charset = a2.optjstring("UTF-8")
            LuaValue.valueOf(java.net.URLEncoder.encode(a1.checkjstring(), charset))
        } catch (_: Exception) { LuaValue.NIL }
    }

    private inner class UrlResolveFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            LuaValue.valueOf(URI(a1.checkjstring()).resolve(a2.checkjstring()).toString())
        } catch (_: Exception) { a2 }
    }

    // ── String utils ──────────────────────────────────────────────────────────

    private inner class RegexReplaceFunction : ThreeArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue, a3: LuaValue): LuaValue = try {
            LuaValue.valueOf(a1.checkjstring().replace(Regex(a2.checkjstring()), a3.checkjstring()))
        } catch (_: Exception) { a1 }
    }

    private inner class StringNormalizeFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            LuaValue.valueOf(java.text.Normalizer.normalize(arg.checkjstring(), java.text.Normalizer.Form.NFKC))
        } catch (_: Exception) { arg }
    }

    /**
     * string_split(str, separator) → array of strings
     */
    private inner class StringSplitFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            val parts = a1.checkjstring().split(a2.checkjstring())
            LuaTable().also { t -> parts.forEachIndexed { i, s -> t.set(i + 1, LuaValue.valueOf(s)) } }
        } catch (_: Exception) { LuaTable() }
    }

    /**
     * string_trim(str) → trimmed string
     */
    private inner class StringTrimFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            LuaValue.valueOf(arg.checkjstring().trim())
        } catch (_: Exception) { arg }
    }

    /**
     * string_starts_with(str, prefix) → boolean
     */
    private inner class StringStartsWithFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            LuaValue.valueOf(a1.checkjstring().startsWith(a2.checkjstring()))
        } catch (_: Exception) { LuaValue.FALSE }
    }

    /**
     * string_ends_with(str, suffix) → boolean
     */
    private inner class StringEndsWithFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            LuaValue.valueOf(a1.checkjstring().endsWith(a2.checkjstring()))
        } catch (_: Exception) { LuaValue.FALSE }
    }

    private inner class RegexMatchFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, a2: LuaValue): LuaValue = try {
            LuaTable().also { t ->
                Regex(a2.checkjstring()).findAll(a1.checkjstring())
                    .forEachIndexed { i, m -> t.set(i + 1, LuaValue.valueOf(m.value)) }
            }
        } catch (_: Exception) { LuaTable() }
    }

    private inner class UnescapeUnicodeFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            LuaValue.valueOf(
                Regex("\\\\u([0-9a-fA-F]{4})").replace(arg.checkjstring()) { m ->
                    m.groupValues[1].toInt(16).toChar().toString()
                }
            )
        } catch (_: Exception) { arg }
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    private inner class JsonParseFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            convertToLua(gson.fromJson(arg.checkjstring(), Any::class.java))
        } catch (e: Exception) { Timber.e(e, "json_parse"); LuaValue.NIL }
    }

    private inner class JsonStringifyFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue = try {
            LuaValue.valueOf(gson.toJson(convertFromLua(arg)))
        } catch (e: Exception) { Timber.e(e, "json_stringify"); LuaValue.NIL }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    private inner class DetectPaginationFunction : TwoArgFunction() {
        override fun call(a1: LuaValue, @Suppress("UNUSED_PARAMETER") a2: LuaValue): LuaValue = try {
            val html = htmlFromValue(a1)
            val next = Jsoup.parse(html).select("a[href]:contains(next), a[href]:contains(›), a[href]:contains(»)")
            LuaTable().also { t ->
                t.set("hasNext",  LuaValue.valueOf(next.isNotEmpty()))
                val nextUrl = next.firstOrNull()?.attr("abs:href")
                t.set("next_url", if (!nextUrl.isNullOrBlank()) LuaValue.valueOf(nextUrl) else LuaValue.NIL)
            }
        } catch (_: Exception) { LuaValue.NIL }
    }

    /**
     * sleep(milliseconds) — задержка между запросами
     * Используется в Jaomix и WtrLab для избежания rate-limit
     */
    private inner class SleepFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            val ms = arg.optlong(500)
            Thread.sleep(ms)
            return LuaValue.NIL
        }
    }

    private inner class LogInfoFunction  : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue { Timber.i("Lua: ${arg.optjstring("")}"); return LuaValue.NIL }
    }
    private inner class LogErrorFunction : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue { Timber.e("Lua: ${arg.optjstring("")}"); return LuaValue.NIL }
    }

    // ── Java ↔ Lua ────────────────────────────────────────────────────────────

    fun convertToLua(obj: Any?): LuaValue = when (obj) {
        null        -> LuaValue.NIL
        is String   -> LuaValue.valueOf(obj)
        is Number   -> LuaValue.valueOf(obj.toDouble())
        is Boolean  -> LuaValue.valueOf(obj)
        is Map<*,*> -> LuaTable().also { t ->
            obj.forEach { (k, v) -> t.set(LuaValue.valueOf(k.toString()), convertToLua(v)) }
        }
        is List<*>  -> LuaTable().also { t ->
            obj.forEachIndexed { i, v -> t.set(i + 1, convertToLua(v)) }
        }
        else        -> LuaValue.valueOf(obj.toString())
    }

    fun convertFromLua(v: LuaValue): Any? = when {
        v.isnil()     -> null
        v.isboolean() -> v.toboolean()
        v.isnumber()  -> v.todouble()
        v.isstring()  -> v.tojstring()
        v.istable()   -> {
            val t = v.checktable(); val keys = t.keys()
            if (keys.all { it.isnumber() && it.toint() > 0 })
                (1..t.length()).map { convertFromLua(t.get(it)) }
            else
                keys.associate { it.tojstring() to convertFromLua(t.get(it)) }
        }
        else -> v.tojstring()
    }
}


// =============================================================================
// LuaSourceLoader
// =============================================================================

@Singleton
class LuaSourceLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkClient: NetworkClient,
    private val luaEngine: LuaEngine,
    private val extensionRepository: ExtensionManager
) {
    private val yaml  = Yaml()
    // ponytail: LRU cache with fixed max size. Evicts least recently used VMs to keep native heap bounded.
    // Cost: ~100ms reload from disk .lua file on cache miss.
    // LruCache is already thread-safe (synchronized internally).
    private val cache = object : LruCache<String, SourceInterface>(MAX_CACHED_SOURCES) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: SourceInterface, newValue: SourceInterface?) {
            if (evicted) Timber.d("Evicted Lua source from LRU cache: $key")
        }
    }

    private val luaDir: File
        get() = File(context.filesDir, "lua_extensions").also { it.mkdirs() }

    fun clearCache() { cache.evictAll(); Timber.d("Lua source cache cleared") }

    suspend fun loadAllSources(): Result<List<SourceInterface>> = withContext(Dispatchers.IO) {
        runCatching {
            val sources = loadInstalledSources()
            sources.forEach { cache.put(it.id, it) }
            Timber.d("Loaded ${sources.size} Lua sources")
            sources
        }
    }

    suspend fun downloadAndCacheScript(id: String, codeUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = networkClient.get(codeUrl)
            if (!response.isSuccessful) {
                Timber.e("Download failed $id: HTTP ${response.code}")
                return@withContext false
            }
            val code = response.body.string().ifBlank {
                Timber.e("Empty body for $id")
                return@withContext false
            }
            luaFile(id).writeText(code, Charsets.UTF_8)
            cache.remove(id)
            Timber.d("Saved $id.lua")
            true
        } catch (e: Exception) {
            Timber.e(e, "downloadAndCacheScript failed for $id")
            false
        }
    }

    fun removeScript(id: String) { luaFile(id).delete(); cache.remove(id) }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun loadInstalledSources(): List<SourceInterface> {
        val enabled = try {
            extensionRepository.getEnabledExtensions()
        } catch (e: Exception) {
            Timber.e(e, "getEnabledExtensions failed")
            return emptyList()
        }
        return coroutineScope {
            enabled.map { ext ->
                async(Dispatchers.IO.limitedParallelism(4)) {
                    try {
                        val iconUrl = extractIconUrl(ext)
                        loadFromDisk(ext.id, iconUrl) ?: run {
                            val codeUrl = extractCodeUrl(ext)
                            if (codeUrl != null && downloadAndCacheScript(ext.id, codeUrl))
                                loadFromDisk(ext.id, iconUrl)
                            else {
                                Timber.w("Cannot load ${ext.id}: no .lua and no codeUrl")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load ${ext.id}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun loadFromDisk(id: String, iconUrl: String? = null): SourceInterface? {
        val file = luaFile(id)
        if (!file.exists()) return null
        return try {
            val script = luaEngine.loadScript(file.readText(Charsets.UTF_8))
            createLuaSourceAdapter(context, script, luaEngine, iconUrl, id)
                .also { cache.put(id, it); Timber.d("Loaded from disk: $id") }
        } catch (e: Exception) {
            Timber.e(e, "Compile error for $id")
            null
        }
    }

    private suspend fun getExtensionSettingsMap(ext: my.noveldokusha.core.Extension): Map<String, Any>? {
        val raw = try {
            extensionRepository.getExtensionSettings(ext.id)
        } catch (e: Exception) {
            Timber.w(e, "Failed to get settings for ${ext.id}")
            return null
        }
        if (raw.isNullOrBlank() || raw == "{}") return null
        return try {
            @Suppress("UNCHECKED_CAST")
            yaml.load<Any>(raw) as? Map<String, Any>
        } catch (e: Exception) {
            Timber.w(e, "Bad settings YAML for ${ext.id}: $raw")
            null
        }
    }

    private suspend fun extractCodeUrl(ext: my.noveldokusha.core.Extension): String? =
        getExtensionSettingsMap(ext)?.get("codeUrl")?.toString()

    private suspend fun extractIconUrl(ext: my.noveldokusha.core.Extension): String? {
        // Читаем из YAML settings: icon / iconUrl / icon_url
        val map = getExtensionSettingsMap(ext) ?: return null
        return (map["icon"] ?: map["iconUrl"] ?: map["icon_url"])?.toString()
    }

    private fun luaFile(id: String) = File(luaDir, "$id.lua")

    companion object {
        // ponytail: 15 VMs max. Each LuaJ Globals ~2-5MB. 15 * 5 = 75MB ceiling.
        // Upgrade path: increase if user reports slow extension loading.
        private const val MAX_CACHED_SOURCES = 15
    }
}

// ── Дополнения для полной поддержки всех источников ──────────────────────────

// base64_encode — нужен для Quanben5 (кодирует поисковый запрос)
private class Base64EncodeFunction : OneArgFunction() {
    override fun call(arg: LuaValue): LuaValue = try {
        LuaValue.valueOf(android.util.Base64.encodeToString(
            arg.checkjstring().toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        ))
    } catch (_: Exception) { LuaValue.NIL }
}

// os_time — Unix timestamp в миллисекундах (для cache-busting в URL)
private class OsTimeFunction : ZeroArgFunction() {
    override fun call(): LuaValue = LuaValue.valueOf(System.currentTimeMillis().toDouble())
}
