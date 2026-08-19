package my.noveldokusha.feature.local_database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import my.noveldokusha.feature.local_database.tables.Book
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * updateStatus/updateLastUpdateDate: статус книги и дата последнего обновления
 * парсятся с сайта источником и пишутся в Book.status / Book.lastUpdateDate.
 * Источники без поддержки этих полей не заполняют их, поэтому дефолт обязан
 * оставаться '' и не перетирать уже сохранённые значения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryDaoStatusTest {

    private lateinit var db: AppRoomDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `updateStatus writes and reads back`() = runBlocking {
        val url = "https://site/book/ongoing"
        db.libraryDao().insert(
            Book(
                title = "Ongoing",
                url = url,
                inLibrary = true
            )
        )

        db.libraryDao().updateStatus(url, "Ongoing")

        val stored = db.libraryDao().get(url)
        assertEquals("Ongoing", stored?.status)
    }

    @Test
    fun `updateLastUpdateDate writes and reads back`() = runBlocking {
        val url = "https://site/book/dated"
        db.libraryDao().insert(
            Book(
                title = "Dated",
                url = url,
                inLibrary = true
            )
        )

        db.libraryDao().updateLastUpdateDate(url, "2026-08-19")

        val stored = db.libraryDao().get(url)
        assertEquals("2026-08-19", stored?.lastUpdateDate)
    }

    @Test
    fun `new book defaults status and lastUpdateDate to empty`() = runBlocking {
        val url = "https://site/book/plain"
        db.libraryDao().insert(
            Book(
                title = "Plain",
                url = url,
                inLibrary = true
            )
        )

        val stored = db.libraryDao().get(url)
        assertEquals("", stored?.status)
        assertEquals("", stored?.lastUpdateDate)
    }

    @Test
    fun `updateStatus overwrites previous value`() = runBlocking {
        val url = "https://site/book/switch"
        db.libraryDao().insert(
            Book(
                title = "Switch",
                url = url,
                inLibrary = true,
                status = "Ongoing"
            )
        )

        db.libraryDao().updateStatus(url, "Completed")

        val stored = db.libraryDao().get(url)
        assertEquals("Completed", stored?.status)
    }

    @Test
    fun `updateLastUpdateDate overwrites previous value`() = runBlocking {
        val url = "https://site/book/redate"
        db.libraryDao().insert(
            Book(
                title = "Redate",
                url = url,
                inLibrary = true,
                lastUpdateDate = "2026-01-01"
            )
        )

        db.libraryDao().updateLastUpdateDate(url, "2026-08-19")

        val stored = db.libraryDao().get(url)
        assertEquals("2026-08-19", stored?.lastUpdateDate)
    }

    @Test
    fun `updateStatus on missing url is a no-op`() = runBlocking {
        db.libraryDao().updateStatus("https://site/absent", "Ongoing")
        assertNull(db.libraryDao().get("https://site/absent"))
    }

    @Test
    fun `updateLastUpdateDate on missing url is a no-op`() = runBlocking {
        db.libraryDao().updateLastUpdateDate("https://site/absent", "2026-08-19")
        assertNull(db.libraryDao().get("https://site/absent"))
    }
}