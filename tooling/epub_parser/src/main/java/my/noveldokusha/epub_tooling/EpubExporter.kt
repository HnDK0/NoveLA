package my.noveldokusha.epub_tooling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import my.noveldokusha.core.utils.STRIP_HTML_TAGS
import org.jsoup.parser.Parser
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Тот же разделитель абзацев, что и в ридере: \n\s*\n (а не "\n\n") —
// иначе текст с CRLF (\r\n\r\n) склеится в один абзац.
private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")

private const val MIME_TYPE = "application/epub+zip"

private val MIME_BYTES = MIME_TYPE.toByteArray(Charsets.UTF_8)

private const val XML_PROLOG = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"

private const val XHTML_NS = "xmlns=\"http://www.w3.org/1999/xhtml\""

// xmlns:epub — префикс ДОЛЖЕН быть объявлен: epub:type="toc" без него
// не валиден (unbound prefix), а два голых xmlns в одном теге — дубликат
// атрибута, из-за которого nav.xhtml не является well-formed XML.
private const val EPUB_NS = "xmlns:epub=\"http://www.idpf.org/2007/ops\""

private const val NCX_NS = "xmlns=\"http://www.daisy.org/z3986/2005/ncx/\""

// Размер батча глав, загружаемых за один вызов chapterLoader: ограничивает
// пиковую память (тела глав не копятся в одном списке) и держит число
// bind-переменных SQLite-запроса далеко от лимита ~999.
private const val CHAPTER_BATCH = 200

/** Экранирование XML-спецсимволов — обязательнo, иначе контент главы ломает парсинг книги. */
private fun xmlEscape(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

/** Определение MIME-типа изображения по сигнатуре (magic bytes) — как в isImage из core. */
private fun detectImageMime(bytes: ByteArray): String = when {
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
        "image/jpeg"
    bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
    bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() -> "image/gif"
    bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() -> "image/webp"
    else -> "image/jpeg"
}

/** Расширение файла обложки по MIME — должно совпадать с записанными байтами. */
private fun coverExtension(mime: String): String = when (mime) {
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    else -> "jpg"
}

/**
 * Записывает книгу в формат EPUB3 с максимальной совместимостью ридеров:
 * nav.xhtml (обязательный TOC EPUB3) И toc.ncx (legacy-ридеры ищут именно его,
 * без него многие открывают файл с ошибкой «ncx file missing»).
 *
 * Обложка (если переданы байты) кладётся в images/cover.<ext> и объявляется
 * в манифесте с properties="cover-image" + <meta name="cover"> — это стандарт
 * EPUB3. Отдельной cover.xhtml НЕТ: ридеры, строящие список глав из spine,
 * показали бы её лишней «главой» (NoveLA-ридер приклеивает её к первой главе).
 * Описание книги, если есть, попадает в <dc:description>.
 *
 * Обёртка над [exportStreaming]: список глав уже целиком в памяти — для
 * интерактивных вызовов и тестов. Тяжёлые книги (тысячи глав) экспортируйте
 * через [exportStreaming], чтобы не держать весь текст в памяти (OOM).
 */
suspend fun export(
    outputStream: OutputStream,
    bookTitle: String,
    language: String,
    chapters: List<Pair<String, String>>,
    coverBytes: ByteArray? = null,
    description: String? = null,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
) = exportStreaming(
    outputStream,
    bookTitle,
    language,
    chapters.size,
    { offset, count -> chapters.subList(offset, minOf(offset + count, chapters.size)) },
    coverBytes,
    description,
    onProgress
)

/**
 * Потоковая запись EPUB: главы пишутся в архив по мере загрузки из
 * [chapterLoader] батчами по [CHAPTER_BATCH], поэтому книги на тысячи глав
 * не держат весь текст в памяти одновременно (без этого ~7000 глав
 * валят 256MB-кучу OutOfMemoryError).
 *
 * [chapterLoader] вызывается последовательно с (offset, count) и должен
 * возвращать пары (заголовок, тело) для глав этого диапазона; пустые тела
 * пропускаются. Порядок записей в zip: mimetype, container, главы, затем
 * content.opf/nav.xhtml/toc.ncx/обложка — имена файлов глав известны заранее
 * (chapter-NNN.xhtml), поэтому навигация пишется последней.
 *
 * Прогресс: done — число фактически записанных глав, total — общее число
 * глав книги (известно до начала экспорта).
 */
suspend fun exportStreaming(
    outputStream: OutputStream,
    bookTitle: String,
    language: String,
    totalChapters: Int,
    chapterLoader: suspend (offset: Int, count: Int) -> List<Pair<String, String>>,
    coverBytes: ByteArray? = null,
    description: String? = null,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
) = withContext(Dispatchers.IO) {
    require(totalChapters > 0) { "Список глав пуст — EPUB без контента недопустим" }

    // Один UUID на всю книгу: используется и в dc:identifier, и в dtb:uid NCX.
    val uid = "urn:uuid:${UUID.randomUUID()}"

    // dcterms:modified требует формат без доли секунды: Instant.now().toString()
    // даёт их, поэтому форматируем вручную по UTC.
    val modified = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    val escapedTitle = xmlEscape(bookTitle)
    val escapedDescription = description?.takeIf { it.isNotBlank() }?.let(::xmlEscape)
    val coverMime = coverBytes?.let(::detectImageMime)
    val coverExt = coverMime?.let(::coverExtension)

    ZipOutputStream(outputStream).use { zip ->
        // mimetype обязан быть ПЕРВЫМ и несжатым (спецификация OCF).
        // Для STORED-записи size и crc должны быть известны заранее,
        // иначе closeEntry() бросает ZipException.
        zip.putNextEntry(ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = MIME_BYTES.size.toLong()
            crc = CRC32().apply { update(MIME_BYTES) }.value
        })
        zip.write(MIME_BYTES)
        zip.closeEntry()

        zip.writeEntry(
            "META-INF/container.xml",
            """$XML_PROLOG
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
        )

        // 1) Главы пишутся сразу в архив; заголовки записанных копятся в
        //    validTitles (это десятки КБ даже для тысяч глав) — по ним потом
        //    строятся content.opf, nav.xhtml и toc.ncx.
        val validTitles = mutableListOf<String>()
        var offset = 0
        while (offset < totalChapters) {
            // Отмена: прерываемся между батчами, чтобы не оставлять битый архив на полпути.
            coroutineContext.ensureActive()

            val batch = chapterLoader(offset, minOf(CHAPTER_BATCH, totalChapters - offset))
            offset += CHAPTER_BATCH
            batch.forEach { (title, body) ->
                // Снимаем HTML-сущности один раз: иначе &amp; из исходника после
                // xmlEscape превратится в &amp;amp; и ридер покажет «&amp;» буквально.
                val trimmedTitle = Parser.unescapeEntities(title.trim(), false)
                val trimmedBody = body.trim()
                if (trimmedBody.isEmpty()) return@forEach

                val paragraphs = trimmedBody
                    .replace(STRIP_HTML_TAGS, "") // снимаем разметку, оставляя только текст
                    .let { Parser.unescapeEntities(it, false) }
                    .split(PARAGRAPH_BREAK)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                // Глава, содержащая только разметку (например, один <img>), после
                // снятия тегов не даёт текста — не пишем пустой файл в навигацию.
                if (paragraphs.isEmpty()) return@forEach
                val bodyXml = buildString {
                    paragraphs.forEach { p ->
                        if (isNotEmpty()) appendLine()
                        append("""    <p>${xmlEscape(p)}</p>""")
                    }
                }
                val name = chapterFileName(validTitles.size)
                zip.writeEntry(
                    "OEBPS/$name",
                    """$XML_PROLOG
<html $XHTML_NS>
  <head><title>${xmlEscape(trimmedTitle)}</title></head>
  <body>
    <h2>${xmlEscape(trimmedTitle)}</h2>
$bodyXml
  </body>
</html>"""
                )
                validTitles += trimmedTitle
                onProgress(validTitles.size, totalChapters)
            }
        }
        if (validTitles.isEmpty()) {
            throw IllegalArgumentException("Все главы пусты — нечего записывать")
        }

        // 2) Служебные файлы — по накопленным заголовкам записанных глав.
        val coverImageItem = if (coverMime != null && coverExt != null) {
            """    <item id="cover-image" href="images/cover.$coverExt" media-type="$coverMime" properties="cover-image"/>"""
        } else null
        val manifest = buildString {
            append("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            appendLine()
            append("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
            coverImageItem?.let {
                appendLine()
                append(it)
            }
            validTitles.forEachIndexed { i, _ ->
                val name = chapterFileName(i)
                appendLine()
                append("""    <item id="$name" href="$name" media-type="application/xhtml+xml"/>""")
            }
        }
        val spine = buildString {
            validTitles.forEachIndexed { i, _ ->
                val name = chapterFileName(i)
                if (isNotEmpty()) appendLine()
                append("""    <itemref idref="$name"/>""")
            }
        }
        val metadataDescription = escapedDescription?.let {
            "\n    <dc:description>$it</dc:description>"
        } ?: ""
        val coverMeta = if (coverImageItem != null) "\n    <meta name=\"cover\" content=\"cover-image\"/>" else ""
        // Accessibility-метаданные (schema.org): epubcheck требует accessMode,
        // accessibilityFeature и accessibilityHazard; остальное — рекомендовано.
        val accessibilityMeta = listOf(
            """<meta property="schema:accessMode">textual</meta>""",
            """<meta property="schema:accessMode">visual</meta>""",
            """<meta property="schema:accessModeSufficient">textual;visual</meta>""",
            """<meta property="schema:accessibilityFeature">structuralNavigation</meta>""",
            """<meta property="schema:accessibilityFeature">tableOfContents</meta>""",
            """<meta property="schema:accessibilityHazard">none</meta>""",
            """<meta property="schema:accessibilitySummary">Textual content with images and structural navigation via the table of contents.</meta>"""
        ).joinToString(separator = "\n    ", prefix = "\n    ")
        zip.writeEntry(
            "OEBPS/content.opf",
            """$XML_PROLOG
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>$escapedTitle</dc:title>
    <dc:language>${xmlEscape(language.ifBlank { "und" })}</dc:language>
    <dc:identifier id="book-id">$uid</dc:identifier>$metadataDescription
    <meta property="dcterms:modified">$modified</meta>$coverMeta$accessibilityMeta
  </metadata>
  <manifest>
$manifest
  </manifest>
  <spine toc="ncx">
$spine
  </spine>
</package>"""
        )

        val navItems = buildString {
            validTitles.forEachIndexed { i, title ->
                if (i > 0) appendLine()
                append("""      <li><a href="${chapterFileName(i)}">${xmlEscape(title)}</a></li>""")
            }
        }
        zip.writeEntry(
            "OEBPS/nav.xhtml",
            """$XML_PROLOG
<html $XHTML_NS $EPUB_NS>
  <head><title>$escapedTitle</title></head>
  <body>
    <nav epub:type="toc" id="toc" role="doc-toc">
      <ol>
$navItems
      </ol>
    </nav>
  </body>
</html>"""
        )

        // toc.ncx — старый формат навигации: без него legacy-ридеры
        // сообщают «ncx file missing» и отказываются открывать книгу.
        val navMap = buildString {
            validTitles.forEachIndexed { i, title ->
                if (i > 0) appendLine()
                append(
                    """    <navPoint id="navPoint-${i + 1}" playOrder="${i + 1}">
      <navLabel><text>${xmlEscape(title)}</text></navLabel>
      <content src="${chapterFileName(i)}"/>
    </navPoint>"""
                )
            }
        }
        zip.writeEntry(
            "OEBPS/toc.ncx",
            """$XML_PROLOG
<ncx $NCX_NS version="2005-1">
  <head>
    <meta name="dtb:uid" content="$uid"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>$escapedTitle</text></docTitle>
  <navMap>
$navMap
  </navMap>
</ncx>"""
        )

        // Обложка: только бинарный файл; ссылка на неё — через
        // properties="cover-image" в манифесте (EPUB3-стандарт).
        if (coverBytes != null && coverMime != null && coverExt != null) {
            zip.writeEntryBytes("OEBPS/images/cover.$coverExt", coverBytes)
        }
    }
}

/** Имя файла главы с нумерацией до трёх цифр: chapter-001.xhtml. */
private fun chapterFileName(index: Int): String = "chapter-${(index + 1).toString().padStart(3, '0')}.xhtml"

private fun ZipOutputStream.writeEntry(name: String, content: String) {
    putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

/** Запись бинарного содержимого (обложка) — в отличие от текстовой writeEntry. */
private fun ZipOutputStream.writeEntryBytes(name: String, bytes: ByteArray) {
    putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
    write(bytes)
    closeEntry()
}