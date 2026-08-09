package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import my.noveldokusha.feature.local_database.tables.ChapterPages

@Dao
interface ChapterPagesDao {
    @Query("SELECT * FROM ChapterPages WHERE url = :url")
    suspend fun get(url: String): ChapterPages?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(chapterPages: ChapterPages)

    @Query("DELETE FROM ChapterPages WHERE url IN (:urls)")
    suspend fun removeRows(urls: List<String>)

    @Query("DELETE FROM ChapterPages WHERE ChapterPages.url NOT IN (SELECT Chapter.url FROM Chapter)")
    suspend fun removeAllNonChapterRows()

    @Query("""
        DELETE FROM ChapterPages 
        WHERE EXISTS (
            SELECT 1 FROM Chapter 
            WHERE Chapter.url = ChapterPages.url 
            AND Chapter.bookUrl IN (:bookUrls)
        )
    """)
    suspend fun removeChapterPagesByBookUrls(bookUrls: List<String>)

    @Query("SELECT COALESCE(SUM(LENGTH(pages)), 0) FROM ChapterPages")
    suspend fun getCacheSizeBytes(): Long

    @Query("DELETE FROM ChapterPages")
    suspend fun deleteAll(): Int

    @Query("""
        SELECT ChapterPages.* FROM ChapterPages
        INNER JOIN Chapter ON Chapter.url = ChapterPages.url
        WHERE Chapter.bookUrl IN (:bookUrls)
    """)
    fun getByBookUrls(bookUrls: List<String>): Flow<List<ChapterPages>>
}
