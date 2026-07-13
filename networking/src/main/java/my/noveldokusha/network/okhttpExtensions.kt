package my.noveldokusha.network

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.Strictness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
private suspend fun Call.await(): Response = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }
}

suspend fun OkHttpClient.call(builder: Request.Builder) = newCall(builder.build()).await()

fun Response.toDocument(): Document {
    val html = body.string()
    return Jsoup.parse(html, request.url.toString())
}

fun Response.toDocument(charset: String): Document {
    val bytes = body.bytes()
    val html = String(bytes, Charset.forName(charset))
    val baseUrl = request.url.toString()
    return Jsoup.parse(html, baseUrl)
}

private val lenientGson: Gson by lazy {
    GsonBuilder().setStrictness(Strictness.LENIENT).create()
}

fun Response.toJson(): JsonElement {
    val jsonString = body.string()
    return lenientGson.fromJson(jsonString, JsonElement::class.java)
}