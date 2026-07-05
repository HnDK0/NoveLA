package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import my.noveldokusha.feature.local_database.tables.ChapterBody

@Dao
interface ChapterBodyDao {
    @Query("SELECT * FROM ChapterBody")
    suspend fun getAll(): List<ChapterBody>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(chapterBody: ChapterBody)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(chapterBody: List<ChapterBody>)

    @Query("SELECT * FROM ChapterBody WHERE url = :url")
    suspend fun get(url: String): ChapterBody?

    @Query("DELETE FROM ChapterBody WHERE ChapterBody.url NOT IN (SELECT Chapter.url FROM Chapter)")
    suspend fun removeAllNonChapterRows()

    @Query("DELETE FROM ChapterBody WHERE ChapterBody.url IN (:chaptersUrl)")
    suspend fun removeChapterRows(chaptersUrl: List<String>)

    @Query("""
        DELETE FROM ChapterBody 
        WHERE ChapterBody.url IN (
            SELECT ChapterBody.url 
            FROM ChapterBody 
            INNER JOIN Chapter ON ChapterBody.url = Chapter.url 
            WHERE Chapter.bookUrl IN (:bookUrls)
        )
    """)
    suspend fun removeChapterBodiesByBookUrls(bookUrls: List<String>)

    @Query("SELECT COUNT(*) FROM ChapterBody")
    suspend fun count(): Int

    @Query("SELECT * FROM ChapterBody LIMIT :limit OFFSET :offset")
    suspend fun getChunk(limit: Int, offset: Int): List<ChapterBody>

    @Query("SELECT COALESCE(SUM(LENGTH(body)), 0) FROM ChapterBody")
    suspend fun getCacheSizeBytes(): Long

    @Query("DELETE FROM ChapterBody")
    suspend fun deleteAll(): Int
}
