package my.noveldokusha.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

/**
 * Регресс бага #216: обложки, созданные приложениями до 1263574c, лежат
 * в легаси-папке Base64(url) без лимита длины, а актуальное имя для длинных
 * https-URL — это h_<sha256-prefix>. resolvedBookImagePath обязан читать
 * легаси-файл, а не возвращать remote URL (тогда офлайн обложки не грузятся).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppFileResolverCoverPathTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var resolver: AppFileResolver

    // Base64 такого URL = 216 символов: больше лимита 200 (актуальная папка h_...),
    // но влезает в лимит ФС 255 — именно такие папки создавали старые приложения.
    private val longBookUrl = "https://example.com/" + "a".repeat(135) + "/book"
    private val coverUrl = "https://example.com/cover.jpg"

    @Before
    fun setUp() {
        resolver = AppFileResolver(context)
        resolver.folderBooks.deleteRecursively()
    }

    private fun legacyCoverFile(bookUrl: String): File = File(
        resolver.folderBooks,
        Base64.getEncoder().encodeToString(bookUrl.encodeToByteArray()) + File.separator + "__cover_image"
    )

    @Test
    fun `long https url resolves legacy base64 cover file when h_ folder is empty`() {
        val encoded = Base64.getEncoder().encodeToString(longBookUrl.encodeToByteArray())
        assertTrue("base64 must exceed 200 chars to hit the legacy branch", encoded.length > 200)
        assertTrue("base64 must fit the 255-byte filesystem limit", encoded.length <= 255)
        assertTrue("h_ folder must differ from legacy one", resolver.getLocalBookFolderName(longBookUrl) != encoded)

        val legacyFile = legacyCoverFile(longBookUrl)
        legacyFile.parentFile!!.mkdirs()
        legacyFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) // PNG magic

        val result = resolver.resolvedBookImagePath(bookUrl = longBookUrl, imagePath = coverUrl)

        assertEquals(legacyFile, result)
    }

    @Test
    fun `long https url without any cover file on disk returns remote url`() {
        val result = resolver.resolvedBookImagePath(bookUrl = longBookUrl, imagePath = coverUrl)

        assertEquals(coverUrl, result)
    }
}
