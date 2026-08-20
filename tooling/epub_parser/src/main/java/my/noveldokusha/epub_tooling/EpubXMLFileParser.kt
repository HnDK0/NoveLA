package my.noveldokusha.epub_tooling

import android.graphics.BitmapFactory
import android.util.Xml
import my.noveldokusha.core.BookTextMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.invariantSeparatorsPathString

internal class EpubXMLFileParser(
    fileAbsolutePath: String,
    val data: ByteArray,
    private val zipFile: ZipFile
) {
    data class Output(val title: String?, val body: String)

    private val fileParentFolder: File = File(fileAbsolutePath).parentFile ?: File("")

    fun parseAsDocument(): Output =
        // Потоковый XmlPullParser — минимальные аллокации (на книгах в тысячи глав
        // Jsoup-DOM каждой главы даёт GC-шторм). Невалидный HTML (незакрытые теги,
        // нестандартные entities) роняет XmlPull — откатываемся на толерантный Jsoup.
        runCatching { parseAsDocumentXmlPull() }
            .getOrElse { parseAsDocumentJsoup() }

    private fun parseAsDocumentXmlPull(): Output {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(data.inputStream(), "UTF-8")

        val out = StringBuilder()
        val titleBuffer = StringBuilder()
        val pBuffer = StringBuilder()
        var title: String? = null
        var inTitle = false
        var inP = false
        var skipDepth = 0 // head/script/style: содержимое не попадает в книгу
        var textDepth = 0 // вложенные текстовые контейнеры (div/span/...)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name == "html" || name == "body" -> {}
                        name == "head" || name == "script" || name == "style" -> skipDepth++
                        name == "p" -> {
                            inP = true
                            pBuffer.setLength(0)
                        }
                        name == "br" ->
                            when {
                                inP -> pBuffer.append('\n')
                                inTitle -> titleBuffer.append(' ')
                                else -> out.append('\n')
                            }
                        name == "hr" ->
                            when {
                                inP -> pBuffer.append("\n\n")
                                !inTitle -> out.append("\n\n")
                            }
                        name == "img" || name == "image" -> {
                            val relPath = parser.getAttributeValue(null, "src")
                                ?: parser.getAttributeValue(null, "xlink:href")
                                ?: ""
                            when {
                                inP -> pBuffer.append(declareImgEntry(relPath))
                                !inTitle -> out.append(declareImgEntry(relPath))
                            }
                        }
                        name == "h1" || name == "h2" || name == "h3" ||
                            name == "h4" || name == "h5" || name == "h6" ->
                            if (!inTitle && title == null) inTitle = true else textDepth++
                        inP -> {} // вложенный тег внутри <p>: текст всё равно идёт в параграф
                        inTitle -> {} // вложенный тег внутри заголовка
                        else -> textDepth++
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text
                    when {
                        skipDepth > 0 -> {}
                        inTitle -> titleBuffer.append(text)
                        inP -> pBuffer.append(text)
                        textDepth > 0 -> {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty()) out.append(trimmed).append("\n\n")
                        }
                        else -> out.append(text.trim())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    when {
                        name == "head" || name == "script" || name == "style" -> skipDepth--
                        name == "p" -> if (inP) {
                            val paragraph = pBuffer.toString().trim()
                            if (paragraph.isNotEmpty()) out.append(paragraph).append("\n\n")
                            inP = false
                        }
                        name == "h1" || name == "h2" || name == "h3" ||
                            name == "h4" || name == "h5" || name == "h6" ->
                            if (inTitle) {
                                inTitle = false
                                title = titleBuffer.toString().replace(Regex("\\s+"), " ").trim()
                                    .ifEmpty { null }
                            } else if (textDepth > 0) textDepth--
                        inP -> {} // баланс вложенных тегов внутри <p>
                        else -> if (textDepth > 0) textDepth--
                    }
                }
                else -> {} // COMMENT, DOCDECL, PROCESSING_INSTRUCTION
            }
            event = parser.next()
        }
        // Незакрытый <p> в битом XML: XmlPull не требует баланса тегов,
        // flush остатка, чтобы текст параграфа не потерялся.
        if (inP) {
            val paragraph = pBuffer.toString().trim()
            if (paragraph.isNotEmpty()) out.append(paragraph).append("\n\n")
        }
        return Output(title, out.toString())
    }

    private fun parseAsDocumentJsoup(): Output {
        val body = Jsoup.parse(data.inputStream(), "UTF-8", "").body()

        val title = body.selectFirst("h1, h2, h3, h4, h5, h6")?.text()
        body.selectFirst("h1, h2, h3, h4, h5, h6")?.remove()

        return Output(
            title = title,
            body = getNodeStructuredText(body)
        )
    }

    fun parseAsImage(absolutePathImage: String): String {
        // Use run catching so it can be run locally without crash
        val bitmap = zipFile.readEntry(absolutePathImage)?.runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(this, 0, this.size, options)
            options.outWidth to options.outHeight
        }?.getOrNull()

        val text = BookTextMapper.ImgEntry(
            path = absolutePathImage,
            // decodeByteArray с inJustDecodeBounds может молча вернуть null —
            // тогда outWidth остаётся 0, и деление дало бы NaN. Ширина <= 0 —
            // тот же дефолт 1.45f, что и при отсутствии битмапа.
            yrel = bitmap?.let { (w, h) -> if (w > 0) h.toFloat() / w.toFloat() else 1.45f } ?: 1.45f
        ).toXMLString()

        return "\n\n$text\n\n"
    }

    // Rewrites the image node to xml for the next stage.
    private fun declareImgEntry(node: org.jsoup.nodes.Node): String {
        val attrs = node.attributes().associate { it.key to it.value }
        return declareImgEntry(attrs["src"] ?: attrs["xlink:href"] ?: "")
    }

    private fun declareImgEntry(relPath: String): String {
        val absolutePathImage = File(fileParentFolder, relPath.decodedURL)
            .canonicalFile
            .toPath()
            .invariantSeparatorsPathString
            .removePrefix("/")

        return parseAsImage(absolutePathImage)
    }

    // Аккумулятор вместо joinToString на каждом уровне рекурсии:
    // joinToString копировал всё поддерево на каждом узле (O(n^2) на главу),
    // на книгах в тысячи глав это гигабайты скопированных строк и GC-шторм.
    private fun getPTraverse(node: org.jsoup.nodes.Node, out: StringBuilder) {
        val sb = StringBuilder()
        for (child in node.childNodes()) {
            when {
                child.nodeName() == "br" -> sb.append('\n')
                child.nodeName() == "img" -> sb.append(declareImgEntry(child))
                child.nodeName() == "image" -> sb.append(declareImgEntry(child))
                child is TextNode -> sb.append(child.text())
                else -> getPTraverse(child, sb)
            }
        }
        val paragraph = sb.toString().trim()
        if (paragraph.isNotEmpty()) out.append(paragraph).append("\n\n")
    }

    private fun getNodeTextTraverse(node: org.jsoup.nodes.Node, out: StringBuilder) {
        for (child in node.childNodes()) {
            when {
                child.nodeName() == "p" -> getPTraverse(child, out)
                child.nodeName() == "br" -> out.append('\n')
                child.nodeName() == "hr" -> out.append("\n\n")
                child.nodeName() == "img" -> out.append(declareImgEntry(child))
                child.nodeName() == "image" -> out.append(declareImgEntry(child))
                child is TextNode -> {
                    val text = child.text().trim()
                    if (text.isNotEmpty()) out.append(text).append("\n\n")
                }
                else -> getNodeTextTraverse(child, out)
            }
        }
    }

    private fun getNodeStructuredText(node: org.jsoup.nodes.Node): String {
        val out = StringBuilder()
        for (child in node.childNodes()) {
            when {
                child.nodeName() == "p" -> getPTraverse(child, out)
                child.nodeName() == "br" -> out.append('\n')
                child.nodeName() == "hr" -> out.append("\n\n")
                child.nodeName() == "img" -> out.append(declareImgEntry(child))
                child.nodeName() == "image" -> out.append(declareImgEntry(child))
                child is TextNode -> out.append(child.text().trim())
                else -> getNodeTextTraverse(child, out)
            }
        }
        return out.toString()
    }
}