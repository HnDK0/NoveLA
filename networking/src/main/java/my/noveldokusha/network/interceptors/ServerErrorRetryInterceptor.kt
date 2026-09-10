package my.noveldokusha.network.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * Retry для transient server errors (502/503/504) с экспоненциальным backoff.
 * Ретраит только GET-запросы (идемпотентные).
 */
class ServerErrorRetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialBackoffMs: Long = 500
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.method.equals("GET", ignoreCase = true)) {
            return chain.proceed(request)
        }

        var lastResponse: Response? = null
        var lastException: IOException? = null

        repeat(maxRetries + 1) { attempt ->
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                lastException = e
                null
            }

            if (response != null && !isRetryable(response.code)) {
                return response
            }

            // Close failed response before retry
            lastResponse?.close()
            lastResponse = response

            if (attempt < maxRetries) {
                val backoff = initialBackoffMs * (1 shl attempt) // exponential: 500, 1000, 2000
                Timber.d("ServerErrorRetry: ${response?.code ?: "exception"} on ${request.url.host}, retry ${attempt + 1}/$maxRetries in ${backoff}ms")
                Thread.sleep(backoff)
            }
        }

        // All retries exhausted
        lastResponse?.let { return it }
        throw lastException ?: IOException("All retries exhausted")
    }

    private fun isRetryable(code: Int): Boolean = code in RETRYABLE_CODES

    companion object {
        // ponytail: 502/503/504 — typical transient CDN errors. 429 omitted — handled by CloudFareVerificationInterceptor.
        private val RETRYABLE_CODES = setOf(502, 503, 504)
    }
}
