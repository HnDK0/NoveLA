package my.noveldokusha.tooling.epub_importer

import android.content.Context
import android.net.Uri
import my.noveldokusha.epub_tooling.EpubBook
import my.noveldokusha.epub_tooling.epubParser
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream

private const val PDF_TEXT_OPERATOR_SPACING = "\u0000"

enum class LocalBookImportType(
    val displayName: String,
    val mimeType: String,
) {
    EPUB("EPUB", "application/epub+zip"),
    TXT("TXT", "text/plain"),
    PDF("PDF", "application/pdf"),
}

internal suspend fun parseLocalBook(
    context: Context,
    uri: Uri,
    fileName: String,
    type: LocalBookImportType,
): EpubBook {
    return when (type) {
        LocalBookImportType.EPUB -> context.contentResolver.openInputStream(uri).use { inputStream ->
            requireNotNull(inputStream) { context.getString(R.string.failed_get_file) }
            epubParser(inputStream)
        }
        LocalBookImportType.TXT -> parseTxtBook(context, uri, fileName)
        LocalBookImportType.PDF -> parsePdfBook(context, uri, fileName)
    }
}

private fun parseTxtBook(context: Context, uri: Uri, fileName: String): EpubBook {
    val bytes = context.contentResolver.openInputStream(uri).use { inputStream ->
        requireNotNull(inputStream) { context.getString(R.string.failed_get_file) }.readBytes()
    }
    val text = decodeUtf8(bytes)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    require(text.isNotBlank()) { context.getString(R.string.failed_to_extract_text) }
    return text.asSingleChapterBook(fileName, "Text")
}

private fun decodeUtf8(bytes: ByteArray): String {
    val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
    } catch (e: CharacterCodingException) {
        throw IllegalArgumentException("Selected TXT file is not valid UTF-8", e)
    }
}

private fun parsePdfBook(context: Context, uri: Uri, fileName: String): EpubBook {
    val bytes = context.contentResolver.openInputStream(uri).use { inputStream ->
        requireNotNull(inputStream) { context.getString(R.string.failed_get_file) }.readBytes()
    }
    val text = extractSelectablePdfText(bytes)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map { it.trimEnd() }
        .joinToString("\n")
        .replace(Regex("[ \t]{2,}"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    require(text.isNotBlank()) { context.getString(R.string.failed_to_extract_text_scanned_pdf) }
    return text.asSingleChapterBook(fileName, "PDF")
}

/**
 * Lightweight selectable-text extraction for common PDFs.
 *
 * This intentionally avoids bundling a full PDF engine, keeping the release
 * APK near its previous size. It scans page content streams,
 * inflates FlateDecode streams, and extracts text drawing operands from BT/ET
 * sections. Image-only/scanned PDFs naturally produce blank text and surface the
 * existing scanned-PDF error.
 */
private fun extractSelectablePdfText(bytes: ByteArray): String {
    val pdf = bytes.toString(StandardCharsets.ISO_8859_1)
    return STREAM_REGEX.findAll(pdf)
        .mapNotNull { match ->
            val streamBytes = bytes.copyOfRange(match.range.first + match.value.indexOf("stream") + "stream".length, match.range.last - "endstream".length + 1)
                .trimPdfStreamBoundaries()
            decodePdfStream(streamBytes, match.value)
        }
        .joinToString("\n\n", transform = ::extractTextFromContentStream)
        .replace(PDF_TEXT_OPERATOR_SPACING, " ")
}

private fun decodePdfStream(streamBytes: ByteArray, streamObject: String): String? {
    val decodedBytes = if (streamObject.contains("/FlateDecode")) {
        runCatching { InflaterInputStream(ByteArrayInputStream(streamBytes)).readBytes() }.getOrNull()
    } else {
        streamBytes
    } ?: return null
    return decodedBytes.toString(StandardCharsets.ISO_8859_1)
}

private fun ByteArray.trimPdfStreamBoundaries(): ByteArray {
    var start = 0
    var end = size
    if (start < end && this[start] == '\r'.code.toByte()) start++
    if (start < end && this[start] == '\n'.code.toByte()) start++
    while (end > start && (this[end - 1] == '\n'.code.toByte() || this[end - 1] == '\r'.code.toByte())) end--
    return copyOfRange(start, end)
}

private fun extractTextFromContentStream(stream: String): String {
    return TEXT_OBJECT_REGEX.findAll(stream).joinToString("\n") { textObject ->
        val body = textObject.groupValues[1]
        PDF_TEXT_TOKEN_REGEX.findAll(body).joinToString("") { token ->
            when {
                token.groupValues[1].isNotEmpty() -> decodePdfLiteralString(token.groupValues[1])
                token.groupValues[2].isNotEmpty() -> decodePdfHexString(token.groupValues[2])
                else -> operatorSpacing(token.groupValues[3])
            }
        }
    }
}

private fun operatorSpacing(operator: String): String {
    return when (operator) {
        "Tj", "TJ", "'", "\"" -> ""
        "Td", "TD", "T*" -> "\n"
        else -> PDF_TEXT_OPERATOR_SPACING
    }
}

private fun decodePdfLiteralString(value: String): String {
    val builder = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char != '\\' || index == value.lastIndex) {
            builder.append(char)
            index++
            continue
        }
        index++
        when (val escaped = value[index]) {
            'n' -> builder.append('\n')
            'r' -> builder.append('\r')
            't' -> builder.append('\t')
            'b' -> builder.append('\b')
            'f' -> builder.append('\u000C')
            '(', ')', '\\' -> builder.append(escaped)
            '\n' -> Unit
            '\r' -> if (index + 1 < value.length && value[index + 1] == '\n') index++
            in '0'..'7' -> {
                val octal = buildString {
                    append(escaped)
                    repeat(2) {
                        if (index + 1 < value.length && value[index + 1] in '0'..'7') append(value[++index])
                    }
                }
                builder.append(octal.toInt(8).toChar())
            }
            else -> builder.append(escaped)
        }
        index++
    }
    return builder.toString()
}

private fun decodePdfHexString(value: String): String {
    val normalized = value.filterNot { it.isWhitespace() }.let { if (it.length % 2 == 0) it else it + "0" }
    val bytes = normalized.chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
    return when {
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16BE)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16LE)
        else -> bytes.toString(StandardCharsets.ISO_8859_1)
    }
}

private fun String.asSingleChapterBook(fileName: String, chapterTitle: String): EpubBook = EpubBook(
    fileName = fileName,
    title = fileName.substringBeforeLast('.').ifBlank { fileName },
    author = null,
    description = null,
    coverImage = null,
    chapters = listOf(EpubBook.Chapter(absPath = "chapter-1.xhtml", title = chapterTitle, body = this)),
    images = emptyList(),
)

private val STREAM_REGEX = Regex("(?s)<<.*?>>\\s*stream\\r?\\n.*?\\r?\\nendstream")
private val TEXT_OBJECT_REGEX = Regex("(?s)BT(.*?)ET")
private val PDF_TEXT_TOKEN_REGEX = Regex("(?s)\\(((?:\\\\.|[^\\\\()])*)\\)|<([0-9A-Fa-f\\s]+)>|(Tj|TJ|Td|TD|T\\*|'|\")")
