package my.noveldokusha.sourceexplorer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.states.PagedListIteratorState
import my.noveldokusha.core.appPreferences.ListLayoutMode
import my.noveldokusha.core.appPreferences.SortOrder
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.scraper.ActiveFilters
import my.noveldokusha.scraper.LuaFilter

internal data class SourceCatalogScreenState(
    val sourceCatalogNameStrId: State<Int>,
    val sourceCatalogName: State<String?>,
    val searchTextInput: MutableState<String>,
    val fetchIterator: PagedListIteratorState<BookMetadata>,
    val toolbarMode: MutableState<ToolbarMode>,
    val listLayoutMode: MutableState<ListLayoutMode>,
    val sortOrder: MutableState<SortOrder>,

    // Размер сетки — общий preference (2..6), дефолт 3
    val gridColumns: MutableState<Int>,

    // Фильтры — показывать кнопку только если источник реализует FilterableCatalog
    val hasFilters: Boolean,
    val filterList: State<List<LuaFilter>>,
    val activeFilters: MutableState<ActiveFilters>,
    val isFilterSheetOpen: MutableState<Boolean>,
)
