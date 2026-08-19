package my.noveldokusha.features.reader.manga.viewer.webtoon

import my.noveldokusha.features.reader.manga.MangaChapter
import my.noveldokusha.features.reader.manga.MangaPage
import my.noveldokusha.features.reader.manga.viewer.webtoon.MangaWebtoonWindow.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Юнит-тест окна вебтун-ленты: плоский список ↔ глава/страница,
 * расширение окна, вытеснение, НИКОГДА не выкидывающее текущую главу.
 */
class MangaWebtoonWindowTest {

    private fun chapter(index: Int, pages: Int) = MangaChapter(
        url = "url/$index",
        title = "Chapter $index",
        index = index,
        pages = List(pages) { pageIndex -> MangaPage("url/$index/$pageIndex", pageIndex) },
    )

    @Test
    fun singleChapterProducesOnlyPagesNoTransitions() {
        val w = MangaWebtoonWindow()
        w.setChapter(chapter(0, 3))
        assertEquals(3, w.itemCount)
        val items = w.buildItems()
        assertEquals(3, items.size)
        assertTrue(items.all { it is Item.Page })
    }

    @Test
    fun consecutiveChaptersInsertTransitionBetween() {
        val w = MangaWebtoonWindow()
        w.setChapter(chapter(0, 2))
        assertTrue(w.appendNext(chapter(1, 2)))
        // items: 2 pages + transition + 2 pages
        assertEquals(5, w.itemCount)
        val items = w.buildItems()
        assertTrue(items[0] is Item.Page)
        assertTrue(items[1] is Item.Page)
        assertTrue(items[2] is Item.Transition)
        assertTrue(items[3] is Item.Page)
        assertTrue(items[4] is Item.Page)
        assertEquals("url/1/0", (items[3] as Item.Page).pageUrl)
    }

    @Test
    fun appendRequiresConsecutiveNextIndex() {
        val w = MangaWebtoonWindow()
        w.setChapter(chapter(0, 1))
        // Непоследовательная глава не вставляется.
        assertFalse(w.appendNext(chapter(2, 1)))
        assertTrue(w.appendNext(chapter(1, 1)))
    }

    @Test
    fun prependKeepsCurrentIndexConsistent() {
        val w = MangaWebtoonWindow()
        w.setChapter(chapter(1, 2))
        val before = w.currentChapterIndex
        assertTrue(w.prependPrevious(chapter(0, 1)))
        assertEquals(0, w.chapters.first().index)
        // Текущая глава теперь стоит на 1 позиции позже.
        assertEquals(before + 1, w.currentChapterIndex)
        assertEquals(1, w.chapters[w.currentChapterIndex].index)
    }

    @Test
    fun locateMapsFlatPositionToChapterAndPage() {
        val w = MangaWebtoonWindow()
        w.setChapter(chapter(0, 2))
        w.appendNext(chapter(1, 3))
        // flat: [0.p0, 0.p1, T, 1.p0, 1.p1, 1.p2]
        assertEquals(0 to 0, w.locate(0))
        assertEquals(0 to 1, w.locate(1))
        assertEquals(1 to 0, w.locate(2)) // переход принадлежит следующей главе
        assertEquals(1 to 0, w.locate(3)) // первая страница главы 1
        assertEquals(1 to 1, w.locate(4))
        assertEquals(1 to 2, w.locate(5)) // последняя страница последней главы
        assertNull(w.locate(6)) // itemCount = 6, позиция 6 за пределами
        assertNull(w.locate(-1))
    }

    @Test
    fun flatPositionMatchesFirstPageOffset() {
        val w = MangaWebtoonWindow()
        w.setChapter(chapter(0, 2))
        w.appendNext(chapter(1, 3))
        // первая страница главы 1 стоит после: 2 страницы + 1 переход
        assertEquals(3, w.firstPagePositionOf(1))
        assertEquals(4, w.flatPosition(1, 1))
    }

    @Test
    fun pruneDoesNothingWhenWithinBudget() {
        val w = MangaWebtoonWindow()
        w.maxChapters = 3
        w.setChapter(chapter(1, 1))
        w.prependPrevious(chapter(0, 1))
        w.appendNext(chapter(2, 1))
        assertFalse(w.prune())
        assertEquals(3, w.chapters.size)
    }
}