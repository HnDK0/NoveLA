package my.noveldokusha.features.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Discover (new home page) rail derivation.
 * Pure JVM — no Android dependencies. The rails must be derived from
 * real library/history data only.
 */
class DiscoverRailsTest {

    private val now = 1_800_000_000_000L // fixed "today"

    private fun book(
        title: String,
        url: String = "https://src.example/${title.replace(' ', '-')}",
        genres: List<String> = emptyList(),
        rating: String = "",
        completed: Boolean = false,
        chapters: Int = 100,
        read: Int = 0,
        inLibrary: Boolean = true,
        lastRead: Long = 0L,
        added: Long = 0L,
        updated: Long = 0L,
        source: String? = "ExampleSource",
    ) = DiscoverBook(
        title = title,
        url = url,
        coverUrl = "",
        genres = genres,
        rating = rating,
        completed = completed,
        chaptersCount = chapters,
        chaptersReadCount = read,
        inLibrary = inLibrary,
        lastReadEpoch = lastRead,
        addedToLibraryEpoch = added,
        lastUpdateEpoch = updated,
        sourceName = source,
    )

    private fun derive(
        books: List<DiscoverBook>,
        feed: HomeFeed = HomeFeed.FOR_YOU,
        feedSources: Set<String> = emptySet(),
        genreChip: String = "All genres",
    ) = DiscoverRails.derive(
        books = books,
        feed = feed,
        feedSourceUrls = feedSources,
        genreChip = genreChip,
        now = now,
        isCompatible = { bookUrl, baseUrl -> bookUrl.startsWith(baseUrl) },
        ratingValue = { raw -> raw.toFloatOrNull() },
    )

    @Test
    fun `continue reading only includes in-library books with progress and not completed`() {
        val content = derive(
            listOf(
                book("Reading", read = 40, lastRead = now - 1000),
                book("NotStarted", read = 0),
                book("Finished", read = 100, completed = true, lastRead = now - 500),
                book("NotInLibrary", read = 10, inLibrary = false, lastRead = now - 200),
            )
        )
        assertEquals(listOf("Reading"), content.continueReading.map { it.title })
    }

    @Test
    fun `continue reading is sorted by most recent`() {
        val content = derive(
            listOf(
                book("Old", read = 1, lastRead = now - 900_000),
                book("New", read = 1, lastRead = now - 10),
                book("Middle", read = 1, lastRead = now - 100),
            )
        )
        assertEquals(listOf("New", "Middle", "Old"), content.continueReading.map { it.title })
    }

    @Test
    fun `trending uses this-week reading pulse and backfills with recent additions`() {
        val content = derive(
            listOf(
                book("ThisWeek", read = 1, lastRead = now - 2 * 24 * 60 * 60 * 1000),
                book("OldRead", read = 1, lastRead = now - 30L * 24 * 60 * 60 * 1000),
                book("FreshAdd", added = now - 1000),
            )
        )
        // thisWeek has 1 item (<3) → backfill from recent additions, OldRead excluded
        val titles = content.trending.map { it.title }
        assertTrue(titles.contains("ThisWeek"))
        assertTrue(titles.contains("FreshAdd"))
        assertTrue(!titles.contains("OldRead"))
    }

    @Test
    fun `because you read matches genre neighbours of most recent book and excludes itself`() {
        val content = derive(
            listOf(
                book("Base", genres = listOf("Fantasy", "Mystery"), lastRead = now - 10),
                book("SameGenre", genres = listOf("fantasy"), lastRead = now - 20),
                book("Other", genres = listOf("Romance"), lastRead = now - 30),
            )
        )
        assertEquals("Because you read Base", content.becauseYouReadLabel)
        assertEquals(listOf("SameGenre"), content.becauseYouRead.map { it.title })
    }

    @Test
    fun `because you read is null without any genre data`() {
        val content = derive(listOf(book("Solo")))
        assertNull(content.becauseYouReadLabel)
        assertTrue(content.becauseYouRead.isEmpty())
    }

    @Test
    fun `picks for-you feed prefers library books then most recent`() {
        val content = derive(
            listOf(
                book("HistOnly", inLibrary = false, lastRead = now - 5),
                book("LibOld", read = 1, lastRead = now - 5000),
                book("LibNew", read = 1, lastRead = now - 50),
            )
        )
        assertEquals(
            listOf("LibNew", "LibOld", "HistOnly"),
            content.picks.map { it.title }
        )
    }

    @Test
    fun `picks trending feed sorts by rating value`() {
        val content = derive(
            books = listOf(
                book("Low", rating = "3.0"),
                book("High", rating = "4.8"),
                book("Mid", rating = "4.0"),
            ),
            feed = HomeFeed.TRENDING,
        )
        assertEquals(listOf("High", "Mid", "Low"), content.picks.map { it.title })
    }

    @Test
    fun `picks respects contributing sources filter`() {
        val content = derive(
            books = listOf(
                book("FromA", source = "A", url = "https://a.example/x"),
                book("FromB", source = "B", url = "https://b.example/y"),
            ),
            feedSources = setOf("https://a.example"),
        )
        assertEquals(listOf("FromA"), content.picks.map { it.title })
    }

    @Test
    fun `picks respects genre chip`() {
        val content = derive(
            books = listOf(
                book("Fantasy", genres = listOf("Fantasy")),
                book("Romance", genres = listOf("Romance")),
            ),
            genreChip = "Fantasy",
        )
        assertEquals(listOf("Fantasy"), content.picks.map { it.title })
    }

    @Test
    fun `updates list only shelf books with unread chapters`() {
        val content = derive(
            listOf(
                book("AllRead", chapters = 10, read = 10),
                book("Unread", chapters = 12, read = 9, updated = now - 1),
                book("NotInLibrary", chapters = 5, read = 1, inLibrary = false),
            )
        )
        assertEquals(1, content.updates.size)
        assertEquals("Unread", content.updates.first().book.title)
        assertEquals(3, content.updates.first().unread)
    }

    @Test
    fun `popular sorts by chapter count and finished holds completed shelf books`() {
        val content = derive(
            listOf(
                book("Small", chapters = 10),
                book("Huge", chapters = 3000),
                book("DoneOld", completed = true, chapters = 50),
                book("DoneNew", completed = true, chapters = 80),
            )
        )
        assertEquals(listOf("Huge", "DoneNew", "DoneOld", "Small"), content.popular.map { it.title })
        assertEquals(listOf("DoneOld", "DoneNew"), content.finished.map { it.title })
    }

    @Test
    fun `featured is the most recently read book with chapters`() {
        val content = derive(
            listOf(
                book("Recent", lastRead = now - 10),
                book("Older", lastRead = now - 999),
            )
        )
        assertNotNull(content.featured)
        assertEquals("Recent", content.featured?.title)
    }

    @Test
    fun `genres list always starts with All genres and dedupes`() {
        val content = derive(
            listOf(
                book("A", genres = listOf("Fantasy", "Fantasy", "Wuxia")),
                book("B", genres = listOf("Mystery")),
            )
        )
        assertEquals("All genres", content.genres.first())
        assertEquals(content.genres.size, content.genres.distinct().size)
        assertTrue("Wuxia" in content.genres)
    }

    @Test
    fun `home feed parses stored label or name and falls back to For you`() {
        assertEquals(HomeFeed.TRENDING, HomeFeed.from("Trending"))
        assertEquals(HomeFeed.LATEST, HomeFeed.from("LATEST"))
        assertEquals(HomeFeed.FOR_YOU, HomeFeed.from("garbage"))
        assertEquals(HomeFeed.FOR_YOU, HomeFeed.from(null))
    }

    @Test
    fun `unread count never goes negative`() {
        val b = book("Weird", chapters = 3, read = 10)
        assertEquals(0, b.unreadCount)
        assertTrue(!b.hasProgress || b.completed == false)
    }
}
