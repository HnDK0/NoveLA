package my.noveldokusha.epub_tooling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import my.noveldokusha.epub_tooling.EpubBook.*
import org.jsoup.Jsoup
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

// Импорт потоковый: temp-файл + ZipFile дают произвольный доступ к записям,
// в памяти держится только одна глава, а не весь архив (OOM на книгах в 7k глав).
internal suspend fun copyToTempFile(inputStream: InputStream): File = withContext(Dispatchers.IO) {
    File.createTempFile("novela_epub_import_", ".epub").apply {
        deleteOnExit()
        outputStream().use { out -> inputStream.copyTo(out) }
    }
}

// Верхний предел размера одной распакованной записи: крафтовый архив с одной
// огромной записью (zip-bomb) раньше приводил к OOM.
private const val MAX_ENTRY_SIZE = 32L * 1024 * 1024

// Суммарный бюджет на все изображения книги: MAX_ENTRY_SIZE ограничивает одну
// запись, но сотни картинок по мегабайту каждая всё равно дают OOM. Превышение
// бюджета не роняет импорт — оставшиеся записи пропускаются.
private const val MAX_TOTAL_IMAGE_BYTES = 64L * 1024 * 1024

internal fun ZipFile.readEntry(path: String): ByteArray? {
    val entry = getEntry(path) ?: return null
    if (entry.size > MAX_ENTRY_SIZE) throw Exception("EPUB entry too large: ${entry.name}")
    return getInputStream(entry).use { it.readBytes() }
}

@Throws(Exception::class)
suspend fun epubParser(
    inputStream: InputStream
): EpubBook = withContext(Dispatchers.Default) {
    val tempFile = copyToTempFile(inputStream)
    try {
        ZipFile(tempFile).use { zip -> parseEpub(zip) }
    } finally {
        tempFile.delete()
    }
}

private suspend fun parseEpub(zip: ZipFile): EpubBook {
    val container = zip.readEntry("META-INF/container.xml")
        ?: throw Exception("META-INF/container.xml file missing")

    val opfFilePath = parseXmlDocument(container.inputStream())
        ?.selectFirstTag("rootfile")
        ?.attr("full-path")
        ?.decodedURL ?: throw Exception("Invalid container.xml file")
    // Extract rootPath
    val rootPath = opfFilePath.substringBefore('/', "") // Get the part before the first slash
    val opfFile = zip.readEntry(opfFilePath) ?: throw Exception(".opf file missing")

    val document = parseXmlDocument(opfFile.inputStream())
        ?: throw Exception(".opf file failed to parse data")
    val metadata = document.selectFirstTag("metadata")
        ?: throw Exception(".opf file metadata section missing")
    val manifest = document.selectFirstTag("manifest")
        ?: throw Exception(".opf file manifest section missing")
    val spine = document.selectFirstTag("spine")
        ?: throw Exception(".opf file spine section missing")
    val guide = document.selectFirstTag("guide")
    val metadataTitle = metadata.selectFirstChildTag("dc:title")?.textContent
        ?: "Unknown Title"
    val metadataCreator = metadata.selectFirstChildTag("dc:creator")?.textContent

    val metadataDesc = metadata.selectFirstChildTag("dc:description")?.textContent

    val metadataCoverId = metadata
        .selectChildTag("meta")
        .find { it.attr("name") == "cover" }
        ?.attr("content")


    val hrefRootPath = File(opfFilePath).parentFile ?: File("")

    val manifestItems = manifest.selectChildTag("item").map {
        ManifestItem(
            id = it.attr("id").orEmpty(),
            absPath = it.attr("href").orEmpty().decodedURL.hrefAbsolutePath(hrefRootPath),
            mediaType = it.attr("media-type").orEmpty(),
            properties = it.attr("properties").orEmpty()
        )
    }.associateBy { it.id }



    fun parseCoverImageFromXhtml(coverFilePath: String): Image? {
        val coverData = zip.readEntry(coverFilePath) ?: return null
        val doc = Jsoup.parse(coverData.inputStream(), "UTF-8", "")
        // Find the <img> tag within the XHTML file
        val imgTag = doc.selectFirst("img")

        if (imgTag != null) {
            val src = imgTag.attr("src")
            if (src.isNotEmpty()) {
                var imgSrc = src.hrefAbsolutePath(hrefRootPath)
                if (!imgSrc.startsWith("$rootPath/")) {
                    imgSrc = "$rootPath/$imgSrc"
                }

                val imgFile = zip.readEntry(imgSrc)

                if (imgFile != null) {
                    return Image(absPath = imgSrc, image = imgFile)
                }
            }
        }
        return null
    }

    // 1. Primary Method: Try to get the cover image from the manifest
    var coverImage = manifestItems[metadataCoverId]
        ?.let { item ->
            zip.readEntry(item.absPath)?.let { bytes ->
                Image(absPath = item.absPath, image = bytes)
            }
        }

    // 2. Fallback: Check the `<guide>` tag if the primary method didn't yield a cover
    if (coverImage == null) {
        var coverHref = guide?.selectChildTag("reference")
            ?.find { it.attr("type") == "cover" }
            ?.attr("href")?.decodedURL?.hrefAbsolutePath(hrefRootPath)

        if (guide == null) {
            val manifestCoverItem = manifestItems["cover"]
            coverHref = manifestCoverItem?.absPath
        }
        if (coverHref != null) {
            coverImage = parseCoverImageFromXhtml(coverHref)
        }
    }

    val ncxFile = manifestItems["ncx"]?.absPath?.let { zip.readEntry(it) }
        ?: throw Exception("ncx file missing")


    val doc = parseXmlDocument(ncxFile.inputStream())
        ?: throw Exception("Invalid NCX file")
    val navMap = doc.selectFirstTag("navMap") ?: throw Exception("Invalid NCX file: navMap not found")

    val tocEntries = navMap.selectTag("navPoint").map { navPoint ->
        // Схлопывание whitespace в заголовке (аналог Jsoup .text()): многострочный
        // <text> иначе даёт заголовки с переносами строк.
        val title = (navPoint.selectFirstTag("navLabel")?.selectFirstTag("text")?.textContent ?: "")
            .replace(Regex("\\s+"), " ")
            .trim()
        var link = navPoint.selectFirstTag("content")?.attr("src") ?: "" // Add the prefix
        if (!link.startsWith(rootPath))
            link = "$rootPath/$link"
        ToCEntry(title, link)
    }

    // Function to check if a spine item is a chapter
    fun isChapter(item: ManifestItem): Boolean {
        val extension = item.absPath.substringAfterLast('.')
        return listOf("xhtml", "xml", "html").contains(extension)
    }

    fun findTocEntryForChapter(tocEntries: List<ToCEntry>, chapterUrl: String): ToCEntry? {
        // Remove any potential fragment identifier from chapterUrl
        val chapterUrlWithoutFragment = chapterUrl.substringBefore('#')
        return tocEntries.firstOrNull {
            it.chapterLink.substringBefore('#').equals(chapterUrlWithoutFragment, ignoreCase = true)
        }
    }

    // Parse chapter bodies in parallel (Jsoup is the bottleneck on large books),
    // then aggregate in spine order. Batches cap peak memory: without batching all
    // chapter bodies would be in RAM at once (OOM on 7k-chapter books).
    val chapters = mutableListOf<Chapter>()
    var currentTOC: ToCEntry? = null
    // StringBuilder вместо конкатенации: += на 7k глав даёт O(n²) копирований.
    var currentChapterBody = StringBuilder()

    coroutineScope {
        spine.selectChildTag("itemref")
            .mapNotNull { itemRef ->
                val itemId = itemRef.attr("idref").orEmpty()
                val spineItem = manifestItems[itemId]
                if (spineItem == null || !isChapter(spineItem)) return@mapNotNull null
                val spineUrl = if (spineItem.absPath.startsWith(rootPath))
                    spineItem.absPath else "$rootPath/${spineItem.absPath}"
                spineItem to spineUrl
            }
            .chunked(128)
            .forEach { batch ->
                val parsed = batch.map { (spineItem, spineUrl) ->
                    async {
                        val parser = EpubXMLFileParser(
                            spineUrl,
                            zip.readEntry(spineUrl) ?: ByteArray(0),
                            zip
                        )
                        ParsedChapter(spineItem, spineUrl, parser.parseAsDocument())
                    }
                }.awaitAll()

                for (p in parsed) {
                    val tocEntry = findTocEntryForChapter(tocEntries, p.spineUrl)

                    // If currentTOC exists and we have a new tocEntry, add the accumulated chapter content
                    if (currentTOC != null && tocEntry != null && currentChapterBody.isNotEmpty()) {
                        chapters.add(Chapter(currentTOC!!.chapterLink, currentTOC!!.chapterTitle, currentChapterBody.toString()))
                        currentChapterBody = StringBuilder()
                    }

                    if (tocEntry == null) {
                        if (!p.output.body.isBlank()) currentChapterBody.append("\n\n").append(p.output.body)
                    } else {
                        currentTOC = tocEntry
                        // Append the chapter content to the current chapter body
                        if (!p.output.body.isBlank()) currentChapterBody.append("\n\n").append(p.output.body)
                    }
                }
            }
    }

    // Add the last chapter if any content remains
    if (currentTOC != null && currentChapterBody.isNotEmpty()) {
        chapters.add(Chapter(currentTOC!!.chapterLink, currentTOC!!.chapterTitle, currentChapterBody.toString()))
    }


    val imageExtensions =
        listOf("png", "gif", "raw", "jpg", "jpeg", "webp", "svg").map { ".$it" }

    // Загрузка картинки с учётом общего бюджета: превышение → null (запись
    // пропускается, импорт не падает).
    var totalImageBytes = 0L
    fun loadImage(absPath: String, bytes: ByteArray): Image? {
        if (totalImageBytes + bytes.size > MAX_TOTAL_IMAGE_BYTES) return null
        totalImageBytes += bytes.size
        return Image(absPath = absPath, image = bytes)
    }

    val unlistedImages = zip.entries()
        .asSequence()
        .filter { entry ->
            imageExtensions.any { entry.name.endsWith(it, ignoreCase = true) }
        }
        .mapNotNull { entry ->
            zip.readEntry(entry.name)?.let { loadImage(entry.name, it) }
        }

    val listedImages = manifestItems.asSequence()
        .map { it.value }
        .filter { it.mediaType.startsWith("image") }
        .mapNotNull { item -> zip.readEntry(item.absPath)?.let { loadImage(item.absPath, it) } }

    val images = (listedImages + unlistedImages).distinctBy { it.absPath }


    return EpubBook(
        fileName = metadataTitle.asFileName(),
        title = metadataTitle,
        author = metadataCreator,
        description = metadataDesc,
        coverImage = coverImage,
        chapters = chapters.toList(),
        images = images.toList(),
    )
}

private data class ParsedChapter(
    val spineItem: ManifestItem,
    val spineUrl: String,
    val output: EpubXMLFileParser.Output
)