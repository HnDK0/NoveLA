package my.noveldokusha.globalsourcesearch

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSourceOrderTest {

    private data class S(val url: String, val name: String)

    private val keyOf: (S) -> String = { it.url }

    @Test
    fun `priority sources come first in dragged order`() {
        val sources = listOf(
            S("https://a.com", "Alpha"),
            S("https://b.com", "Beta"),
            S("https://c.com", "Gamma"),
        )
        val result = SearchSourceOrder.apply(sources, listOf("https://c.com", "https://a.com"), keyOf)
        assertEquals(listOf("https://c.com", "https://a.com", "https://b.com"), result.map { it.url })
    }

    @Test
    fun `unlisted sources keep natural order after priority`() {
        val sources = listOf(
            S("https://a.com", "Alpha"),
            S("https://b.com", "Beta"),
            S("https://c.com", "Gamma"),
            S("https://d.com", "Delta"),
        )
        val result = SearchSourceOrder.apply(sources, listOf("https://d.com"), keyOf)
        assertEquals(
            listOf("https://d.com", "https://a.com", "https://b.com", "https://c.com"),
            result.map { it.url },
        )
    }

    @Test
    fun `empty priority list is a no-op`() {
        val sources = listOf(S("https://b.com", "B"), S("https://a.com", "A"))
        assertEquals(sources, SearchSourceOrder.apply(sources, emptyList(), keyOf))
    }

    @Test
    fun `stale priority keys are ignored`() {
        val sources = listOf(S("https://a.com", "A"), S("https://b.com", "B"))
        val result = SearchSourceOrder.apply(sources, listOf("https://gone.com", "https://b.com"), keyOf)
        assertEquals(listOf("https://b.com", "https://a.com"), result.map { it.url })
    }

    @Test
    fun `sort is stable for unranked`() {
        val sources = listOf(S("https://z.com", "Z"), S("https://a.com", "A"), S("https://m.com", "M"))
        val result = SearchSourceOrder.apply(sources, listOf("https://m.com"), keyOf)
        assertEquals(listOf("https://m.com", "https://z.com", "https://a.com"), result.map { it.url })
    }

    @Test
    fun `moved relocates a dragged item`() {
        assertEquals(
            listOf("c", "a", "b", "d"),
            SearchSourceOrder.moved(listOf("a", "b", "c", "d"), from = 2, to = 0),
        )
        assertEquals(
            listOf("a", "c", "d", "b"),
            SearchSourceOrder.moved(listOf("a", "b", "c", "d"), from = 1, to = 3),
        )
    }

    @Test
    fun `moved with invalid indices is a no-op`() {
        val list = listOf("a", "b")
        assertEquals(list, SearchSourceOrder.moved(list, 0, 5))
        assertEquals(list, SearchSourceOrder.moved(list, -1, 0))
        assertEquals(list, SearchSourceOrder.moved(list, 1, 1))
    }

    @Test
    fun `display order puts priority first then the rest`() {
        val all = listOf("a", "b", "c", "d")
        assertEquals(
            listOf("c", "a", "b", "d"),
            SearchSourceOrder.displayOrder(all, listOf("c", "a")),
        )
        assertEquals(all, SearchSourceOrder.displayOrder(all, emptyList()))
    }
}
