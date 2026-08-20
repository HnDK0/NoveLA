package my.noveldokusha.epub_tooling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

@Throws(Exception::class)
suspend fun epubCoverParser(
    inputStream: InputStream
): EpubBook.Image? = withContext(Dispatchers.Default) {
    val tempFile = copyToTempFile(inputStream)
    try {
        ZipFile(tempFile).use { zip ->
            val container = zip.readEntry("META-INF/container.xml")
                ?: throw Exception("META-INF/container.xml file missing")

            val opfFilePath = parseXmlDocument(container.inputStream())
                ?.selectFirstTag("rootfile")
                ?.attr("full-path")
                ?.decodedURL ?: throw Exception("Invalid container.xml file")

            val opfFile = zip.readEntry(opfFilePath) ?: throw Exception(".opf file missing")

            val document = parseXmlDocument(opfFile.inputStream())
                ?: throw Exception(".opf file failed to parse data")
            val metadata = document.selectFirstTag("metadata")
                ?: throw Exception(".opf file metadata section missing")
            val manifest = document.selectFirstTag("manifest")
                ?: throw Exception(".opf file manifest section missing")

            val metadataCoverId = metadata
                .selectChildTag("meta")
                .find { it.attr("name") == "cover" }
                ?.attr("content")

            val hrefRootPath = File(opfFilePath).parentFile ?: File("")

            data class EpubManifestItem(
                val id: String,
                val absoluteFilePath: String,
                val mediaType: String,
                val properties: String
            )

            val manifestItems = manifest
                .selectChildTag("item").map {
                    EpubManifestItem(
                        id = it.attr("id").orEmpty(),
                        absoluteFilePath = it.attr("href").orEmpty().decodedURL.hrefAbsolutePath(hrefRootPath),
                        mediaType = it.attr("media-type").orEmpty(),
                        properties = it.attr("properties").orEmpty()
                    )
                }.associateBy { it.id }

            manifestItems[metadataCoverId]
                ?.let { item ->
                    zip.readEntry(item.absoluteFilePath)?.let { bytes ->
                        EpubBook.Image(absPath = item.absoluteFilePath, image = bytes)
                    }
                }
        }
    } finally {
        tempFile.delete()
    }
}