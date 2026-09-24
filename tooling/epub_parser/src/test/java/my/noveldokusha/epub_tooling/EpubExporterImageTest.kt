package my.noveldokusha.epub_tooling

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class EpubExporterImageTest {

    /** JPEG-сигнатура: FF D8 FF ... */
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    private fun exportStreamingToBytes(
        totalChapters: Int,
        chapterLoader: suspend (offset: Int, count: Int) -> List<Pair<String, String>>,
        imageByteLoader: suspend (src: String) -> ByteArray? = { null },
    ): ByteArray = runBlocking {
        val out = ByteArrayOutputStream()
        exportStreaming(
            out, "Img Book", "en", totalChapters, chapterLoader,
            imageByteLoader = imageByteLoader
        )
        out.toByteArray()
    }

    /** Распаковка с сохранением бинарного содержимого (для сравнения байтов картинки). */
    private fun unzipRaw(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun rotatePositions(entries: Map<String, ByteArray>): List<String> {
        val chapter = entries.getValue("OEBPS/chapter-001.xhtml").toString(Charsets.UTF_8)
        return Regex("<p>(.*?)</p>|<img[^>]*src=\"([^\"]*)\"")
            .findAll(chapter)
            .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            .toList()
    }

    @Test
    fun `image bytes are embedded and manifest updated`() {
        val bytes = exportStreamingToBytes(
            totalChapters = 1,
            chapterLoader = { _, _ -> listOf("WithImg" to "Before <img src=\"fig.png\" alt=\"fig\"> After") },
            imageByteLoader = { if (it == "fig.png") jpeg else null }
        )
        val entries = unzipRaw(bytes)

        // Картинка записана в zip с именем от главы, байты совпадают.
        assertTrue(entries.containsKey("OEBPS/images/chapter-001-img-0.jpg"))
        assertEquals(jpeg.toList(), entries.getValue("OEBPS/images/chapter-001-img-0.jpg").toList())
        assertFalse(entries.containsKey("OEBPS/images/fig.png"))

        // src в теле главы переписан на локальный файл.
        val chapter = entries.getValue("OEBPS/chapter-001.xhtml").toString(Charsets.UTF_8)
        assertTrue(chapter.contains("""<img src="images/chapter-001-img-0.jpg" alt=""/>"""))
        assertFalse(chapter.contains("fig.png"))

        // Manifest объявляет картинку.
        val opf = entries.getValue("OEBPS/content.opf").toString(Charsets.UTF_8)
        assertTrue(opf.contains("""<item id="img-0" href="images/chapter-001-img-0.jpg" media-type="image/jpeg"/>"""))
    }

    @Test
    fun `image without bytes keeps original src and no manifest entry`() {
        val bytes = exportStreamingToBytes(
            totalChapters = 1,
            chapterLoader = { _, _ -> listOf("NoBytes" to "Text <img src=\"remote.png\">") },
            imageByteLoader = { null }
        )
        val entries = unzipRaw(bytes)

        assertFalse(entries.keys.any { it.startsWith("OEBPS/images/") })
        val chapter = entries.getValue("OEBPS/chapter-001.xhtml").toString(Charsets.UTF_8)
        assertTrue(chapter.contains("""<img src="remote.png" alt=""/>"""))
        // Навигация не ссылается на несуществующую картинку.
        val opf = entries.getValue("OEBPS/content.opf").toString(Charsets.UTF_8)
        assertFalse(opf.contains("""<item id="img-"""))
    }

    @Test
    fun `src html entities are unescaped once then xml escaped`() {
        // &amp; в src — типичный скраперный HTML: декодируется до &, loader
        // получает реальный URL, фолбэк пишет ровно один уровень экранирования.
        val loaderSeen = mutableListOf<String>()
        val bytes = exportStreamingToBytes(
            totalChapters = 1,
            chapterLoader = { _, _ ->
                listOf("Ent" to "Text <img src=\"https://img.site/a.png?id=1&amp;token=abc\">")
            },
            imageByteLoader = { src ->
                loaderSeen += src
                null
            }
        )
        val chapter = unzipRaw(bytes).getValue("OEBPS/chapter-001.xhtml").toString(Charsets.UTF_8)

        assertEquals(listOf("https://img.site/a.png?id=1&token=abc"), loaderSeen)
        assertTrue(chapter.contains("""<img src="https://img.site/a.png?id=1&amp;token=abc" alt=""/>"""))
        assertFalse(chapter.contains("&amp;amp;"))
    }

    @Test
    fun `image-only chapter is kept and text order is preserved`() {
        val bytes = exportStreamingToBytes(
            totalChapters = 2,
            chapterLoader = { _, _ ->
                listOf(
                    "Text" to "First\n\nSecond",
                    "Manga" to "<img src=\"page1.png\"><img src=\"page2.jpeg\">",
                )
            },
            imageByteLoader = { if (it.endsWith(".png")) pngBytes() else jpeg }
        )
        val entries = unzipRaw(bytes)

        // Обе главы на месте — глава из одних картинок не потеряна.
        assertTrue(entries.containsKey("OEBPS/chapter-001.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter-002.xhtml"))

        // Порядок текста первой главы сохранён.
        assertEquals(listOf("First", "Second"), rotatePositions(entries))

        // Две картинки второй главы вшиты с уникальными именами.
        assertTrue(entries.containsKey("OEBPS/images/chapter-002-img-0.png"))
        assertTrue(entries.containsKey("OEBPS/images/chapter-002-img-1.jpg"))
        val chapter2 = entries.getValue("OEBPS/chapter-002.xhtml").toString(Charsets.UTF_8)
        assertTrue(chapter2.contains("""<img src="images/chapter-002-img-0.png" alt=""/>"""))
        assertTrue(chapter2.contains("""<img src="images/chapter-002-img-1.jpg" alt=""/>"""))

        // Манифест содержит оба item'а картинок.
        val opf = entries.getValue("OEBPS/content.opf").toString(Charsets.UTF_8)
        assertTrue(opf.contains("""<item id="img-0""""))
        assertTrue(opf.contains("""<item id="img-1""""))
    }

    @Test
    fun `chapter without text and without img src is skipped`() {
        // Глава с «битым» тегом <img> без src: нет текста и нет src — пропускается,
        // но не ломает экспорт книги.
        val bytes = exportStreamingToBytes(
            totalChapters = 2,
            chapterLoader = { _, _ ->
                listOf(
                    "Good" to "Some text",
                    "Broken" to "<img alt=\"x\">",
                )
            },
            imageByteLoader = { jpeg }
        )
        val entries = unzipRaw(bytes)

        assertTrue(entries.containsKey("OEBPS/chapter-001.xhtml"))
        assertFalse(entries.containsKey("OEBPS/chapter-002.xhtml"))
        assertFalse(entries.keys.any { it.startsWith("OEBPS/images/") })
    }

    private fun pngBytes() = byteArrayOf(
        0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3
    )
}