package my.noveldokusha.core.appPreferences

import kotlinx.serialization.Serializable

@Serializable
enum class LibrarySortOption(val displayName: String) {
    TITLE("title"),
    UNREAD_CHAPTERS("unread_chapters"),
    LAST_READ("last_read"),
    LAST_UPDATE("last_update"),
    ADDED("date_added");

    fun getNextSortOption(): LibrarySortOption {
        return when (this) {
            TITLE -> UNREAD_CHAPTERS
            UNREAD_CHAPTERS -> LAST_READ
            LAST_READ -> LAST_UPDATE
            LAST_UPDATE -> ADDED
            ADDED -> TITLE
        }
    }

    companion object {
        val DEFAULT = LAST_READ
    }
}

@Serializable
enum class SortDirection {
    ASC, DESC;

    fun toggle(): SortDirection = when (this) {
        ASC -> DESC
        DESC -> ASC
    }
}

@Serializable
data class SortConfig(
    val option: LibrarySortOption,
    val direction: SortDirection
) {
    fun toggleDirection(): SortConfig = copy(direction = direction.toggle())

    fun nextOption(): SortConfig = copy(option = option.getNextSortOption())

    companion object {
        val DEFAULT = SortConfig(LibrarySortOption.LAST_READ, SortDirection.DESC)
    }
}
