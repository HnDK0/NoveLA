package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.tables.Extension

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM Extension")
    suspend fun getAll(): List<Extension>

    @Query("SELECT * FROM Extension WHERE installed == 1")
    suspend fun getAllInstalled(): List<Extension>

    @Query("SELECT * FROM Extension WHERE installed == 1 AND enabled == 1")
    suspend fun getAllEnabled(): List<Extension>

    @Query("SELECT * FROM Extension WHERE installed == 1")
    fun getAllInstalledFlow(): Flow<List<Extension>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(extension: Extension)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(extension: List<Extension>)

    @Delete
    suspend fun remove(extension: Extension)

    @Query("DELETE FROM Extension WHERE id = :extensionId")
    suspend fun remove(extensionId: String)

    @Update
    suspend fun update(extension: Extension)

    @Query("UPDATE Extension SET enabled = :enabled WHERE id == :extensionId")
    suspend fun updateEnabled(extensionId: String, enabled: Boolean)

    @Query("UPDATE Extension SET installed = :installed WHERE id == :extensionId")
    suspend fun updateInstalled(extensionId: String, installed: Boolean)

    @Query("UPDATE Extension SET settings = :settings WHERE id == :extensionId")
    suspend fun updateSettings(extensionId: String, settings: String)

    @Query("SELECT * FROM Extension WHERE id = :extensionId")
    suspend fun get(extensionId: String): Extension?

    @Query("SELECT * FROM Extension WHERE id = :extensionId")
    fun getFlow(extensionId: String): Flow<Extension?>

    @Query("SELECT EXISTS(SELECT * FROM Extension WHERE id == :extensionId AND installed == 1)")
    suspend fun exists(extensionId: String): Boolean
}
