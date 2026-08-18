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
 * updateContentType: метка формата книги (NOVEL/MANGA/COMIC) пишется в
 * Book.contentType и читается обратно. Не-Lua источники не заполняют поле,
 * поэтому дефолт обязан оставаться '' и не перетирать уже сохранённую метку.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryDaoContentTypeTest {

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
    fun `updateContentType writes and reads back`() = runBlocking {
        val url = "https://site/book/manga"
        db.libraryDao().insert(
            Book(
                title = "Manga",
                url = url,
                inLibrary = true
            )
        )

        db.libraryDao().updateContentType(url, "MANGA")

        val stored = db.libraryDao().get(url)
        assertEquals("MANGA", stored?.contentType)
    }

    @Test
    fun `new book defaults contentType to empty (NOVEL)`() = runBlocking {
        val url = "https://site/book/novel"
        db.libraryDao().insert(
            Book(
                title = "Novel",
                url = url,
                inLibrary = true
            )
        )

        val stored = db.libraryDao().get(url)
        assertEquals("", stored?.contentType)
    }

    @Test
    fun `updateContentType overwrites previous value`() = runBlocking {
        val url = "https://site/book/switch"
        db.libraryDao().insert(
            Book(
                title = "Switch",
                url = url,
                inLibrary = true,
                contentType = "NOVEL"
            )
        )

        db.libraryDao().updateContentType(url, "COMIC")

        val stored = db.libraryDao().get(url)
        assertEquals("COMIC", stored?.contentType)
    }

    @Test
    fun `updateContentType on missing url is a no-op`() = runBlocking {
        db.libraryDao().updateContentType("https://site/absent", "MANGA")
        assertNull(db.libraryDao().get("https://site/absent"))
    }
}
