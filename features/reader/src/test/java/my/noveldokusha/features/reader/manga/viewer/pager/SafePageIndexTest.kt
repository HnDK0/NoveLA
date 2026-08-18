package my.noveldokusha.features.reader.manga.viewer.pager

import my.noveldokusha.features.reader.manga.MangaPage
import org.junit.Assert.assertEquals
import org.junit.Test

class SafePageIndexTest {

    private val pages = List(3) { i -> MangaPage(url = "page$i", index = i) }

    @Test
    fun emptyListReturnsZero() {
        assertEquals(0, safePageIndex(emptyList(), startPage = 5))
    }

    @Test
    fun startPageBelowRangeCoercesToZero() {
        assertEquals(0, safePageIndex(pages, startPage = -3))
    }

    @Test
    fun startPageInRangeIsKept() {
        assertEquals(1, safePageIndex(pages, startPage = 1))
    }

    @Test
    fun startPageAboveRangeCoercesToLastIndex() {
        assertEquals(2, safePageIndex(pages, startPage = 99))
    }
}
