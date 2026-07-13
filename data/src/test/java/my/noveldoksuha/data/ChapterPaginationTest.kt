package my.noveldokusha.data

import my.noveldokusha.core.domain.ChapterPagination.MIN_CHAPTERS_PER_PAGE
import my.noveldokusha.core.domain.ChapterPagination.isPageCounterConsistent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPaginationTest {

    @Test
    fun `single page needs only one chapter`() {
        assertTrue(isPageCounterConsistent(lastPage = 1, chapterCount = 1))
        assertTrue(isPageCounterConsistent(lastPage = 1, chapterCount = 5))
    }

    @Test
    fun `single empty page is inconsistent`() {
        assertFalse(isPageCounterConsistent(lastPage = 1, chapterCount = 0))
    }

    @Test
    fun `multi page below floor is inconsistent`() {
        // N=10 → нижняя граница = 9*30 + 1 = 271
        assertFalse(isPageCounterConsistent(lastPage = 10, chapterCount = 270))
        assertFalse(isPageCounterConsistent(lastPage = 10, chapterCount = 50))
    }

    @Test
    fun `multi page at or above floor is consistent`() {
        assertTrue(isPageCounterConsistent(lastPage = 10, chapterCount = 271))
        assertTrue(isPageCounterConsistent(lastPage = 10, chapterCount = 2000))
    }

    @Test
    fun `zero or negative page is inconsistent`() {
        assertFalse(isPageCounterConsistent(lastPage = 0, chapterCount = 100))
        assertFalse(isPageCounterConsistent(lastPage = -1, chapterCount = 100))
    }

    @Test
    fun `floor formula matches constant`() {
        // граница для N страниц = (N-1)*MIN + 1
        val n = 3
        val boundary = (n - 1) * MIN_CHAPTERS_PER_PAGE + 1
        assertFalse(isPageCounterConsistent(lastPage = n, chapterCount = boundary - 1))
        assertTrue(isPageCounterConsistent(lastPage = n, chapterCount = boundary))
    }
}
