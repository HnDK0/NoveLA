package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.tables.DownloadedPageChapter

@Dao
interface DownloadedPageChaptersDao {
    @Query("SELECT * FROM DownloadedPageChapter WHERE url = :url")
    suspend fun get(url: String): DownloadedPageChapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(chapter: DownloadedPageChapter)

    @Query("DELETE FROM DownloadedPageChapter WHERE url IN (:urls)")
    suspend fun removeRows(urls: List<String>)

    @Query("SELECT * FROM DownloadedPageChapter WHERE url IN (:urls)")
    suspend fun getByUrls(urls: List<String>): List<DownloadedPageChapter>

    @Query("""
        SELECT DownloadedPageChapter.* FROM DownloadedPageChapter
        INNER JOIN Chapter ON Chapter.url = DownloadedPageChapter.url
        WHERE Chapter.bookUrl IN (:bookUrls)
    """)
    suspend fun getByBookUrls(bookUrls: List<String>): List<DownloadedPageChapter>

    @Query("""
        SELECT DownloadedPageChapter.* FROM DownloadedPageChapter
        INNER JOIN Chapter ON Chapter.url = DownloadedPageChapter.url
        WHERE Chapter.bookUrl IN (:bookUrls)
    """)
    fun getByBookUrlsFlow(bookUrls: List<String>): Flow<List<DownloadedPageChapter>>

    @Query("SELECT url FROM DownloadedPageChapter WHERE url IN (:urls)")
    suspend fun getExistingUrls(urls: List<String>): List<String>

    @Query("DELETE FROM DownloadedPageChapter")
    suspend fun deleteAll(): Int
}
