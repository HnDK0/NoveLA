package my.noveldokusha.tooling.novel_migration.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.tooling.novel_migration.data.MigrationRepository
import javax.inject.Inject

data class MigrationTabState(
    val sourcesWithCounts: List<Pair<SourceInterface.Catalog, Int>> = emptyList(),
    val loading: Boolean = false,
)

@HiltViewModel
class MigrationTabViewModel @Inject constructor(
    private val scraperRepository: ScraperRepository,
    private val migrationRepository: MigrationRepository,
    private val libraryBooks: LibraryBooksRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MigrationTabState())
    val uiState: StateFlow<MigrationTabState> = _uiState.asStateFlow()

    init {
        loadData()
        observeLibraryChanges()
    }

    private fun observeLibraryChanges() {
        viewModelScope.launch {
            libraryBooks.getBooksInLibraryWithContextFlow.collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val catalogs = scraperRepository.scraper.sourcesList
                .filterIsInstance<SourceInterface.Catalog>()

            val sourceUrls = catalogs.map { it.baseUrl }
            val counts = withContext(Dispatchers.IO) {
                migrationRepository.getBookCountPerSource(sourceUrls)
            }

            _uiState.value = MigrationTabState(
                sourcesWithCounts = catalogs.map { it to (counts[it.baseUrl] ?: 0) },
                loading = false,
            )
        }
    }

    private fun loadData() {
        refresh()
    }
}
