package my.noveldokusha.features.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedSourcesPolicyTest {

    @Test
    fun `resolve returns the source chosen for the category`() {
        val map = mapOf("Trending" to "https://a.com", "Latest" to "https://b.com")
        assertEquals("https://a.com", FeedSourcesPolicy.resolveSource(map, HomeFeed.TRENDING))
        assertEquals("https://b.com", FeedSourcesPolicy.resolveSource(map, HomeFeed.LATEST))
    }

    @Test
    fun `missing category key resolves to null (not set up)`() {
        assertNull(FeedSourcesPolicy.resolveSource(mapOf("Trending" to "https://a.com"), HomeFeed.FOR_YOU))
        assertNull(FeedSourcesPolicy.resolveSource(emptyMap(), HomeFeed.FOR_YOU))
    }

    @Test
    fun `blank source value resolves to null`() {
        assertNull(FeedSourcesPolicy.resolveSource(mapOf("For you" to ""), HomeFeed.FOR_YOU))
    }

    @Test
    fun `withSource writes one category without touching others`() {
        val next = FeedSourcesPolicy.withSource(
            mapOf("Trending" to "https://a.com"),
            HomeFeed.LATEST,
            "https://b.com",
        )
        assertEquals("https://a.com", next["Trending"])
        assertEquals("https://b.com", next["Latest"])
    }

    @Test
    fun `withSource with null clears the category`() {
        val next = FeedSourcesPolicy.withSource(
            mapOf("Trending" to "https://a.com", "Latest" to "https://b.com"),
            HomeFeed.TRENDING,
            null,
        )
        assertEquals(1, next.size)
        assertEquals("https://b.com", next["Latest"])
    }

    @Test
    fun `visibleFeeds empty pref means every category`() {
        assertEquals(HomeFeed.entries.toList(), FeedSourcesPolicy.visibleFeeds(emptySet()))
    }

    @Test
    fun `visibleFeeds keeps only enabled categories in canonical order`() {
        val shown = FeedSourcesPolicy.visibleFeeds(setOf("Latest", "For you"))
        assertEquals(listOf(HomeFeed.FOR_YOU, HomeFeed.LATEST), shown)
    }

    @Test
    fun `withVisibleFeed hiding one of all keeps the rest`() {
        val next = FeedSourcesPolicy.withVisibleFeed(emptySet(), HomeFeed.FOR_YOU, shown = false)
        // Empty pref expands to "all" first, then FOR_YOU is removed.
        assertEquals(setOf("Trending", "Latest", "Recently updated"), next)
    }

    @Test
    fun `withVisibleFeed hides the last visible row only by keeping it`() {
        val next = FeedSourcesPolicy.withVisibleFeed(setOf("For you"), HomeFeed.FOR_YOU, shown = false)
        assertEquals(setOf("For you"), next)
    }

    @Test
    fun `withVisibleFeed adds and removes categories`() {
        val added = FeedSourcesPolicy.withVisibleFeed(setOf("For you"), HomeFeed.LATEST, shown = true)
        assertEquals(setOf("For you", "Latest"), added)
        val removed = FeedSourcesPolicy.withVisibleFeed(added, HomeFeed.FOR_YOU, shown = false)
        assertEquals(setOf("Latest"), removed)
    }
}

