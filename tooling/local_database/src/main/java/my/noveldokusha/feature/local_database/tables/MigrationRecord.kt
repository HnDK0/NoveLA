package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "migration_records",
    indices = [
        Index(value = ["oldBookUrl"]),
        Index(value = ["newBookUrl"]),
    ]
)
data class MigrationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val oldBookUrl: String,
    val newBookUrl: String,
    val oldSourceId: String,
    val newSourceId: String,
    val status: String,
    val chaptersTotal: Int,
    val chaptersMatched: Int,
    val chaptersWithProgress: Int,
    val chaptersWithBody: Int,
    val migratedAt: Long,
)
