package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.BookWithContext
import my.noveldokusha.feature.local_database.tables.Book

@Dao
interface LibraryDao {
    @Query("SELECT * FROM Book")
    suspend fun getAll(): List<Book>

    @Query("SELECT COUNT(*) FROM Book")
    suspend fun count(): Int

    @Query("SELECT * FROM Book LIMIT :limit OFFSET :offset")
    suspend fun getChunk(limit: Int, offset: Int): List<Book>

    @Query("SELECT * FROM Book WHERE inLibrary == 1")
    suspend fun getAllInLibrary(): List<Book>

    @Query("SELECT * FROM Book WHERE inLibrary == 1")
    fun booksInLibraryFlow(): Flow<List<Book>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: Book)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: List<Book>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(book: List<Book>)

    @Delete
    suspend fun remove(book: Book)

    @Query("DELETE FROM Book WHERE Book.url = :bookUrl")
    suspend fun remove(bookUrl: String)

    @Update
    suspend fun update(book: Book)

    @Query("UPDATE Book SET coverImageUrl = :coverUrl WHERE url == :bookUrl")
    suspend fun updateCover(bookUrl: String, coverUrl: String)

    @Query("UPDATE Book SET title = :title WHERE url == :bookUrl")
    suspend fun updateTitle(bookUrl: String, title: String)

    @Query("UPDATE Book SET lastReadEpochTimeMilli = :lastReadEpochTimeMilli WHERE url == :bookUrl")
    suspend fun updateLastReadEpochTimeMilli(bookUrl: String, lastReadEpochTimeMilli: Long)

    @Query("UPDATE Book SET lastUpdateEpochTimeMilli = :lastUpdateEpochTimeMilli WHERE url == :bookUrl")
    suspend fun updateLastUpdateEpochTimeMilli(bookUrl: String, lastUpdateEpochTimeMilli: Long)

    @Query("UPDATE Book SET description = :description WHERE url == :bookUrl")
    suspend fun updateDescription(bookUrl: String, description: String)

    @Query("UPDATE Book SET lastReadChapter = :chapterUrl WHERE url == :bookUrl")
    suspend fun updateLastReadChapter(bookUrl: String, chapterUrl: String)

    @Query("UPDATE Book SET category = :category WHERE url == :bookUrl")
    suspend fun updateCategory(bookUrl: String, category: String)

    @Query("UPDATE Book SET completed = 1 - completed WHERE url == :bookUrl")
    suspend fun toggleCompleted(bookUrl: String)

    @Query("UPDATE Book SET category = :category, completed = :isCompleted WHERE url == :bookUrl")
    suspend fun updateCategoryAndCompleted(bookUrl: String, category: String, isCompleted: Boolean)

    @Query("UPDATE Book SET chaptersListHash = :hash WHERE url == :bookUrl")
    suspend fun updateChaptersListHash(bookUrl: String, hash: String?)

    @Query("UPDATE Book SET chaptersLastPage = :page WHERE url == :bookUrl")
    suspend fun updateChaptersLastPage(bookUrl: String, page: Int?)

    @Query("UPDATE Book SET url = :newUrl WHERE url == :oldUrl")
    suspend fun updateUrl(oldUrl: String, newUrl: String)

    @Query("SELECT * FROM Book WHERE url = :url")
    suspend fun get(url: String): Book?

    @Query("SELECT * FROM Book WHERE url = :url")
    fun getFlow(url: String): Flow<Book?>

    @Query("SELECT EXISTS(SELECT * FROM Book WHERE url == :url AND inLibrary == 1)")
    suspend fun existInLibrary(url: String): Boolean

    @Query(
        """
        SELECT Book.*, COUNT(Chapter.read) AS chaptersCount, SUM(Chapter.read) AS chaptersReadCount
        FROM Book
        LEFT JOIN Chapter ON Chapter.bookUrl = Book.url
        WHERE Book.inLibrary == 1
        GROUP BY Book.url
    """
    )
    fun getBooksInLibraryWithContextFlow(): Flow<List<BookWithContext>>

    @Query("DELETE FROM Book WHERE inLibrary == 0")
    suspend fun removeAllNonLibraryRows()

    @Query("SELECT url FROM Book WHERE inLibrary == 0")
    suspend fun getNonLibraryBookUrls(): List<String>

    @Query("DELETE FROM Book WHERE url IN (:urls)")
    suspend fun removeBooksByUrls(urls: List<String>)

    @Query("UPDATE Book SET inLibrary = 0 WHERE url = :bookUrl")
    suspend fun setNotInLibrary(bookUrl: String)

    @Query("UPDATE Book SET inLibrary = 0 WHERE url IN (:bookUrls)")
    suspend fun setNotInLibrary(bookUrls: List<String>)

    @Query("UPDATE Book SET category = :category, completed = :isCompleted WHERE url IN (:bookUrls)")
    suspend fun updateCategoryAndCompleted(bookUrls: List<String>, category: String, isCompleted: Boolean)

    @Query("""
        UPDATE Book SET 
            inLibrary = 1 - inLibrary,
            lastUpdateEpochTimeMilli = :currentTime,
            addedToLibraryEpochTimeMilli = CASE WHEN inLibrary = 0 THEN :currentTime ELSE addedToLibraryEpochTimeMilli END
        WHERE url = :bookUrl
    """)
    suspend fun toggleInLibrary(bookUrl: String, currentTime: Long): Int

    // ─── Жанры (хранятся в поле genres через запятую) ────────────────────────

    /** Все уникальные жанры во всей библиотеке — для экрана фильтрации */
    @Query("""
        SELECT DISTINCT Book.genres 
        FROM Book 
        WHERE Book.inLibrary = 1 AND Book.genres != ''
    """)
    fun getAllLibraryGenresRawFlow(): Flow<List<String>>

    /** URL книг в библиотеке с указанным жанром — для фильтрации библиотеки */
    @Query("""
        SELECT Book.url 
        FROM Book 
        WHERE Book.inLibrary = 1 AND Book.genres LIKE '%' || :genre || '%'
    """)
    suspend fun getBooksWithGenre(genre: String): List<String>

    /** Flow-версия для реактивной фильтрации библиотеки */
    @Query("""
        SELECT Book.url 
        FROM Book 
        WHERE Book.inLibrary = 1 AND Book.genres LIKE '%' || :genre || '%'
    """)
    fun getBooksWithGenreFlow(genre: String): Flow<List<String>>

    /** Обновить жанры книги */
    @Query("UPDATE Book SET genres = :genres WHERE url == :bookUrl")
    suspend fun updateGenres(bookUrl: String, genres: String)

}
