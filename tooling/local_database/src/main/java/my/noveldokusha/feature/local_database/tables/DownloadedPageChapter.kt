package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Скачанные страничные главы (манхва/манга): файлы картинок лежат в
 * filesDir/downloaded_pages/<sha256(url)>/, здесь — ссылка на источник
 * (исходные CDN URL страниц в порядке), реальный размер и качество,
 * в котором качали.
 */
@Entity
data class DownloadedPageChapter(
    @PrimaryKey val url: String,
    /** JSON-массив исходных URL страниц (тот же порядок, что файлы 000, 001…) */
    val pages: String,
    val totalBytes: Long,
    val quality: String
)
