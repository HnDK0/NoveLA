package my.noveldokusha.data

import android.net.Uri
import kotlinx.coroutines.runBlocking
import my.noveldokusha.core.isCoverValid
import my.noveldokusha.core.isImage
import my.noveldokusha.network.NetworkClient
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoverRepositoryTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02)
    private fun png() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
    private fun webp() = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x00)
    private fun gif() = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)

    private class FakeNetworkClient : NetworkClient {
        var calls = 0
            private set
        var nextBytes: ByteArray = ByteArray(0)
        var nextSuccess = true

        override val cookieJar: CookieJar = CookieJar.NO_COOKIES

        override suspend fun call(request: Request.Builder, followRedirects: Boolean): Response =
            throw UnsupportedOperationException()

        override suspend fun get(url: String): Response {
            calls++
            return Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(if (nextSuccess) 200 else 404)
                .message("")
                .body(nextBytes.toResponseBody("image/jpeg".toMediaType()))
                .build()
        }

        override suspend fun getWithHeaders(url: String, headers: Map<String, String>): Response =
            throw UnsupportedOperationException()

        override suspend fun get(url: Uri.Builder): Response =
            throw UnsupportedOperationException()
    }

    private fun repo() = CoverRepository(FakeNetworkClient())

    // ---- isImage ----

    @Test
    fun `isImage accepts real image signatures`() {
        assertTrue(isImage(jpeg()))
        assertTrue(isImage(png()))
        assertTrue(isImage(webp()))
        assertTrue(isImage(gif()))
    }

    @Test
    fun `isImage rejects html and empty`() {
        assertFalse(isImage(ByteArray(0)))
        assertFalse(isImage("<html>not an image</html>".toByteArray()))
        assertFalse(isImage("random bytes ......".toByteArray()))
    }

    // ---- isCoverValid ----

    @Test
    fun `isCoverValid true only for existing non-empty image file`() {
        val file = tempDir.newFile()
        file.writeBytes(jpeg())
        assertTrue(isCoverValid(file))

        val empty = tempDir.newFile()
        assertFalse(isCoverValid(empty))

        val html = tempDir.newFile()
        html.writeBytes("<html>".toByteArray())
        assertFalse(isCoverValid(html))
    }

    // ---- ensureCover happy path ----

    @Test
    fun `ensureCover downloads and writes a valid image`() = runBlocking {
        val client = FakeNetworkClient().apply { nextBytes = png() }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")

        val result = repo.ensureCover(cover, "https://example.com/c.png")

        assertEquals(true, result)
        assertEquals(1, client.calls)
        assertTrue(isCoverValid(cover))
    }

    @Test
    fun `ensureCover rejects non-https and blank urls`() = runBlocking {
        val repo = repo()
        val cover = File(tempDir.root, "cover.png")

        assertEquals(false, repo.ensureCover(cover, "http://example.com/c.png"))
        assertEquals(false, repo.ensureCover(cover, ""))
        assertEquals(false, repo.ensureCover(cover, null))
    }

    @Test
    fun `ensureCover fails on http error and does not create file`() = runBlocking {
        val client = FakeNetworkClient().apply { nextSuccess = false }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")

        assertEquals(false, repo.ensureCover(cover, "https://example.com/c.png"))
        assertFalse(cover.exists())
    }

    @Test
    fun `ensureCover fails on non-image body and does not create file`() = runBlocking {
        val client = FakeNetworkClient().apply { nextBytes = "<html>error</html>".toByteArray() }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")

        assertEquals(false, repo.ensureCover(cover, "https://example.com/c.png"))
        assertFalse(cover.exists())
    }

    // ---- atomicity ----

    @Test
    fun `ensureCover leaves a valid existing cover untouched on failed download`() = runBlocking {
        val client = FakeNetworkClient().apply { nextBytes = "<html>corrupt</html>".toByteArray() }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")
        cover.writeBytes(png()) // pre-existing valid cover

        val result = repo.ensureCover(cover, "https://example.com/c.png")

        assertEquals(false, result)
        // The previous valid cover must still be in place.
        assertTrue(isCoverValid(cover))
    }

    // ---- idempotency ----

    @Test
    fun `ensureCover skips an already valid cover without hitting network`() = runBlocking {
        val client = FakeNetworkClient().apply { nextBytes = jpeg() }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")
        cover.writeBytes(png())

        val result = repo.ensureCover(cover, "https://example.com/c.png")

        assertEquals(true, result)
        assertEquals(0, client.calls)
        assertTrue(isCoverValid(cover))
    }

    @Test
    fun `ensureCover does not re-download a valid existing cover`() = runBlocking {
        val client = FakeNetworkClient().apply { nextBytes = jpeg() }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")
        cover.writeBytes(png())

        val result = repo.ensureCover(cover, "https://example.com/c.png")

        assertEquals(true, result)
        assertEquals(0, client.calls)
        assertTrue(isCoverValid(cover))
    }

    @Test
    fun `ensureCover re-downloads a corrupt existing cover`() = runBlocking {
        val client = FakeNetworkClient().apply { nextBytes = jpeg() }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")
        cover.writeBytes("<html>broken</html>".toByteArray())

        val result = repo.ensureCover(cover, "https://example.com/c.png")

        assertEquals(true, result)
        assertTrue(isCoverValid(cover))
    }

    @Test
    fun `ensureCover leaves no orphan temp or backup files`() = runBlocking {
        val client = FakeNetworkClient().apply { nextBytes = jpeg() }
        val repo = CoverRepository(client)
        val cover = File(tempDir.root, "cover.png")

        assertEquals(true, repo.ensureCover(cover, "https://example.com/c.png"))
        assertTrue(isCoverValid(cover))
        assertFalse(File(cover.path + ".bak").exists())
        assertFalse(File(cover.path + ".tmp").exists())
    }
}
