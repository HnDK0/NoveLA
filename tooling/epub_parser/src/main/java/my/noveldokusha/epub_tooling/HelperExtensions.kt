package my.noveldokusha.epub_tooling

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory


internal val NodeList.elements get() = (0..length).asSequence().mapNotNull { item(it) as? Element }
internal val Node.childElements get() = childNodes.elements
internal fun Document.selectFirstTag(tag: String): Node? = getElementsByTagName(tag).item(0)
internal fun Node.selectFirstChildTag(tag: String) = childElements.find { it.tagName == tag }
internal fun Node.selectChildTag(tag: String) = childElements.filter { it.tagName == tag }
internal fun Node.getAttributeValue(attribute: String): String? =
    attributes?.getNamedItem(attribute)?.textContent

internal fun newSecureDocumentBuilder(): DocumentBuilder {
    val dbf = DocumentBuilderFactory.newInstance()
    // best-effort: на части Android-реализаций (org.apache.harmony...) эти вызовы
    // кидают ParserConfigurationException — изолируем каждый, чтобы один
    // неподдерживаемый флаг не ронял парсер целиком. Реальная защита — EntityResolver ниже.
    runCatching { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { dbf.isXIncludeAware = false }

    val builder = dbf.newDocumentBuilder()
    // Гарантированный кросс-платформенный барьер: любой запрос на резолв внешней
    // entity/DTD (файл, http://, ...) получает пустой источник вместо реального контента.
    // Не зависит от того, поддержал ли парсер флаги выше.
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    return builder
}

internal fun parseXMLFile(inputSteam: InputStream): Document? =
    newSecureDocumentBuilder().parse(inputSteam)

internal fun parseXMLFile(byteArray: ByteArray): Document? = parseXMLFile(byteArray.inputStream())
@Suppress("unused")
internal fun parseXMLText(text: String): Document? = text.reader().runCatching {
    newSecureDocumentBuilder().parse(InputSource(this))
}.getOrNull()

internal val String.decodedURL: String get() = URLDecoder.decode(this, "UTF-8")
internal fun String.asFileName(): String = this.replace("/", "_")

internal fun ZipInputStream.entries() = generateSequence { nextEntry }
