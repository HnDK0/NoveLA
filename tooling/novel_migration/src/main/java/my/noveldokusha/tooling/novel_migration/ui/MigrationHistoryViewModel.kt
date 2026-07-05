package my.noveldokusha.tooling.novel_migration.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.feature.local_database.tables.MigrationRecord
import my.noveldokusha.tooling.novel_migration.data.MigrationRepository
import javax.inject.Inject

data class SourcePairGroup(
    val oldSourceId: String,
    val newSourceId: String,
    val oldSourceName: String,
    val newSourceName: String,
    val totalCount: Int,
    val completedCount: Int,
    val partialCount: Int,
)

@HiltViewModel
class MigrationHistoryViewModel @Inject constructor(
    private val migrationRepository: MigrationRepository,
    private val scraperRepository: ScraperRepository,
    private val application: Application,
) : ViewModel() {

    private val _groups = MutableStateFlow<List<SourcePairGroup>>(emptyList())
    val groups: StateFlow<List<SourcePairGroup>> = _groups.asStateFlow()

    init {
        loadHistory()
    }

    private fun resolveSourceName(sourceId: String): String {
        val source = scraperRepository.scraper.sourcesList.find { it.id == sourceId }
        return source?.name
            ?: source?.let { src ->
                if (src.nameStrId != 0) {
                    try { application.getString(src.nameStrId) } catch (_: Exception) { null }
                } else null
            }
            ?: sourceId.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val records = withContext(Dispatchers.IO) {
                migrationRepository.getMigrationHistory()
            }
            val groups = records.groupBy { it.oldSourceId to it.newSourceId }
                .map { (key, recs) ->
                    SourcePairGroup(
                        oldSourceId = key.first,
                        newSourceId = key.second,
                        oldSourceName = resolveSourceName(key.first),
                        newSourceName = resolveSourceName(key.second),
                        totalCount = recs.size,
                        completedCount = recs.count { it.status == "completed" },
                        partialCount = recs.count { it.status != "completed" },
                    )
                }
                .sortedByDescending { it.totalCount }
            _groups.value = groups
        }
    }

    fun deleteGroup(oldSourceId: String, newSourceId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                migrationRepository.deleteMigrationRecordsBySourcePair(oldSourceId, newSourceId)
            }
            loadHistory()
        }
    }

    fun refresh() {
        loadHistory()
    }
}
