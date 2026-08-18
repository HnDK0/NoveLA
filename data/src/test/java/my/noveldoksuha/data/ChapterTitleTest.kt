package my.noveldokusha.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import my.noveldokusha.core.Response
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.scraper.ChapterDownload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Интеграционный тест восстановления обновления названия главы (регресс 5f96c931):
 * - insertWithTitle (через fetchBody) обновляет title непустым значением из ChapterDownload
 *   и НЕ затирает сохранённый title пустым/пробельным;
 * - merge() обновляет title новым непустым значением и не затирает пустой заглушкой.
 *
 * Реальная Room-БД: AppRoomDatabase internal (недоступен из модуля data), поэтому
 * используется публичный AppDatabase.createRoom — файловая БД в песочнице Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChapterTitleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val downloaderRepository = mock<DownloaderRepository>()
    private val downloadedPageChaptersStore = mock<DownloadedPageChaptersStore>()

    private lateinit var db: AppDatabase
    private lateinit var bookChaptersRepository: BookChaptersRepository
    private lateinit var repo: ChapterBodyRepository

    private val bookUrl = "https://site/book/1"
    private val chapterUrl = "https://site/ch/1"
    private val validBody =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam."

    @Before
    fun setUp() {
        db = AppDatabase.createRoom(context, "chapter-title-test")
        bookChaptersRepository = BookChaptersRepository(chapterDao = db.chapterDao(), appDatabase = db)
        repo = ChapterBodyRepository(
            chapterBodyDao = db.chapterBodyDao(),
            chapterPagesDao = db.chapterPagesDao(),
            chapterTranslationDao = db.chapterTranslationDao(),
            appDatabase = db,
            bookChaptersRepository = bookChaptersRepository,
            downloaderRepository = downloaderRepository,
            downloadedPageChaptersStore = downloadedPageChaptersStore,
        )
    }

    @After
    fun tearDown() {
        db.closeDatabase()
    }

    private fun insertChapter(title: String, position: Int = 1) = runBlocking {
        bookChaptersRepository.insert(
            listOf(Chapter(title = title, url = chapterUrl, bookUrl = bookUrl, position = position))
        )
    }

    @Test
    fun `insertWithTitle updates title when non-blank`() = runBlocking {
        insertChapter("Старое название")
        whenever(downloaderRepository.bookChapter(chapterUrl)).thenReturn(
            Response.Success(ChapterDownload(body = validBody, title = "Глава 1"))
        )

        repo.fetchBody(chapterUrl)

        assertEquals("Глава 1", bookChaptersRepository.get(chapterUrl)?.title)
    }

    @Test
    fun `insertWithTitle keeps title when blank`() = runBlocking {
        insertChapter("Старое название")
        whenever(downloaderRepository.bookChapter(chapterUrl)).thenReturn(
            Response.Success(ChapterDownload(body = validBody, title = "  "))
        )

        repo.fetchBody(chapterUrl)

        assertEquals("Старое название", bookChaptersRepository.get(chapterUrl)?.title)
    }

    @Test
    fun `merge keeps old title when new chapter title is blank`() = runBlocking {
        insertChapter("Старое название")

        bookChaptersRepository.merge(
            newChapters = listOf(Chapter(title = "  ", url = chapterUrl, bookUrl = bookUrl, position = 2)),
            bookUrl = bookUrl,
        )

        assertEquals("Старое название", bookChaptersRepository.get(chapterUrl)?.title)
    }

    @Test
    fun `merge updates title when new chapter title is non-blank`() = runBlocking {
        insertChapter("Старое название")

        bookChaptersRepository.merge(
            newChapters = listOf(Chapter(title = "Новое название", url = chapterUrl, bookUrl = bookUrl, position = 2)),
            bookUrl = bookUrl,
        )

        assertEquals("Новое название", bookChaptersRepository.get(chapterUrl)?.title)
    }
}
