package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.feature.local_database.tables.Chapter

@Dao
interface ChapterDao {
    @Query("SELECT * FROM Chapter")
    suspend fun getAll(): List<Chapter>

    @Query("SELECT COUNT(*) FROM Chapter")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM Chapter WHERE bookUrl = :bookUrl")
    suspend fun countByBookUrl(bookUrl: String): Int

    @Query("SELECT * FROM Chapter LIMIT :limit OFFSET :offset")
    suspend fun getChunk(limit: Int, offset: Int): List<Chapter>

    @Query(
        """
        SELECT * FROM Chapter
        WHERE Chapter.bookUrl == :bookUrl
        ORDER BY Chapter.position ASC
    """
    )
    suspend fun chapters(bookUrl: String): List<Chapter>

    @Update
    suspend fun update(chapter: Chapter)

    @Query("SELECT EXISTS(SELECT * FROM Chapter WHERE Chapter.bookUrl = :bookUrl LIMIT 1)")
    suspend fun hasChapters(bookUrl: String): Boolean

    @Query(
        """
        SELECT * FROM Chapter
        WHERE Chapter.bookUrl = :bookUrl
        ORDER BY Chapter.position ASC
        LIMIT 1
    """
    )
    suspend fun getFirstChapter(bookUrl: String): Chapter?

    @Query("UPDATE Chapter SET read = 1 WHERE url IN (:chaptersUrl)")
    suspend fun setAsRead(chaptersUrl: List<String>)

    @Query("UPDATE Chapter SET read = :read WHERE url = :chapterUrl")
    suspend fun setAsRead(chapterUrl: String, read: Boolean)

    @Query("UPDATE Chapter SET read = 1 WHERE bookUrl = :bookUrl")
    suspend fun setAllAsReadByBookUrl(bookUrl: String)

    @Query(
        """
        UPDATE Chapter 
        SET lastReadPosition = :lastReadPosition, lastReadOffset = :lastReadOffset
        WHERE url = :chapterUrl
    """
    )
    suspend fun updatePosition(chapterUrl: String, lastReadPosition: Int, lastReadOffset: Int)

    @Query("UPDATE Chapter SET title = :title WHERE url == :url")
    suspend fun updateTitle(url: String, title: String)

    @Query("UPDATE Chapter SET read = 0 WHERE url IN (:chaptersUrl)")
    suspend fun setAsUnread(chaptersUrl: List<String>)

    @Query("UPDATE Chapter SET read = 0 WHERE bookUrl = :bookUrl")
    suspend fun setAllAsUnreadByBookUrl(bookUrl: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(chapters: List<Chapter>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(chapters: List<Chapter>)

    @Query("SELECT * FROM Chapter WHERE url = :url")
    suspend fun get(url: String): Chapter?

    @Query("DELETE FROM Chapter WHERE Chapter.bookUrl = :bookUrl")
    suspend fun removeAllFromBook(bookUrl: String)

    @Query("DELETE FROM Chapter WHERE Chapter.bookUrl NOT IN (SELECT Book.url FROM Book)")
    suspend fun removeAllNonLibraryRows()

    @Query("DELETE FROM Chapter WHERE Chapter.bookUrl IN (:bookUrls)")
    suspend fun removeAllFromBooks(bookUrls: List<String>)

    @Query("DELETE FROM Chapter WHERE url IN (:urls)")
    suspend fun removeByUrls(urls: List<String>)

    @Query("UPDATE Chapter SET bookUrl = :newBookUrl WHERE bookUrl = :oldBookUrl")
    suspend fun updateBookUrl(oldBookUrl: String, newBookUrl: String)

    @Query(
        """
        SELECT Chapter.*, 0 AS downloaded, Book.lastReadChapter IS NOT NULL AS lastReadChapter
        FROM Chapter
        LEFT JOIN Book ON Book.url = :bookUrl AND Book.lastReadChapter == Chapter.url
        WHERE Chapter.bookUrl == :bookUrl
        ORDER BY position ASC
    """
    )
    fun getChaptersWithContextFlow(bookUrl: String): Flow<List<ChapterWithContext>>

    @Query("SELECT url FROM Chapter WHERE bookUrl == :bookUrl ORDER BY position ASC")
    suspend fun getChapterUrls(bookUrl: String): List<String>
}