package my.noveldokusha.features.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.Response
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.ReadingHistoryDao
import my.noveldokusha.features.reader.domain.ChapterState
import my.noveldokusha.features.reader.domain.InitialPositionChapter
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ReadingHistory
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ReaderRepository @Inject constructor(
    private val scope: AppCoroutineScope,
    private val database: AppDatabase,
    private val bookChaptersRepository: BookChaptersRepository,
    private val libraryBooksRepository: LibraryBooksRepository,
    private val appRepository: AppRepository,
    private val readingHistoryDao: ReadingHistoryDao,
) {

    fun saveBookLastReadPositionState(
        bookUrl: String,
        newChapter: ChapterState,
        oldChapter: ChapterState? = null
    ) {
        scope.launch(Dispatchers.IO) {
            database.transaction {
                libraryBooksRepository.updateLastReadChapter(
                    bookUrl = bookUrl,
                    lastReadChapterUrl = newChapter.chapterUrl
                )

                if (oldChapter?.chapterUrl != null) bookChaptersRepository.updatePosition(
                    chapterUrl = oldChapter.chapterUrl,
                    lastReadPosition = oldChapter.chapterItemPosition,
                    lastReadOffset = oldChapter.offset
                )

                bookChaptersRepository.updatePosition(
                    chapterUrl = newChapter.chapterUrl,
                    lastReadPosition = newChapter.chapterItemPosition,
                    lastReadOffset = newChapter.offset
                )

                upsertReadingHistory(bookUrl, newChapter.chapterUrl)
            }
        }
    }

    internal suspend fun upsertReadingHistory(bookUrl: String, chapterUrl: String) {
        val book = libraryBooksRepository.get(bookUrl)
        val chapter = bookChaptersRepository.get(chapterUrl)
        val title = chapter?.title
        val total = bookChaptersRepository.countByBookUrl(bookUrl)
        val readChapters = if (total > 0) {
            bookChaptersRepository.countReadByBookUrl(bookUrl)
        } else 0
        readingHistoryDao.upsert(
            ReadingHistory(
                bookUrl = bookUrl,
                bookTitle = book?.title ?: "",
                bookCoverUrl = book?.coverImageUrl ?: "",
                lastReadChapterUrl = chapterUrl,
                lastReadChapterTitle = title,
                lastReadEpochTimeMilli = System.currentTimeMillis(),
                totalChapters = total,
                readChapters = readChapters,
            )
        )
    }

    suspend fun getInitialChapterItemPosition(
        bookUrl: String,
        chapterIndex: Int,
        chapter: Chapter,
    ): InitialPositionChapter = coroutineScope {
        val titleChapterItemPosition = 0 // Hardcode or no?
        val book = async { appRepository.libraryBooks.get(bookUrl) }
        val position = InitialPositionChapter(
            chapterIndex = chapterIndex,
            chapterItemPosition = chapter.lastReadPosition,
            chapterItemOffset = chapter.lastReadOffset,
        )

        when {
            chapter.url == book.await()?.lastReadChapter -> position
            chapter.read -> InitialPositionChapter(
                chapterIndex = chapterIndex,
                chapterItemPosition = titleChapterItemPosition,
                chapterItemOffset = 0,
            )
            else -> position
        }
    }

    suspend fun deleteChapterBody(chapterUrl: String) {
        appRepository.chapterBody.removeRows(listOf(chapterUrl))
    }
    suspend fun downloadChapter(chapterUrl: String): Response<String> {
        Timber.d("ReaderLoad: downloadChapter url=$chapterUrl")
        return appRepository.chapterBody.fetchBody(chapterUrl).onSuccess {
            Timber.d("ReaderLoad: downloadChapter OK url=$chapterUrl bodyLen=${it.length}")
        }.onError {
            Timber.w("ReaderLoad: downloadChapter ERROR url=$chapterUrl msg=${it.message}")
        }
    }
}