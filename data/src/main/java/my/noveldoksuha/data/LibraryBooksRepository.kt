package my.noveldokusha.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.fileImporter
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.tables.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryBooksRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val chapterDao: ChapterDao,
    private val chapterBodyDao: ChapterBodyDao,
    private val chapterTranslationDao: ChapterTranslationDao,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context,
    private val appFileResolver: AppFileResolver,
    private val appCoroutineScope: AppCoroutineScope,
) {
    val getBooksInLibraryWithContextFlow by lazy {
        libraryDao.getBooksInLibraryWithContextFlow()
    }

    fun getFlow(url: String) = libraryDao.getFlow(url)
    suspend fun insert(book: Book) = if (isValid(book)) libraryDao.insert(book) else Unit
    @Suppress("unused")
    suspend fun insert(books: List<Book>) = libraryDao.insert(books.filter(::isValid))
    suspend fun insertReplace(books: List<Book>) =
        libraryDao.insertReplace(books.filter(::isValid))

    suspend fun remove(bookUrl: String) = libraryDao.remove(bookUrl)
    @Suppress("unused")
    suspend fun remove(book: Book) = libraryDao.remove(book)
    suspend fun update(book: Book) = libraryDao.update(book)
    suspend fun updateLastReadEpochTimeMilli(bookUrl: String, lastReadEpochTimeMilli: Long) =
        libraryDao.updateLastReadEpochTimeMilli(bookUrl, lastReadEpochTimeMilli)

    suspend fun updateLastUpdateEpochTimeMilli(
        bookUrl: String,
        lastUpdateEpochTimeMilli: Long = System.currentTimeMillis()
    ) = libraryDao.updateLastUpdateEpochTimeMilli(bookUrl, lastUpdateEpochTimeMilli)

    suspend fun updateCover(bookUrl: String, coverUrl: String) =
        libraryDao.updateCover(bookUrl, coverUrl)

    suspend fun updateTitle(bookUrl: String, title: String) =
        libraryDao.updateTitle(bookUrl, title)

    suspend fun updateDescription(bookUrl: String, description: String) =
        libraryDao.updateDescription(bookUrl, description)

    suspend fun get(url: String) = libraryDao.get(url)

    suspend fun updateLastReadChapter(bookUrl: String, lastReadChapterUrl: String) =
        libraryDao.updateLastReadChapter(
            bookUrl = bookUrl,
            chapterUrl = lastReadChapterUrl
        )

    suspend fun updateCategory(bookUrl: String, category: String) =
        libraryDao.updateCategory(bookUrl, category)

    suspend fun updateCategoryAndCompleted(bookUrl: String, category: String, isCompleted: Boolean) =
        libraryDao.updateCategoryAndCompleted(bookUrl, category, isCompleted)

    suspend fun updateChaptersListHash(bookUrl: String, hash: String?) =
        libraryDao.updateChaptersListHash(bookUrl, hash)

    suspend fun updateChaptersLastPage(bookUrl: String, page: Int?) =
        libraryDao.updateChaptersLastPage(bookUrl, page)

    suspend fun getAll() = libraryDao.getAll()
    suspend fun count() = libraryDao.count()
    suspend fun getChunk(limit: Int, offset: Int) = libraryDao.getChunk(limit, offset)
    suspend fun getAllInLibrary() = libraryDao.getAllInLibrary()
    suspend fun existInLibrary(url: String) = libraryDao.existInLibrary(url)

    /**
     * Completely deletes a book and ALL associated data:
     * translations, chapter bodies, chapters, and the book record itself.
     * This is the correct way to remove a book from the library.
     */
    suspend fun deleteBookCompletely(bookUrl: String) = withContext(Dispatchers.IO) {
        appDatabase.transaction {
            // 1. Delete chapter translations
            chapterTranslationDao.deleteTranslationsByBookUrls(listOf(bookUrl))
            // 2. Delete chapter bodies
            chapterBodyDao.removeChapterBodiesByBookUrls(listOf(bookUrl))
            // 3. Delete chapters
            chapterDao.removeAllFromBook(bookUrl)
            // 4. Delete the book record itself
            libraryDao.remove(bookUrl)
        }
    }

    /**
     * Completely deletes multiple books and ALL associated data in one transaction.
     */
    suspend fun deleteBooksCompletely(bookUrls: List<String>) = withContext(Dispatchers.IO) {
        if (bookUrls.isEmpty()) return@withContext
        appDatabase.transaction {
            // Process in chunks to avoid SQLite variable limit
            bookUrls.chunked(500).forEach { chunk ->
                chapterTranslationDao.deleteTranslationsByBookUrls(chunk)
                chapterBodyDao.removeChapterBodiesByBookUrls(chunk)
                chapterDao.removeAllFromBooks(chunk)
                chunk.forEach { libraryDao.remove(it) }
            }
        }
    }

    suspend fun toggleBookmark(
        bookUrl: String,
        bookTitle: String
    ): Boolean = appDatabase.transaction {
        val currentTime = System.currentTimeMillis()
        when (val book = get(bookUrl)) {
            null -> {
                insert(
                    Book(
                        title = bookTitle,
                        url = bookUrl,
                        inLibrary = true,
                        addedToLibraryEpochTimeMilli = currentTime,
                        lastUpdateEpochTimeMilli = currentTime
                    )
                )
                true
            }
            else -> {
                val newInLibrary = !book.inLibrary
                update(
                    book.copy(
                        inLibrary = newInLibrary,
                        addedToLibraryEpochTimeMilli = if (newInLibrary) currentTime else book.addedToLibraryEpochTimeMilli,
                        lastUpdateEpochTimeMilli = currentTime
                    )
                )
                newInLibrary
            }
        }
    }

    fun saveImageAsCover(imageUri: Uri, bookUrl: String) {
        appCoroutineScope.launch {
            val imageData = context.contentResolver.openInputStream(imageUri)
                ?.use { it.readBytes() } ?: return@launch
            val bookFolderName = appFileResolver.getLocalBookFolderName(
                bookUrl = bookUrl
            )
            val bookCoverFile = appFileResolver.getStorageBookCoverImageFile(
                bookFolderName = bookFolderName
            )
            fileImporter(targetFile = bookCoverFile, imageData = imageData)
            delay(timeMillis = 1_000)
            updateCover(bookUrl = bookUrl, coverUrl = appFileResolver.getLocalBookCoverPath())
        }
    }
}