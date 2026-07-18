package my.noveldokusha.libraryexplorer

import android.content.Context
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.BaseViewModel
import my.noveldokusha.data.AppRepository
import my.noveldokusha.core.Toasty
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.LibrarySortOption
import my.noveldokusha.core.appPreferences.SortConfig
import my.noveldokusha.core.appPreferences.SortDirection
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.core.domain.LibraryCategory
import my.noveldokusha.core.utils.GenreUtils
import my.noveldokusha.core.utils.toState
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.interactor.WorkersInteractions
import my.noveldokusha.scraper.Scraper
import javax.inject.Inject

@HiltViewModel
internal class LibraryPageViewModel @Inject constructor(
    private val appRepository: AppRepository,
    private val preferences: AppPreferences,
    private val toasty: Toasty,
    private val workersInteractions: WorkersInteractions,
    private val libraryDao: LibraryDao,
    private val scraper: Scraper,
    @ApplicationContext private val context: Context,
) : BaseViewModel() {
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

    // Все доступные жанры в библиотеке — парсим из поля Book.genres
    val availableGenres = libraryDao.getAllLibraryGenresRawFlow()
        .map { rawList ->
            rawList.flatMap { GenreUtils.parse(it) }
                .distinct()
                .sorted()
        }
        .toState(viewModelScope, emptyList())

    // Полная карта жанр → Set<bookUrl> — парсим из Book.genres
    private val genreToBookUrls = appRepository.libraryBooks
        .getBooksInLibraryWithContextFlow
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
        .toState(viewModelScope, emptyMap())

    // Доступные имена плагинов в библиотеке — определяем динамически из списка книг
    private val availableSourcesState = appRepository.libraryBooks
        .getBooksInLibraryWithContextFlow
        .map { list ->
            list.mapNotNull { book ->
                if (book.book.url.isLocalUri) "Local"
                else scraper.getCompatibleSource(book.book.url)?.resolveName(context)
            }.distinct().sorted()
        }
        .toState(viewModelScope, emptyList<String>())

    val availableSources = availableSourcesState

    // Shared pre-category-filter flow — all filters EXCEPT category selection
    private val preCategoryFilterFlow = appRepository.libraryBooks
        .getBooksInLibraryWithContextFlow
        .combine(preferences.LIBRARY_FILTER_READ.flow()) { list, filterRead ->
            when (filterRead) {
                TernaryState.Active -> list.filter { it.chaptersCount == it.chaptersReadCount }
                TernaryState.Inverse -> list.filter { it.chaptersCount != it.chaptersReadCount }
                TernaryState.Inactive -> list
            }
        }.combine(_searchQueryFlow) { list, query ->
            if (query.isBlank()) list
            else {
                val q = query.trim()
                val cache = genreToBookUrls.value
                list.filter { book ->
                    val sourceName = if (book.book.url.isLocalUri) "Local"
                    else scraper.getCompatibleSource(book.book.url)?.resolveName(context) ?: ""
                    book.book.title.contains(q, ignoreCase = true) ||
                            sourceName.contains(q, ignoreCase = true) ||
                            cache.any { (genre, urls) ->
                                book.book.url in urls && genre.contains(q, ignoreCase = true)
                            }
                }
            }
        }.combine(_selectedGenres) { list, selectedGenres ->
            if (selectedGenres.isEmpty()) list
            else {
                val cache = genreToBookUrls.value
                list.filter { book ->
                    selectedGenres.all { genre ->
                        book.book.url in (cache[genre] ?: emptySet())
                    }
                }
            }
        }.combine(_selectedSources) { list, selectedSources ->
            if (selectedSources.isEmpty()) list
            else {
                list.filter { book ->
                    val sourceName = if (book.book.url.isLocalUri) "Local"
                    else scraper.getCompatibleSource(book.book.url)?.resolveName(context) ?: ""
                    sourceName in selectedSources
                }
            }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    // Base flow with category filter — used for the actual filtered list
    private val baseLibraryFlow = preCategoryFilterFlow
        .combine(_selectedCategories) { list, categories ->
            if (categories.isEmpty()) list // All
            else {
                list.filter { book ->
                    categories.any { cat ->
                        when (cat) {
                            "" -> book.book.category == "" // Reading
                            "Completed" -> book.book.category == "Completed"
                            else -> book.book.category == cat // Custom
                        }
                    }
                }
            }
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    // Single filtered list instead of listReading/listCompleted
    val filteredList = baseLibraryFlow
        .combine(preferences.LIBRARY_SORT_CONFIG.flow()) { list, sortConfig ->
            when (sortConfig.direction) {
                SortDirection.ASC -> when (sortConfig.option) {
                    LibrarySortOption.TITLE -> list.sortedBy { it.book.title.lowercase() }
                    LibrarySortOption.UNREAD_CHAPTERS -> list.sortedBy { it.chaptersCount - it.chaptersReadCount }
                    LibrarySortOption.LAST_READ -> list.sortedBy { it.book.lastReadEpochTimeMilli }
                    LibrarySortOption.LAST_UPDATE -> list.sortedBy { it.book.lastUpdateEpochTimeMilli }
                    LibrarySortOption.ADDED -> list.sortedBy { it.book.addedToLibraryEpochTimeMilli }
                }
                SortDirection.DESC -> when (sortConfig.option) {
                    LibrarySortOption.TITLE -> list.sortedByDescending { it.book.title.lowercase() }
                    LibrarySortOption.UNREAD_CHAPTERS -> list.sortedByDescending { it.chaptersCount - it.chaptersReadCount }
                    LibrarySortOption.LAST_READ -> list.sortedByDescending { it.book.lastReadEpochTimeMilli }
                    LibrarySortOption.LAST_UPDATE -> list.sortedByDescending { it.book.lastUpdateEpochTimeMilli }
                    LibrarySortOption.ADDED -> list.sortedByDescending { it.book.addedToLibraryEpochTimeMilli }
                }
            }
        }
        .toState(viewModelScope, listOf())

    // Count of items in each category for the chips (category → count)
    // Built from preCategoryFilterFlow so counts are unaffected by which category is selected
    val categoryCounts = preCategoryFilterFlow
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

    fun resetAllFilters() {
        _selectedGenres.value = emptySet()
        _selectedSources.value = emptySet()
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

    fun getSourceName(url: String): String {
        if (url.isLocalUri) return "Local"
        return scraper.getCompatibleSource(url)?.resolveName(context) ?: "Unknown Source"
    }
}