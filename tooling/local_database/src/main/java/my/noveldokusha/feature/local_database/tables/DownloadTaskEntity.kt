package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DownloadTask")
data class DownloadTaskEntity(
    @PrimaryKey val bookUrl: String,
    val bookTitle: String,
    val chapterUrlsJson: String,  // JSON-строка со списком URL глав
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false,
    val isCompleted: Boolean = false,
    val isWaitingForNetwork: Boolean = false,
    val errorCount: Int = 0,
    val successCount: Int = 0,
    val consecutiveErrors: Int = 0,
    val skippedCount: Int = 0,
    val translationErrorCount: Int = 0,
)