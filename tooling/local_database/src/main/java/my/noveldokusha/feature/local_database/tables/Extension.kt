package my.noveldokusha.feature.local_database.tables

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity
data class Extension(
    @PrimaryKey val id: String,
    val name: String,
    val fileName: String,
    val imageURL: String = "",
    val language: String,
    val version: String,
    val md5: String,
    val enabled: Boolean = true,
    val installed: Boolean = true,
    val chapterType: String = "HTML",
    val settings: String = "{}" // JSON string for extension settings
) : Parcelable
