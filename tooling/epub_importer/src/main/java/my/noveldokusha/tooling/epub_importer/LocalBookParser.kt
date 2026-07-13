package my.noveldokusha.tooling.epub_importer

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import my.noveldokusha.epub_tooling.EpubBook
import my.noveldokusha.epub_tooling.epubParser
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

enum class LocalBookImportType(
    val displayName: String,
    val mimeType: String,
) {
    EPUB("EPUB", "application/epub+zip"),
    TXT("TXT", "text/plain"),
    PDF("PDF", "application/pdf"),
}

internal fun parseLocalBook(
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
    PDFBoxResourceLoader.init(context.applicationContext)
    val bytes = context.contentResolver.openInputStream(uri).use { inputStream ->
        requireNotNull(inputStream) { context.getString(R.string.failed_get_file) }.readBytes()
    }
    val text = PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
        PDFTextStripper().apply {
            sortByPosition = true
            lineSeparator = "\n"
            paragraphStart = "\n"
            paragraphEnd = "\n"
        }.getText(document)
    }.replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
    require(text.isNotBlank()) { context.getString(R.string.failed_to_extract_text_scanned_pdf) }
    return text.asSingleChapterBook(fileName, "PDF")
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
