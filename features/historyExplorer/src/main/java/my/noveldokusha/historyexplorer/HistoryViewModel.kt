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
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.feature.local_database.DAOs.BookTranslationDao
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val readingHistoryDao: ReadingHistoryDao,
    private val bookTranslationDao: BookTranslationDao,
    private val translationSettingsResolver: TranslationSettingsResolver,
    private val appPreferences: AppPreferences,
    private val scraper: Scraper,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sourceNameCache = ConcurrentHashMap<String, String>()

    // bookUrl → переведённое название. Per-plugin gate: TRANSLATION_PLUGIN_HIDE_HISTORY.
    val translatedTitles: StateFlow<Map<String, String>> = readingHistoryDao
        .getAllFlow()
        .combine(translationSettingsResolver.translationSettingsChangeSignal()) { items, _ -> items }
        .flatMapLatest { items ->
            if (items.isEmpty()) flowOf(emptyMap())
            else combine(items.map { item ->
                val url = item.bookUrl
                // Per-plugin hide: skip translation if the source plugin hides history titles.
                val sourceId = scraper.getCompatibleSource(url)?.id
                if (sourceId != null && appPreferences.TRANSLATION_PLUGIN_HIDE_HISTORY.value[sourceId] == true) {
                    return@map flowOf(url to "")
                }
                val targetLang = translationSettingsResolver.translationTargetForBook(url)
                val enabled = translationSettingsResolver.translationEnabledForBook(url)
                val scope = translationSettingsResolver.translationScopeForBook(url)
                val sourceLang = translationSettingsResolver.translationPairForBook(url).source
                if (targetLang.isBlank() || !enabled || scope != AppPreferences.TRANSLATION_SCOPE_FULL) flowOf(url to "")
                else bookTranslationDao.getTranslatedBookFlow(url, targetLang)
                    .map { rows ->
                        val row = rows.firstOrNull { it.sourceLang == sourceLang }
                        url to (row?.titleTranslation ?: "")
                    }
            }) { results -> results.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiState: StateFlow<HistoryUiState> = combine(
        readingHistoryDao.getAllFlow(),
        translatedTitles
    ) { items, titles ->
        if (items.isEmpty()) HistoryUiState.Empty
        else HistoryUiState.Content(items.map { it.toHistoryItem(titles) })
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

    private fun ReadingHistory.toHistoryItem(titles: Map<String, String> = emptyMap()) = HistoryItem(
        bookUrl = bookUrl,
        bookTitle = titles[bookUrl]?.takeIf { it.isNotBlank() } ?: bookTitle,
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
