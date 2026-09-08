package my.noveldokusha.sourceexplorer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    FilterBottomSheet(
        filterList = filterList,
        activeFilters = activeFilters,
        onApply = viewModel::onApplyFilters,
        onDismiss = onDismiss,
        presets = presets,
        textHistory = textHistory,
        onPresetSave = viewModel::onPresetSave,
        onPresetLoad = viewModel::onPresetLoad,
        onPresetDelete = viewModel::onPresetDelete,
        onTextHistoryAdd = viewModel::onTextHistoryAdd,
    )
}
