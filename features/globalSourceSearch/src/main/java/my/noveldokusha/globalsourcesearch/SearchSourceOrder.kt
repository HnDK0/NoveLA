package my.noveldokusha.globalsourcesearch

/**
 * Pure policy for the global-search source priority setting.
 *
 * The user drags sources into a personal priority order (persisted as a list of
 * source baseUrls). Global search renders one horizontal row per source, and the
 * rows follow this priority: sources present in the list come first (in the
 * dragged order), everything else keeps its natural order after them.
 */
internal object SearchSourceOrder {

    /**
     * Stable-sorts [sources] by the persisted [priority] list of keys.
     * Unlisted sources keep their relative order at the end.
     */
    fun <T> apply(
        sources: List<T>,
        priority: List<String>,
        keyOf: (T) -> String,
    ): List<T> {
        if (priority.isEmpty() || sources.size < 2) return sources
        val rank = HashMap<String, Int>(priority.size)
        priority.forEachIndexed { index, key -> if (key !in rank) rank[key] = index }
        // sortedBy is stable: unranked sources keep their natural order.
        return sources
            .mapIndexed { _, source -> source to (rank[keyOf(source)] ?: Int.MAX_VALUE) }
            .sortedBy { it.second }
            .map { it.first }
    }

    /** Reorders the full displayed list after a drag: move [from] to [to]. */
    fun moved(list: List<String>, from: Int, to: Int): List<String> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Full displayed order (priority first, then the rest alphabetically). */
    fun displayOrder(allKeys: List<String>, priority: List<String>): List<String> {
        val rest = allKeys.filter { it !in priority }
        return priority.filter { it in allKeys.toSet() } + rest
    }

}
