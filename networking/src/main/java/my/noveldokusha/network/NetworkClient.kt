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
import okhttp3.Dns
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
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

    // Общий пул: CloudFareVerificationInterceptor эвиктит его (evictAll) перед
    // ретраями, чтобы не переиспользовать соединение, отравленное челленджем.
    // Тот же пул использует и call() для своих ретраев — единая точка очистки.
    private val cfConnectionPool = ConnectionPool(15, 5, TimeUnit.MINUTES)

    // Жёсткий потолок на один вызов call(): запас, чтобы UI-триггеры не
    // зависали при каскаде неудач (п.8). Поднят выше бюджета CF-обхода в
    // CloudFareVerificationInterceptor (auto 15с + manual 35с = до 50с):
    // иначе глобальный withTimeout режет CF-челлендж в WebView ровно на 30с,
    // до того как cf_clearance будет готов. Per-attempt OkHttp-таймауты всё
    // ещё быстро роняют дохлые коннекты на обычных запросах; CF-интерцептор
    // сам не висит дольше ~50с, поэтому 90с — безопасный потолок, не бесконечность.
    private val TOTAL_TIMEOUT_MS = 90_000L

    // Асимметричные таймауты по попыткам (п.3): короткие ранние (3с) — чтобы
    // быстро выбросить зависший/дохлый коннект из пула при массовой загрузке
    // глав, длинный хвост (15с) — на случай медленного, но живого хоста или
    // невезучей серии. 4×3с + 15с = 27с, под глобальным потолком 90с.
    // 5 попыток: p^5 ≈ 3% шанс не вытянуть (против ~12% на 3 попытках) —
    // реже ручной перезапуск скрина.
    private val attemptTimeoutsMs = longArrayOf(3_000, 3_000, 3_000, 3_000, 15_000)

    // Jitter перед ретраем (0–400мс): разносит во времени повторы параллельных
    // запросов (каталог + обложки), упавших одновременно на одном CF-edge,
    // чтобы не бить по edge синхронно (thundering herd). Не влияет на шанс
    // успеха одной попытки — только сглаживает нагрузку во времени.
    private val retryJitterMs = 400L

    // DoH-резолвер с фолбэком на системный DNS: если DoH-эндпоинт
    // недоступен (заблокирован сетью/провайдером/файрволом), запросы не
    // падают массово с DNS-ошибкой, а резолвятся системным резолвером.
    private val dnsOverHttps = DnsOverHttps()
    private val fallbackDns = Dns { hostname ->
        try {
            dnsOverHttps.lookup(hostname)
        } catch (e: Exception) {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    // Единый OkHttp-клиент для ВСЕГО (скрапер, переводчики, картинки).
    // Без per-host пулов и без форса HTTP/1.1 — они ломали evictAll CF-интерцептора
    // (evictAll чистил не тот пул) и возвращали Socket is closed на h1.
    private fun baseBuilder(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .apply {
                if (appInternalState.isDebugMode) {
                    okhttpLoggingInterceptor?.let { addInterceptor(it) }
                }
                addInterceptor(UserAgentInterceptor(appPreferences))
                addInterceptor(DecodeResponseInterceptor())
                if (appPreferences.CLOUDFLARE_BYPASS_ENABLED.value) {
                    addInterceptor(CloudFareVerificationInterceptor(appContext, appPreferences, cfConnectionPool))
                }
                dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
                cookieJar(cookieJar)
                cache(Cache(cacheDir, cacheSize))
                // Таймауты 10с: edge (Cloudflare) интермиттирующе сбрасывает h2-стримы
                // (RST CANCEL) и тихо столлит соединения. Ретрай в call() подхватывает
                // сбой на свежем соединении (evictAll), поэтому короткий таймаут не вредит.
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(10, TimeUnit.SECONDS)
                writeTimeout(10, TimeUnit.SECONDS)
                dns(fallbackDns)
            }
    }

    val client: OkHttpClient by lazy {
        baseBuilder().connectionPool(cfConnectionPool).build()
    }

    // Отдельный клиент с follow-redirects — готовый, без newBuilder() на каждый
    // вызов (п.5). Делит тот же cfConnectionPool, так что evictAll работает
    // единообразно с обычным client.
    val clientWithRedirects: OkHttpClient by lazy {
        baseBuilder()
            .connectionPool(cfConnectionPool)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override suspend fun call(request: Request.Builder, followRedirects: Boolean): Response {
        val built = request.build()
        val baseClient = if (followRedirects) clientWithRedirects else this.client
        // Тело отдаём сырым вызывающему — буферизация здесь удваивала бы память
        // на горячем пути загрузки картинок (caller читает body повторно). Ретрай
        // ловит сетевые/таймаут-сбои уровня соединения; сбой дочитки тела
        // (RST CANCEL на h2-edge) всплывёт в caller'е как ошибка — приемлемо.
        // Только идемпотентный GET — не дублируем POST.
        //
        // Таймауты попытки — OkHttp-native (connect/read/write), НЕ корутинный
        // withTimeout. Иначе withTimeout убивал бы всю попытку ровно через 3с —
        // включая обработку интерцепторов. CloudFareVerificationInterceptor
        // решает Turnstile-челлендж в WebView ~8с: это локальная обработка, а не
        // сетевая операция, поэтому OkHttp-таймауты её не трогают и челлендж
        // успевает пройти (п.3 + корректный обход CF).
        var lastException: Exception? = null
        var result: Response? = null
        withTimeout(TOTAL_TIMEOUT_MS) { // жёсткий потолок на весь цикл ретраев (п.8)
            for (attempt in attemptTimeoutsMs.indices) {
                var response: Response? = null
                // Per-attempt OkHttp-таймауты на свежем клиенте, делящем тот же
                // cfConnectionPool, — evictAll ниже очищает пул базового клиента.
                val attemptClient = baseClient.newBuilder()
                    .connectTimeout(attemptTimeoutsMs[attempt], TimeUnit.MILLISECONDS)
                    .readTimeout(attemptTimeoutsMs[attempt], TimeUnit.MILLISECONDS)
                    .writeTimeout(attemptTimeoutsMs[attempt], TimeUnit.MILLISECONDS)
                    .build()
                try {
                    response = attemptClient.call(built.newBuilder())
                    result = response
                    return@withTimeout
                } catch (e: CancellationException) {
                    // Внешняя отмена (навигация, уничтожение скоупа) — пробрасываем.
                    response?.close()
                    throw e
                } catch (e: Exception) {
                    response?.close()
                    // Собственный таймаут попытки (SocketTimeout) или сетевой сбой
                    // (RST CANCEL/reset) — ретраим на свежем коннекте. Только GET.
                    if ((e is SocketTimeoutException || isRetryable(e)) &&
                        built.method.equals("GET", ignoreCase = true)
                    ) {
                        lastException = e
                        baseClient.connectionPool.evictAll()
                        delay(Random.nextLong(0, retryJitterMs + 1))
                    } else {
                        throw e
                    }
                }
            }
        }
        return result ?: throw lastException ?: IllegalStateException("Unreachable retry loop")
    }

    private fun isRetryable(e: Throwable): Boolean {
        if (e !is IOException) return false
        if (e is SocketTimeoutException) return true
        val msg = e.message ?: return false
        return msg.contains("CANCEL", ignoreCase = true) || msg.contains("reset", ignoreCase = true)
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


