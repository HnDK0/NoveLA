package my.noveldokusha.feature.local_database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import my.noveldokusha.feature.local_database.tables.BookTranslation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BookTranslationDao: перевод метаданных книги пишется и читается по паре
 * языков. insertReplace перезаписывает строку с тем же составным PK
 * (нет дублей), а getTranslatedBookFlow возвращает только строки с
 * непустым переводом для запрошенного targetLang.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookTranslationDaoTest {

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
    fun `getTranslatedBookFlow returns only rows for targetLang with non-empty text`() = runBlocking {
        val book = "https://site/book/1"
        db.bookTranslationDao().insertReplace(
            BookTranslation(bookUrl = book, sourceLang = "en", targetLang = "ru",
                titleTranslation = "Заголовок", descriptionTranslation = "Описание")
        )
        db.bookTranslationDao().insertReplace(
            BookTranslation(bookUrl = book, sourceLang = "en", targetLang = "de",
                titleTranslation = "Titel", descriptionTranslation = "Beschreibung")
        )

        val ru = db.bookTranslationDao().getTranslatedBookFlow(book, "ru").first()
        assertEquals(1, ru.size)
        assertEquals("en", ru[0].sourceLang)
        assertEquals("Заголовок", ru[0].titleTranslation)
        assertEquals("Описание", ru[0].descriptionTranslation)
    }

    @Test
    fun `insertReplace overwrites same primary key without duplicates`() = runBlocking {
        val book = "https://site/book/2"
        db.bookTranslationDao().insertReplace(
            BookTranslation(bookUrl = book, sourceLang = "en", targetLang = "ru",
                titleTranslation = "Старый", descriptionTranslation = "Старое")
        )
        db.bookTranslationDao().insertReplace(
            BookTranslation(bookUrl = book, sourceLang = "en", targetLang = "ru",
                titleTranslation = "Новый", descriptionTranslation = "Новое")
        )

        val rows = db.bookTranslationDao().getTranslatedBookFlow(book, "ru").first()
        assertEquals(1, rows.size)
        assertEquals("Новый", rows[0].titleTranslation)
        assertEquals("Новое", rows[0].descriptionTranslation)
    }

    @Test
    fun `get returns null for unknown language pair`() = runBlocking {
        val book = "https://site/book/3"
        db.bookTranslationDao().insertReplace(
            BookTranslation(bookUrl = book, sourceLang = "en", targetLang = "ru",
                titleTranslation = "Заголовок", descriptionTranslation = "Описание")
        )

        assertNull(db.bookTranslationDao().get(book, "en", "de"))
        assertEquals("Заголовок", db.bookTranslationDao().get(book, "en", "ru")?.titleTranslation)
    }

    @Test
    fun `getTranslatedBookFlow excludes rows with empty title and description`() = runBlocking {
        val book = "https://site/book/4"
        db.bookTranslationDao().insertReplace(
            BookTranslation(bookUrl = book, sourceLang = "en", targetLang = "ru")
        )

        val rows = db.bookTranslationDao().getTranslatedBookFlow(book, "ru").first()
        assertEquals(0, rows.size)
    }
}