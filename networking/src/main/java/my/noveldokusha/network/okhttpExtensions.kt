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

/**
 * Konversi Response OkHttp ke Jsoup Document menggunakan streaming (byteStream).
 * Menghindari alokasi intermediate String raksasa di heap JVM Android (ramah memori).
 * Menggunakan charset dari HTTP Content-Type jika tersedia, atau auto-detect Jsoup jika null.
 */
fun Response.toDocument(): Document {
    val baseUrl = request.url.toString()
    val charsetName = body.contentType()?.charset()?.name()
    return body.byteStream().use { stream ->
        Jsoup.parse(stream, charsetName, baseUrl)
    }
}

/**
 * Konversi Response OkHttp ke Jsoup Document dengan charset khusus via streaming.
 * Menghindari alokasi ByteArray ganda dan intermediate String di heap.
 */
fun Response.toDocument(charset: String): Document {
    val baseUrl = request.url.toString()
    return body.byteStream().use { stream ->
        Jsoup.parse(stream, charset, baseUrl)
    }
}

private val lenientGson: Gson by lazy {
    GsonBuilder().setStrictness(Strictness.LENIENT).create()
}

fun Response.toJson(): JsonElement {
    val jsonString = body.string()
    return lenientGson.fromJson(jsonString, JsonElement::class.java)
}