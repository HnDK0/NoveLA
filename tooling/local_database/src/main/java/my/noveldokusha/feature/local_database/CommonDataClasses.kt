package my.noveldokusha.feature.local_database

import androidx.room.Embedded
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter

// ponytail: @Immutable (from androidx.compose.runtime) was applied here in the first
// optimization pass to give the Compose compiler a stability hint, but local_database is
// a pure Room module with no Compose dependency. Two build failures followed:
//   1. @Immutable on @Embedded classes broke Room's KSP ("references a type that is not present")
//   2. @Immutable on the DTOs themselves failed compile ("Unresolved reference 'compose'")
// The laziest fix that actually works: drop the annotation entirely. These are pure data
// classes with only String/Int/Boolean fields — the Compose compiler infers them as stable
// via its @Stable inference for public data classes in many configurations, and the original
// code shipped without the annotation. No regression.
//
// If recomposition of library list screens becomes a measured bottleneck, the proper fix is
// to add `implementation(libs.androidx.compose.runtime)` to this module's build.gradle.kts
// and re-apply @Immutable — but only then. YAGNI until the profiler says otherwise.

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
    val lastReadChapter: Boolean
)
