package my.noveldokusha.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Кастомный DNS resolver через DNS-over-HTTPS (DoH).
 *
 * Пробует системный DNS первым. Если он упал (Doze mode, блокировка порта 53),
 * перебирает DoH-провайдеров по доменным именам, пока один не ответит.
 *
 * Для DoH-запросов используются домены (cloudflare-dns.com, dns.google и т.д.),
 * а не IP — чтобы HTTPS-сертификат совпадал с hostname.
 * Резолвинг этих доменов выполняется через кастомный Dns с хардкоженными IP,
 * без обращения к системному DNS.
 *
 * Провайдеры (в порядке попытки):
 *   1. Cloudflare  — cloudflare-dns.com        (1.1.1.1 / 1.0.0.1)
 *   2. Google      — dns.google                (8.8.8.8 / 8.8.4.4)
 *   3. Yandex      — common.dot.dns.yandex.net (77.88.8.8 / 77.88.8.1)
 *   4. AdGuard     — dns.adguard-dns.com        (94.140.14.14 / 94.140.15.15)
 *   5. Quad9       — dns.quad9.net             (9.9.9.9 / 149.112.112.112)
 *   6. Comodo      — cdn77-doh.com             (8.26.56.26 / 8.20.247.20)
 */
class DnsOverHttps : Dns {

    private data class DoHProvider(
        val name: String,
        val domain: String,
        val primaryIp: String,
        val fallbackIp: String,
    )

    private val providers = listOf(
        DoHProvider("Cloudflare", "cloudflare-dns.com", "1.1.1.1", "1.0.0.1"),
        DoHProvider("Google", "dns.google", "8.8.8.8", "8.8.4.4"),
        DoHProvider("Yandex", "common.dot.dns.yandex.net", "77.88.8.8", "77.88.8.1"),
        DoHProvider("AdGuard", "dns.adguard-dns.com", "94.140.14.14", "94.140.15.15"),
        DoHProvider("Quad9", "dns.quad9.net", "9.9.9.9", "149.112.112.112"),
        DoHProvider("Comodo", "cdn77-doh.com", "8.26.56.26", "8.20.247.20"),
    )

    /**
     * Маппинг домен DoH-провайдера -> IP-адреса для резолвинга без системного DNS.
     * Используется кастомным Dns ниже.
     */
    private val dohDomainIps: Map<String, List<InetAddress>> = run {
        val map = mutableMapOf<String, List<InetAddress>>()
        for (p in providers) {
            map[p.domain] = listOf(
                InetAddress.getByName(p.primaryIp),
                InetAddress.getByName(p.fallbackIp),
            )
        }
        map
    }

    /**
     * OkHttpClient для DoH-запросов.
     * Использует кастомный Dns, который для доменов DoH-провайдеров
     * возвращает IP из хардкоженного маппинга (без системного DNS),
     * а для всех остальных доменов — системный DNS.
     */
    private val dohClient = OkHttpClient.Builder()
        .dns(Dns { hostname ->
            val hardcoded = dohDomainIps[hostname]
            if (hardcoded != null) {
                hardcoded
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        })
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // In-memory кэш — TTL 5 минут
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val timestamp: Long,
    ) {
        fun isValid(): Boolean = System.currentTimeMillis() - timestamp < CACHE_TTL_MS
    }

    companion object {
        private const val TAG = "DnsOverHttps"
        private const val MEDIA_TYPE = "application/dns-json"
        private const val CACHE_TTL_MS = 300_000L
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. Проверяем кэш
        cache[hostname]?.let { entry ->
            if (entry.isValid() && entry.addresses.isNotEmpty()) {
                Timber.tag(TAG).d("CACHE: %s -> %s", hostname, entry.addresses.firstOrNull()?.hostAddress)
                return entry.addresses
            }
        }

        // 2. Пробуем системный DNS (работает когда экран включён)
        try {
            val systemResult = Dns.SYSTEM.lookup(hostname)
            if (systemResult.isNotEmpty()) {
                Timber.tag(TAG).d("SYSTEM: %s -> %s", hostname, systemResult.firstOrNull()?.hostAddress)
                cache[hostname] = CacheEntry(systemResult, System.currentTimeMillis())
                return systemResult
            }
        } catch (_: UnknownHostException) {
            Timber.tag(TAG).w("SYSTEM: %s -> DNS failed, trying DoH", hostname)
        }

        // 3. Перебираем DoH-провайдеров через домен (сертификат валиден!)
        for (provider in providers) {
            val result = tryResolveViaProvider(hostname, provider)
            if (result != null) return result
        }

        // 4. Все методы исчерпаны
        Timber.tag(TAG).w("ALL DNS FAILED: %s", hostname)
        throw UnknownHostException("Unable to resolve host \"$hostname\": all DNS methods (system + 6 DoH providers) failed")
    }

    private fun tryResolveViaProvider(
        hostname: String,
        provider: DoHProvider,
    ): List<InetAddress>? {
        return try {
            val url = "https://${provider.domain}/dns-query?name=$hostname&type=A"
            val request = Request.Builder()
                .url(url)
                .header("Accept", MEDIA_TYPE)
                .build()

            val response = dohClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.tag(TAG).d("DoH %s: HTTP %d for %s", provider.name, response.code, hostname)
                response.close()
                return null
            }

            val body = response.body?.string() ?: run {
                response.close()
                Timber.tag(TAG).d("DoH %s: empty body for %s", provider.name, hostname)
                return null
            }
            response.close()

            val addresses = mutableListOf<InetAddress>()
            val answerRegex = Regex("\"data\":\"(\\d+\\.\\d+\\.\\d+\\.\\d+)\"")
            for (match in answerRegex.findAll(body)) {
                try {
                    addresses.add(InetAddress.getByName(match.groupValues[1]))
                } catch (_: Exception) { /* skip invalid IPs */ }
            }

            if (addresses.isEmpty()) {
                Timber.tag(TAG).d("DoH %s: no A records for %s", provider.name, hostname)
                return null
            }

            Timber.tag(TAG).d("DoH %s: %s -> %s", provider.name, hostname, addresses.firstOrNull()?.hostAddress)
            cache[hostname] = CacheEntry(addresses, System.currentTimeMillis())
            addresses
        } catch (e: Exception) {
            Timber.tag(TAG).d("DoH %s: failed for %s: %s", provider.name, hostname, e.message ?: e.javaClass.simpleName)
            null
        }
    }
}