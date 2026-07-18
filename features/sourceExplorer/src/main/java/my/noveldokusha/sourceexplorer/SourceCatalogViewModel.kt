package my.noveldokusha.sourceexplorer

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.BaseViewModel
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.states.PagedListIteratorState
import my.noveldokusha.data.AppRepository
import my.noveldokusha.mappers.mapToBookMetadata
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.asMutableStateOf
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.network.interceptors.CloudflareBypassSignal
import my.noveldokusha.scraper.ActiveFilters
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import timber.log.Timber
import javax.inject.Inject

interface SourceCatalogStateBundle {
    var sourceBaseUrl: String
}

@HiltViewModel
internal class SourceCatalogViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val toasty: Toasty,
    stateHandle: SavedStateHandle,
    appPreferences: AppPreferences,
    scraper: Scraper,
) : BaseViewModel(), SourceCatalogStateBundle {

    override var sourceBaseUrl by StateExtra_String(stateHandle)
    private val source = scraper.getCompatibleSourceCatalog(sourceBaseUrl)!!
    private val filterableSource = source as? SourceInterface.FilterableCatalog

    private val _filterList = mutableStateOf(emptyList<my.noveldokusha.scraper.LuaFilter>())
    private val _activeFilters = mutableStateOf(ActiveFilters())

    val state = SourceCatalogScreenState(
        sourceCatalogNameStrId = mutableIntStateOf(source.nameStrId),
        sourceCatalogName      = mutableStateOf(source.name),
        searchTextInput        = stateHandle.asMutableStateOf("searchTextInput") { "" },
        toolbarMode            = stateHandle.asMutableStateOf("toolbarMode") { ToolbarMode.MAIN },
        fetchIterator          = PagedListIteratorState(viewModelScope) {
            source.getCatalogList(it).mapToBookMetadata()
        },
        listLayoutMode         = appPreferences.BOOKS_LIST_LAYOUT_MODE.state(viewModelScope),
        sortOrder              = appPreferences.SOURCE_SORT_ORDER.state(viewModelScope),
        gridColumns            = appPreferences.BOOKS_GRID_COLUMNS.state(viewModelScope),
        hasFilters             = filterableSource != null,
        filterList             = _filterList,
        activeFilters          = _activeFilters,
        isFilterSheetOpen      = mutableStateOf(false),
    )

    init {
        onSearchCatalog()

        if (filterableSource != null) {
            viewModelScope.launch {
                filterableSource.getFilterList()
                    .onSuccess { _filterList.value = it }
                    .onError { Timber.e(it.exception, "Failed to load filter list") }
            }
        }

        // Перезагружаем текущий список после успешного обхода CF.
        // SharedFlow — сигнал получают все подписчики одновременно.
        // Сравниваем host источника чтобы не трогать каталоги других сайтов.
        viewModelScope.launch {
            val sourceHost = runCatching {
                android.net.Uri.parse(sourceBaseUrl).host
            }.getOrNull()

            CloudflareBypassSignal.bypassCompleted.collect { bypassedHost ->
                Log.d(
                    "CF_DEBUG",
                    "bypassCompleted received: $bypassedHost, sourceHost: $sourceHost"
                )
                if (sourceHost != null && sourceHost == bypassedHost) {
                    Log.d("CF_DEBUG", "Reloading catalog for $sourceHost")
                    state.fetchIterator.reset()
                    state.fetchIterator.fetchNext()
                }
            }
        }
    }

    fun onSearchCatalog() {
        state.fetchIterator.setFunction { source.getCatalogList(it).mapToBookMetadata() }
        state.fetchIterator.reset()
        state.fetchIterator.fetchNext()
    }

    fun onSearchText(input: String) {
        state.fetchIterator.setFunction { source.getCatalogSearch(it, input).mapToBookMetadata() }
        state.fetchIterator.reset()
        state.fetchIterator.fetchNext()
    }

    fun onApplyFilters(filters: ActiveFilters) {
        _activeFilters.value = filters
        state.isFilterSheetOpen.value = false

        if (filterableSource != null && !filters.isEmpty) {
            state.fetchIterator.setFunction {
                filterableSource.getCatalogFiltered(it, filters).mapToBookMetadata()
            }
        } else {
            state.fetchIterator.setFunction { source.getCatalogList(it).mapToBookMetadata() }
        }
        state.fetchIterator.reset()
        state.fetchIterator.fetchNext()
    }

    fun onResetFilters() {
        onApplyFilters(ActiveFilters())
    }

    fun addToLibraryToggle(book: BookMetadata) =
        viewModelScope.launch(Dispatchers.IO) {
            val isInLibrary =
                appRepository.toggleBookmark(bookUrl = book.url, bookTitle = book.title)
            val res = if (isInLibrary) R.string.added_to_library else R.string.removed_from_library
            toasty.show(res)
        }
}