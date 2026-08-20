package my.noveldokusha.epub_tooling

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.BookTextMapper
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

// Потоковый XmlPullParser вместо полного DOM: дерево книги в 7k глав занимает
// сотни МБ и не влезает в heap 256MB. Два прохода по raw-байтам: первый —
// metadata + <binary>-картинки, второй — body → главы.

// Прямой текст этих тегов — whitespace-мусор между элементами, в главу не попадает.
// <title> в списке нет: вне секции (в <poem>, <td>) это контент, а не заголовок.
private val STRUCTURAL_TAGS = setOf("section", "image", "empty-line", "br", "body", "binary")

// Верхний предел одной <binary>-картинки в base64 (≈32 МБ в распакованном виде).
private const val MAX_BINARY_BASE64_LENGTH = 45_000_000

// Верхний предел FB2-файла: легаси-книги в 7k глав весят десятки МБ; больше —
// крафтовый файл (защита от OOM при чтении raw-байтов для двух проходов).
private const val MAX_FB2_SIZE = 128L * 1024 * 1024

private class Fb2XmlParser(
    private val parseBody: Boolean
) {
    // --- результат metadata-прохода ---
    var title: String = "Unknown Title"
    var author: String? = null
    var description: String? = null
    var coverHref: String? = null
    val images = LinkedHashMap<String, ByteArray>()

    // --- результат body-прохода ---
    val chapters = mutableListOf<EpubBook.Chapter>()
    private var chapterIdx = 0

    // --- состояние парсера ---
    private val tagStack = ArrayDeque<String>()
    private var inAnnotation = false
    private val annotationText = StringBuilder()
    private var inBookTitle = false
    private val bookTitleText = StringBuilder()
    private val authorParts = LinkedHashMap<String, StringBuilder>()
    private var currentAuthorField: StringBuilder? = null
    private var inCoverpage = false
    private var binaryId: String? = null
    private val binaryBuffer = StringBuilder()

    private class Section {
        val title = StringBuilder()
        val body = StringBuilder()
        var inTitle = false
    }
    private val sections = ArrayDeque<Section>()

    fun parse(input: InputStream) {
        val parser = Xml.newPullParser()
        // Namespace-обработка выключена: имена и атрибуты ("l:href") приходят в
        // исходной (qualified) форме. kxml не обрабатывает DTD/внешние entity —
        // XXE исключён.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> onStartTag(parser.name, parser)
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> onText(parser.text)
                XmlPullParser.END_TAG -> onEndTag(parser.name)
            }
            event = parser.next()
        }
    }

    private fun onStartTag(name: String, parser: XmlPullParser) {
        val parent = tagStack.lastOrNull()
        tagStack.addLast(name)

        when (name) {
            "annotation" -> {
                inAnnotation = true
                // Несколько <annotation> не конкатенируются: описание берём из последнего.
                annotationText.setLength(0)
            }
            "book-title" -> inBookTitle = true
            "author" -> authorParts.clear()
            "first-name", "middle-name", "last-name" -> {
                if ("author" in tagStack) {
                    val sb = StringBuilder()
                    authorParts[name] = sb
                    currentAuthorField = sb
                }
            }
            "coverpage" -> inCoverpage = true
            "image" -> {
                val href = parser.getAttributeValue(null, "href")
                    ?: parser.getAttributeValue(null, "l:href")
                when {
                    inCoverpage && coverHref == null -> coverHref = href
                    inAnnotation -> annotationText.append("[image]")
                    parseBody && sections.isNotEmpty() -> onImage(href, parent)
                }
            }
            "binary" -> {
                binaryId = parser.getAttributeValue(null, "id")
                binaryBuffer.clear()
            }
            "section" -> if (parseBody) sections.addLast(Section())
            "title" -> if (parseBody && parent == "section") sections.lastOrNull()?.inTitle = true
            "empty-line" -> {
                if (inAnnotation) annotationText.append("\n\n")
                // Внутри <p> пустая строка ничего не добавляет: абзац и так отделён
                // переносами от соседних блоков.
                else if (parent != "p" && parseBody && sections.isNotEmpty() && !sections.last().inTitle)
                    sections.last().body.append("\n")
            }
            "br" -> {
                if (inAnnotation) annotationText.append("\n")
                else if (parseBody && sections.isNotEmpty() && !sections.last().inTitle) sections.last().body.append("\n")
            }
        }
    }

    private fun onText(text: String) {
        if (text.isEmpty()) return
        val top = tagStack.lastOrNull() ?: return
        when {
            binaryId != null -> {
                // Верхний предел одной <binary>-картинки (base64): крафтовая книга
                // с гигантским блобом раньше приводила к OOM.
                if (binaryBuffer.length + text.length > MAX_BINARY_BASE64_LENGTH)
                    throw Exception("FB2 binary too large")
                binaryBuffer.append(text)
            }
            currentAuthorField != null -> currentAuthorField?.append(text)
            inBookTitle -> bookTitleText.append(text)
            inAnnotation -> annotationText.append(text)
            parseBody && sections.isNotEmpty() -> {
                val sec = sections.last()
                when {
                    sec.inTitle -> sec.title.append(text)
                    // <title> вне секции (в <poem>, <td>) — контент абзаца, а не заголовок.
                    top == "title" -> sec.body.append(text)
                    top !in STRUCTURAL_TAGS -> sec.body.append(text)
                }
            }
        }
    }

    private fun onImage(href: String?, parent: String?) {
        val id = href?.removePrefix("#") ?: ""
        val body = sections.last().body
        // Прямой ребёнок секции — XML-вставка изображения; внутри inline-тегов — плейсхолдер.
        if (parent == "section" || parent == "body") {
            val data = images[id] ?: return
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            val yrel = if (options.outWidth > 0) options.outHeight.toFloat() / options.outWidth.toFloat() else 1.45f
            body.appendLine().appendLine(BookTextMapper.ImgEntry(path = id, yrel = yrel).toXMLString()).appendLine()
        } else {
            body.append("[$id]")
        }
    }

    private fun onEndTag(name: String) {
        when (name) {
            "annotation" -> {
                inAnnotation = false
                description = annotationText.toString()
            }
            "book-title" -> {
                inBookTitle = false
                title = bookTitleText.toString().trim().ifEmpty { "Unknown Title" }
            }
            "author" -> if (author == null) {
                val f = authorParts["first-name"]?.toString()?.trim().orEmpty()
                val m = authorParts["middle-name"]?.toString()?.trim().orEmpty()
                val l = authorParts["last-name"]?.toString()?.trim().orEmpty()
                author = buildString {
                    if (f.isNotEmpty()) append(f)
                    if (m.isNotEmpty()) { if (isNotEmpty()) append(" "); append(m) }
                    if (l.isNotEmpty()) { if (isNotEmpty()) append(" "); append(l) }
                }.ifEmpty { null }
                currentAuthorField = null
            }
            "first-name", "middle-name", "last-name" -> currentAuthorField = null
            "coverpage" -> inCoverpage = false
            "binary" -> {
                val id = binaryId
                if (id != null) {
                    // id используется как путь записи: крафтовые значения (абсолютные,
                    // "..", служебные символы) отклоняем до обращения к хранилищу.
                    if (!id.isSafeBookPath()) throw Exception("Unsafe binary id in FB2: $id")
                    val decoded = runCatching { Base64.decode(binaryBuffer.toString(), Base64.DEFAULT) }.getOrNull()
                    if (decoded != null) images[id] = decoded
                }
                binaryId = null
                binaryBuffer.clear()
            }
            "section" -> if (parseBody && sections.isNotEmpty()) {
                val sec = sections.removeLast()
                val bodyText = sec.body.toString().trim()
                if (bodyText.isNotEmpty()) {
                    val secTitle = sec.title.toString().trim()
                    chapters.add(EpubBook.Chapter("fb2_$chapterIdx", secTitle.ifEmpty { "Глава ${chapterIdx + 1}" }, bodyText))
                    chapterIdx++
                }
            }
            "title" -> if (parseBody) sections.lastOrNull()?.inTitle = false
            "p", "subtitle", "cite", "epigraph", "table" -> {
                if (inAnnotation) annotationText.append("\n\n")
                else if (parseBody && sections.isNotEmpty() && !sections.last().inTitle) sections.last().body.append("\n\n")
            }
        }
        tagStack.removeLast()
    }
}

// ── Main parser ─────────────────────────────────────────────────────────────

@Throws(Exception::class)
suspend fun fb2Parser(inputStream: InputStream): EpubBook = withContext(Dispatchers.IO) {
    val rawData = readFb2Data(inputStream)
    val metadata = Fb2XmlParser(parseBody = false).apply { parse(rawData.inputStream()) }
    val body = Fb2XmlParser(parseBody = true).apply {
        images.putAll(metadata.images)
        parse(rawData.inputStream())
    }

    val coverImage = metadata.coverHref?.removePrefix("#")?.let { id ->
        metadata.images[id]?.let { EpubBook.Image(absPath = id, image = it) }
    }

    EpubBook(
        fileName = metadata.title.replace("/", "_").replace("\\", "_"),
        title = metadata.title,
        author = metadata.author,
        description = metadata.description,
        coverImage = coverImage,
        chapters = body.chapters,
        images = metadata.images.map { (id, bytes) -> EpubBook.Image(absPath = id, image = bytes) },
        toc = body.chapters.map { EpubBook.ToCEntry(chapterTitle = it.title, chapterLink = it.absPath) }
    )
}

// ── Cover-only parser ───────────────────────────────────────────────────────

suspend fun fb2CoverParser(inputStream: InputStream): EpubBook.Image? = withContext(Dispatchers.IO) {
    val rawData = readFb2Data(inputStream)
    val metadata = Fb2XmlParser(parseBody = false).apply { parse(rawData.inputStream()) }
    val id = metadata.coverHref?.removePrefix("#") ?: return@withContext null
    metadata.images[id]?.let { EpubBook.Image(absPath = id, image = it) }
}

// ── File reading ────────────────────────────────────────────────────────────

private fun readFb2Data(input: InputStream): ByteArray {
    val raw = readLimited(input, MAX_FB2_SIZE)
    return try {
        ZipInputStream(raw.inputStream()).use { zip ->
            val entry = zip.nextEntry
            if (entry != null && !entry.isDirectory) {
                readLimited(zip, MAX_FB2_SIZE)
            } else {
                raw
            }
        }
    } catch (_: Exception) { raw }
}

// Чтение с верхним пределом: файл-переросток отклоняем вместо OOM.
private fun readLimited(input: InputStream, limit: Long): ByteArray {
    val buffer = ByteArray(64 * 1024)
    val out = java.io.ByteArrayOutputStream()
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) throw Exception("FB2 file too large")
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}