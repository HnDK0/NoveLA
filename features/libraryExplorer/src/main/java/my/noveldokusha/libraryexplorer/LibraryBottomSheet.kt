package my.noveldokusha.libraryexplorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.components.NovelaFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import my.noveldokusha.coreui.components.PosNegCheckbox
import my.noveldokusha.core.appPreferences.LibrarySortOption
import my.noveldokusha.core.appPreferences.SortDirection
import my.noveldokusha.core.appPreferences.SourceStripPosition
import my.noveldokusha.core.utils.toToggleableState

import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    model: LibraryViewModel = viewModel(),
    pageModel: LibraryPageViewModel = viewModel()
) {
    if (!visible) return

    val uiState by model.uiState.collectAsStateWithLifecycle()
    val availableGenres by pageModel.availableGenres
    val selectedGenres by pageModel.selectedGenres.collectAsStateWithLifecycle()
    val availableSources by pageModel.availableSources
    val selectedSources by pageModel.selectedSources.collectAsStateWithLifecycle()
    val selectedContentType by pageModel.selectedContentType.collectAsStateWithLifecycle()
    // Читаем текущее значение из общего preference
    val gridColumns = uiState.gridColumns

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(top = 8.dp, bottom = 64.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Хедер с кнопкой сброса ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.filter),
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    )
                )
                TextButton(onClick = { pageModel.resetAllFilters() }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(id = R.string.reset_filters),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // ── Размер сетки ──────────────────────────────────────────────────
            Text(
                text = stringResource(id = R.string.grid_columns).uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Метка с текущим числом колонок слева
                Text(
                    text = gridColumns.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Slider(
                    value = gridColumns.toFloat(),
                    onValueChange = { model.setGridColumns(it.toInt()) },
                    valueRange = 2f..6f,
                    steps = 3, // шаги: 2, 3, 4, 5, 6 → 3 внутренних шага
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            // Подписи минимума и максимума
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    text = "2",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "6",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // ── Стиль карточки ─────────────────────────────────────
            Text(
                text = stringResource(id = R.string.card_style).uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                NovelaFilterChip(
                    selected = uiState.sourceStripPosition == SourceStripPosition.OnCover,
                    onClick = { model.setSourceStripPosition(SourceStripPosition.OnCover) },
                    label = stringResource(R.string.source_strip_on_cover),
                )
                NovelaFilterChip(
                    selected = uiState.sourceStripPosition == SourceStripPosition.BelowCover,
                    onClick = { model.setSourceStripPosition(SourceStripPosition.BelowCover) },
                    label = stringResource(R.string.source_strip_below_cover),
                )
                NovelaFilterChip(
                    selected = uiState.sourceStripPosition == SourceStripPosition.InfoPanel,
                    onClick = { model.setSourceStripPosition(SourceStripPosition.InfoPanel) },
                    label = stringResource(R.string.source_strip_info_panel),
                )
            }

            // ── Тип контента (Все/Манга/Новелла) ────────────────────────────
            Text(
                text = stringResource(id = R.string.type).uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                ContentTypeFilter.entries.forEach { filter ->
                    NovelaFilterChip(
                        selected = selectedContentType == filter,
                        onClick = { pageModel.setContentTypeFilter(filter) },
                        label = stringResource(filter.labelRes),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(0.4f)
            )

            // ── Сортировка ────────────────────────────────────────────────────
            Text(
                text = stringResource(id = R.string.sort).uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val sortOptions = listOf(
                LibrarySortOption.TITLE           to R.string.title,
                LibrarySortOption.UNREAD_CHAPTERS to R.string.unread_chapters,
                LibrarySortOption.LAST_READ       to R.string.last_read,
                LibrarySortOption.LAST_UPDATE     to R.string.last_update,
                LibrarySortOption.ADDED           to R.string.date_added,
                LibrarySortOption.SOURCE          to R.string.sort_by_source,
            )

            sortOptions.forEach { (option, labelRes) ->
                val isSelected = uiState.sortConfig.option == option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { model.setSortOption(option) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = stringResource(id = labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        IconButton(onClick = { model.sortConfigToggleDirection() }) {
                            Icon(
                                imageVector = if (uiState.sortConfig.direction == SortDirection.ASC)
                                    Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .alpha(0.4f)
            )

            // ── Фильтры ───────────────────────────────────────────────────────
            Text(
                text = stringResource(id = R.string.filter).uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PosNegCheckbox(
                text = stringResource(id = R.string.read),
                state = uiState.readFilter.toToggleableState(),
                onStateChange = { model.readFilterToggle() },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Жанры — только если есть хоть один в библиотеке ──────────────
            if (availableGenres.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .alpha(0.4f)
                )
                Text(
                text = stringResource(id = R.string.genres).uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    NovelaFilterChip(
                        selected = selectedGenres.isEmpty(),
                        onClick = { pageModel.clearGenreFilters() },
                        label = stringResource(R.string.all_genres),
                    )
                    availableGenres.forEach { genre ->
                        NovelaFilterChip(
                            selected = genre in selectedGenres,
                            onClick = { pageModel.toggleGenreFilter(genre) },
                            label = genre,
                        )
                    }
                }
            }

            // ── Плагины (источники) — только если их >1 ─────────────────────
            if (availableSources.size > 1) {
                HorizontalDivider(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .alpha(0.4f)
                )
                Text(
                text = stringResource(id = R.string.sources).uppercase(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    NovelaFilterChip(
                        selected = selectedSources.isEmpty(),
                        onClick = { pageModel.clearSourceFilters() },
                        label = stringResource(R.string.all_sources),
                    )
                    availableSources.forEach { sourceName ->
                        NovelaFilterChip(
                            selected = sourceName in selectedSources,
                            onClick = { pageModel.toggleSourceFilter(sourceName) },
                            label = sourceName,
                        )
                    }
                }
            }
        }
    }
}