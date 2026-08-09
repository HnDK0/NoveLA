package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Кэш страничных глав (манхва/манга): упорядоченный список URL страниц,
 * сериализованный в JSON-массив. Пустой массив ("[]") означает
 * «источник опрошён — глава не страничная» и не перезапрашивается.
 */
@Entity
data class ChapterPages(
    @PrimaryKey val url: String,
    val pages: String
)
