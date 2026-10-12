package my.noveldokusha.features.discover

/**
 * Pure policy for the homepage feed settings:
 *
 * - Each homepage category (feed) maps to exactly ONE source, persisted in
 *   [my.noveldokusha.core.appPreferences.AppPreferences.HOME_FEED_SOURCE_MAP]
 *   (feed label -> source baseUrl). A missing key = category not set up yet.
 * - All configured categories are shown on the homepage at once, stacked as
 *   rows; the category labels at the top act as scroll shortcuts.
 * - A separate visibility set controls which category rows appear at all
 *   (never empty — at least one row stays).
 */
internal object FeedSourcesPolicy {

    /** The source chosen for [feed] (null = none chosen yet). */
    fun resolveSource(
        sourceMap: Map<String, String>,
        feed: HomeFeed,
    ): String? = sourceMap[feed.label]?.takeIf { it.isNotBlank() }

    /** Writes/clears one category's source (null clears the category). */
    fun withSource(
        sourceMap: Map<String, String>,
        feed: HomeFeed,
        sourceBaseUrl: String?,
    ): Map<String, String> {
        val next = sourceMap.toMutableMap()
        if (sourceBaseUrl.isNullOrBlank()) next.remove(feed.label) else next[feed.label] = sourceBaseUrl
        return next
    }

    /** Category rows shown on the homepage. Empty pref = all; guarantee at least one. */
    fun visibleFeeds(visible: Set<String>): List<HomeFeed> {
        if (visible.isEmpty()) return HomeFeed.entries.toList()
        val shown = HomeFeed.entries.filter { it.label in visible }
        return if (shown.isEmpty()) listOf(HomeFeed.FOR_YOU) else shown
    }

    /** Adds/removes a category row (never allows an empty result). */
    fun withVisibleFeed(visible: Set<String>, feed: HomeFeed, shown: Boolean): Set<String> {
        val current = if (visible.isEmpty()) HomeFeed.entries.map { it.label }.toSet() else visible
        val next = if (shown) current + feed.label else current - feed.label
        return if (next.isEmpty()) setOf(feed.label) else next
    }
}
