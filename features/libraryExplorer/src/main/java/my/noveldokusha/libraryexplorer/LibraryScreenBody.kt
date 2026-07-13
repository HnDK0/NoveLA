package my.noveldokusha.libraryexplorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import my.noveldokusha.coreui.R
import my.noveldokusha.core.domain.LibraryCategory
import my.noveldokusha.feature.local_database.BookWithContext

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
internal fun LibraryScreenBody(
    innerPadding: PaddingValues,
    topAppBarState: TopAppBarState? = null,
    onBookClick: (BookWithContext) -> Unit,
    onBookLongClick: (BookWithContext) -> Unit,
    gridColumns: Int = 3,
    selectedBooks: Map<String, Boolean> = emptyMap(),
    isSelectionMode: Boolean = false,
    pendingRemoval: Set<String> = emptySet(),
    showCategories: Boolean = false,
    customCategories: List<String> = emptyList(),
    onCreateCategory: () -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    viewModel: LibraryPageViewModel = viewModel(),
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()
    var showDeleteCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteCategoryConfirm by remember { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = viewModel.isUpdating,
        onRefresh = {
            viewModel.onLibraryCategoryRefresh(LibraryCategory.DEFAULT)
        },
        state = pullToRefreshState,
        modifier = Modifier.padding(innerPadding)
    ) {
        Column {
            // Category filter chips (like LanguageFilterChips in Finder)
            if (showCategories) {
                val selectedCategories by viewModel.selectedCategories.collectAsState()
                val catCounts by viewModel.categoryCounts
                val allCategories = buildList {
                    add("" to stringResource(R.string.reading))
                    add("Completed" to stringResource(R.string.completed))
                    customCategories.forEach { cat ->
                        add(cat to cat)
                    }
                }
                val totalCount = catCounts.values.sum()

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(8.dp)
                ) {
                    // "All" chip
                    FilterChip(
                        selected = selectedCategories.isEmpty(),
                        onClick = { viewModel.clearCategoryFilters() },
                        label = {
                            Text(
                                text = "${stringResource(R.string.all_categories)} ($totalCount)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategories.isEmpty(),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                        )
                    )
                    // Category chips
                    allCategories.forEach { (category, label) ->
                        val count = catCounts[category] ?: 0
                        val isSelected = category in selectedCategories
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleCategory(category) },
                            label = {
                                Text(
                                    text = "$label ($count)",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                            )
                        )
                    }
                    // "+" chip
                    FilterChip(
                        selected = false,
                        onClick = onCreateCategory,
                        label = {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.create_category),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        )
                    )
                    // Delete chip (only when custom categories exist)
                    if (customCategories.isNotEmpty()) {
                        FilterChip(
                            selected = false,
                            onClick = { showDeleteCategoryPicker = true },
                            label = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete_category),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.error,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = false,
                                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            )
                        )
                    }
                }
            }

            // Single list filtered by selected categories
            val list by viewModel.filteredList
            val sources by viewModel.luaSources.collectAsState()
            if (viewModel.isLibraryLoaded) {
                LibraryPageBody(
                    list = list,
                    onClick = onBookClick,
                    onLongClick = onBookLongClick,
                    getSourceName = remember(sources) { { viewModel.getSourceName(it) } },
                    gridColumns = gridColumns,
                    selectedBooks = selectedBooks,
                    isSelectionMode = isSelectionMode,
                    pendingRemoval = pendingRemoval,
                    gridState = gridState,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Delete category picker dialog (choose which category to delete)
    if (showDeleteCategoryPicker && customCategories.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteCategoryPicker = false },
            title = {
                Text(
                    text = stringResource(R.string.delete_category),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select a category to delete:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(customCategories) { category ->
                            androidx.compose.material3.ListItem(
                                headlineContent = {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier.clickable {
                                    showDeleteCategoryPicker = false
                                    showDeleteCategoryConfirm = category
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeleteCategoryPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete category confirmation dialog
    showDeleteCategoryConfirm?.let { category ->
        AlertDialog(
            onDismissRequest = { showDeleteCategoryConfirm = null },
            title = {
                Text(
                    text = stringResource(R.string.delete_category),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.category_delete_confirm, category),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(category)
                        showDeleteCategoryConfirm = null
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteCategoryConfirm = null }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}