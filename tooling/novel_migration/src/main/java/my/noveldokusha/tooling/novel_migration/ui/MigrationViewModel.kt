package my.noveldokusha.tooling.novel_migration.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import my.noveldokusha.core.Response
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.LibraryBooksRepository
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.tooling.novel_migration.chapters_matcher.ChaptersMatcher
import my.noveldokusha.tooling.novel_migration.chapters_matcher.MatchResult
import my.noveldokusha.tooling.novel_migration.data.MigrationOptions
import my.noveldokusha.strings.R
import my.noveldokusha.tooling.novel_migration.data.MigrationRepository
import timber.log.Timber
import javax.inject.Inject

data class PickerResult(
    val source: SourceInterface.Catalog,
    val book: BookResult,
    val chapters: List<ChapterResult>?,
    val error: String? = null,
) {
    val isLoaded get() = chapters != null
    val chapterCount get() = chapters?.size ?: 0
}

data class MigrationUiState(
    val bookUrl: String = "",
    val bookTitle: String = "",
    val searchResults: List<PickerResult> = emptyList(),
    val isSearching: Boolean = false,
    val chaptersLoadingIndex: Int = -1,
    val selectedResult: ScoredSearchResult? = null,
    val matchResult: MatchResult? = null,
    val isMatching: Boolean = false,
    val isMigrating: Boolean = false,
    val isComplete: Boolean = false,
    val migrationSuccess: Boolean = false,
    val chaptersMatched: Int = 0,
    val chaptersTotal: Int = 0,
    val migrationError: String? = null,
    val error: String? = null,
)

@HiltViewModel
class MigrationViewModel @Inject constructor(
    private val application: Application,
    private val scraperRepository: ScraperRepository,
    private val migrationRepository: MigrationRepository,
    private val libraryBooks: LibraryBooksRepository,
    private val bookChapters: BookChaptersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MigrationUiState())
    val uiState: StateFlow<MigrationUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val chaptersMatcher = ChaptersMatcher()

    fun searchAllSources() {
        val sources = scraperRepository.scraper.sourcesList
            .filterIsInstance<SourceInterface.Catalog>()
        Timber.e("searchAllSources: found ${sources.size} Catalog sources, title=${_uiState.value.bookTitle}")
        if (sources.isEmpty()) {
            _uiState.update { it.copy(error = application.getString(R.string.migration_no_sources_available), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true, error = null, searchResults = emptyList(), chaptersLoadingIndex = -1) }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val rawResults = mutableListOf<PickerResult>()

            for (source in sources) {
                val results = searchCatalogWithRetry(source, _uiState.value.bookTitle)
                rawResults.addAll(results)
            }

            Timber.e("searchAllSources: total rawResults=${rawResults.size}")
            _uiState.update {
                it.copy(
                    searchResults = rawResults,
                    isSearching = false,
                    error = if (rawResults.isEmpty()) application.getString(R.string.migration_no_matches_found) else null,
                )
            }
        }
    }

    private suspend fun searchCatalogWithRetry(source: SourceInterface.Catalog, title: String): List<PickerResult> {
        for (attempt in 1..2) {
            val resp = withTimeoutOrNull(20_000L) {
                withContext(Dispatchers.IO) {
                    source.getCatalogSearch(0, title)
                }
            }
            if (resp is Response.Success) {
                Timber.e("searchCatalogWithRetry: source=${source.baseUrl} returned ${resp.data.list.size} books")
                return resp.data.list.map { PickerResult(source = source, book = it, chapters = null) }
            }
            if (attempt < 2) {
                Timber.e("searchCatalogWithRetry: retry $attempt for ${source.baseUrl}")
                delay(1000L)
            } else {
                Timber.e("searchCatalogWithRetry: source=${source.baseUrl} failed after 2 attempts")
            }
        }
        return emptyList()
    }

    fun onResultPicked(index: Int) {
        val result = _uiState.value.searchResults.getOrNull(index) ?: return
        Timber.e("onResultPicked: index=$index book=${result.book.title} chaptersLoaded=${result.isLoaded}")
        if (result.chapters == null) {
            _uiState.update { it.copy(chaptersLoadingIndex = index) }
            loadChaptersForResult(index, result)
        } else {
            selectResult(ScoredSearchResult(
                source = result.source,
                book = result.book,
                chapters = result.chapters,
            ))
        }
    }

    private fun loadChaptersForResult(index: Int, result: PickerResult) {
        viewModelScope.launch {
            try {
                val chapters = ChapterFetcher.fetchChapters(result.source, result.book.url)
                Timber.e("loadChaptersForResult: index=$index book=${result.book.title} chapters=${chapters.size}")
                _uiState.update { state ->
                    val updated = state.searchResults.toMutableList()
                    updated[index] = result.copy(chapters = chapters)
                    state.copy(searchResults = updated, chaptersLoadingIndex = -1)
                }
            } catch (e: Exception) {
                Timber.e(e, "loadChaptersForResult: index=$index book=${result.book.title} failed")
                _uiState.update { state ->
                    val updated = state.searchResults.toMutableList()
                    updated[index] = result.copy(chapters = emptyList(), error = e.message)
                    state.copy(searchResults = updated, chaptersLoadingIndex = -1)
                }
            }
        }
    }

    private fun selectResult(result: ScoredSearchResult) {
        Timber.e("selectResult: book=${result.book.title} chapters=${result.chapters.size} source=${result.source.baseUrl}")
        _uiState.update { it.copy(selectedResult = result, matchResult = null, isMatching = true, error = null) }
        loadAndMatchChapters(result)
    }

    private fun loadAndMatchChapters(result: ScoredSearchResult) {
        viewModelScope.launch {
            try {
                val oldBookUrl = _uiState.value.bookUrl
                Timber.e("loadAndMatchChapters: oldBookUrl=$oldBookUrl reading from local DB")

                val oldChapters = withContext(Dispatchers.IO) {
                    bookChapters.chapters(oldBookUrl).map { ChapterResult(title = it.title, url = it.url) }
                }
                Timber.e("loadAndMatchChapters: local DB returned ${oldChapters.size} chapters")

                val newChapters = result.chapters
                Timber.e("loadAndMatchChapters: oldChapters=${oldChapters.size} newChapters=${newChapters.size}")

                if (oldChapters.isEmpty() || newChapters.isEmpty()) {
                    val reason = when {
                        oldChapters.isEmpty() -> "old chapters empty"
                        newChapters.isEmpty() -> "new chapters empty"
                        else -> "unknown"
                    }
                    Timber.e("loadAndMatchChapters: FAILED - $reason")
                    _uiState.update { it.copy(error = application.getString(R.string.migration_failed_to_load_chapters, reason), isMatching = false) }
                    return@launch
                }

                val matchResult = withContext(Dispatchers.Default) {
                    chaptersMatcher.match(oldChapters, newChapters)
                }
                Timber.e("loadAndMatchChapters: matched ${matchResult.matched.size}/${newChapters.size}")
                _uiState.update { it.copy(matchResult = matchResult, isMatching = false) }
            } catch (e: Exception) {
                Timber.e(e, "loadAndMatchChapters: exception")
                _uiState.update { it.copy(error = e.message ?: application.getString(R.string.migration_unknown_error), isMatching = false) }
            }
        }
    }

    fun startMigration(options: MigrationOptions = MigrationOptions()) {
        val current = _uiState.value
        val result = current.selectedResult
        val matchResult = current.matchResult

        Timber.e("startMigration: called selectedResult=${result != null} matchResult=${matchResult != null}")

        if (result == null || matchResult == null) {
            val msg = if (result == null) application.getString(R.string.migration_no_book_selected) else application.getString(R.string.migration_chapter_matching_not_ready)
            Timber.e("startMigration: cannot start - $msg")
            _uiState.update { it.copy(error = msg) }
            return
        }

        _uiState.update { it.copy(isMigrating = true, error = null) }

        viewModelScope.launch {
            try {
                val migrateResult = withContext(Dispatchers.IO) {
                    migrationRepository.migrate(
                        oldBookUrl = _uiState.value.bookUrl,
                        newBookUrl = result.book.url,
                        newChapters = result.chapters,
                        matchedChapters = matchResult.matched,
                        newBookTitle = result.book.title,
                        options = options,
                    )
                }
                Timber.e("startMigration: done success=${migrateResult.success} chaptersMatched=${migrateResult.chaptersMatched}/${migrateResult.chaptersTotal} error=${migrateResult.error}")
                _uiState.update {
                    it.copy(
                        isMigrating = false,
                        isComplete = true,
                        migrationSuccess = migrateResult.success,
                        chaptersMatched = migrateResult.chaptersMatched,
                        chaptersTotal = migrateResult.chaptersTotal,
                        migrationError = migrateResult.error ?: application.getString(R.string.migration_unknown_error),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "startMigration: exception")
                _uiState.update {
                    it.copy(
                        isMigrating = false,
                        isComplete = true,
                        migrationSuccess = false,
                        migrationError = e.message ?: application.getString(R.string.migration_unknown_error),
                    )
                }
            }
        }
    }

    fun init(bookUrl: String, bookTitle: String) {
        Timber.e("init: bookUrl=$bookUrl bookTitle=$bookTitle")
        _uiState.value = MigrationUiState(bookUrl = bookUrl, bookTitle = bookTitle)
    }

    fun reset() {
        searchJob?.cancel()
        _uiState.value = MigrationUiState()
    }
}
