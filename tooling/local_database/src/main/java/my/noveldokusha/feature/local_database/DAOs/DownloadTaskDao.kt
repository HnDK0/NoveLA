package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import my.noveldokusha.feature.local_database.tables.DownloadTaskEntity

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM DownloadTask")
    suspend fun getAll(): List<DownloadTaskEntity>

    @Query("SELECT * FROM DownloadTask WHERE bookUrl = :bookUrl")
    suspend fun get(bookUrl: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTaskEntity)

    @Query("DELETE FROM DownloadTask WHERE bookUrl = :bookUrl")
    suspend fun delete(bookUrl: String)

    @Query("DELETE FROM DownloadTask")
    suspend fun deleteAll()
}