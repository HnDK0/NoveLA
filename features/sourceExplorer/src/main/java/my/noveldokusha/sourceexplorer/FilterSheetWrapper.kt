package my.noveldokusha.sourceexplorer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import my.noveldokusha.core.appPreferences.FilterHistoryEntry
import my.noveldokusha.core.appPreferences.FilterPreset
import my.noveldokusha.scraper.ActiveFilters

/**
 * Bridges ViewModel state to FilterBottomSheet params.
 *
 * Absorbs new wiring so the call-site in SourceCatalogScreen stays minimal.
 */
@Composable
internal fun FilterSheetWrapper(
    viewModel: SourceCatalogViewModel,
    onDismiss: () -> Unit,
) {
    val filterList by viewModel.state.filterList
    val activeFilters by viewModel.state.activeFilters
    val presets by viewModel.presets
    val textHistory by viewModel.textHistory

    var contentTypeFilter by remember { mutableStateOf(activeFilters.contentType) }

    // Sync contentTypeFilter when a preset is loaded (activeFilters changes)
    LaunchedEffect(activeFilters.contentType) {
        contentTypeFilter = activeFilters.contentType
    }

    FilterBottomSheet(
        filterList = filterList,
        activeFilters = activeFilters,
        onApply = { filters ->
            viewModel.onApplyFilters(filters.copy(contentType = contentTypeFilter))
        },
        onDismiss = onDismiss,
        presets = presets,
        textHistory = textHistory,
        onPresetSave = viewModel::onPresetSave,
        onPresetLoad = viewModel::onPresetLoad,
        onPresetDelete = viewModel::onPresetDelete,
        onTextHistoryAdd = viewModel::onTextHistoryAdd,
        onTextHistoryRemove = viewModel::onTextHistoryRemove,
        contentTypeFilter = contentTypeFilter,
        onContentTypeChange = { contentTypeFilter = it },
    )
}
