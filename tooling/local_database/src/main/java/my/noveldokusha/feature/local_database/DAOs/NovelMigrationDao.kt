package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import my.noveldokusha.feature.local_database.tables.MigrationRecord

@Dao
interface NovelMigrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MigrationRecord)

    @Query("SELECT * FROM migration_records ORDER BY migratedAt DESC")
    suspend fun getAll(): List<MigrationRecord>

    @Query("SELECT * FROM migration_records WHERE oldBookUrl = :bookUrl OR newBookUrl = :bookUrl ORDER BY migratedAt DESC")
    suspend fun getByBookUrl(bookUrl: String): List<MigrationRecord>

    @Query("DELETE FROM migration_records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM migration_records WHERE oldSourceId = :oldSourceId AND newSourceId = :newSourceId")
    suspend fun deleteBySourcePair(oldSourceId: String, newSourceId: String)
}
