package my.noveldokusha.tooling.local_source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.Grey25
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.core.asSequence
import my.noveldokusha.core.fileImporter
import my.noveldokusha.core.getOrNull
import my.noveldokusha.data.LocalBookImporterRepository
import my.noveldokusha.epub_tooling.epubCoverParser
import my.noveldokusha.epub_tooling.fb2CoverParser
import my.noveldokusha.epub_tooling.epubParser
import my.noveldokusha.epub_tooling.fb2Parser
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.scraper.sources.LocalSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalSources @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val localSourcesDirectories: LocalSourcesDirectories,
    private val appFileResolver: AppFileResolver,
) : LocalSource {
    override val id = "local_source"
    override val nameStrId = R.string.source_name_local
    override val baseUrl = "local://"
    override val catalogUrl = "local://"
    override val isLocalSource = true
    override val language = null
    override val iconUrl: String? = null
    override val iconResId: Int = R.drawable.ic_epub_fb2


    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> {
        // This should always fail as is local
        return Response.Error(
            "LocalSource doesn't have remote API",
            UnsupportedOperationException()
        )
    }

    private val validMIMES = setOf(
        "application/epub+zip",
        "application/x-fictionbook+xml",
        "application/fb2+zip",
        "application/x-zip-compressed-fb2",
        DocumentsContract.Document.MIME_TYPE_DIR
    )

    private fun String.isBookFile(): Boolean {
        val lower = this.lowercase()
        return lower.endsWith(".epub") ||
                lower.endsWith(".fb2") ||
                lower.endsWith(".fb2.zip")
    }

    private fun String.isEpubFile(): Boolean {
        return this.lowercase().endsWith(".epub")
    }

    private fun String.isFb2File(): Boolean {
        val lower = this.lowercase()
        return lower.endsWith(".fb2") || lower.endsWith(".fb2.zip")
    }

    private fun Uri.cursorRecursiveGetAllFiles(): Sequence<BookResult> {
        val rootURI = this
        return appContext.contentResolver.query(
            rootURI,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        ).asSequence().flatMap {
            val mime = it.getString(2)
            val fileName = it.getString(0)
            // Check both MIME type and file extension
            if (mime !in validMIMES && !(mime == DocumentsContract.Document.MIME_TYPE_DIR) && !fileName.isBookFile()) {
                return@flatMap emptySequence()
            }

            val id = it.getString(1)
            when {
                mime == DocumentsContract.Document.MIME_TYPE_DIR -> {
                    val fileURI = DocumentsContract.buildChildDocumentsUriUsingTree(rootURI, id)
                    fileURI.cursorRecursiveGetAllFiles()
                }
                mime in validMIMES || fileName.isBookFile() -> {
                    val fileURI = DocumentsContract.buildDocumentUriUsingTree(rootURI, id)
                    sequenceOf(
                        BookResult(
                            title = fileName,
                            url = fileURI.toString(),
                        )
                    )
                }
                else -> emptySequence()
            }
        }
    }

    private fun Uri.cursorRecursiveSearchAllFilesWithName(text: String): Sequence<BookResult> {
        val rootURI = this
        return appContext.contentResolver.query(
            rootURI,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        ).asSequence().flatMap {
            val mime = it.getString(2)
            val fileName = it.getString(0)
            // Check both MIME type and file extension
            if (mime !in validMIMES && !(mime == DocumentsContract.Document.MIME_TYPE_DIR) && !fileName.isBookFile()) {
                return@flatMap emptySequence()
            }

            val id = it.getString(1)
            when {
                mime == DocumentsContract.Document.MIME_TYPE_DIR -> {
                    val fileURI = DocumentsContract.buildChildDocumentsUriUsingTree(rootURI, id)
                    fileURI.cursorRecursiveSearchAllFilesWithName(text)
                }
                mime in validMIMES || fileName.isBookFile() -> {
                    if (fileName.contains(text, ignoreCase = true)) {
                        val fileURI = DocumentsContract.buildDocumentUriUsingTree(rootURI, id)
                        sequenceOf(
                            BookResult(
                                title = fileName,
                                url = fileURI.toString(),
                            )
                        )
                    } else {
                        sequenceOf()
                    }
                }
                else -> emptySequence()
            }
        }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.IO) {
        tryConnect {
            val files = localSourcesDirectories
                .list
                .asSequence()
                .flatMap {
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        it, DocumentsContract.getTreeDocumentId(it)
                    ).cursorRecursiveGetAllFiles()
                }
                .map { async { tryConnect { addCover(it) }.getOrNull() } }
                .toList()
                .awaitAll()
                .filterNotNull()

            PagedList(
                list = files,
                index = 0,
                isLastPage = true
            )
        }
    }

    private suspend fun addCover(
        bookResult: BookResult
    ): BookResult = withContext(Dispatchers.IO) {
        val coverFile = appFileResolver.getStorageBookCoverImageFile(bookResult.title)
        if (!coverFile.exists()) {
            val inputStream = appContext.contentResolver.openInputStream(bookResult.url.toUri())
                ?: return@withContext bookResult
            val coverImage = inputStream.use { stream ->
                if (bookResult.title.isFb2File()) {
                    fb2CoverParser(inputStream = stream)
                } else {
                    epubCoverParser(inputStream = stream)
                }
            } ?: return@withContext bookResult
            fileImporter(
                targetFile = coverFile,
                imageData = coverImage.image,
            )
        }
        bookResult.copy(
            coverImageUrl = coverFile.canonicalFile.absolutePath
        )
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.IO) {
        if (index > 0) {
            return@withContext Response.Success(PagedList.createEmpty(index))
        }
        tryConnect {
            val files = localSourcesDirectories
                .list
                .asSequence()
                .flatMap {
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        it, DocumentsContract.getTreeDocumentId(it)
                    ).cursorRecursiveSearchAllFilesWithName(input)
                }
                .map { async { tryConnect { addCover(it) }.getOrNull() } }
                .toList()
                .awaitAll()
                .filterNotNull()

            PagedList(
                list = files,
                index = 0,
                isLastPage = true
            )
        }
    }

    private var isImporting by mutableStateOf(false)
    private var importProgress by mutableStateOf("")

    private fun scanDirectoryForBooks(dirUri: Uri): List<Pair<String, Uri>> {
        val treeUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            dirUri, DocumentsContract.getTreeDocumentId(dirUri)
        )
        val books = mutableListOf<Pair<String, Uri>>()
        appContext.contentResolver.query(
            treeUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(0)
                val docId = cursor.getString(1)
                val mime = cursor.getString(2)
                if (fileName.isBookFile() || mime in validMIMES) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    books.add(fileName to fileUri)
                }
            }
        }
        return books
    }

    private suspend fun importAllBooksFromDirectory(dirUri: Uri) {
        withContext(Dispatchers.IO) {
            isImporting = true
            try {
                val books = scanDirectoryForBooks(dirUri)
                var imported = 0
                val epubImporterRepository = EntryPointAccessors.fromApplication<LocalBookImporterEntryPoint>(appContext).epubImporterRepository()
                for ((fileName, fileUri) in books) {
                    importProgress = appContext.getString(
                        R.string.importing_books_progress,
                        imported + 1,
                        books.size
                    )
                    try {
                        val inputStream = appContext.contentResolver.openInputStream(fileUri)
                            ?: continue
                        val bookData = inputStream.use { stream ->
                            if (fileName.isFb2File()) {
                                fb2Parser(inputStream = stream)
                            } else {
                                epubParser(inputStream = stream)
                            }
                        }
                        epubImporterRepository.epubImporter(
                            storageFolderName = fileName,
                            epub = bookData,
                            addToLibrary = true
                        )
                        imported++
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to import: $fileName")
                    }
                }
                importProgress = appContext.getString(R.string.imported_books_count, imported)
            } finally {
                isImporting = false
            }
        }
    }

    @Composable
    override fun ScreenConfig() {
        val context by rememberUpdatedState(LocalContext.current)
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onDoAddLocalSourceDirectory(
                    onResult = { localSourcesDirectories.add(it) }
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CreateNewFolder, null)
                    Text(text = stringResource(R.string.add_local_directory))
                }
            }
            val list by localSourcesDirectories.listState.collectAsState()
            if (list.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_directories_added_please_add_them_to_see_them_in_the_source_catalog_list),
                    Modifier.textPadding()
                )
            }
            if (isImporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 4.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = importProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            for (dirUri in list) {
                val item = remember(dirUri.toString()) { DocumentFile.fromTreeUri(context, dirUri) }
                SlimListItem(
                    headlineContent = {
                        Text(text = item?.name ?: "** Access denied **")
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Folder, null)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        importAllBooksFromDirectory(dirUri)
                                    }
                                },
                                enabled = !isImporting
                            ) {
                                Icon(
                                    Icons.Filled.AddToPhotos,
                                    stringResource(id = R.string.import_all_books)
                                )
                            }
                            IconButton(onClick = { localSourcesDirectories.remove(dirUri) }) {
                                Icon(Icons.Filled.Delete, stringResource(id = R.string.delete))
                            }
                        }
                    }
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalBookImporterEntryPoint {
    fun epubImporterRepository(): my.noveldokusha.data.LocalBookImporterRepository
}