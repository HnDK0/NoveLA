package my.noveldokusha.tooling.novel_migration.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import my.noveldokusha.core.Response
import my.noveldokusha.data.BookChaptersRepository
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.tooling.novel_migration.chapters_matcher.ChaptersMatcher
import my.noveldokusha.strings.R
import my.noveldokusha.tooling.novel_migration.data.MigrationOptions
import my.noveldokusha.tooling.novel_migration.data.MigrationRepository
import timber.log.Timber
import javax.inject.Inject

private val TAG = "MassMigration"

data class MigratingBook(
    val book: Book,
    val result: ScoredSearchResult? = null,
    val results: List<ScoredSearchResult> = emptyList(),
    val selectedIndex: Int = -1,
    val isSearching: Boolean = false,
    val isSkipped: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null,
)

data class MassMigrationUiState(
    val source: SourceInterface.Catalog? = null,
    val books: List<Book> = emptyList(),
    val bookChapterCounts: Map<String, Int> = emptyMap(),
    val selectedBooks: Set<String> = emptySet(),
    val targetSource: SourceInterface.Catalog? = null,
    val targetSources: List<SourceInterface.Catalog> = emptyList(),
    val migratingBooks: List<MigratingBook> = emptyList(),
    val isSearchingAll: Boolean = false,
    val isMigrating: Boolean = false,
    val migrateProgress: Int = 0,
    val migrateTotal: Int = 0,
    val successCount: Int = 0,
    val skipCount: Int = 0,
    val failCount: Int = 0,
    val isComplete: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MassMigrationViewModel @Inject constructor(
    private val application: Application,
    private val migrationRepository: MigrationRepository,
    private val scraperRepository: ScraperRepository,
    private val bookChapters: BookChaptersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MassMigrationUiState())
    val uiState: StateFlow<MassMigrationUiState> = _uiState.asStateFlow()

    private val chaptersMatcher = ChaptersMatcher()
    private var searchJob: Job? = null
    private var migrateJob: Job? = null
    private val sourceFailures = mutableMapOf<String, Int>()

    fun setSource(source: SourceInterface.Catalog) {
        val otherSources = allSources.filter { it.baseUrl != source.baseUrl }
        _uiState.update { it.copy(source = source, targetSources = otherSources) }
        loadBooks(source.baseUrl)
    }

    private fun loadBooks(sourceBaseUrl: String) {
        viewModelScope.launch {
            val books = withContext(Dispatchers.IO) {
                migrationRepository.getLibraryBooksFromSource(sourceBaseUrl)
            }
            val chapterCounts = withContext(Dispatchers.IO) {
                books.associate { book ->
                    val count = bookChapters.chapters(book.url).size
                    book.url to count
                }
            }
            _uiState.update { it.copy(books = books, bookChapterCounts = chapterCounts, selectedBooks = emptySet()) }
        }
    }

    fun setTargetSource(source: SourceInterface.Catalog?) {
        _uiState.update { it.copy(targetSource = source) }
    }

    fun toggleBookSelection(book: Book) {
        val current = _uiState.value.selectedBooks.toMutableSet()
        if (book.url in current) current.remove(book.url) else current.add(book.url)
        _uiState.update { it.copy(selectedBooks = current) }
    }

    fun selectAllBooks() {
        _uiState.update { it.copy(selectedBooks = _uiState.value.books.map { it.url }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedBooks = emptySet()) }
    }

    val allSources: List<SourceInterface.Catalog>
        get() = scraperRepository.scraper.sourcesList.filterIsInstance<SourceInterface.Catalog>()

    fun startSearch() {
        val current = _uiState.value
        val selected = current.books.filter { it.url in current.selectedBooks }
        if (selected.isEmpty()) return

        val sourceBaseUrl = current.source?.baseUrl ?: return
        val candidates = current.targetSource?.let { listOf(it) }
            ?: current.targetSources

        Timber.e("startSearch: selected=${selected.size} books, candidates=${candidates.size} targetSource=${current.targetSource?.baseUrl}")

        val migratingBooks = selected.map { MigratingBook(book = it, isSearching = true) }
        _uiState.update {
            it.copy(
                migratingBooks = migratingBooks,
                isSearchingAll = true,
                error = null,
            )
        }

        sourceFailures.clear()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val books = _uiState.value.migratingBooks.toMutableList()
            for ((index, mb) in books.withIndex()) {
                ensureActive()
                val oldChaptersCount = current.bookChapterCounts[mb.book.url] ?: 0
                Timber.e("startSearch: book=${mb.book.title} oldChapters=$oldChaptersCount")

                val result = searchBook(mb.book, candidates, oldChaptersCount)
                books[index] = result
                Timber.e("startSearch: book=${mb.book.title} result=${result.result?.book?.title} skipped=${result.result == null} error=${result.error}")
                _uiState.update { it.copy(migratingBooks = books.toList()) }
            }
            _uiState.update { it.copy(isSearchingAll = false) }
        }
    }

    private suspend fun searchBook(
        book: Book,
        candidates: List<SourceInterface.Catalog>,
        oldChaptersCount: Int,
    ): MigratingBook {
        val allResults = mutableListOf<ScoredSearchResult>()
        var bestResult: ScoredSearchResult? = null
        var lastError: String? = null

        for (source in candidates) {
            if (sourceFailures.getOrDefault(source.baseUrl, 0) >= 3) {
                Timber.e("searchBook: skipping ${source.baseUrl} (${sourceFailures[source.baseUrl]} failures)")
                continue
            }

            val searchResult = searchSource(source, book.title) ?: continue
            val chapters = fetchChaptersWithRetry(source, searchResult.url) ?: continue

            val scored = ScoredSearchResult(source = source, book = searchResult, chapters = chapters)
            allResults.add(scored)

            if (chapters.size >= oldChaptersCount) {
                Timber.e("searchBook: FOUND at ${source.baseUrl} chapters=${chapters.size} >= old=$oldChaptersCount")
                bestResult = scored
                break
            }

            if (bestResult == null || chapters.size > bestResult.chapters.size) {
                bestResult = scored
            }
        }

        val finalResult = bestResult
        val bestIndex = allResults.indexOf(finalResult)
        return MigratingBook(
            book = book,
            result = finalResult,
            results = allResults,
            selectedIndex = bestIndex,
            isSearching = false,
            isSkipped = finalResult == null,
            error = if (finalResult == null) (lastError ?: application.getString(R.string.migration_no_matching_source)) else null,
        )
    }

    private suspend fun searchSource(source: SourceInterface.Catalog, title: String): my.noveldokusha.scraper.domain.BookResult? {
        for (attempt in 1..2) {
            val resp = withTimeoutOrNull(20_000L) {
                withContext(Dispatchers.IO) {
                    source.getCatalogSearch(0, title)
                }
            }
            if (resp is Response.Success && resp.data.list.isNotEmpty()) {
                sourceFailures.remove(source.baseUrl)
                return resp.data.list.first()
            }
            if (attempt < 2) {
                Timber.e("searchSource: retry $attempt for ${source.baseUrl} title=$title")
                delay(1000L)
            } else {
                sourceFailures[source.baseUrl] = (sourceFailures[source.baseUrl] ?: 0) + 1
                Timber.e("searchSource: failed after 2 attempts ${source.baseUrl} title=$title")
            }
        }
        return null
    }

    private suspend fun fetchChaptersWithRetry(source: SourceInterface.Catalog, url: String): List<ChapterResult>? {
        for (attempt in 1..2) {
            try {
                val chapters = ChapterFetcher.fetchChapters(source, url)
                if (chapters.isNotEmpty()) {
                    sourceFailures.remove(source.baseUrl)
                    return chapters
                }
            } catch (e: Exception) {
                Timber.e(e, "fetchChaptersWithRetry: attempt $attempt for ${source.baseUrl} $url")
            }
            if (attempt < 2) delay(1000L)
        }
        sourceFailures[source.baseUrl] = (sourceFailures[source.baseUrl] ?: 0) + 1
        Timber.e("fetchChaptersWithRetry: failed after 2 attempts ${source.baseUrl} $url")
        return null
    }

    fun skipBook(index: Int) {
        val books = _uiState.value.migratingBooks.toMutableList()
        if (index in books.indices) {
            books[index] = books[index].copy(isSkipped = true, isDone = true)
            _uiState.update { it.copy(migratingBooks = books) }
        }
    }

    fun setResultForBook(bookIndex: Int, resultIndex: Int) {
        val books = _uiState.value.migratingBooks.toMutableList()
        if (bookIndex in books.indices && resultIndex in books[bookIndex].results.indices) {
            val mb = books[bookIndex]
            books[bookIndex] = mb.copy(
                result = mb.results[resultIndex],
                selectedIndex = resultIndex,
                isSkipped = false,
                isDone = false,
                error = null,
            )
            _uiState.update { it.copy(migratingBooks = books) }
        }
    }

    fun migrateAll(options: MigrationOptions = MigrationOptions()) {
        val current = _uiState.value
        val toMigrate = current.migratingBooks.filter { it.result != null && !it.isSkipped && !it.isDone }
        Timber.e("migrateAll: toMigrate=${toMigrate.size}")
        if (toMigrate.isEmpty()) return

        _uiState.update {
            it.copy(
                isMigrating = true,
                migrateProgress = 0,
                migrateTotal = toMigrate.size,
                successCount = 0,
                skipCount = 0,
                failCount = 0,
            )
        }

        migrateJob?.cancel()
        migrateJob = viewModelScope.launch {
            var success = 0
            var fail = 0
            val migratedBooks = _uiState.value.migratingBooks.toMutableList()

            for ((index, mb) in toMigrate.withIndex()) {
                ensureActive()
                val result = mb.result ?: continue
                Timber.e("migrateAll: migrating book=${mb.book.title} -> ${result.book.title}")
                var bookError: String? = null
                var isBookSuccess = false

                try {
                    val oldChapters = withContext(Dispatchers.IO) {
                        bookChapters.chapters(mb.book.url).map { ChapterResult(title = it.title, url = it.url) }
                    }
                    val matchResult = withContext(Dispatchers.Default) {
                        chaptersMatcher.match(oldChapters, result.chapters)
                    }
                    Timber.e("migrateAll: matched ${matchResult.matched.size}/${result.chapters.size}")
                    val migrateResult = migrationRepository.migrate(
                        oldBookUrl = mb.book.url,
                        newBookUrl = result.book.url,
                        newChapters = result.chapters,
                        matchedChapters = matchResult.matched,
                        newBookTitle = result.book.title,
                        options = options,
                    )
                    Timber.e("migrateAll: done success=${migrateResult.success} error=${migrateResult.error}")
                    isBookSuccess = migrateResult.success
                    bookError = migrateResult.error
                } catch (e: Exception) {
                    Timber.e(e, "migrateAll: exception for ${mb.book.title}")
                    bookError = e.message ?: application.getString(R.string.migration_unknown_error)
                }

                if (isBookSuccess) success++ else fail++

                val bookIndex = migratedBooks.indexOfFirst { it.book.url == mb.book.url }
                if (bookIndex >= 0) {
                    migratedBooks[bookIndex] = migratedBooks[bookIndex].copy(
                        isDone = true,
                        error = bookError,
                    )
                }

                _uiState.update {
                    it.copy(
                        migratingBooks = migratedBooks.toList(),
                        migrateProgress = index + 1,
                        successCount = success,
                        failCount = fail,
                    )
                }
            }

            _uiState.update {
                it.copy(
                    migratingBooks = migratedBooks.toList(),
                    isMigrating = false,
                    isComplete = true,
                    skipCount = _uiState.value.migratingBooks.size - toMigrate.size,
                )
            }
        }
    }

    fun reset() {
        searchJob?.cancel()
        migrateJob?.cancel()
        sourceFailures.clear()
        _uiState.value = MassMigrationUiState()
    }
}
