package my.noveldokusha.features.reader

import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.features.reader.ChapterSequence.Found
import my.noveldokusha.features.reader.ChapterSequence.InvalidChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Логика последовательности глав: идентичность по URL, индексы,
 * hasPrev/hasNext, отсутствие фолбэка на индекс 0 (Bug1a).
 */
class ChapterSequenceTest {

    private val chapters = listOf(
        chapter("c1"),
        chapter("c2"),
        chapter("c3"),
    )

    private fun chapter(url: String) = Chapter(
        title = "Chapter $url",
        url = url,
        bookUrl = "book",
        position = 0,
    )

    // ---- идентичность по URL ----

    @Test
    fun findsChapterByUrl() {
        val seq = ChapterSequence.of(chapters, "c2")
        assertTrue(seq is Found)
        assertEquals(1, (seq as Found).index)
        assertEquals("c2", seq.chapter.url)
    }

    @Test
    fun firstChapterHasIndexZero() {
        val seq = ChapterSequence.of(chapters, "c1")
        assertTrue(seq is Found)
        assertEquals(0, (seq as Found).index)
    }

    @Test
    fun lastChapterHasLastIndex() {
        val seq = ChapterSequence.of(chapters, "c3")
        assertTrue(seq is Found)
        assertEquals(2, (seq as Found).index)
    }

    // ---- глава не найдена -> InvalidChapter, БЕЗ фолбэка на 0 (Bug1a) ----

    @Test
    fun missingChapterIsInvalidNotFirst() {
        val seq = ChapterSequence.of(chapters, "https://example.com/unknown")
        assertEquals(InvalidChapter, seq)
    }

    @Test
    fun emptyListIsInvalid() {
        assertEquals(InvalidChapter, ChapterSequence.of(emptyList(), "c1"))
    }

    @Test
    fun duplicateUrlsResolveToFirstOccurrence() {
        val dup = listOf(chapter("c1"), chapter("c1"), chapter("c2"))
        val seq = ChapterSequence.of(dup, "c1")
        assertTrue(seq is Found)
        assertEquals(0, (seq as Found).index)
    }

    // ---- hasPrev/hasNext ----

    @Test
    fun firstChapterHasNoPrevButHasNext() {
        val seq = ChapterSequence.of(chapters, "c1") as Found
        assertFalse(seq.hasPrev)
        assertTrue(seq.hasNext)
    }

    @Test
    fun middleChapterHasPrevAndNext() {
        val seq = ChapterSequence.of(chapters, "c2") as Found
        assertTrue(seq.hasPrev)
        assertTrue(seq.hasNext)
    }

    @Test
    fun lastChapterHasPrevButNoNext() {
        val seq = ChapterSequence.of(chapters, "c3") as Found
        assertTrue(seq.hasPrev)
        assertFalse(seq.hasNext)
    }

    @Test
    fun singleChapterHasNoPrevAndNoNext() {
        val seq = ChapterSequence.of(listOf(chapter("c1")), "c1") as Found
        assertFalse(seq.hasPrev)
        assertFalse(seq.hasNext)
    }

    // ---- neighbor ----

    @Test
    fun neighborReturnsAdjacentChapter() {
        val seq = ChapterSequence.of(chapters, "c2") as Found
        assertEquals("c1", seq.neighbor(-1)?.url)
        assertEquals("c3", seq.neighbor(1)?.url)
    }

    @Test
    fun neighborOutOfBoundsIsNull() {
        val seq = ChapterSequence.of(chapters, "c1") as Found
        assertNull(seq.neighbor(-1))
        val last = ChapterSequence.of(chapters, "c3") as Found
        assertNull(last.neighbor(1))
    }
}