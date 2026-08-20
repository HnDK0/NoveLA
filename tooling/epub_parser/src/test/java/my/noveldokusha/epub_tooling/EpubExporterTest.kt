package my.noveldokusha.epub_tooling

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class EpubExporterTest {

    private fun exportToBytes(
        title: String = "Test Book",
        language: String = "en",
        chapters: List<Pair<String, String>>,
        coverBytes: ByteArray? = null,
        description: String? = null,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ByteArray = runBlocking {
        val out = ByteArrayOutputStream()
        export(out, title, language, chapters, coverBytes, description, onProgress)
        out.toByteArray()
    }

    /** Распаковка в порядке записей архива — первый ключ обязан быть "mimetype". */
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun paragraphContent(chapterXhtml: String): String {
        val match = Regex("<p>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(chapterXhtml)
        assertNotNull("chapter must contain a <p> element", match)
        return match!!.groupValues[1]
    }

    // ─── 1. Happy path ───────────────────────────────────────────────────────

    @Test
    fun `happy path produces valid epub structure`() {
        val bytes = exportToBytes(
            chapters = listOf(
                "Chapter One" to "Para 1a\n\nPara 1b",
                "Chapter Two" to "Para 2a\n\nPara 2b",
            )
        )
        val entries = unzip(bytes)

        assertEquals("mimetype", entries.keys.first())
        val mimetype = ZipInputStream(bytes.inputStream()).use { zip ->
            val e = zip.nextEntry
            assertEquals(ZipEntry.STORED, e.method)
            zip.readBytes().toString(Charsets.UTF_8)
        }
        assertEquals("application/epub+zip", mimetype)

        for (name in listOf(
            "META-INF/container.xml",
            "OEBPS/content.opf",
            "OEBPS/nav.xhtml",
            "OEBPS/toc.ncx",
            "OEBPS/chapter-001.xhtml",
            "OEBPS/chapter-002.xhtml",
        )) {
            assertTrue("missing entry $name", entries.containsKey(name))
        }

        val opf = entries.getValue("OEBPS/content.opf")
        assertEquals(2, Regex("<itemref idref=").findAll(opf).count())
        assertTrue(opf.contains("""properties="nav""""))
        assertTrue(opf.contains("dcterms:modified"))
        // dc:language обязателен (RFC 5646) и не должен быть пустым.
        assertTrue(opf.contains("<dc:language>en</dc:language>"))
        assertFalse(opf.contains("<dc:language></dc:language>"))
        // toc.ncx должен быть подключён и к манифесту, и к spine — иначе
        // legacy-ридеры сообщают «ncx file missing» и отказываются открывать книгу.
        assertTrue(opf.contains("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""))
        assertTrue(opf.contains("""<spine toc="ncx">"""))

        val ncx = entries.getValue("OEBPS/toc.ncx")
        assertTrue(ncx.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertTrue(ncx.contains("xmlns=\"http://www.daisy.org/z3986/2005/ncx/\""))
        assertTrue(ncx.contains("<docTitle><text>Test Book</text></docTitle>"))
        assertEquals(2, Regex("<navPoint ").findAll(ncx).count())
        assertTrue(ncx.contains("""playOrder="1""""))
        assertTrue(ncx.contains("""playOrder="2""""))
        assertTrue(ncx.contains("""<content src="chapter-001.xhtml"/>"""))
        assertTrue(ncx.contains("""<content src="chapter-002.xhtml"/>"""))

        // nav.xhtml должен быть well-formed: ровно один голый xmlns (XHTML) +
        // объявленный префикс epub для epub:type="toc" — иначе epubcheck
        // отвергает книгу (duplicate xmlns / unbound prefix).
        val nav = entries.getValue("OEBPS/nav.xhtml")
        assertTrue(nav.contains("""xmlns:epub="http://www.idpf.org/2007/ops""""))
        assertFalse(nav.contains("""xmlns="xmlns=""""))
        assertEquals(1, Regex("""<html[^>]*xmlns=""").findAll(nav).count())

        for (name in listOf("OEBPS/chapter-001.xhtml", "OEBPS/chapter-002.xhtml")) {
            val chapter = entries.getValue(name)
            assertTrue(chapter.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
            assertTrue(chapter.contains("xmlns=\"http://www.w3.org/1999/xhtml\""))
            assertFalse(chapter.contains("""xmlns="xmlns=""""))
            assertEquals(2, Regex("<p>").findAll(chapter).count())
        }
    }

    // ─── 2. XML escaping ─────────────────────────────────────────────────────

    @Test
    fun `special characters are xml escaped`() {
        val bytes = exportToBytes(
            chapters = listOf("Esc" to "5 < 10 & 7 > 3 \"quoted\"")
        )
        val chapter = unzip(bytes).getValue("OEBPS/chapter-001.xhtml")
        val p = paragraphContent(chapter)

        assertTrue(p.contains("&lt;"))
        assertTrue(p.contains("&amp;"))
        assertTrue(p.contains("&gt;"))
        assertTrue(p.contains("&quot;"))
        assertFalse(p.contains("<"))
        assertFalse(p.contains(">"))
        // Ни одного «голого» амперсанда вне сущности.
        assertFalse(Regex("&(?!amp;|lt;|gt;|quot;|apos;)").containsMatchIn(p))
    }

    // ─── 3. Tag stripping and paragraph splitting ────────────────────────────

    @Test
    fun `html tags are stripped from body`() {
        val bytes = exportToBytes(
            chapters = listOf("Tags" to "First <img src=\"x\" yrel=\"1.45\"> line<br>second")
        )
        val chapter = unzip(bytes).getValue("OEBPS/chapter-001.xhtml")
        val p = paragraphContent(chapter)

        assertFalse(p.contains("<"))
        assertFalse(p.contains(">"))
        assertFalse(p.contains("img"))
        assertFalse(p.contains("br"))
        assertTrue(p.contains("First"))
        assertTrue(p.contains("second"))
    }

    @Test
    fun `crlf blank line splits into two paragraphs`() {
        val bytes = exportToBytes(
            chapters = listOf("Crlf" to "Para one\r\n\r\nPara two")
        )
        val chapter = unzip(bytes).getValue("OEBPS/chapter-001.xhtml")

        assertEquals(2, Regex("<p>").findAll(chapter).count())
        assertTrue(chapter.contains("<p>Para one</p>"))
        assertTrue(chapter.contains("<p>Para two</p>"))
    }

    // ─── 4. Progress callback ────────────────────────────────────────────────

    @Test
    fun `progress reports each written chapter`() {
        val calls = mutableListOf<Pair<Int, Int>>()
        exportToBytes(
            chapters = listOf("A" to "text", "B" to "text"),
            onProgress = { done, total -> calls.add(done to total) }
        )

        assertEquals(listOf(1 to 2, 2 to 2), calls)
    }

    // ─── 5. Failure and empty-body handling ──────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `empty chapter list throws`() = runBlocking {
        export(ByteArrayOutputStream(), "t", "en", emptyList())
    }

    @Test
    fun `chapter with empty body is skipped`() {
        val bytes = exportToBytes(
            chapters = listOf("First" to "Some text", "Empty" to "   ")
        )
        val entries = unzip(bytes)

        assertTrue(entries.containsKey("OEBPS/chapter-001.xhtml"))
        assertFalse(entries.containsKey("OEBPS/chapter-002.xhtml"))
        assertEquals(1, Regex("<itemref idref=").findAll(entries.getValue("OEBPS/content.opf")).count())
    }

    // ─── 6. Cover and description ────────────────────────────────────────────

    @Test
    fun `cover bytes produce cover image entry and cover page`() {
        // JPEG-сигнатура: FF D8 FF ...
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val bytes = exportToBytes(chapters = listOf("A" to "text"), coverBytes = jpeg)
        val entries = unzip(bytes)
        val rawEntries = unzipRawBytes(bytes)

        assertTrue(entries.containsKey("OEBPS/images/cover.jpg"))
        assertEquals(jpeg.toList(), rawEntries.getValue("OEBPS/images/cover.jpg").toList())
        // cover.xhtml отсутствует намеренно: ридеры, строящие список глав из
        // spine, показали бы её лишней «главой» (NoveLA приклеивает к первой).
        assertFalse(entries.containsKey("OEBPS/cover.xhtml"))

        val opf = entries.getValue("OEBPS/content.opf")
        assertTrue(opf.contains("""<item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>"""))
        assertTrue(opf.contains("""<meta name="cover" content="cover-image"/>"""))
        assertFalse(opf.contains("""<itemref idref="cover""""))
        assertFalse(opf.contains("""<reference href="cover.xhtml""""))
    }

    @Test
    fun `png cover gets png extension and mime`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2)
        val entries = unzip(exportToBytes(chapters = listOf("A" to "text"), coverBytes = png))
        assertTrue(entries.containsKey("OEBPS/images/cover.png"))
        assertTrue(entries.getValue("OEBPS/content.opf").contains("media-type=\"image/png\""))
    }

    @Test
    fun `description is added to metadata and escaped`() {
        val entries = unzip(
            exportToBytes(
                chapters = listOf("A" to "text"),
                description = "Great <book> & \"wow\"",
            )
        )
        val opf = entries.getValue("OEBPS/content.opf")
        assertTrue(opf.contains("<dc:description>Great &lt;book&gt; &amp; &quot;wow&quot;</dc:description>"))
    }

    @Test
    fun `blank description is omitted`() {
        val entries = unzip(exportToBytes(chapters = listOf("A" to "text"), description = "  "))
        assertFalse(entries.getValue("OEBPS/content.opf").contains("dc:description"))
    }

    // ─── 7. Язык книги ──────────────────────────────────────────────────────

    @Test
    fun `blank language falls back to und`() {
        val entries = unzip(
            exportToBytes(chapters = listOf("A" to "text"), language = "")
        )
        val opf = entries.getValue("OEBPS/content.opf")
        assertTrue(opf.contains("<dc:language>und</dc:language>"))
        assertFalse(opf.contains("<dc:language></dc:language>"))
    }

    // ─── 8. Потоковый экспорт (exportStreaming) ─────────────────────────────

    /** Экспорт через [exportStreaming] — тот же путь, что использует BookExportWorker. */
    private fun exportStreamingToBytes(
        totalChapters: Int,
        chapterLoader: suspend (offset: Int, count: Int) -> List<Pair<String, String>>,
    ): ByteArray = runBlocking {
        val out = ByteArrayOutputStream()
        exportStreaming(out, "Test Book", "en", totalChapters, chapterLoader)
        out.toByteArray()
    }

    @Test
    fun `streaming export with 450 chapters uses multiple batches and writes all`() {
        val batchCalls = mutableListOf<Pair<Int, Int>>()
        val bytes = exportStreamingToBytes(totalChapters = 450) { offset, count ->
            batchCalls.add(offset to count)
            (offset until minOf(offset + count, 450)).map { i ->
                "Chapter ${i + 1}" to "Body of chapter ${i + 1}"
            }
        }
        val entries = unzip(bytes)

        // Батч 200 глав: 450 = 200 + 200 + 50 → ровно три вызова загрузчика.
        assertEquals(listOf(0 to 200, 200 to 200, 400 to 50), batchCalls)

        // Все главы записаны: от chapter-001 до chapter-450.
        assertEquals(450, entries.keys.count { it.startsWith("OEBPS/chapter-") })
        assertTrue(entries.containsKey("OEBPS/chapter-001.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter-450.xhtml"))

        // Навигация согласована с записанными главами.
        val opf = entries.getValue("OEBPS/content.opf")
        assertEquals(450, Regex("<itemref idref=").findAll(opf).count())
        val nav = entries.getValue("OEBPS/nav.xhtml")
        assertEquals(450, Regex("<li>").findAll(nav).count())
        val ncx = entries.getValue("OEBPS/toc.ncx")
        assertEquals(450, Regex("<navPoint ").findAll(ncx).count())
    }

    @Test
    fun `chapter numbering beyond 999 keeps unique padded names`() {
        val total = 1005
        val bytes = exportStreamingToBytes(totalChapters = total) { offset, count ->
            (offset until minOf(offset + count, total)).map { i ->
                "Chapter ${i + 1}" to "Body ${i + 1}"
            }
        }
        val entries = unzip(bytes)

        // padStart(3): до 999 — трёхзначный номер, с 1000 — четырёхзначный без обрезки.
        assertTrue(entries.containsKey("OEBPS/chapter-001.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter-999.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter-1000.xhtml"))
        assertTrue(entries.containsKey("OEBPS/chapter-1005.xhtml"))

        // Коллизий имён нет: число записей глав равно числу глав книги.
        assertEquals(total, entries.keys.count { it.startsWith("OEBPS/chapter-") })

        val opf = entries.getValue("OEBPS/content.opf")
        assertEquals(total, Regex("<itemref idref=").findAll(opf).count())
        assertTrue(opf.contains("""<item id="chapter-1000.xhtml" href="chapter-1000.xhtml""""))
        val nav = entries.getValue("OEBPS/nav.xhtml")
        assertEquals(total, Regex("<li>").findAll(nav).count())
        assertTrue(nav.contains("""href="chapter-1000.xhtml""""))
        val ncx = entries.getValue("OEBPS/toc.ncx")
        assertEquals(total, Regex("<navPoint ").findAll(ncx).count())
        assertTrue(ncx.contains("""<content src="chapter-1000.xhtml"/>"""))
    }

    // ─── 9. Главы только из разметки ────────────────────────────────────────

    @Test
    fun `chapter with only img markup is skipped entirely`() {
        val bytes = exportToBytes(
            chapters = listOf(
                "First" to "Some text",
                "ImgOnly" to "<img src=\"cover.jpg\" alt=\"cover\">",
            )
        )
        val entries = unzip(bytes)

        // Глава из одного <img> не даёт текста после снятия тегов — файл не пишется.
        assertTrue(entries.containsKey("OEBPS/chapter-001.xhtml"))
        assertFalse(entries.containsKey("OEBPS/chapter-002.xhtml"))

        // И из навигации она тоже исключена.
        val opf = entries.getValue("OEBPS/content.opf")
        assertEquals(1, Regex("<itemref idref=").findAll(opf).count())
        assertFalse(opf.contains("chapter-002"))
        val nav = entries.getValue("OEBPS/nav.xhtml")
        assertEquals(1, Regex("<li>").findAll(nav).count())
        assertFalse(nav.contains("ImgOnly"))
        val ncx = entries.getValue("OEBPS/toc.ncx")
        assertEquals(1, Regex("<navPoint ").findAll(ncx).count())
        assertFalse(ncx.contains("ImgOnly"))
    }

    // ─── 10. HTML-сущности: unescape один раз, затем xmlEscape ──────────────

    @Test
    fun `html entities are unescaped once then xml escaped`() {
        val bytes = exportToBytes(
            chapters = listOf(
                "Tom &amp; Jerry" to "A &amp;lt; B &amp; C",
            )
        )
        val chapter = unzip(bytes).getValue("OEBPS/chapter-001.xhtml")
        val p = paragraphContent(chapter)

        // &amp;lt; → unescape → &lt; → xmlEscape → &amp;lt; (один уровень экранирования,
        // без двойного &amp;amp;).
        assertTrue(p.contains("&amp;lt;"))
        assertTrue(p.contains("&amp;"))
        assertFalse(p.contains("&amp;amp;"))

        // Заголовок с сущностью: Tom &amp; Jerry → Tom &amp; Jerry (не &amp;amp;).
        assertTrue(chapter.contains("<title>Tom &amp; Jerry</title>"))
        assertTrue(chapter.contains("<h2>Tom &amp; Jerry</h2>"))
        assertFalse(chapter.contains("Tom &amp;amp; Jerry"))
    }

    /** Распаковка с сохранением бинарного содержимого (для сравнения байтов обложки). */
    private fun unzipRawBytes(bytes: ByteArray): Map<String, ByteArray> {
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
}