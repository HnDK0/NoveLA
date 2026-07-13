package my.noveldokusha.globalsourcesearch

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import my.noveldokusha.coreui.states.PagedListIteratorState
import my.noveldokusha.data.CatalogItem
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.core.utils.asMutableStateOf
import my.noveldokusha.network.interceptors.CloudflareBypassSignal
import javax.inject.Inject

internal interface GlobalSourceSearchStateBundle {
    val initialInput: String
}

@HiltViewModel
internal class GlobalSourceSearchViewModel @Inject constructor(
    state: SavedStateHandle,
    private val scraperRepository: ScraperRepository,
) : ViewModel(), GlobalSourceSearchStateBundle {
    override val initialInput by StateExtra_String(state)

    @Volatile
    private var searchJob: Job? = null

    val searchInput = state.asMutableStateOf("searchInput") { initialInput }
    val sourcesResults = mutableStateListOf<SourceResults>()

    init {
        search(text = searchInput.value)

        // После обхода CF перезапускаем поиск только для источников
        // чей домен совпадает с пройденным хостом.
        // SharedFlow — сигнал получают все подписчики одновременно.
        viewModelScope.launch {
            CloudflareBypassSignal.bypassCompleted.collect { bypassedHost ->
                sourcesResults
                    .filter { result ->
                        runCatching {
                            android.net.Uri.parse(result.source.catalog.baseUrl).host == bypassedHost
                        }.getOrDefault(false)
                    }
                    .forEach { result ->
                        result.fetchIterator.reset()
                        result.fetchIterator.fetchNext()
                    }
            }
        }
    }

    fun search(text: String) {
        if (text.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            sourcesResults.clear()
            scraperRepository.sourcesCatalogListFlow()
                .take(1)
                .collect { sources ->
                    sources.map { source ->
                        SourceResults(
                            source = source,
                            searchInput = text,
                            coroutineScope = this@launch
                        )
                    }.let(sourcesResults::addAll)
                }
        }
    }
}

internal data class SourceResults(
    val source: CatalogItem,
    val searchInput: String,
    val coroutineScope: CoroutineScope
) {
    val fetchIterator = PagedListIteratorState(coroutineScope) {
        source.catalog.getCatalogSearch(it, searchInput)
    }

    init {
        fetchIterator.fetchNext()
    }
}