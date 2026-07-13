package my.noveldokusha.epub_tooling

import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.BookTextMapper
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.InputStream
import java.util.zip.ZipInputStream

// ── XML helpers ────────────────────────────────────────────────────────────

private fun NodeList.elementSeq(): Sequence<Element> = (0 until length).asSequence()
    .mapNotNull { item(it) as? Element }

private fun Element.childTag(tag: String): Element? {
    val c = childNodes
    for (i in 0 until c.length) {
        val n = c.item(i)
        if (n is Element && (n.tagName == tag || n.localName == tag)) return n
    }
    return null
}

private fun Element.getAttr(name: String): String? =
    getAttribute(name).takeIf { it.isNotEmpty() }
        ?: getAttributeNS(null, name)?.takeIf { it.isNotEmpty() }

private fun parseFb2(data: ByteArray): Document =
    newSecureDocumentBuilder().parse(data.inputStream())

private fun Node.childElements(): List<Element> {
    val c = childNodes
    return (0 until c.length).mapNotNull { c.item(it) as? Element }
}

// ── ByteArray helpers (create instance from ByteArray to satisfy Kotlin type system) ──

private fun newByteArray(src: ByteArray): ByteArray = src

// ── Main parser ─────────────────────────────────────────────────────────────

@Throws(Exception::class)
suspend fun fb2Parser(inputStream: InputStream): EpubBook = withContext(Dispatchers.IO) {
    val rawData = readFb2Data(inputStream)
    val doc = parseFb2(rawData)
    val root = doc.documentElement ?: throw Exception("FB2: root element missing")

    // ── Description ─────────────────────────────────────────────────────
    val ti = root.childTag("description")?.childTag("title-info")
    val title = ti?.childTag("book-title")?.textContent?.trim() ?: "Unknown Title"

    val authorName = ti?.childTag("author")?.let { n ->
        val f = n.childTag("first-name")?.textContent?.trim().orEmpty()
        val m = n.childTag("middle-name")?.textContent?.trim().orEmpty()
        val l = n.childTag("last-name")?.textContent?.trim().orEmpty()
        buildString {
            if (f.isNotEmpty()) append(f)
            if (m.isNotEmpty()) { if (isNotEmpty()) append(" "); append(m) }
            if (l.isNotEmpty()) { if (isNotEmpty()) append(" "); append(l) }
        }.ifEmpty { null }
    }

    val descriptionText = ti?.childTag("annotation")?.let { plainText(it) }

    // ── Binary images ────────────────────────────────────────────────────
    val imageIds = mutableListOf<String>()
    val imageBytes = mutableListOf<ByteArray>()
    fun addImage(name: String, bytes: ByteArray) {
        imageIds.add(name)
        imageBytes.add(bytes)
    }
    fun findImage(name: String): ByteArray? {
        val idx = imageIds.indexOf(name)
        return if (idx >= 0) imageBytes[idx] else null
    }

    for (el in root.childElements()) {
        if (el.localName == "binary" || el.tagName == "binary") {
            val id = el.getAttr("id") ?: continue
            val raw = el.textContent?.trim() ?: continue
            try {
                val bytes = newByteArray(Base64.decode(raw, Base64.DEFAULT))
                addImage(id, bytes)
            } catch (_: Exception) { }
        }
    }

    // ── Cover ───────────────────────────────────────────────────────────
    var coverImage: EpubBook.Image? = null
    val coverpageEl = ti?.childTag("coverpage")
    if (coverpageEl != null) {
        val coverImg = coverpageEl.childElements().firstOrNull()
        if (coverImg != null) {
            val href = coverImg.getAttr("href") ?: coverImg.getAttr("l:href")
            if (href != null) {
                val bytes = findImage(href.removePrefix("#"))
                if (bytes != null) {
                    coverImage = EpubBook.Image(absPath = href.removePrefix("#"), image = bytes)
                }
            }
        }
    }

    // ── Chapters ─────────────────────────────────────────────────────────
    val chapters = mutableListOf<EpubBook.Chapter>()
    var chIdx = 0

    for (el in root.childElements()) {
        if (el.localName == "body" || el.tagName == "body") {
            for (sec in el.childElements()) {
                if (sec.localName == "section" || sec.tagName == "section") {
                    val result = parseSection(sec, ::findImage, chIdx)
                    if (result != null) {
                        chapters.addAll(result.first)
                        chIdx = result.second
                    }
                }
            }
        }
    }

    val safeName = title.replace("/", "_").replace("\\", "_")

    // ── Build image list ─────────────────────────────────────────────────
    val imageList = mutableListOf<EpubBook.Image>()
    for (i in imageIds.indices) {
        imageList.add(EpubBook.Image(absPath = imageIds[i], image = imageBytes[i]))
    }

    return@withContext EpubBook(
        fileName = safeName,
        title = title,
        author = authorName,
        description = descriptionText,
        coverImage = coverImage,
        chapters = chapters,
        images = imageList,
        toc = chapters.map { EpubBook.ToCEntry(chapterTitle = it.title, chapterLink = it.absPath) }
    )
}

// ── Cover-only parser ───────────────────────────────────────────────────────

suspend fun fb2CoverParser(inputStream: InputStream): EpubBook.Image? = withContext(Dispatchers.IO) {
    val rawData = readFb2Data(inputStream)
    val doc = parseFb2(rawData)
    val root = doc.documentElement ?: return@withContext null
    val ti = root.childTag("description")?.childTag("title-info")

    val imgNames = mutableListOf<String>()
    val imgData = mutableListOf<ByteArray>()
    fun addImage(name: String, bytes: ByteArray) {
        imgNames.add(name)
        imgData.add(bytes)
    }
    fun findImage(name: String): ByteArray? {
        val idx = imgNames.indexOf(name)
        return if (idx >= 0) imgData[idx] else null
    }

    for (el in root.childElements()) {
        if (el.localName == "binary" || el.tagName == "binary") {
            val id = el.getAttr("id") ?: continue
            val raw = el.textContent?.trim() ?: continue
            try {
                val bytes = newByteArray(Base64.decode(raw, Base64.DEFAULT))
                addImage(id, bytes)
            } catch (_: Exception) { }
        }
    }

    val href: String = ti?.childTag("coverpage")
        ?.childElements()?.firstOrNull()
        ?.let { it.getAttr("href") ?: it.getAttr("l:href") }
        ?: return@withContext null

    val bytes = findImage(href.removePrefix("#"))
        ?: return@withContext null
    return@withContext EpubBook.Image(absPath = href.removePrefix("#"), image = bytes)
}

// ── File reading ────────────────────────────────────────────────────────────

private fun readFb2Data(input: InputStream): ByteArray {
    val raw = input.readBytes()
    return try {
        ZipInputStream(raw.inputStream()).use { zip ->
            val entry = zip.nextEntry
            if (entry != null && !entry.isDirectory) {
                val unzipped: ByteArray = zip.readBytes()
                unzipped
            } else {
                raw
            }
        }
    } catch (_: Exception) { raw }
}

// ── Section parsing ────────────────────────────────────────────────────────

private typealias BinaryLookup = (String) -> ByteArray?

private fun parseSection(
    sec: Element,
    getBytes: BinaryLookup,
    startIdx: Int
): Pair<List<EpubBook.Chapter>, Int>? {
    val secTitle = sec.childTag("title")?.let { plainText(it) }?.trim().orEmpty()
    val children = sec.childElements().filter {
        it.localName == "section" || it.tagName == "section"
    }
    val chapters = mutableListOf<EpubBook.Chapter>()
    var idx = startIdx

    if (children.isNotEmpty()) {
        for (child in children) {
            val r = parseSection(child, getBytes, idx)
            if (r != null) { chapters.addAll(r.first); idx = r.second }
        }
        val direct = sectionBody(sec, getBytes)
        if (direct.isNotBlank()) {
            chapters.add(EpubBook.Chapter("fb2_$idx", secTitle.ifEmpty { "Глава ${idx + 1}" }, direct))
            idx++
        }
    } else {
        val body = sectionBody(sec, getBytes)
        if (body.isNotBlank()) {
            chapters.add(EpubBook.Chapter("fb2_$idx", secTitle.ifEmpty { "Глава ${idx + 1}" }, body))
            idx++
        }
    }
    return if (chapters.isEmpty()) null else chapters to idx
}

private fun sectionBody(sec: Element, getBytes: BinaryLookup): String {
    val sb = StringBuilder()
    for (el in sec.childElements()) {
        when (el.localName ?: el.tagName) {
            "title" -> { }
            "image" -> {
                val href = el.getAttr("href") ?: el.getAttr("l:href") ?: continue
                val id = href.removePrefix("#")
                val data = getBytes(id) ?: continue
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                val yrel = if (options.outWidth > 0) options.outHeight.toFloat() / options.outWidth.toFloat() else 1.45f
                sb.appendLine()
                sb.appendLine(BookTextMapper.ImgEntry(path = id, yrel = yrel).toXMLString())
                sb.appendLine()
            }
            "p" -> {
                val t = inlineText(el).trim()
                if (t.isNotEmpty()) { sb.appendLine(t); sb.appendLine() }
            }
            "subtitle", "epigraph", "cite" -> {
                val t = inlineText(el).trim()
                if (t.isNotEmpty()) { sb.appendLine(t); sb.appendLine() }
            }
            "empty-line" -> sb.appendLine()
            "table" -> {
                val t = inlineText(el).trim()
                if (t.isNotEmpty()) { sb.appendLine(t); sb.appendLine() }
            }
            else -> {
                val t = inlineText(el).trim()
                if (t.isNotEmpty()) { sb.appendLine(t); sb.appendLine() }
            }
        }
    }
    return sb.toString().trim()
}

// ── Text helpers ────────────────────────────────────────────────────────────

private fun plainText(node: Node): String {
    val sb = StringBuilder()
    val c = node.childNodes
    for (i in 0 until c.length) {
        val child = c.item(i)
        when {
            child is org.w3c.dom.Text -> sb.append(child.textContent)
            child is Element -> when (child.localName ?: child.tagName) {
                "p" -> {
                    val t = inlineText(child).trim()
                    if (t.isNotEmpty()) { sb.appendLine(t); sb.appendLine() }
                }
                "image" -> sb.append("[image]")
                "empty-line" -> sb.append("\n\n")
                "subtitle" -> {
                    val t = inlineText(child).trim()
                    if (t.isNotEmpty()) { sb.appendLine(t); sb.appendLine() }
                }
                else -> sb.append(inlineText(child))
            }
        }
    }
    return sb.toString()
}

private fun inlineText(node: Node): String {
    val sb = StringBuilder()
    val c = node.childNodes
    for (i in 0 until c.length) {
        val child = c.item(i)
        when {
            child is org.w3c.dom.Text -> sb.append(child.textContent)
            child is Element -> when (child.localName ?: child.tagName) {
                "emphasis", "strong", "a", "style" -> sb.append(inlineText(child))
                "image" -> {
                    val href = child.getAttr("href") ?: child.getAttr("l:href") ?: ""
                    sb.append("[${href.removePrefix("#")}]")
                }
                "br" -> sb.append("\n")
                else -> sb.append(child.textContent ?: "")
            }
        }
    }
    return sb.toString()
}