package my.noveldokusha.data

import kotlinx.coroutines.runBlocking
import my.noveldokusha.core.Response
import my.noveldokusha.data.DownloadedPageChaptersStore
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.ChapterPagesDao
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ChapterPages
import my.noveldokusha.scraper.ChapterDownload
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Кэширование страничных глав (манхва/манга): fetchPages пишет список
 * страниц в ChapterPages, повторное открытие не ходит в сеть, офлайн-путь
 * берёт страницы из DownloadedPageChaptersStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChapterBodyRepositoryFetchPagesTest {

    private val chapterBodyDao = mock<ChapterBodyDao>()
    private val chapterPagesDao = mock<ChapterPagesDao>()
    private val chapterTranslationDao = mock<ChapterTranslationDao>()
    private val appDatabase = mock<AppDatabase>()
    private val bookChaptersRepository = mock<BookChaptersRepository>()
    private val downloaderRepository = mock<DownloaderRepository>()
    private val store = mock<DownloadedPageChaptersStore>()

    private val repo = ChapterBodyRepository(
        chapterBodyDao = chapterBodyDao,
        chapterPagesDao = chapterPagesDao,
        chapterTranslationDao = chapterTranslationDao,
        appDatabase = appDatabase,
        bookChaptersRepository = bookChaptersRepository,
        downloaderRepository = downloaderRepository,
        downloadedPageChaptersStore = store,
    )

    private val chapterUrl = "https://site/ch/1"
    private val pages = listOf("https://site/img/1.jpg", "https://site/img/2.webp")

    @Test
    fun `fetchPages caches pages and returns them`() { runBlocking {
        whenever(downloaderRepository.bookChapter(chapterUrl)).thenReturn(
            Response.Success(ChapterDownload(body = "", title = null, pages = pages))
        )
        whenever(appDatabase.transaction(any<suspend () -> Any>())).thenAnswer { invocation ->
            runBlocking { invocation.getArgument<suspend () -> Any>(0).invoke() }
        }

        val result = repo.fetchPages(chapterUrl)

        assertTrue(result is Response.Success)
        assertEquals(pages, (result as Response.Success).data)
        verify(chapterPagesDao).insertReplace(
            eq(ChapterPages(url = chapterUrl, pages = JSONArray(pages).toString()))
        )
        // Пустое тело страничной главы не пишется в кэш тела
        verify(chapterBodyDao, never()).insertReplace(any<ChapterBody>())
    }
    }

    @Test
    fun `fetchPages returns cached pages without network`() { runBlocking {
        whenever(chapterPagesDao.get(chapterUrl)).thenReturn(
            ChapterPages(url = chapterUrl, pages = """["https://site/img/1.jpg","https://site/img/2.webp"]""")
        )

        val result = repo.fetchPages(chapterUrl)

        assertTrue(result is Response.Success)
        assertEquals(pages, (result as Response.Success).data)
        verify(downloaderRepository, never()).bookChapter(any())
    }
    }

    @Test
    fun `fetchPages falls back to downloaded store when cache row missing`() { runBlocking {
        whenever(chapterPagesDao.get(chapterUrl)).thenReturn(null)
        whenever(store.getChapterPages(chapterUrl)).thenReturn(pages)

        val result = repo.fetchPages(chapterUrl)

        assertTrue(result is Response.Success)
        assertEquals(pages, (result as Response.Success).data)
        verify(downloaderRepository, never()).bookChapter(any())
    }
    }

    @Test
    fun `fetchPages caches empty marker for non-page chapter`() {
        runBlocking {
            val body = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam."
            whenever(downloaderRepository.bookChapter(chapterUrl)).thenReturn(
                Response.Success(ChapterDownload(body = body, title = null, pages = null))
            )
            whenever(appDatabase.transaction(any<suspend () -> Any>())).thenAnswer { invocation ->
                runBlocking { invocation.getArgument<suspend () -> Any>(0).invoke() }
            }

            val result = repo.fetchPages(chapterUrl)

            assertTrue(result is Response.Success)
            assertEquals(emptyList<String>(), (result as Response.Success).data)
            verify(chapterPagesDao).insertReplace(eq(ChapterPages(url = chapterUrl, pages = "[]")))
            // Текстовая глава кэшируется в кэш тела
            verify(chapterBodyDao).insertReplace(eq(ChapterBody(url = chapterUrl, body = body)))
        }
    }
}