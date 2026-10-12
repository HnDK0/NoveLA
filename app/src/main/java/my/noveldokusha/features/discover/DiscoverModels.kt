package my.noveldokusha.features.discover

/**
 * Redesign home (Discover) — data models + pure rail-derivation logic.
 *
 * The Discover screen is the NEW home page (prototype pages/discover):
 * the old homepage (Library) keeps its own tab, and this screen adds the
 * editorial-style home with curated sections. All rails are derived from
 * real app data: the Room library (Book + chapter counters) and the
 * ReadingHistory table. No stock/demo data anywhere.
 */

data class DiscoverBook(
    val title: String,
    val url: String,
    val coverUrl: String,
    val genres: List<String> = emptyList(),
    val rating: String = "",
    val status: String = "",
    val completed: Boolean = false,
    val chaptersCount: Int = 0,
    val chaptersReadCount: Int = 0,
    val inLibrary: Boolean = false,
    val lastReadEpoch: Long = 0L,
    val addedToLibraryEpoch: Long = 0L,
    val lastUpdateEpoch: Long = 0L,
    val sourceName: String? = null,
) {
    val unreadCount: Int get() = (chaptersCount - chaptersReadCount).coerceAtLeast(0)
    val hasProgress: Boolean get() = chaptersReadCount > 0 && !completed
}

data class UpdateEntry(
    val book: DiscoverBook,
    val unread: Int,
)

enum class HomeFeed(val label: String) {
    FOR_YOU("For you"),
    TRENDING("Trending"),
    LATEST("Latest"),
    RECENTLY_UPDATED("Recently updated");

    companion object {
        fun from(raw: String?): HomeFeed =
            entries.find { it.name == raw || it.label == raw } ?: FOR_YOU
    }
}

/**
 * One homepage catalog rail: the real catalog listing of a single source
 * (the same data the source's Catalog browse screen shows), fetched fresh
 * from the network. Each homepage category shows exactly one source's catalog.
 */
data class CatalogRailState(
    val sourceBaseUrl: String,
    val sourceName: String,
    val loading: Boolean = true,
    val error: String? = null,
    val books: List<DiscoverBook> = emptyList(),
)

/**
 * One homepage category row: the category (For you / Trending / Latest /
 * Recently updated), the single source chosen for it (null = none yet),
 * and that source's catalog rail when chosen.
 */
data class FeedSectionState(
    val feed: HomeFeed,
    val sourceBaseUrl: String? = null,
    val sourceName: String? = null,
    val rail: CatalogRailState? = null,
)

/** Everything the Discover screen renders, derived in one pure pass. */
data class DiscoverContent(
    val featured: DiscoverBook?,
    val continueReading: List<DiscoverBook>,
    val trending: List<DiscoverBook>,
    val becauseYouReadLabel: String?,
    val becauseYouRead: List<DiscoverBook>,
    val picks: List<DiscoverBook>,
    val updates: List<UpdateEntry>,
    val popular: List<DiscoverBook>,
    val finished: List<DiscoverBook>,
    val genres: List<String>,
)

object DiscoverRails {

    /** Genres shown as browse chips (prototype: All genres, Fantasy, Mystery, ...). */
    val FIXED_GENRES = listOf("All genres", "Fantasy", "Mystery", "Romance", "Action", "Sci-Fi")

    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    /**
     * Derives every rail from the merged library+history books.
     *
     * @param feed            selected homepage feed tab
     * @param feedSourceUrls  contributing source baseUrls (empty = all)
     * @param isCompatible    source matcher: book url ↔ source baseUrl
     * @param ratingValue     rating parser returning a comparable float (null = unparsed)
     */
    fun derive(
        books: List<DiscoverBook>,
        feed: HomeFeed,
        feedSourceUrls: Set<String>,
        genreChip: String,
        now: Long,
        isCompatible: (bookUrl: String, sourceBaseUrl: String) -> Boolean,
        ratingValue: (String) -> Float?,
    ): DiscoverContent {
        val library = books.filter { it.inLibrary }
        val byLastRead = books.sortedByDescending { it.lastReadEpoch }

        // — Featured: the book the user is actually reading right now —
        val featured = byLastRead.firstOrNull { it.chaptersCount > 0 }

        // — Continue reading: in-library books with progress, not finished —
        val continueReading = library
            .filter { it.hasProgress }
            .sortedByDescending { it.lastReadEpoch }
            .take(10)

        // — Trending this week: reading pulse of the last 7 days; backfilled
        //   with books that carry a recent signal (added/updated) so the rail
        //   stays honest — no stale entries without any recency —
        val thisWeek = books
            .filter { it.lastReadEpoch > 0 && now - it.lastReadEpoch <= WEEK_MILLIS }
            .sortedByDescending { it.lastReadEpoch }
        val trending = if (thisWeek.size >= 3) thisWeek.take(8)
        else {
            val recentSignal = library
                .filter { it.url !in thisWeek.map(DiscoverBook::url).toSet() }
                .filter { it.addedToLibraryEpoch > 0 || it.lastUpdateEpoch > 0 }
                .sortedByDescending { maxOf(it.addedToLibraryEpoch, it.lastUpdateEpoch) }
            (thisWeek + recentSignal).take(8)
        }

        // — Because you read X: genre neighbours of the most recent read —
        val base = byLastRead.firstOrNull { it.genres.isNotEmpty() }
        val becauseYouRead = base?.let { b ->
            val baseGenres = b.genres.map { it.lowercase().trim() }.toSet()
            books
                .filter { it.url != b.url }
                .filter { book -> book.genres.any { it.lowercase().trim() in baseGenres } }
                .take(8)
        }
        val becauseYouReadLabel = base?.let { "Because you read ${it.title}" }

        // — Picked for you: homepage feed rail (feed + contributing sources + genre chip) —
        var picks = books.asSequence()
        if (feedSourceUrls.isNotEmpty()) {
            picks = picks.filter { book ->
                feedSourceUrls.any { base -> isCompatible(book.url, base) } || book.sourceName == "Local"
            }
        }
        picks = when (feed) {
            HomeFeed.FOR_YOU -> picks.sortedWith(
                compareByDescending<DiscoverBook> { it.inLibrary }
                    .thenByDescending { it.lastReadEpoch }
            )
            HomeFeed.TRENDING -> picks.sortedByDescending { ratingValue(it.rating) ?: 0f }
            HomeFeed.LATEST, HomeFeed.RECENTLY_UPDATED ->
                picks.sortedByDescending { it.lastUpdateEpoch }
        }
        if (genreChip != "All genres") picks = picks.filter { genreChip in it.genres }
        val pickedList = picks.take(10).toList()

        // — New chapters, quietly arriving: shelf books with unread chapters —
        val updates = library
            .filter { it.unreadCount > 0 }
            .sortedByDescending { it.lastUpdateEpoch }
            .take(3)
            .map { UpdateEntry(it, it.unreadCount) }

        // — Popular across sources: heftiest books first —
        val popular = books
            .filter { it.chaptersCount > 0 }
            .sortedByDescending { it.chaptersCount }
            .take(6)

        // — Finished, not forgotten —
        val finished = library.filter { it.completed }.take(8)

        // — Browse chips: fixed set first, then real genres present in the data —
        val genres = buildList {
            addAll(FIXED_GENRES)
            books.flatMap { it.genres }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .filter { it !in FIXED_GENRES }
                .sorted()
                .take(4)
                .forEach { add(it) }
        }

        return DiscoverContent(
            featured = featured,
            continueReading = continueReading,
            trending = trending,
            becauseYouReadLabel = becauseYouReadLabel,
            becauseYouRead = becauseYouRead.orEmpty(),
            picks = pickedList,
            updates = updates,
            popular = popular,
            finished = finished,
            genres = genres,
        )
    }
}
