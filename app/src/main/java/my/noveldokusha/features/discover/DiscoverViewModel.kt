package my.noveldokusha.features.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.DAOs.ReadingHistoryDao
import my.noveldokusha.feature.local_database.BookWithContext
import my.noveldokusha.feature.local_database.tables.ReadingHistory
import my.noveldokusha.scraper.CachedSource
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.domain.BookResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** A book source selectable in the homepage feed settings. */
data class FeedSource(
    val baseUrl: String,
    val name: String,
    val language: String,
)

data class DiscoverUiState(
    /**
     * All homepage category rows in display order (visible feeds only).
     * Each row carries its chosen source and that source's catalog rail
     * (rail == null when no source is chosen for the category yet).
     */
    val sections: List<FeedSectionState> = HomeFeed.entries.map { FeedSectionState(it) },
    val installedSources: List<FeedSource> = emptyList(),
    val content: DiscoverContent? = null,
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val appPreferences: AppPreferences,
    private val scraper: Scraper,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Tick flow so the UI can force a re-read of the Lua source set. */
    private val sourcesTickFlow = MutableStateFlow(0)

    /** Catalog rails per category label (only categories with a chosen source). */
    private val railsFlow = MutableStateFlow<Map<String, CatalogRailState>>(emptyMap())

    private var catalogFetchJob: Job? = null

    /** Session cache so re-opening the homepage doesn't refetch the same catalogs. */
    private val catalogCache = ConcurrentHashMap<String, List<DiscoverBook>>()

    private val sourceNamesCache = ConcurrentHashMap<String, String>()

    // — preferences —

    private val feedSourceMapFlow = appPreferences.HOME_FEED_SOURCE_MAP.flow()

    private val visibleFeedsFlow = appPreferences.HOME_FEEDS_VISIBLE.flow()

    private val booksFlow = combine(
        libraryDao.getBooksInLibraryWithContextFlow(),
        readingHistoryDao.getAllFlow(),
    ) { library, history -> merge(library, history) }

    /** Live theme/dark-mode state for the Discover appearance sheet. */
    val theme: StateFlow<AppTheme> = appPreferences.APP_THEME.flow()
        .map { raw ->
            runCatching { AppTheme.valueOf(raw) }.getOrDefault(AppTheme.PAPER)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.PAPER)

    val darkMode: StateFlow<DarkMode> = appPreferences.THEME_DARK_MODE.flow()
        .map { raw ->
            runCatching { DarkMode.valueOf(raw) }.getOrDefault(DarkMode.SYSTEM)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DarkMode.SYSTEM)

    fun setTheme(theme: AppTheme) {
        appPreferences.APP_THEME.value = theme.name
    }

    fun setDarkMode(mode: DarkMode) {
        appPreferences.THEME_DARK_MODE.value = mode.name
    }

    // — homepage sections: every visible category, one source each —

    private val sectionsFlow = combine(
        feedSourceMapFlow,
        visibleFeedsFlow,
        railsFlow,
    ) { sourceMap, visibleRaw, rails ->
        val visible = FeedSourcesPolicy.visibleFeeds(visibleRaw)
        visible.map { feed ->
            val baseUrl = FeedSourcesPolicy.resolveSource(sourceMap, feed)
            FeedSectionState(
                feed = feed,
                sourceBaseUrl = baseUrl,
                sourceName = baseUrl?.let { sourceNamesCache[it] },
                rail = baseUrl?.let { rails[feed.label] },
            )
        }
    }

    val uiState: StateFlow<DiscoverUiState> = combine(
        booksFlow,
        sectionsFlow,
        sourcesTickFlow,
    ) { books, sections, _ ->
        val content = DiscoverRails.derive(
            books = books,
            feed = sections.firstOrNull()?.feed ?: HomeFeed.FOR_YOU,
            feedSourceUrls = emptySet(),
            genreChip = DiscoverRails.FIXED_GENRES.first(),
            now = System.currentTimeMillis(),
            isCompatible = ::isCompatible,
            ratingValue = ::ratingValue,
        )
        DiscoverUiState(
            sections = sections,
            installedSources = bookSources(),
            content = content,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    init {
        // Fetch catalogs whenever the per-category source choices, section
        // visibility, or the installed source set change. Cached rails skip
        // the network; fetches run concurrently (4 at a time).
        viewModelScope.launch {
            combine(feedSourceMapFlow, visibleFeedsFlow, sourcesTickFlow) { map, visible, _ ->
                Triple(map, FeedSourcesPolicy.visibleFeeds(visible), Unit)
            }.collect { (sourceMap, visible, _) ->
                startCatalogFetch(sourceMap, visible)
            }
        }
    }

    private fun startCatalogFetch(sourceMap: Map<String, String>, visible: List<HomeFeed>) {
        catalogFetchJob?.cancel()
        catalogFetchJob = viewModelScope.launch {
            // Category -> chosen source (visible categories only).
            val targets = visible.mapNotNull { feed ->
                FeedSourcesPolicy.resolveSource(sourceMap, feed)?.let { feed to it }
            }
            // Drop rails for categories that lost their source or visibility.
            val keepLabels = targets.map { it.first.label }.toSet()
            railsFlow.value = railsFlow.value.filterKeys { it in keepLabels }
            if (targets.isEmpty()) return@launch

            // Wait (bounded) for real Lua sources — CachedSource stubs can't fetch data.
            val loaded = withTimeoutOrNull(20_000) {
                scraper.sourcesCatalogListFlow
                    .map { list -> list.filter { it !is CachedSource && !it.isLocalSource } }
                    .first { it.isNotEmpty() }
            }.orEmpty()
            val byBaseUrl = loaded.associateBy { it.baseUrl.trimEnd('/') }

            // Placeholder rails so every configured category shows its skeleton at once.
            railsFlow.value = targets.associate { (feed, baseUrl) ->
                val name = sourceNamesCache.getOrPut(baseUrl) {
                    byBaseUrl[baseUrl]?.resolveName(context) ?: baseUrl
                }
                feed.label to (railsFlow.value[feed.label]
                    ?: CatalogRailState(sourceBaseUrl = baseUrl, sourceName = name))
            }

            val semaphore = Semaphore(4)
            coroutineScope {
                targets.forEach { (feed, baseUrl) ->
                    launch {
                        semaphore.withPermit {
                            byBaseUrl[baseUrl]?.let { fetchCatalogInto(feed.label, baseUrl, it) }
                                ?: updateRail(feed.label) {
                                    it.copy(loading = false, error = "Source not loaded")
                                }
                        }
                    }
                }
            }
        }
    }

    /** Fetches page 0 of a source's catalog and updates its category rail. */
    private suspend fun fetchCatalogInto(
        feedLabel: String,
        baseUrl: String,
        source: SourceInterface.Catalog,
    ) {
        val cached = catalogCache[baseUrl]
        if (cached != null) {
            updateRail(feedLabel) { it.copy(loading = false, books = cached, error = null) }
            return
        }
        val response = runCatching { source.getCatalogList(0) }.fold(
            onSuccess = { it },
            onFailure = { e ->
                Response.Error(e.message ?: "Couldn't load catalog", e as? Exception ?: java.io.IOException(e))
            },
        )
        when (response) {
            is Response.Success -> {
                val books = response.data.list
                    .filter { it.url.isNotBlank() }
                    .map { result -> result.toDiscoverBook() }
                    .take(20)
                catalogCache[baseUrl] = books
                updateRail(feedLabel) { it.copy(loading = false, books = books, error = null) }
            }
            is Response.Error -> updateRail(feedLabel) {
                it.copy(loading = false, books = emptyList(), error = response.message)
            }
        }
    }

    private fun BookResult.toDiscoverBook(): DiscoverBook = DiscoverBook(
        title = title,
        url = url,
        coverUrl = coverImageUrl,
        rating = rating.orEmpty(),
        sourceName = null, // the row header already names the source
    )

    private fun updateRail(feedLabel: String, transform: (CatalogRailState) -> CatalogRailState) {
        val current = railsFlow.value
        val rail = current[feedLabel] ?: return
        railsFlow.value = current + (feedLabel to transform(rail))
    }

    /** Retry one failed catalog rail ("Retry" action on its error row). */
    fun retryRail(sourceBaseUrl: String) {
        val entry = railsFlow.value.entries.find { it.value.sourceBaseUrl == sourceBaseUrl } ?: return
        if (entry.value.loading) return
        catalogFetchJob = viewModelScope.launch {
            updateRail(entry.key) { it.copy(loading = true, error = null) }
            val source = scraper.loadedSourcesList
                .filterIsInstance<SourceInterface.Catalog>()
                .find { it.baseUrl.trimEnd('/') == sourceBaseUrl }
            if (source == null) {
                updateRail(entry.key) { it.copy(loading = false, error = "Source not loaded") }
            } else {
                fetchCatalogInto(entry.key, sourceBaseUrl, source)
            }
        }
    }

    // — settings actions —

    /** Sets/clears the single source for one homepage category (null clears). */
    fun setFeedSource(feed: HomeFeed, sourceBaseUrl: String?) {
        appPreferences.HOME_FEED_SOURCE_MAP.value = FeedSourcesPolicy.withSource(
            appPreferences.HOME_FEED_SOURCE_MAP.value, feed, sourceBaseUrl,
        )
    }

    /** Shows/hides a homepage category row (never lets the set become empty). */
    fun setVisibleFeed(feed: HomeFeed, shown: Boolean) {
        appPreferences.HOME_FEEDS_VISIBLE.value =
            FeedSourcesPolicy.withVisibleFeed(appPreferences.HOME_FEEDS_VISIBLE.value, feed, shown)
    }

    fun refreshSources() {
        catalogCache.clear()
        sourcesTickFlow.value = sourcesTickFlow.value + 1
    }

    // — helpers —

    private fun bookSources(): List<FeedSource> =
        scraper.sourcesList
            .filterIsInstance<SourceInterface.Catalog>()
            .filter { !it.isLocalSource }
            .map { source ->
                FeedSource(
                    baseUrl = source.baseUrl.trimEnd('/'),
                    name = sourceNamesCache.getOrPut(source.baseUrl) {
                        source.resolveName(context)
                    },
                    language = source.language?.iso639_1.orEmpty(),
                )
            }
            .sortedBy { it.name.lowercase() }

    private fun isCompatible(bookUrl: String, sourceBaseUrl: String): Boolean {
        if (bookUrl.isLocalUri) return false
        val source = scraper.getCompatibleSource(bookUrl) ?: return false
        return source.baseUrl.trimEnd('/') == sourceBaseUrl.trimEnd('/')
    }

    private fun ratingValue(raw: String): Float? =
        my.noveldokusha.coreui.components.parseBookRating(raw)?.score

    /**
     * Library books joined with history (progress + recency), plus
     * history-only books (read but never saved). Library rows win on clash.
     */
    private fun merge(
        library: List<BookWithContext>,
        history: List<ReadingHistory>,
    ): List<DiscoverBook> {
        val historyByUrl = history.associateBy { it.bookUrl }
        val merged = LinkedHashMap<String, DiscoverBook>(library.size + history.size)

        library.forEach { row ->
            val book = row.book
            val hist = historyByUrl[book.url]
            merged[book.url] = DiscoverBook(
                title = book.title,
                url = book.url,
                coverUrl = book.coverImageUrl,
                genres = book.genres.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                rating = book.rating,
                status = book.status,
                completed = book.completed,
                chaptersCount = row.chaptersCount,
                chaptersReadCount = row.chaptersReadCount,
                inLibrary = true,
                lastReadEpoch = maxOf(
                    book.lastReadEpochTimeMilli,
                    hist?.lastReadEpochTimeMilli ?: 0L
                ),
                addedToLibraryEpoch = book.addedToLibraryEpochTimeMilli,
                lastUpdateEpoch = book.lastUpdateEpochTimeMilli,
                sourceName = resolveSourceName(book.url),
            )
        }

        history.forEach { hist ->
            if (merged.containsKey(hist.bookUrl)) return@forEach
            merged[hist.bookUrl] = DiscoverBook(
                title = hist.bookTitle,
                url = hist.bookUrl,
                coverUrl = hist.bookCoverUrl,
                chaptersCount = hist.totalChapters,
                chaptersReadCount = hist.readChapters,
                inLibrary = false,
                lastReadEpoch = hist.lastReadEpochTimeMilli,
                sourceName = resolveSourceName(hist.bookUrl),
            )
        }
        return merged.values.toList()
    }

    private fun resolveSourceName(url: String): String? {
        if (url.isLocalUri) return "Local"
        val id = scraper.getSourceId(url) ?: return null
        val source = scraper.sourcesList.find { it.id == id } ?: return null
        return sourceNamesCache.getOrPut(url) { source.resolveName(context) }
    }
}
