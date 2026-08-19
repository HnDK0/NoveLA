package my.noveldokusha.scraper

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import kotlinx.coroutines.runBlocking
import my.noveldokusha.network.NetworkClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.luaj.vm2.LuaValue
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * TTL-кэш http_get в LuaEngine: повторные геттеры одной страницы книги делят один сетевой запрос.
 *
 * Используется РЕАЛЬНЫЙ LuaEngine (в отличие от других тестов scraper, которые мокают его):
 * замоканы только Context (filesDir для байткод-кэша, resources для defaultHeaders) и
 * NetworkClient (возвращает настоящий okhttp3.Response — не mock(ResponseBody), у него bytes() реальный).
 */
class LuaEngineHttpGetCacheTest {

    private val tempDir: File = Files.createTempDirectory("lua-http-get-cache").toFile()

    private fun createEngine(): Pair<LuaEngine, NetworkClient> {
        val networkClient = mock<NetworkClient>()
        runBlocking {
            whenever(networkClient.call(any(), any())).thenAnswer { inv ->
                val request = inv.getArgument<Request.Builder>(0).build()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("body-for-${request.url}".toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
        }

        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempDir)
        val resources = mock<Resources>()
        val configuration = Configuration()
        configuration.locale = Locale.US
        whenever(resources.configuration).thenReturn(configuration)
        whenever(context.resources).thenReturn(resources)

        val engine = LuaEngine(context, networkClient)
        engine.httpGetCacheTtlMs = 50
        return engine to networkClient
    }

    /** Публичный путь: loadScript регистрирует http_get на globals и возвращает их. */
    private fun httpGetBody(engine: LuaEngine, url: String): Pair<String, LuaValue> {
        val globals = runBlocking { engine.loadScript("-- http_get cache probe") }
        val table = globals.get("http_get")
            .call(LuaValue.valueOf(url), LuaValue.NIL)
            .checktable()
        return table.get("body").tojstring() to table
    }

    @Test
    fun `same url within ttl hits cache and shares one network request`() {
        val (engine, networkClient) = createEngine()
        val url = "http://example.com/book"

        val (body1, table1) = httpGetBody(engine, url)
        val (body2, table2) = httpGetBody(engine, url)

        runBlocking { verify(networkClient, times(1)).call(any(), any()) }
        assertEquals("body-for-$url", body1)
        assertEquals(body1, body2)
        // hit пересоздаёт таблицу из примитивов — общая LuaTable не отдаётся
        assertNotSame(table1, table2)
    }

    @Test
    fun `different url misses cache and performs a second request`() {
        val (engine, networkClient) = createEngine()
        val url1 = "http://example.com/book"
        val url2 = "http://example.com/book?tab=2"

        httpGetBody(engine, url1)
        httpGetBody(engine, url1)
        val body2 = httpGetBody(engine, url2).first

        runBlocking { verify(networkClient, times(2)).call(any(), any()) }
        assertEquals("body-for-$url2", body2)
    }

    @Test
    fun `expired entry refetches from network`() {
        val (engine, networkClient) = createEngine()
        val url = "http://example.com/book"
        val otherUrl = "http://example.com/other"

        httpGetBody(engine, url)         // miss — network call 1
        httpGetBody(engine, url)         // hit
        httpGetBody(engine, otherUrl)    // miss — network call 2 (другой ключ)
        Thread.sleep(100)                // > TTL 50ms
        httpGetBody(engine, url)         // просроченная запись — снова в сеть, network call 3

        runBlocking { verify(networkClient, times(3)).call(any(), any()) }
    }
}
