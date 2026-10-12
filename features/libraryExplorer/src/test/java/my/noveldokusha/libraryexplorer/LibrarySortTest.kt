package my.noveldokusha.libraryexplorer

import my.noveldokusha.core.appPreferences.LibrarySortOption
import my.noveldokusha.core.appPreferences.SortConfig
import my.noveldokusha.core.appPreferences.SortDirection
import my.noveldokusha.feature.local_database.BookWithContext
import my.noveldokusha.feature.local_database.tables.Book
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-sort regression suite for the library shelf, including the new
 * SOURCE grouping sort (prototype: "sort by Source").
 */
class LibrarySortTest {

    private fun book(
        title: String,
        url: String,
        lastRead: Long = 0,
        added: Long = 0,
        updated: Long = 0,
    ) = BookWithContext(
        book = Book(
            title = title,
            url = url,
            lastReadEpochTimeMilli = lastRead,
            addedToLibraryEpochTimeMilli = added,
            lastUpdateEpochTimeMilli = updated,
        ),
        chaptersCount = 10,
        chaptersReadCount = 0,
    )

    private fun entry(url: String) = when {
        url.contains("royalroad") -> "Royal Road"
        url.contains("webnovel") -> "Webnovel"
        url.isLocalUriLike() -> "Local"
        else -> null
    }

    private fun String.isLocalUriLike() = startsWith("file:") || startsWith("content:")

    @Test
    fun `source sort groups books by source name ascending with title tiebreak`() {
        val books = listOf(
            book("Zeta Adventure", "https://webnovel.com/zeta"),
            book("Alpha Quest", "https://royalroad.com/alpha"),
            book("A Story", "https://webnovel.com/astory"),
            book("Beta Tale", "https://royalroad.com/beta"),
        )
        val sorted = sortLibraryBooks(
            books,
            SortConfig(LibrarySortOption.SOURCE, SortDirection.ASC),
            ::entry,
        )
        val keys = sorted.map { entry(it.book.url) }
        // Royal Road group (Alpha, Beta) then Webnovel group (A Story, Zeta)
        assertEquals(
            listOf("Royal Road", "Royal Road", "Webnovel", "Webnovel"),
            keys,
        )
        assertEquals(
            listOf("Alpha Quest", "Beta Tale", "A Story", "Zeta Adventure"),
            sorted.map { it.book.title },
        )
    }

    @Test
    fun `source sort descending reverses source groups but keeps titles sorted`() {
        val books = listOf(
            book("Alpha Quest", "https://royalroad.com/alpha"),
            book("Zeta Adventure", "https://webnovel.com/zeta"),
        )
        val sorted = sortLibraryBooks(
            books,
            SortConfig(LibrarySortOption.SOURCE, SortDirection.DESC),
            ::entry,
        )
        assertEquals(listOf("Webnovel", "Royal Road"), sorted.map { entry(it.book.url) })
    }

    @Test
    fun `unknown sources sink after known sources`() {
        val books = listOf(
            book("Mystery Book", "https://unknown-source.com/mystery"),
            book("Known Book", "https://royalroad.com/known"),
        )
        val sorted = sortLibraryBooks(
            books,
            SortConfig(LibrarySortOption.SOURCE, SortDirection.ASC),
            ::entry,
        )
        assertEquals("Known Book", sorted.first().book.title)
        assertEquals("Mystery Book", sorted.last().book.title)
    }

    @Test
    fun `title sort is case insensitive`() {
        val books = listOf(
            book("banana", "https://royalroad.com/b"),
            book("Apple", "https://royalroad.com/a"),
            book("Cherry", "https://royalroad.com/c"),
        )
        val sorted = sortLibraryBooks(
            books,
            SortConfig(LibrarySortOption.TITLE, SortDirection.ASC),
            ::entry,
        )
        assertEquals(
            listOf("Apple", "banana", "Cherry"),
            sorted.map { it.book.title },
        )
    }

    @Test
    fun `unread chapters sort puts least-unread first ascending`() {
        val b1 = book("A", "https://royalroad.com/a").copy(chaptersCount = 10, chaptersReadCount = 2) // 8 unread
        val b2 = book("B", "https://royalroad.com/b").copy(chaptersCount = 10, chaptersReadCount = 9) // 1 unread
        val b3 = book("C", "https://royalroad.com/c").copy(chaptersCount = 10, chaptersReadCount = 5) // 5 unread
        val sorted = sortLibraryBooks(
            listOf(b1, b2, b3),
            SortConfig(LibrarySortOption.UNREAD_CHAPTERS, SortDirection.ASC),
            ::entry,
        )
        assertEquals(listOf("B", "C", "A"), sorted.map { it.book.title })
    }

    @Test
    fun `last read sort desc puts most recent first`() {
        val b1 = book("Old", "https://royalroad.com/old", lastRead = 100)
        val b2 = book("New", "https://royalroad.com/new", lastRead = 900)
        val b3 = book("Mid", "https://royalroad.com/mid", lastRead = 500)
        val sorted = sortLibraryBooks(
            listOf(b1, b2, b3),
            SortConfig(LibrarySortOption.LAST_READ, SortDirection.DESC),
            ::entry,
        )
        assertEquals(listOf("New", "Mid", "Old"), sorted.map { it.book.title })
    }

    @Test
    fun `added sort asc puts oldest additions first`() {
        val b1 = book("Late", "https://royalroad.com/late", added = 300)
        val b2 = book("Early", "https://royalroad.com/early", added = 100)
        val sorted = sortLibraryBooks(
            listOf(b1, b2),
            SortConfig(LibrarySortOption.ADDED, SortDirection.ASC),
            ::entry,
        )
        assertEquals(listOf("Early", "Late"), sorted.map { it.book.title })
    }

    @Test
    fun `sort is stable for equal keys`() {
        val b1 = book("First", "https://royalroad.com/1", updated = 5)
        val b2 = book("Second", "https://royalroad.com/2", updated = 5)
        val sorted = sortLibraryBooks(
            listOf(b1, b2),
            SortConfig(LibrarySortOption.LAST_UPDATE, SortDirection.ASC),
            ::entry,
        )
        assertEquals(listOf("First", "Second"), sorted.map { it.book.title })
    }
}
