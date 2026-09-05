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
import timber.log.Timber
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

    private val sharedBooksFlow = appRepository.libraryBooks
        .getBooksInLibraryWithContextFlow
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
        .toState(viewModelScope, emptyMap())

    // Доступные имена плагинов в библиотеке — определяем динамически из списка книг
    private val availableSourcesState = sharedBooksFlow
        .map { list ->
            list.mapNotNull { book ->
                resolveSourceName(book.book.url)
            }.distinct().sorted()
        }
        .toState(viewModelScope, emptyList<String>())

    val availableSources = availableSourcesState

    val luaSources: StateFlow<Set<SourceInterface>> get() = scraper.luaSources

    // Shared pre-category-filter flow — all filters EXCEPT category selection
    private val preCategoryFilterFlow = sharedBooksFlow
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
                    val sourceName = resolveSourceName(book.book.url) ?: ""
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
                    val sourceName = resolveSourceName(book.book.url) ?: ""
                    sourceName in selectedSources
                }
            }
        }.combine(_selectedContentType) { list, contentType ->
            list.filterByType(contentType)
        }.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

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
        }.shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    // bookUrl → переведённое название (read-only отображение из БД).
    // Подписка на DAO-поток идёт только для книг с активным плагинным
    // переводом (пустой targetLang = переводов нет, возвращаем пустую карту).
    // combine с translationSettingsChangeSignal() — триггер пересчёта
    // при смене ЛЮБОЙ настройки перевода (включая плагинные флои).
    val translatedTitles: StateFlow<Map<String, String>> = baseLibraryFlow
        .combine(translationSettingsResolver.translationSettingsChangeSignal()) { books, _ -> books }
        .flatMapLatest { books ->
            if (books.isEmpty()) flowOf(emptyMap())
            else combine(books.map { book ->
                val url = book.book.url
                val targetLang = translationSettingsResolver.translationTargetForBook(url)
                val enabled = translationSettingsResolver.translationEnabledForBook(url)
                val scope = translationSettingsResolver.translationScopeForBook(url)
                val provider = translationSettingsResolver.translationProviderForBook(url)
                // Желаемый source-язык книги — для отбора строки с нужным источником
                // (при смене source-языка старая строка со старым источником не должна
                // просачиваться, как это уже делает конвейер глав).
                val sourceLang = translationSettingsResolver.translationPairForBook(url).source
                // Диагностика: почему название новеллы в библиотеке переводится/нет.
                Timber.d(
                    "libTitle: url=%s enabled=%s target=%s scope=%s provider=%s",
                    url, enabled, targetLang, scope, provider
                )
                // Название новеллы в библиотеке переводим только для включённого
                // плагина с scope=FULL. Глобальный режим и плагин с scope=STANDARD
                // названия НЕ переводят — показываем оригинал (перевод из БД от
                // прошлого FULL/глобала не должен просачиваться).
                if (targetLang.isBlank() || !enabled || scope != AppPreferences.TRANSLATION_SCOPE_FULL) flowOf(url to "")
                else bookTranslationDao.getTranslatedBookFlow(url, targetLang)
                    .map { rows ->
                        val row = rows.firstOrNull { it.sourceLang == sourceLang }
                        url to (row?.titleTranslation ?: "")
                    }
            }) { results -> results.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
        .onEach { isLibraryLoaded = true }
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

        // Listen for data restore (e.g. backup recovery) to refresh caches.
        // Room Flow handles DB updates automatically, but we need to clear the
        // source name cache so that restored/different plugins resolve anew.
        viewModelScope.launch {
            appRepository.eventDataRestored.collect {
                sourceNameCache.clear()
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
