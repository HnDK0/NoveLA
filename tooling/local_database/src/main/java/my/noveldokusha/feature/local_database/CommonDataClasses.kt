package my.noveldokusha.feature.local_database

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter

// ponytail: @Immutable tells the Compose compiler these data classes never mutate after
// construction, so it can skip recomposition of any composable that takes them as a
// parameter unless the value (by equals) actually changes. Without this annotation the
// Compose compiler treats `List<BookMetadata>` / `List<ChapterWithContext>` as unstable
// and recomposes the entire list-binding composables on every parent update.
@Immutable
data class BookMetadata(
    val title: String,
    val url: String,
    val coverImageUrl: String = "",
    val description: String = ""
) {
    override fun equals(other: Any?): Boolean =
        if (other is BookMetadata) (url == other.url) else false

    override fun hashCode(): Int = url.hashCode()
}

@Immutable
data class ChapterMetadata(val title: String, val url: String) {
    override fun equals(other: Any?): Boolean =
        if (other is ChapterMetadata) (url == other.url) else false

    override fun hashCode(): Int = url.hashCode()
}

@Immutable
data class BookWithContext(
    @Embedded val book: Book,
    val chaptersCount: Int,
    val chaptersReadCount: Int
)

@Immutable
data class ChapterWithContext(
    @Embedded val chapter: Chapter,
    val downloaded: Boolean,
    val lastReadChapter: Boolean
)