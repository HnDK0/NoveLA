package my.noveldokusha.network

import com.google.gson.Gson
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build()
private val DEFAULT_HEADERS = Headers.Builder().build()
private val DEFAULT_BODY: RequestBody = FormBody.Builder().build()
private val GSON = Gson()

fun Request.Builder.postPayload(scope: FormBody.Builder.() -> Unit): Request.Builder {
    val builder = FormBody.Builder()
    scope(builder)
    return post(builder.build())
}

fun Request.Builder.postJson(data: Map<String, Any>): Request.Builder {
    val json = GSON.toJson(data)
    val body = json.toRequestBody("application/json; charset=UTF-8".toMediaType())
    return post(body)
}

fun getRequest(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL
) = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)


fun postRequest(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL
) = Request.Builder()
    .url(url)
    .post(body)
    .headers(headers)
    .cacheControl(cache)
