package my.noveldokusha.libraryexplorer

import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.LibrarySortOption
import my.noveldokusha.core.appPreferences.TernaryState
import my.noveldokusha.core.appPreferences.SortConfig
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.utils.asMutableStateOf
import androidx.lifecycle.ViewModel
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.coreui.states.text
import my.noveldokusha.coreui.states.title
import my.noveldokusha.data.AppRepository
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.feature.local_database.BookWithContext
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.interactor.LibraryUpdatesInteractions
import my.noveldokusha.strings.R
import javax.inject.Inject

@Immutable
internal data class LibraryUiState(
    val bookActionsSheetBook: Book? = null,
    val showAddByUrlDialog: Boolean = false,
    val isSelectionMode: Boolean = false,
    val isFixingBooks: Boolean = false,
    val fixProgress: Int = 0,
    val fixTotal: Int = 0,
    val gridColumns: Int = 3,
    val showBottomSheet: Boolean = false,
    val readFilter: TernaryState = TernaryState.Inactive,
    val sortConfig: SortConfig = SortConfig.DEFAULT,
    val customCategories: List<String> = emptyList(),
    val toolbarMode: ToolbarMode = ToolbarMode.MAIN,
    val showCategories: Boolean = false,
)

@HiltViewModel
internal class LibraryViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val appRepository: AppRepository,
    private val scraperRepository: ScraperRepository,
    @ApplicationContext private val context: Context,
    private val libraryUpdatesInteractions: LibraryUpdatesInteractions,
    private val notificationsCenter: NotificationsCenter,
    stateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _selectedBooks = mutableStateMapOf<String, Boolean>()
    val selectedBooks: Map<String, Boolean> = _selectedBooks

    private val _pendingRemoval = mutableStateMapOf<String, Boolean>()
    val pendingRemoval: Map<String, Boolean> = _pendingRemoval

    init {
        // Sync with preferences
        viewModelScope.launch {
            launch {
                appPreferences.BOOKS_GRID_COLUMNS.flow().collect { cols ->
                    _uiState.update { it.copy(gridColumns = cols) }
                }
            }
            launch {
                appPreferences.LIBRARY_FILTER_READ.flow().collect { filter ->
                    _uiState.update { it.copy(readFilter = filter) }
                }
            }
            launch {
                appPreferences.LIBRARY_SORT_CONFIG.flow().collect { config ->
                    _uiState.update { it.copy(sortConfig = config) }
                }
            }
            launch {
                appPreferences.LIBRARY_CUSTOM_CATEGORIES.flow().collect { cats ->
                    _uiState.update { it.copy(customCategories = cats) }
                }
            }
        }
        
        stateHandle.get<Boolean>("showBottomSheet")?.let { restored ->
            _uiState.update { it.copy(showBottomSheet = restored) }
        }
    }

    fun setToolbarMode(mode: ToolbarMode) {
        _uiState.update { it.copy(toolbarMode = mode) }
    }

    fun setBookActionsSheetBook(book: Book?) {
        _uiState.update { it.copy(bookActionsSheetBook = book) }
    }

    fun setShowAddByUrlDialog(show: Boolean) {
        _uiState.update { it.copy(showAddByUrlDialog = show) }
    }

    fun setGridColumns(columns: Int) {
        appPreferences.BOOKS_GRID_COLUMNS.value = columns.coerceIn(2, 6)
    }

    fun toggleSelectionMode() {
        _uiState.update {
            val nextMode = !it.isSelectionMode
            if (!nextMode) _selectedBooks.clear()
            it.copy(isSelectionMode = nextMode)
        }
    }

    fun toggleBookSelection(bookUrl: String) {
        if (_selectedBooks.containsKey(bookUrl)) {
            _selectedBooks.remove(bookUrl)
        } else {
            _selectedBooks[bookUrl] = true
        }
    }

    fun selectAllBooks(books: List<BookWithContext>) {
        _selectedBooks.clear()
        books.forEach { _selectedBooks[it.book.url] = true }
    }

    fun clearSelection() {
        _selectedBooks.clear()
        _pendingRemoval.clear()
    }

    fun fixSelectedBooks() {
        val channelId = "FixBooks"
        val notificationId = channelId.hashCode()
        viewModelScope.launch {
            val selectedUrls = _selectedBooks.keys.toList()
            _selectedBooks.clear()
            _uiState.update { it.copy(isSelectionMode = false) }

            val books = selectedUrls.mapNotNull { appRepository.libraryBooks.get(it) }
                .filter { !it.url.isLocalUri }
            if (books.isEmpty()) return@launch
            _uiState.update { it.copy(isFixingBooks = true, fixProgress = 0, fixTotal = books.size) }

            // Обнуляем кэш-признаки
            books.forEach { book ->
                appRepository.libraryBooks.updateChaptersListHash(book.url, null)
                appRepository.libraryBooks.updateChaptersLastPage(book.url, null)
            }

            // Перечитываем книги — теперь с null-кэшем, чтобы updateBook сделал полный репарс
            val freshBooks = books.map { it.copy(chaptersListHash = null, chaptersLastPage = null) }

            // Прогресс-потоки
            val countingUpdating = MutableStateFlow<LibraryUpdatesInteractions.CountingUpdating?>(null)
            val currentUpdating = MutableStateFlow<Set<Book>>(setOf())
            val newUpdates = MutableStateFlow<Set<LibraryUpdatesInteractions.NewUpdate>>(setOf())
            val failedUpdates = MutableStateFlow<Set<Book>>(setOf())

            // Показываем нотификацию с прогрессом
            val notificationBuilder = notificationsCenter.showNotification(
                channelId = channelId,
                channelName = context.getString(R.string.book_fix),
                notificationId = notificationId,
                importance = NotificationManager.IMPORTANCE_LOW
            ) {
                title = context.getString(R.string.book_fix)
                setStyle(NotificationCompat.BigTextStyle())
            }

            // Подписка на обновление нотификации
            val notifyJob = launch {
                combine(
                    countingUpdating,
                    currentUpdating,
                ) { counting, current ->
                    if (counting != null) {
                        _uiState.update { it.copy(fixProgress = counting.updated, fixTotal = counting.total) }
                        notificationsCenter.modifyNotification(notificationBuilder, notificationId) {
                            title = context.getString(R.string.updating_library, counting.updated, counting.total)
                            text = current.joinToString("\n") { "· " + it.title }
                            setProgress(counting.total, counting.updated, false)
                        }
                    }
                }.collect()
            }

            libraryUpdatesInteractions.updateSpecificBooks(
                books = freshBooks,
                countingUpdating = countingUpdating,
                currentUpdating = currentUpdating,
                newUpdates = newUpdates,
                failedUpdates = failedUpdates,
            )

            notifyJob.cancel()
            notificationsCenter.close(notificationId)

            // Итог
            val failCount = failedUpdates.value.size
            val successCount = freshBooks.size - failCount
            _uiState.update { it.copy(isFixingBooks = false) }
        }
    }

    fun deleteSelectedBooks() {
        val toDelete = _selectedBooks.keys.toList()
        if (toDelete.isEmpty()) return
        _selectedBooks.clear()
        _uiState.update { it.copy(isSelectionMode = false) }
        toDelete.forEach { _pendingRemoval[it] = true }
        viewModelScope.launch {
            delay(300)
            appRepository.libraryBooks.setNotInLibrary(toDelete)
            toDelete.forEach { _pendingRemoval.remove(it) }
        }
    }

    fun setShowBottomSheet(show: Boolean) {
        _uiState.update { it.copy(showBottomSheet = show) }
    }

    fun toggleShowCategories() {
        _uiState.update { it.copy(showCategories = !it.showCategories) }
    }

    fun readFilterToggle() {
        appPreferences.LIBRARY_FILTER_READ.value = _uiState.value.readFilter.next()
    }

    fun sortConfigToggleDirection() {
        appPreferences.LIBRARY_SORT_CONFIG.value = _uiState.value.sortConfig.toggleDirection()
    }

    fun sortConfigNextOption() {
        appPreferences.LIBRARY_SORT_CONFIG.value = _uiState.value.sortConfig.nextOption()
    }

    fun setSortOption(option: LibrarySortOption) {
        appPreferences.LIBRARY_SORT_CONFIG.value = _uiState.value.sortConfig.copy(option = option)
    }

    fun bookCompletedToggle(bookUrl: String) {
        viewModelScope.launch {
            appRepository.libraryBooks.toggleCompleted(bookUrl)
        }
    }

    fun deleteBook(bookUrl: String) {
        _pendingRemoval[bookUrl] = true
        if (_uiState.value.bookActionsSheetBook?.url == bookUrl) {
            _uiState.update { it.copy(bookActionsSheetBook = null) }
        }
        viewModelScope.launch {
            delay(300)
            appRepository.libraryBooks.setNotInLibrary(bookUrl)
            _pendingRemoval.remove(bookUrl)
        }
    }

    fun markAllChaptersAsRead(bookUrl: String) {
        viewModelScope.launch {
            appRepository.bookChapters.setAllAsReadByBookUrl(bookUrl)
        }
    }

    fun markAllChaptersAsUnread(bookUrl: String) {
        viewModelScope.launch {
            appRepository.bookChapters.setAllAsUnreadByBookUrl(bookUrl)
        }
    }

    fun getCategories(): List<String> = listOf("", "Completed") + _uiState.value.customCategories

    fun updateBookCategory(bookUrl: String, category: String) {
        viewModelScope.launch {
            val isCompleted = category == "Completed"
            appRepository.libraryBooks.updateCategoryAndCompleted(bookUrl, category, isCompleted)
        }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        val current = appPreferences.LIBRARY_CUSTOM_CATEGORIES.value
        if (name !in current) {
            appPreferences.LIBRARY_CUSTOM_CATEGORIES.value = current + name
        }
    }

    fun removeCategory(name: String) {
        val current = appPreferences.LIBRARY_CUSTOM_CATEGORIES.value
        appPreferences.LIBRARY_CUSTOM_CATEGORIES.value = current - name
    }

    fun moveBooksToCategory(category: String) {
        viewModelScope.launch {
            val toMove = _selectedBooks.keys.toList()
            if (toMove.isEmpty()) return@launch
            appRepository.libraryBooks.batchUpdateCategoryAndCompleted(
                toMove, category, category == "Completed"
            )
            _selectedBooks.clear()
            _uiState.update { it.copy(isSelectionMode = false) }
        }
    }

    fun getBook(bookUrl: String) = appRepository.libraryBooks.getFlow(bookUrl).filterNotNull()
}