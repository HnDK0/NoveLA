package my.noveldokusha.tooling.novel_migration.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.ChapterBodyRepository
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.NovelMigrationDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ChapterTranslation
import my.noveldokusha.feature.local_database.tables.DownloadTaskEntity
import my.noveldokusha.feature.local_database.tables.MigrationRecord
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.domain.ChapterResult
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MigrationOptions(
    val transferProgress: Boolean = true,
    val transferBodies: Boolean = true,
    val transferTranslations: Boolean = true,
    val deleteOldBook: Boolean = true,
)

data class MigrationResult(
    val oldBookUrl: String,
    val newBookUrl: String,
    val chaptersTotal: Int,
    val chaptersMatched: Int,
    val chaptersWithProgress: Int,
    val chaptersWithBody: Int,
    val success: Boolean,
    val error: String? = null,
)

@Singleton
class MigrationRepository @Inject constructor(
    private val db: AppDatabase,
    private val libraryBooks: LibraryBooksRepository,
    private val bookChapters: BookChaptersRepository,
    private val chapterBody: ChapterBodyRepository,
    private val novelMigrationDao: NovelMigrationDao,
    private val appPreferences: AppPreferences,
    private val scraper: Scraper,
) {
    suspend fun migrate(
        oldBookUrl: String,
        newBookUrl: String,
        newChapters: List<ChapterResult>,
        matchedChapters: List<Pair<ChapterResult, ChapterResult>>,
        newBookTitle: String,
        options: MigrationOptions = MigrationOptions(),
    ): MigrationResult = withContext(Dispatchers.IO) {
        Timber.e("migrate: oldBookUrl=$oldBookUrl newBookUrl=$newBookUrl matched=${matchedChapters.size} newBookTitle=$newBookTitle")
        try {
            val oldBook = libraryBooks.get(oldBookUrl)
            if (oldBook == null) {
                return@withContext MigrationResult(
                    oldBookUrl = oldBookUrl, newBookUrl = newBookUrl,
                    chaptersTotal = 0, chaptersMatched = 0,
                    chaptersWithProgress = 0, chaptersWithBody = 0,
                    success = false, error = "Old book not found in library"
                )
            }

            val oldChapters = bookChapters.chapters(oldBookUrl)
            val oldChapterMap = oldChapters.associateBy { it.url }
            val oldBodyMap = matchedChapters
                .map { (old, _) -> old.url }
                .mapNotNull { url -> db.chapterBodyDao().get(url)?.let { url to it } }
                .toMap()

            val oldSourceId = scraper.getSourceId(oldBookUrl) ?: "unknown"
            val newSourceId = scraper.getSourceId(newBookUrl) ?: "unknown"

            var chaptersWithProgressCount = 0
            var chaptersWithBodyCount = 0

            db.transaction {
                val existingNewBook = libraryBooks.get(newBookUrl)
                val newBook = if (existingNewBook != null) {
                    existingNewBook.copy(
                        title = newBookTitle, inLibrary = true,
                        completed = oldBook.completed, category = oldBook.category,
                        coverImageUrl = oldBook.coverImageUrl.ifEmpty { newBookUrl },
                        description = oldBook.description, genres = oldBook.genres,
                        lastReadEpochTimeMilli = oldBook.lastReadEpochTimeMilli,
                        lastUpdateEpochTimeMilli = System.currentTimeMillis(),
                    )
                } else {
                    Book(
                        title = newBookTitle, url = newBookUrl,
                        completed = oldBook.completed, inLibrary = true,
                        coverImageUrl = oldBook.coverImageUrl.ifEmpty { newBookUrl },
                        description = oldBook.description, lastReadEpochTimeMilli = oldBook.lastReadEpochTimeMilli,
                        addedToLibraryEpochTimeMilli = oldBook.addedToLibraryEpochTimeMilli,
                        lastUpdateEpochTimeMilli = System.currentTimeMillis(),
                        category = oldBook.category, genres = oldBook.genres,
                    )
                }
                libraryBooks.insertReplace(listOf(newBook))

                val oldToNewChapterUrl = mutableMapOf<String, String>()
                val matchedByNewUrl = matchedChapters.associate { (old, new) -> new.url to old }

                for ((idx, newResult) in newChapters.withIndex()) {
                    val oldResult = matchedByNewUrl[newResult.url]
                    val oldChapter = oldResult?.let { oldChapterMap[it.url] }
                    val newChapter = Chapter(
                        title = newResult.title, url = newResult.url, bookUrl = newBookUrl,
                        position = idx,
                        read = if (options.transferProgress) (oldChapter?.read ?: false) else false,
                        lastReadPosition = if (options.transferProgress) (oldChapter?.lastReadPosition ?: 0) else 0,
                        lastReadOffset = if (options.transferProgress) (oldChapter?.lastReadOffset ?: 0) else 0,
                    )
                    db.chapterDao().insertReplace(listOf(newChapter))
                    if (oldResult != null) oldToNewChapterUrl[oldResult.url] = newResult.url

                    if (newChapter.read || newChapter.lastReadPosition > 0) chaptersWithProgressCount++

                    if (options.transferBodies && oldResult != null) {
                        oldBodyMap[oldResult.url]?.let { body ->
                            db.chapterBodyDao().insertReplace(ChapterBody(url = newResult.url, body = body.body))
                            chaptersWithBodyCount++
                        }
                    }

                    if (options.transferTranslations && oldResult != null) {
                        val translations = db.chapterTranslationDao().getTranslationsByChapterUrls(listOf(oldResult.url))
                        for (t in translations) {
                            db.chapterTranslationDao().insertReplace(t.copy(
                                id = 0,
                                chapterUrl = newResult.url
                            ))
                        }
                    }
                }

                oldBook.lastReadChapter?.let { oldLast ->
                    oldToNewChapterUrl[oldLast]?.let { newUrl ->
                        libraryBooks.updateLastReadChapter(newBookUrl, newUrl)
                    }
                }

                val oldTask = db.downloadTaskDao().get(oldBookUrl)
                if (oldTask != null) {
                    val oldUrls = try { JSONArray(oldTask.chapterUrlsJson) } catch (_: Exception) { JSONArray() }
                    val newUrls = JSONArray()
                    for (i in 0 until oldUrls.length()) {
                        val oldUrl = oldUrls.optString(i)
                        newUrls.put(oldToNewChapterUrl[oldUrl] ?: oldUrl)
                    }
                    db.downloadTaskDao().insert(DownloadTaskEntity(
                        bookUrl = newBookUrl, bookTitle = newBookTitle,
                        chapterUrlsJson = newUrls.toString(),
                        currentIndex = oldTask.currentIndex, totalCount = oldTask.totalCount,
                        isPaused = oldTask.isPaused, isCancelled = oldTask.isCancelled,
                        isCompleted = oldTask.isCompleted, isWaitingForNetwork = oldTask.isWaitingForNetwork,
                        errorCount = oldTask.errorCount, successCount = oldTask.successCount,
                        consecutiveErrors = oldTask.consecutiveErrors, skippedCount = oldTask.skippedCount,
                        translationErrorCount = oldTask.translationErrorCount,
                    ))
                    db.downloadTaskDao().delete(oldBookUrl)
                }

                val prompts = appPreferences.TRANSLATION_NOVEL_PROMPTS.value.toMutableMap()
                prompts.remove(oldBookUrl)?.let { prompts[newBookUrl] = it }
                appPreferences.TRANSLATION_NOVEL_PROMPTS.value = prompts

                if (options.deleteOldBook) libraryBooks.deleteBookCompletely(oldBookUrl)

                novelMigrationDao.insert(MigrationRecord(
                    oldBookUrl = oldBookUrl, newBookUrl = newBookUrl,
                    oldSourceId = oldSourceId, newSourceId = newSourceId,
                    status = if (matchedChapters.size == oldChapters.size) "completed" else "partial",
                    chaptersTotal = oldChapters.size, chaptersMatched = matchedChapters.size,
                    chaptersWithProgress = chaptersWithProgressCount, chaptersWithBody = chaptersWithBodyCount,
                    migratedAt = System.currentTimeMillis(),
                ))
            }

            MigrationResult(
                oldBookUrl = oldBookUrl, newBookUrl = newBookUrl,
                chaptersTotal = oldChapters.size, chaptersMatched = matchedChapters.size,
                chaptersWithProgress = chaptersWithProgressCount, chaptersWithBody = chaptersWithBodyCount,
                success = true,
            )
        } catch (e: Exception) {
            Timber.e(e, "Migration failed for $oldBookUrl -> $newBookUrl")
            MigrationResult(
                oldBookUrl = oldBookUrl, newBookUrl = newBookUrl,
                chaptersTotal = 0, chaptersMatched = 0,
                chaptersWithProgress = 0, chaptersWithBody = 0,
                success = false, error = e.message ?: "Unknown error",
            )
        }
    }

    suspend fun getMigrationHistory() = novelMigrationDao.getAll()

    suspend fun deleteMigrationRecord(id: Long) = novelMigrationDao.delete(id)

    suspend fun deleteMigrationRecordsBySourcePair(oldSourceId: String, newSourceId: String) =
        novelMigrationDao.deleteBySourcePair(oldSourceId, newSourceId)

    suspend fun getLibraryBooksFromSource(sourceBaseUrl: String): List<Book> {
        val allBooks = libraryBooks.getAllInLibrary()
        return allBooks.filter { book ->
            book.url.startsWith(sourceBaseUrl.trimEnd('/') + "/") || book.url.startsWith(sourceBaseUrl)
        }
    }

    suspend fun getBookCountPerSource(sources: List<String>): Map<String, Int> = withContext(Dispatchers.IO) {
        val allBooks = libraryBooks.getAllInLibrary()
        sources.associateWith { sourceUrl ->
            allBooks.count { book ->
                book.url.startsWith(sourceUrl.trimEnd('/') + "/") || book.url.startsWith(sourceUrl)
            }
        }
    }
}
