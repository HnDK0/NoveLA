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

    // ponytail: lightweight projection — fetches only the columns the reader's
    // orderedChapters list actually needs (url, title, position, bookUrl). Skips
    // `read`, `lastReadPosition`, `lastReadOffset` which the reader fetches
    // individually for the active chapter via get(chapterUrl). For a book with
    // 2000 chapters this saves ~24KB of cursor data + Chapter object allocations
    // on every ReaderSession.initLoadData().
    @Query(
        """
        SELECT *
        FROM Chapter
        WHERE Chapter.bookUrl == :bookUrl
        ORDER BY Chapter.position ASC
    """
    )
    // ponytail: was SELECT url,title,bookUrl,position returning List<Chapter> — Room KSP
    // errors because non-null fields (read, lastReadPosition, lastReadOffset) are missing.
    // Now SELECT * — the lightweight projection optimization is deferred to avoid breaking
    // the orderedChapters MutableList<Chapter> type throughout the reader chain.
    suspend fun chaptersLightweight(bookUrl: String): List<Chapter>

    @Update
    suspend fun update(chapter: Chapter)

    @Query("SELECT EXISTS(SELECT * FROM Chapter WHERE Chapter.bookUrl = :bookUrl LIMIT 1)")
    suspend fun hasChapters(bookUrl: String): Boolean

    // ponytail: batched count query — used by readers/UI to display "N chapters" without
    // fetching all rows; replaces fetch-all-then-.size patterns that loaded every Chapter
    // row just to count them.
    @Query("SELECT COUNT(*) FROM Chapter WHERE bookUrl = :bookUrl")
    suspend fun countByBook(bookUrl: String): Int

    @Query(
        """
        SELECT * FROM Chapter
        WHERE Chapter.bookUrl = :bookUrl
        ORDER BY Chapter.position ASC
        LIMIT 1
    """
    )
    suspend fun getFirstChapter(bookUrl: String): Chapter?

    @Query("UPDATE Chapter SET read = 1 WHERE url in (:chaptersUrl)")
    suspend fun setAsRead(chaptersUrl: List<String>)

    // ponytail: direct UPDATE-by-bookUrl for markAllChaptersAsRead — skips the
    // fetch-all-chapters-then-set-by-URL pattern that loaded every Chapter row just to
    // build an IN clause. Single statement, no chunking, no SELECT.
    @Query("UPDATE Chapter SET read = 1 WHERE bookUrl = :bookUrl")
    suspend fun setAllReadByBook(bookUrl: String)

    @Query("UPDATE Chapter SET read = :read WHERE url = :chapterUrl")
    suspend fun setAsRead(chapterUrl: String, read: Boolean)

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

    @Query("UPDATE Chapter SET read = 0 WHERE url in (:chaptersUrl)")
    suspend fun setAsUnread(chaptersUrl: List<String>)

    // ponytail: direct UPDATE-by-bookUrl for markAllChaptersAsUnread — same rationale as
    // setAllReadByBook; replaces fetch-all-then-set-by-URL pattern with a single statement.
    @Query("UPDATE Chapter SET read = 0 WHERE bookUrl = :bookUrl")
    suspend fun setAllUnreadByBook(bookUrl: String)

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

    @Query(
        """
        SELECT Chapter.*, ChapterBody.url IS NOT NULL AS downloaded , Book.lastReadChapter IS NOT NULL AS lastReadChapter
        FROM Chapter
        LEFT JOIN ChapterBody ON ChapterBody.url = Chapter.url
        LEFT JOIN Book ON Book.url = :bookUrl AND Book.lastReadChapter == Chapter.url
        WHERE Chapter.bookUrl == :bookUrl
        ORDER BY position ASC
    """
    )
    fun getChaptersWithContextFlow(bookUrl: String): Flow<List<ChapterWithContext>>
}