package my.noveldokusha.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.Response
import my.noveldokusha.core.isContentUri
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val db: AppDatabase,
    @ApplicationContext private val context: Context,
    val libraryBooks: LibraryBooksRepository,
    val bookChapters: BookChaptersRepository,
    val chapterBody: ChapterBodyRepository,
    private val appFileResolver: AppFileResolver,
    private val epubImporterRepository: EpubImporterRepository,
    val downloaderRepository: DownloaderRepository,
    private val libraryDao: LibraryDao,
    private val appCoroutineScope: AppCoroutineScope,
) {
    val settings = Settings()
    val eventDataRestored = MutableSharedFlow<Unit>()

    suspend fun toggleBookmark(bookUrl: String, bookTitle: String): Boolean {
        val realUrl = appFileResolver.getLocalIfContentType(bookUrl, bookFolderName = bookTitle)
        val result = if (bookUrl.isContentUri && libraryBooks.get(realUrl) == null) {
            epubImporterRepository.importEpubFromContentUri(
                contentUri = bookUrl,
                bookTitle = bookTitle,
                addToLibrary = true
            ) is Response.Success
        } else {
            libraryBooks.toggleBookmark(bookUrl = realUrl, bookTitle = bookTitle)
        }
        // Если книга добавлена в библиотеку — загружаем жанры в фоне
        if (result && !realUrl.isContentUri) {
            appCoroutineScope.launch {
                downloaderRepository.bookGenres(bookUrl = realUrl).onSuccess { genres ->
                    if (genres.isNotEmpty()) {
                        val normalized = my.noveldokusha.core.utils.GenreUtils.normalize(genres)
                        libraryDao.updateGenres(realUrl, normalized)
                    }
                }
            }
        }
        return result
    }

    /**
     * Completely removes a book and all its data (chapters, bodies, translations).
     * Use this instead of toggleBookmark when the intent is to delete.
     */
    suspend fun deleteBookCompletely(bookUrl: String) {
        libraryBooks.deleteBookCompletely(bookUrl)
    }

    /**
     * Completely removes multiple books and all their data.
     */
    suspend fun deleteBooksCompletely(bookUrls: List<String>) {
        libraryBooks.deleteBooksCompletely(bookUrls)
    }

    suspend fun getDatabaseSizeBytes() = withContext(Dispatchers.IO) {
        context.getDatabasePath(db.name).length()
    }

    fun close() = db.closeDatabase()

    @Suppress("unused")
    fun delete() = context.deleteDatabase(db.name)
    suspend fun vacuum() = db.vacuum()

    @Suppress("unused")
    suspend fun <T> withTransaction(fn: suspend () -> T) = db.transaction(fn)

    inner class Settings {
        /**
         * Removes all data not belonging to library books:
         * books with inLibrary=false and all their orphan chapters/bodies/translations.
         * Also removes orphan chapters/bodies that don't have a parent book at all.
         */
        suspend fun clearNonLibraryData() = withContext(Dispatchers.IO) {
            // Get URLs of books not in library
            val nonLibraryBookUrls = db.libraryDao().getNonLibraryBookUrls()

            if (nonLibraryBookUrls.isNotEmpty()) {
                Timber.d("clearNonLibraryData: Removing ${nonLibraryBookUrls.size} non-library books")
                nonLibraryBookUrls.chunked(500).forEach { chunk ->
                    db.chapterTranslationDao().deleteTranslationsByBookUrls(chunk)
                    db.chapterBodyDao().removeChapterBodiesByBookUrls(chunk)
                    db.chapterDao().removeAllFromBooks(chunk)
                }
                db.libraryDao().removeBooksByUrls(nonLibraryBookUrls)
            }

            // Also clean orphan rows (chapters without books, bodies without chapters)
            db.chapterDao().removeAllNonLibraryRows()
            db.chapterBodyDao().removeAllNonChapterRows()
            db.chapterTranslationDao().removeOrphanedTranslations()
        }

        /**
         * Clears all cached chapter bodies and translations.
         * Library books, chapters, images and progress are NOT affected.
         */
        suspend fun clearChapterCache() = withContext(Dispatchers.IO) {
            db.chapterBodyDao().deleteAll()
            db.chapterTranslationDao().deleteAllTranslations()
        }

        /**
         * Approximate size (in bytes) of all cached chapter bodies.
         */
        suspend fun getChapterCacheSizeBytes(): Long = db.chapterBodyDao().getCacheSizeBytes()

        /**
         * Folder where additional book data like images is stored.
         * Each subfolder must be an unique folder for each book.
         * Each book folder can have an arbitrary structure internally.
         */
        val folderBooks = appFileResolver.folderBooks
    }
}

fun isValid(book: Book): Boolean = book.url.matches("""^(https?|local)://.*""".toRegex())
fun isValid(chapter: Chapter): Boolean =
    chapter.url.matches("""^(https?|local)://.*""".toRegex())