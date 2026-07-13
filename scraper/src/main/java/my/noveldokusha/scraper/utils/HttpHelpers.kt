package my.noveldokusha.scraper.utils

import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.getRequest
import my.noveldokusha.network.postPayload
import my.noveldokusha.network.postRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toJson
import okhttp3.Headers
import okhttp3.Request

/**
 * HTTP helper functions for scraper operations
 */

/**
 * GET request with optional headers and charset support
 */
suspend fun GET(
    url: String,
    headers: Map<String, String> = emptyMap(),
    networkClient: NetworkClient,
    charset: String? = null
): org.jsoup.nodes.Document {
    val requestHeaders = Headers.Builder().apply {
        headers.forEach { (key, value) -> add(key, value) }
    }.build()

    val requestBuilder = getRequest(url, headers = requestHeaders)
    val response = networkClient.call(requestBuilder)

    return if (charset != null) {
        response.toDocument(charset)
    } else {
        response.toDocument()
    }
}

/**
 * POST request with form data and optional headers
 */
suspend fun POST(
    url: String,
    data: Map<String, String>,
    headers: Map<String, String> = emptyMap(),
    networkClient: NetworkClient
): org.jsoup.nodes.Document {
    val requestHeaders = Headers.Builder().apply {
        headers.forEach { (key, value) -> add(key, value) }
    }.build()

    val requestBuilder = postRequest(url, headers = requestHeaders).apply {
        if (data.isNotEmpty()) {
            postPayload {
                data.forEach { (key, value) -> add(key, value) }
            }
        }
    }

    return networkClient.call(requestBuilder).toDocument()
}

/**
 * Fetch JSON with optional headers (GET request)
 */
suspend fun fetchJson(
    url: String,
    headers: Map<String, String> = emptyMap(),
    networkClient: NetworkClient
): com.google.gson.JsonElement {
    val requestHeaders = Headers.Builder().apply {
        headers.forEach { (key, value) -> add(key, value) }
    }.build()

    val requestBuilder = getRequest(url, headers = requestHeaders)
    return networkClient.call(requestBuilder).toJson()
}

/**
 * POST request returning JSON response
 */
suspend fun postJson(
    url: String,
    data: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    networkClient: NetworkClient
): com.google.gson.JsonElement {
    val requestHeaders = Headers.Builder().apply {
        headers.forEach { (key, value) -> add(key, value) }
    }.build()

    val requestBuilder = postRequest(url, headers = requestHeaders).apply {
        if (data.isNotEmpty()) {
            postPayload {
                data.forEach { (key, value) -> add(key, value) }
            }
        }
    }

    return networkClient.call(requestBuilder).toJson()
}

/**
 * Build AJAX POST request with custom headers
 */
fun ajaxPostRequest(
    url: String,
    data: Map<String, String>,
    referer: String,
    origin: String? = null,
    accept: String? = null,
    acceptLanguage: String? = null
): Request {
    val requestHeaders = Headers.Builder().apply {
        add("X-Requested-With", "XMLHttpRequest")
        add("Referer", referer)
        if (origin != null) add("Origin", origin)
        if (accept != null) add("Accept", accept)
        if (acceptLanguage != null) add("Accept-Language", acceptLanguage)
    }.build()

    return postRequest(url, headers = requestHeaders).apply {
        if (data.isNotEmpty()) {
            postPayload {
                data.forEach { (key, value) -> add(key, value) }
            }
        }
    }.build()
}
