package my.noveldokusha.libraryexplorer

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import my.noveldokusha.data.AppRepository
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.LibrarySortOption
import my.noveldokusha.core.appPreferences.SortConfig
import my.noveldokusha.core.appPreferences.SortDirection
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.core.domain.LibraryCategory
import my.noveldokusha.core.utils.GenreUtils
import my.noveldokusha.core.utils.toState
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.DAOs.BookTranslationDao
import my.noveldokusha.feature.local_database.BookWithContext
import my.noveldokusha.interactor.WorkersInteractions
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

// Фильтр библиотеки по типу контента. «Новелла» = всё, что не помечено как "manga"
// (включая пустую метку "" — дефолт для источников без content_type).
enum class ContentTypeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    MANGA(R.string.content_type_manga),
    NOVEL(R.string.content_type_novel),
}

// Чистая функция фильтрации — вынесена отдельно для юнит-тестирования.
fun List<BookWithContext>.filterByType(type: ContentTypeFilter): List<BookWithContext> =
    when (type) {
        ContentTypeFilter.ALL -> this
        ContentTypeFilter.MANGA -> filter { it.book.contentType == "manga" }
        ContentTypeFilter.NOVEL -> filter { it.book.contentType != "manga" }
    }

// ponytail: single data class for all filter params — replaces 5 separate MutableStateFlows
// in the combine chain. Easier to reason about, fewer intermediate list allocations.
private data class FilterPrefs(
    val read: TernaryState = TernaryState.Inactive,
    val query: String = "",
    val genres: Set<String> = emptySet(),
    val sources: Set<String> = emptySet(),
    val contentType: ContentTypeFilter = ContentTypeFilter.ALL,
    val categories: Set<String> = emptySet(),
)

// ponytail: Sequence-based filters avoid intermediate list allocations.
// For ~1000 books × 5 filters = 5 fewer List<BookWithContext> copies.
private fun Sequence<BookWithContext>.applyReadFilter(filter: TernaryState) = when (filter) {
    TernaryState.Active -> filter { it.chaptersCount == it.chaptersReadCount }
    TernaryState.Inverse -> filter { it.chaptersCount != it.chaptersReadCount }
    TernaryState.Inactive -> this
}

private fun Sequence<BookWithContext>.applySearchFilter(
    query: String,
    genreMap: Map<String, Set<String>>,
    sourceResolver: (String) -> String?,
    translations: Map<String, String> = emptyMap()
) = if (query.isBlank()) this else {
    val q = query.trim()
    filter { book ->
        val sourceName = sourceResolver(book.book.url) ?: ""
        val translatedTitle = translations[book.book.url].orEmpty()
        book.book.title.contains(q, ignoreCase = true) ||
            translatedTitle.contains(q, ignoreCase = true) ||
            sourceName.contains(q, ignoreCase = true) ||
            genreMap.any { (genre, urls) ->
                book.book.url in urls && genre.contains(q, ignoreCase = true)
            }
    }
}

private fun Sequence<BookWithContext>.applyGenreFilter(
    selectedGenres: Set<String>,
    genreMap: Map<String, Set<String>>
) = if (selectedGenres.isEmpty()) this else filter { book ->
    selectedGenres.all { genre ->
        book.book.url in (genreMap[genre] ?: emptySet())
    }
}

private fun Sequence<BookWithContext>.applySourceFilter(
    selectedSources: Set<String>,
    sourceResolver: (String) -> String?
) = if (selectedSources.isEmpty()) this else filter { book ->
    val sourceName = sourceResolver(book.book.url) ?: ""
    sourceName in selectedSources
}

private fun Sequence<BookWithContext>.applyContentTypeFilter(
    contentType: ContentTypeFilter
) = when (contentType) {
    ContentTypeFilter.ALL -> this
    ContentTypeFilter.MANGA -> filter { it.book.contentType == "manga" }
    ContentTypeFilter.NOVEL -> filter { it.book.contentType != "manga" }
}

private fun Sequence<BookWithContext>.applyCategoryFilter(
    categories: Set<String>
) = if (categories.isEmpty()) this else filter { book ->
    categories.any { cat ->
        when (cat) {
            "" -> book.book.category == ""
            "Completed" -> book.book.category == "Completed"
            else -> book.book.category == cat
        }
    }
}

@HiltViewModel
internal class LibraryPageViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val preferences: AppPreferences,
    private val toasty: Toasty,
    private val workersInteractions: WorkersInteractions,
    private val libraryDao: LibraryDao,
    private val scraper: Scraper,
    private val translationSettingsResolver: TranslationSettingsResolver,
    private val bookTranslationDao: BookTranslationDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    var isLibraryLoaded by mutableStateOf(false)
        private set

    private val sourceNameCache = ConcurrentHashMap<String, String>()

    // Room-запрос стартует сразу при создании ViewModel (Eagerly), чтобы к моменту
    // появления UI данные уже были в кэше. WhileSubscribed здесь ломает порядок:
    // toState(listOf()) выдаёт пустой список → isLibraryLoaded=true → "нет книг"
    // → потом Room отвечает → книги появляются.
    private val sharedBooksFlow = appRepository.libraryBooks
        .getBooksInLibraryWithContextFlow
        // ponytail: TTS обновляет Book.lastReadChapter каждые ~5-10с, но библиотека
        // не использует это поле (сортировка LAST_READ → lastReadEpochTimeMilli,
        // фильтр категорий → category). Room InvalidationTracker — table-level,
        // поэтому любая запись в Book/Chapter инвалидирует Flow → каскад 8+ downstream.
        // Нормализуем list: обнуляем lastReadChapter и сравниваем через data class equals().
        .distinctUntilChanged { old, new ->
            if (old.size != new.size) return@distinctUntilChanged false
            old.map { it.copy(book = it.book.copy(lastReadChapter = null)) } ==
                new.map { it.copy(book = it.book.copy(lastReadChapter = null)) }
        }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    var searchQuery by mutableStateOf("")
        private set

    private val _searchQueryFlow = MutableStateFlow("")
    val searchQueryFlow = _searchQueryFlow.asStateFlow()

    // Selected categories: empty = All, otherwise shows books matching ANY of selected categories (OR logic)
    // Values: "" = Reading, "Completed" = Completed, custom = custom category name
    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategories = _selectedCategories.asStateFlow()

    // Жанры-фильтры — пустой Set означает "все жанры"
    private val _selectedGenres = MutableStateFlow<Set<String>>(emptySet())
    val selectedGenres = _selectedGenres.asStateFlow()

    // Фильтр по именам плагинов (источников) — пустой Set = все
    private val _selectedSources = MutableStateFlow<Set<String>>(emptySet())
    val selectedSources = _selectedSources.asStateFlow()

    // Фильтр по типу контента (Все/Манга/Новелла) — состояние VM, как жанры/источники
    private val _selectedContentType = MutableStateFlow(ContentTypeFilter.ALL)
    val selectedContentType = _selectedContentType.asStateFlow()

    // Все доступные жанры в библиотеке — парсим из поля Book.genres
    val availableGenres = libraryDao.getAllLibraryGenresRawFlow()
        .map { rawList ->
            rawList.flatMap { GenreUtils.parse(it) }
                .distinct()
                .sorted()
        }
        .toState(viewModelScope, emptyList())

    // Полная карта жанр → Set<bookUrl> — парсим из Book.genres
    private val genreToBookUrls = sharedBooksFlow
        .map { list ->
            val result = mutableMapOf<String, MutableSet<String>>()
            list.forEach { book ->
                val genres = GenreUtils.parse(book.book.genres)
                genres.forEach { genre ->
                    result.getOrPut(genre) { mutableSetOf() }.add(book.book.url)
                }
            }
            result
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ponytail: same bulk-translation logic as translatedTitles, but branches from
    // sharedBooksFlow (all books) instead of categoryFilteredBooks.
    val allBookTranslations: StateFlow<Map<String, String>> = sharedBooksFlow
        .combine(translationSettingsResolver.translationSettingsChangeSignal()) { books, _ ->
            translationSourceIdCache.clear()
            books
        }
        .flatMapLatest { books ->
            if (books.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            val translationParams = mutableMapOf<String, Pair<String, String>>()
            val noTranslationUrls = mutableListOf<String>()

            for (book in books) {
                val url = book.book.url
                val sourceId = translationSourceIdCache.getOrPut(url) {
                    translationSettingsResolver.resolveSourceId(url)
                }
                if (sourceId != null && preferences.TRANSLATION_PLUGIN_HIDE_LIBRARY.value[sourceId] == true) {
                    noTranslationUrls.add(url)
                    continue
                }
                val targetLang = translationSettingsResolver.translationTargetForBook(url, sourceId)
                val enabled = translationSettingsResolver.translationEnabledForBook(url, sourceId)
                val scope = translationSettingsResolver.translationScopeForBook(url, sourceId)
                val sourceLang = translationSettingsResolver.translationPairForBook(url, sourceId).source

                if (targetLang.isBlank() || !enabled || scope != AppPreferences.TRANSLATION_SCOPE_FULL) {
                    noTranslationUrls.add(url)
                } else {
                    translationParams[url] = targetLang to sourceLang
                }
            }

            if (translationParams.isEmpty()) return@flatMapLatest flowOf(
                books.associate { it.book.url to "" }
            )

            val byTargetLang = translationParams.entries.groupBy({ it.value.first }, { it.key })

            combine(byTargetLang.map { (targetLang, urls) ->
                bookTranslationDao.getTranslatedBooksBulkFlow(urls, targetLang)
                    .map { rows -> targetLang to rows }
            }) { langResults ->
                val langRowsMap = langResults.toMap()
                val result = mutableMapOf<String, String>()

                for (url in noTranslationUrls) result[url] = ""
                for (book in books) {
                    val url = book.book.url
                    if (url in result) continue
                    val (targetLang, sourceLang) = translationParams[url] ?: continue
                    val rows = langRowsMap[targetLang] ?: continue
                    val row = rows.firstOrNull { it.bookUrl == url && it.sourceLang == sourceLang }
                    result[url] = row?.titleTranslation ?: ""
                }
                result
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Доступные имена плагинов в библиотеке — определяем динамически из списка книг
    // ponytail: toState() returns State<T> for Compose `by` delegate — stateIn() doesn't support this.
    private val availableSourcesState = sharedBooksFlow
        .map { list ->
            list.mapNotNull { book ->
                resolveSourceName(book.book.url)
            }.distinct().sorted()
        }
        .toState(viewModelScope, emptyList())

    val availableSources = availableSourcesState

    val luaSources: StateFlow<Set<SourceInterface>> get() = scraper.luaSources

    // ponytail: single combine for all filter prefs — replaces 5 separate .combine() calls.
    // Data class makes the filter state explicit and composable.
    private val filterPrefsFlow = combine(
        preferences.LIBRARY_FILTER_READ.flow(),
        _searchQueryFlow,
        _selectedGenres,
        _selectedSources,
        _selectedContentType
    ) { read, query, genres, sources, contentType ->
        FilterPrefs(read = read, query = query, genres = genres, sources = sources, contentType = contentType)
    }

    // ponytail: pre-category filtered books — all filters except category in a single pass.
    // Source: sharedBooksFlow (single DB query). Sort moves to filteredList.
    private val preCategoryFilteredBooks = combine(
        sharedBooksFlow,
        filterPrefsFlow,
        allBookTranslations
    ) { books, prefs, translations ->
        val genreCache = genreToBookUrls.value
        books.asSequence()
            .applyReadFilter(prefs.read)
            .applySearchFilter(prefs.query, genreCache, ::resolveSourceName, translations)
            .applyGenreFilter(prefs.genres, genreCache)
            .applySourceFilter(prefs.sources, ::resolveSourceName)
            .applyContentTypeFilter(prefs.contentType)
            .toList()
    }

    // Category-filtered books — used by category chips, final list, and translations.
    // shareIn(WhileSubscribed) — only hot when UI collects.
    private val categoryFilteredBooks = combine(
        preCategoryFilteredBooks,
        _selectedCategories
    ) { books, categories ->
        books.asSequence()
            .applyCategoryFilter(categories)
            .toList()
    }
    .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    // bookUrl → переведённое название (read-only отображение из БД).
    // ponytail: bulk query вместо 300+ per-book Room Flow подписок.
    // 1 Room observer вместо 300 — reduce allocation pressure at startup.
    // ponytail: safe on Main dispatcher — all collectors run on viewModelScope
    private val translationSourceIdCache = HashMap<String, String?>()

    val translatedTitles: StateFlow<Map<String, String>> = categoryFilteredBooks
        .combine(translationSettingsResolver.translationSettingsChangeSignal()) { books, _ ->
            translationSourceIdCache.clear()
            books
        }
        .flatMapLatest { books ->
            if (books.isEmpty()) return@flatMapLatest flowOf(emptyMap())

            // Pre-compute: which books need translation, grouped by targetLang.
            val translationParams = mutableMapOf<String, Pair<String, String>>() // url → (targetLang, sourceLang)
            val noTranslationUrls = mutableListOf<String>()

            for (book in books) {
                val url = book.book.url
                val sourceId = translationSourceIdCache.getOrPut(url) {
                    translationSettingsResolver.resolveSourceId(url)
                }
                if (sourceId != null && preferences.TRANSLATION_PLUGIN_HIDE_LIBRARY.value[sourceId] == true) {
                    noTranslationUrls.add(url)
                    continue
                }
                val targetLang = translationSettingsResolver.translationTargetForBook(url, sourceId)
                val enabled = translationSettingsResolver.translationEnabledForBook(url, sourceId)
                val scope = translationSettingsResolver.translationScopeForBook(url, sourceId)
                val sourceLang = translationSettingsResolver.translationPairForBook(url, sourceId).source

                if (targetLang.isBlank() || !enabled || scope != AppPreferences.TRANSLATION_SCOPE_FULL) {
                    noTranslationUrls.add(url)
                } else {
                    translationParams[url] = targetLang to sourceLang
                }
            }

            if (translationParams.isEmpty()) return@flatMapLatest flowOf(
                books.associate { it.book.url to "" }
            )

            // One Room Flow per unique targetLang — typically 1-2 observers vs 300+.
            val byTargetLang = translationParams.entries.groupBy({ it.value.first }, { it.key })

            combine(byTargetLang.map { (targetLang, urls) ->
                bookTranslationDao.getTranslatedBooksBulkFlow(urls, targetLang)
                    .map { rows -> targetLang to rows }
            }) { langResults ->
                val langRowsMap = langResults.toMap()
                val result = mutableMapOf<String, String>()

                for (url in noTranslationUrls) result[url] = ""
                for (book in books) {
                    val url = book.book.url
                    if (url in result) continue
                    val (targetLang, sourceLang) = translationParams[url] ?: continue
                    val rows = langRowsMap[targetLang] ?: continue
                    val row = rows.firstOrNull { it.bookUrl == url && it.sourceLang == sourceLang }
                    result[url] = row?.titleTranslation ?: ""
                }
                result
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    // ponytail: Kotlin sort on already-filtered list — eliminates duplicate SQL GROUP BY
    // query. For ~300 books, O(n log n) sort is negligible vs DB roundtrip.
    val filteredList = combine(
        categoryFilteredBooks,
        preferences.LIBRARY_SORT_CONFIG.flow()
    ) { books, sortConfig ->
        when (sortConfig.direction) {
            SortDirection.ASC -> when (sortConfig.option) {
                LibrarySortOption.TITLE -> books.sortedBy { it.book.title.lowercase() }
                LibrarySortOption.UNREAD_CHAPTERS -> books.sortedBy { it.chaptersCount - it.chaptersReadCount }
                LibrarySortOption.LAST_READ -> books.sortedBy { it.book.lastReadEpochTimeMilli }
                LibrarySortOption.LAST_UPDATE -> books.sortedBy { it.book.lastUpdateEpochTimeMilli }
                LibrarySortOption.ADDED -> books.sortedBy { it.book.addedToLibraryEpochTimeMilli }
            }
            SortDirection.DESC -> when (sortConfig.option) {
                LibrarySortOption.TITLE -> books.sortedByDescending { it.book.title.lowercase() }
                LibrarySortOption.UNREAD_CHAPTERS -> books.sortedByDescending { it.chaptersCount - it.chaptersReadCount }
                LibrarySortOption.LAST_READ -> books.sortedByDescending { it.book.lastReadEpochTimeMilli }
                LibrarySortOption.LAST_UPDATE -> books.sortedByDescending { it.book.lastUpdateEpochTimeMilli }
                LibrarySortOption.ADDED -> books.sortedByDescending { it.book.addedToLibraryEpochTimeMilli }
            }
        }
    }
        .onEach { isLibraryLoaded = true }
        .toState(viewModelScope, listOf())

    // Count of items in each category for the chips (category → count)
    // Built from preCategoryFilteredBooks so counts are unaffected by which category is selected
    val categoryCounts = preCategoryFilteredBooks
        .map { list ->
            list.groupBy { it.book.category ?: "" }
                .mapValues { it.value.size }
        }
        .toState(viewModelScope, emptyMap<String, Int>())

    init {
        // Восстанавливаем сохранённое состояние фильтров из SharedPreferences
        _selectedCategories.value = preferences.LIBRARY_SELECTED_CATEGORIES.value
        _selectedGenres.value = preferences.LIBRARY_SELECTED_GENRES.value
        _selectedSources.value = preferences.LIBRARY_SELECTED_SOURCES.value

        // Синхронизируем изменения фильтров с SharedPreferences
        viewModelScope.launch {
            _selectedCategories.collect { categories ->
                preferences.LIBRARY_SELECTED_CATEGORIES.value = categories
            }
        }
        viewModelScope.launch {
            _selectedGenres.collect { genres ->
                preferences.LIBRARY_SELECTED_GENRES.value = genres
            }
        }
        viewModelScope.launch {
            _selectedSources.collect { sources ->
                preferences.LIBRARY_SELECTED_SOURCES.value = sources
            }
        }

        // Sync the mutable state with the flow
        viewModelScope.launch {
            _searchQueryFlow.collect { newQuery ->
                searchQuery = newQuery
            }
        }

        // Listen for data restore (e.g. backup recovery) to refresh caches.
        // Room Flow handles DB updates automatically, but we need to clear the
        // source name cache so that restored/different plugins resolve anew.
        viewModelScope.launch {
            appRepository.eventDataRestored.collect {
                sourceNameCache.clear()
                translationSourceIdCache.clear()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        _searchQueryFlow.value = query
    }

    fun toggleCategory(category: String) {
        _selectedCategories.update { current ->
            if (category in current) current - category else current + category
        }
    }

    fun clearCategoryFilters() {
        _selectedCategories.value = emptySet()
    }

    fun toggleGenreFilter(genre: String) {
        _selectedGenres.update { current ->
            if (genre in current) current - genre else current + genre
        }
    }

    fun clearGenreFilters() {
        _selectedGenres.value = emptySet()
    }

    fun toggleSourceFilter(sourceName: String) {
        _selectedSources.update { current ->
            if (sourceName in current) current - sourceName else current + sourceName
        }
    }

    fun clearSourceFilters() {
        _selectedSources.value = emptySet()
    }

    fun setContentTypeFilter(filter: ContentTypeFilter) {
        _selectedContentType.value = filter
    }

    fun resetAllFilters() {
        _selectedGenres.value = emptySet()
        _selectedSources.value = emptySet()
        _selectedContentType.value = ContentTypeFilter.ALL
        updateSearchQuery("")
        // Сбрасываем read filter в Inactive
        if (preferences.LIBRARY_FILTER_READ.value != TernaryState.Inactive) {
            preferences.LIBRARY_FILTER_READ.value = TernaryState.Inactive
        }
    }

    // Observes WorkManager state: true while manual update is running
    val isUpdating by workersInteractions.isManualUpdateRunning()
        .toState(viewModelScope, initialValue = false)

    @Suppress("UNUSED_PARAMETER")
    fun onLibraryCategoryRefresh(libraryCategory: LibraryCategory) {
        toasty.show(R.string.updating_library_notice)
        workersInteractions.checkForLibraryUpdates(libraryCategory)
    }

    fun cancelLibraryUpdates() {
        workersInteractions.cancelLibraryUpdates()
        toasty.show(R.string.update_cancelled)
    }

    private fun resolveSourceName(url: String): String? {
        sourceNameCache[url]?.let { return it }
        val result = if (url.isLocalUri) "Local"
        else scraper.getSourceId(url)?.let { id ->
            scraper.sourcesList.find { it.id == id }?.resolveName(context)
        }
        if (result != null) sourceNameCache[url] = result
        return result
    }

    fun getSourceName(url: String): String = resolveSourceName(url) ?: "Unknown Source"
}
