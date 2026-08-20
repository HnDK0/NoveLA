package my.noveldokusha.epub_tooling

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import kotlin.io.path.invariantSeparatorsPathString

// Лимит глубины вложенности XML: реальные документы укладываются в десятки
// уровней, глубже — крафтовый файл (см. readElement).
private const val MAX_XML_DEPTH = 512

// Лёгкое дерево XML-элемента для маленьких файлов-метаданных EPUB
// (container.xml, content.opf, toc.ncx). Файлы эти килобайтные — в отличие от
// глав/FB2 — поэтому построение дерева безопасно по памяти.
internal class XmlElement(
    val name: String,
    val attrs: Map<String, String>,
    val text: String,
    val children: List<XmlElement>
) {
    fun attr(name: String): String? = attrs[name]

    // Поиск по всему поддереву (аналог DOM getElementsByTagName().item(0)).
    fun selectFirstTag(tag: String): XmlElement? =
        if (name == tag) this else children.firstNotNullOfOrNull { it.selectFirstTag(tag) }

    // Все вхождения по всему поддереву, порядок обхода — документный (preorder).
    fun selectTag(tag: String): List<XmlElement> =
        (if (name == tag) listOf(this) else emptyList()) + children.flatMap { it.selectTag(tag) }

    fun selectFirstChildTag(tag: String): XmlElement? = children.firstOrNull { it.name == tag }

    fun selectChildTag(tag: String): List<XmlElement> = children.filter { it.name == tag }

    val textContent: String
        get() = buildString {
            append(text)
            children.forEach { append(it.textContent) }
        }
}

// Парсит маленький XML-документ (container/opf/ncx) в лёгкое дерево.
// XmlPullParser (kxml в Android) по умолчанию не обрабатывает DTD и внешние
// entity — XXE-атаки исключены, дополнительных флагов безопасности не нужно.
internal fun parseXmlDocument(input: InputStream): XmlElement? {
    val parser = Xml.newPullParser()
    // Namespace-обработка выключена: имена и атрибуты ("dc:title", "l:href")
    // приходят в исходной (qualified) форме.
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(input, "UTF-8")

    // Между "?>" и корневым элементом может быть whitespace: kxml отдаёт его
    // как TEXT-событие, и валидный документ с отступом не должен теряться.
    var event = parser.next()
    while (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
        event = parser.next()
    }
    if (event != XmlPullParser.START_TAG) return null

    fun readAttrs(): Map<String, String> =
        (0 until parser.attributeCount).associate { i ->
            parser.getAttributeName(i) to parser.getAttributeValue(i)
        }

    // Глубина вложенности ограничена: рекурсивный спуск крафтового документа
    // с сотнями тысяч вложенных тегов переполнил бы стек (StackOverflowError —
    // это Error, его нельзя перехватить как Exception).
    fun readElement(depth: Int): XmlElement {
        if (depth > MAX_XML_DEPTH) throw RuntimeException("XML nesting too deep")
        val name = parser.name
        val attrs = readAttrs()
        val text = StringBuilder()
        val children = mutableListOf<XmlElement>()
        var event = parser.next()
        while (event != XmlPullParser.END_TAG) {
            when (event) {
                XmlPullParser.START_TAG -> children.add(readElement(depth + 1))
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(parser.text)
            }
            event = parser.next()
        }
        return XmlElement(name, attrs, text.toString(), children)
    }

    val root = readElement(depth = 0)
    parser.next() // END_DOCUMENT
    return root
}

internal val String.decodedURL: String get() = URLDecoder.decode(this, "UTF-8")
internal fun String.asFileName(): String = this.replace("/", "_")

// Безопасный путь записи внутри книги: относительный, без обхода каталогов,
// абсолютных префиксов и служебных символов. Крафтовые пути отклоняются на
// этапе парсинга, чтобы не доводить до сбоя импорта позже (zip-slip / DoS).
internal fun String.isSafeBookPath(): Boolean =
    isNotEmpty() &&
        !startsWith("/") &&
        !startsWith("\\") &&
        !contains("..") &&
        !contains(":") &&
        none { it.code < 0x20 }

// Абсолютный путь записи внутри EPUB относительно каталога OPF-файла.
// Canonical-нормализация нейтрализует "a/../b" — результат остаётся внутри книги.
// Пути используются только для чтения из архива (zip.readEntry), на диск ничего
// не пишется, поэтому zip-slip невозможен; строгая проверка isSafeBookPath()
// здесь лишь ломала импорт валидных книг с href вида "../cover.jpg".
internal fun String.hrefAbsolutePath(rootPath: File): String {
    return File(rootPath, this)
        .canonicalFile
        .toPath()
        .invariantSeparatorsPathString
        .removePrefix("/")
}