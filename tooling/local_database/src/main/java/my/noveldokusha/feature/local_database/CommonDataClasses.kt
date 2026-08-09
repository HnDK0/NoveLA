package my.noveldokusha.feature.local_database

import androidx.room.Embedded
import androidx.room.Ignore
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter

data class BookMetadata(
    val title: String,
    val url: String,
    val coverImageUrl: String = "",
    val description: String = "",
    val rating: String = ""
) {
    override fun equals(other: Any?): Boolean =
        if (other is BookMetadata) (url == other.url) else false

    override fun hashCode(): Int = url.hashCode()
}

data class ChapterMetadata(val title: String, val url: String) {
    override fun equals(other: Any?): Boolean =
        if (other is ChapterMetadata) (url == other.url) else false

    override fun hashCode(): Int = url.hashCode()
}

data class BookWithContext(
    @Embedded val book: Book,
    val chaptersCount: Int,
    val chaptersReadCount: Int
)

data class ChapterWithContext(
    @Embedded val chapter: Chapter,
    val downloaded: Boolean,
    val lastReadChapter: Boolean,
    /** Реальный размер скачанного содержимого (тело главы или файлы страниц), байты. */
    @Ignore val sizeBytes: Long? = null,
    /** Оценка размера страничной главы (манхва/манга) в текущем качестве, байты. */
    @Ignore val estimatedBytes: Long? = null
)