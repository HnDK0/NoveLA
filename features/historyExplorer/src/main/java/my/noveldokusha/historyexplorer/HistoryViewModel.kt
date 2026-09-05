package my.noveldokusha.historyexplorer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.feature.local_database.DAOs.ReadingHistoryDao
import my.noveldokusha.feature.local_database.tables.ReadingHistory
import my.noveldokusha.scraper.Scraper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class HistoryItem(
    val bookUrl: String,
    val bookTitle: String,
    val bookCoverUrl: String,
    val lastReadChapterUrl: String?,
    val lastReadChapterTitle: String?,
    val lastReadEpochTimeMilli: Long,
    val totalChapters: Int,
    val readChapters: Int,
    val sourceName: String?,
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Content(val items: List<HistoryItem>) : HistoryUiState
    data object Empty : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val readingHistoryDao: ReadingHistoryDao,
    private val scraper: Scraper,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sourceNameCache = ConcurrentHashMap<String, String>()

    val uiState: StateFlow<HistoryUiState> = readingHistoryDao
        .getAllFlow()
        .map { list ->
            if (list.isEmpty()) HistoryUiState.Empty
            else HistoryUiState.Content(list.map { it.toHistoryItem() })
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState.Loading
        )

    fun delete(bookUrl: String) {
        viewModelScope.launch {
            readingHistoryDao.delete(bookUrl)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            readingHistoryDao.deleteAll()
        }
    }

    private fun ReadingHistory.toHistoryItem() = HistoryItem(
        bookUrl = bookUrl,
        bookTitle = bookTitle,
        bookCoverUrl = bookCoverUrl,
        lastReadChapterUrl = lastReadChapterUrl,
        lastReadChapterTitle = lastReadChapterTitle,
        lastReadEpochTimeMilli = lastReadEpochTimeMilli,
        totalChapters = totalChapters,
        readChapters = readChapters,
        sourceName = resolveSourceName(bookUrl),
    )

    private fun resolveSourceName(url: String): String? {
        sourceNameCache[url]?.let { return it }
        val result = if (url.isLocalUri) "Local"
        else scraper.getSourceId(url)?.let { id ->
            scraper.sourcesList.find { it.id == id }?.resolveName(context)
        }
        if (result != null) sourceNameCache[url] = result
        return result
    }
}
