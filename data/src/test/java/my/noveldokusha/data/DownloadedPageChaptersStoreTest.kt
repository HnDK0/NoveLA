package my.noveldokusha.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import my.noveldokusha.feature.local_database.DAOs.DownloadedPageChaptersDao
import my.noveldokusha.feature.local_database.tables.DownloadedPageChapter
import my.noveldokusha.network.NetworkClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Скачивание страничной главы: файлы в downloaded_pages/<sha256>/NNN.ext,
 * переиспользование уже скачанных файлов, удаление вместе со строками БД.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadedPageChaptersStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dao = mock<DownloadedPageChaptersDao>()
    private val networkClient = mock<NetworkClient>()
    private lateinit var store: DownloadedPageChaptersStore

    private val chapterUrl = "https://site/ch/1"
    private val pages = listOf("https://site/img/1.jpg", "https://site/img/2.webp")
    private val bytes1 = "IMG1".toByteArray()
    private val bytes2 = "IMG2".toByteArray()

    @Before
    fun setUp() {
        store = DownloadedPageChaptersStore(context, dao, networkClient)
    }

    private fun chapterDir(url: String = chapterUrl): File = File(
        context.filesDir,
        "downloaded_pages/${sha256(url)}"
    )

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun httpResponse(code: Int, bytes: ByteArray): Response =
        Response.Builder()
            .request(Request.Builder().url("https://site/img/x.jpg").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(bytes.toResponseBody())
            .build()

    @Test
    fun `downloadChapter writes files and row`() {
        runBlocking {
            whenever(networkClient.call(any(), any())).thenReturn(
                httpResponse(200, bytes1),
                httpResponse(200, bytes2)
            )

            val total = store.downloadChapter(chapterUrl, pages)

            assertEquals((bytes1.size + bytes2.size).toLong(), total)
            val file1 = File(chapterDir(), "000.jpg")
            val file2 = File(chapterDir(), "001.webp")
            assertTrue("file1 missing", file1.exists())
            assertTrue("file2 missing", file2.exists())
            assertEquals(bytes1.toList(), file1.readBytes().toList())
            assertEquals(bytes2.toList(), file2.readBytes().toList())
            verify(dao).insertReplace(
                eq(
                    DownloadedPageChapter(
                        url = chapterUrl,
                    pages = JSONArray(pages).toString(),
                        totalBytes = total,
                        quality = "HIGH"
                    )
                )
            )
            verify(networkClient, times(2)).call(any(), any())
        }
    }

    @Test
    fun `downloadChapter reuses existing files instead of re-fetching`() {
        runBlocking {
            File(chapterDir(), "000.jpg").let { it.parentFile?.mkdirs(); it.writeBytes(bytes1) }
            File(chapterDir(), "001.webp").writeBytes(bytes2)

            val total = store.downloadChapter(chapterUrl, pages)

            assertEquals((bytes1.size + bytes2.size).toLong(), total)
            verify(networkClient, never()).call(any(), any())
            // Строка БД всё равно обновляется (актуализация totalBytes)
            verify(dao).insertReplace(any())
        }
    }

    @Test
    fun `getLocalPageFile returns null for missing page`() {
        runBlocking {
            whenever(dao.get(chapterUrl)).thenReturn(
                DownloadedPageChapter(
                    url = chapterUrl,
                    pages = """["https://site/img/1.jpg","https://site/img/2.webp"]""",
                    totalBytes = 0,
                    quality = "HIGH"
                )
            )
            // Файла на диске нет
            assertNull(store.getLocalPageFile(chapterUrl, "https://site/img/1.jpg"))
        }
    }

    @Test
    fun `deleteChapters removes files and rows`() {
        runBlocking {
            File(chapterDir(), "000.jpg").let { it.parentFile?.mkdirs(); it.writeBytes(bytes1) }
            whenever(dao.getByUrls(listOf(chapterUrl))).thenReturn(
                listOf(
                    DownloadedPageChapter(
                        url = chapterUrl,
                        pages = """["https://site/img/1.jpg"]""",
                        totalBytes = bytes1.size.toLong(),
                        quality = "HIGH"
                    )
                )
            )

            store.deleteChapters(listOf(chapterUrl))

            assertTrue("dir not removed", !chapterDir().exists())
            verify(dao).removeRows(listOf(chapterUrl))
        }
    }

    @Test
    fun `getDiskSizeBytes sums real files`() {
        runBlocking {
            File(chapterDir("a"), "000.jpg").let { it.parentFile?.mkdirs(); it.writeBytes(bytes1) }
            File(chapterDir("b"), "000.jpg").let { it.parentFile?.mkdirs(); it.writeBytes(bytes2) }

            assertEquals((bytes1.size + bytes2.size).toLong(), store.getDiskSizeBytes())
        }
    }
}