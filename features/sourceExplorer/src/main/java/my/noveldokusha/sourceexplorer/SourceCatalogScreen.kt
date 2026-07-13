package my.noveldokusha.sourceexplorer

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.AnimatedTransition
import my.noveldokusha.coreui.components.BooksVerticalView
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.components.TopAppBarSearch
import my.noveldokusha.core.utils.actionCopyToClipboard
import my.noveldokusha.coreui.states.IteratorState
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.scraper.ActiveFilters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun SourceCatalogScreen(
    state: SourceCatalogScreenState,
    onSearchTextInputChange: (String) -> Unit,
    onSearchTextInputSubmit: (String) -> Unit,
    onSearchCatalogSubmit: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onListLayoutModeChange: (my.noveldokusha.core.appPreferences.ListLayoutMode) -> Unit,
    onToolbarModeChange: (ToolbarMode) -> Unit,
    onOpenSourceWebPage: () -> Unit,
    onBookClicked: (BookMetadata) -> Unit,
    onBookLongClicked: (BookMetadata) -> Unit,
    onPressBack: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    onApplyFilters: (ActiveFilters) -> Unit,
) {
    val context by rememberUpdatedState(newValue = LocalContext.current)
    val focusRequester = remember { FocusRequester() }
    val focusManager by rememberUpdatedState(newValue = LocalFocusManager.current)
    val hasActiveFilters = !state.activeFilters.value.isEmpty

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier,
            topBar = {
                Column {
                    AnimatedTransition(targetState = state.toolbarMode.value) { target ->
                        when (target) {
                            ToolbarMode.MAIN -> TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                                title = {
                                    Column {
                                        val title = state.sourceCatalogName.value
                                            ?: if (state.sourceCatalogNameStrId.value != 0)
                                                stringResource(id = state.sourceCatalogNameStrId.value)
                                            else ""
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.headlineMedium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = stringResource(R.string.catalog),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = onPressBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { onOpenSourceWebPage() }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_baseline_public_24),
                                            contentDescription = stringResource(R.string.open_the_web_view)
                                        )
                                    }
                                    IconButton(onClick = { onToolbarModeChange(ToolbarMode.SEARCH) }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_baseline_search_24),
                                            contentDescription = stringResource(R.string.search_for_title)
                                        )
                                    }
                                    if (state.hasFilters) {
                                        IconButton(onClick = onOpenFilterSheet) {
                                            BadgedBox(
                                                badge = {
                                                    if (hasActiveFilters) {
                                                        Badge(containerColor = colorAccent())
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.FilterList,
                                                    contentDescription = stringResource(R.string.filter),
                                                    tint = if (hasActiveFilters) colorAccent()
                                                    else LocalContentColor
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                            ToolbarMode.SEARCH -> TopAppBarSearch(
                                focusRequester = focusRequester,
                                searchTextInput = state.searchTextInput.value,
                                onClose = {
                                    focusManager.clearFocus()
                                    onToolbarModeChange(ToolbarMode.MAIN)
                                    onSearchCatalogSubmit()
                                },
                                onTextDone = {
                                    onSearchTextInputChange(it)
                                    onSearchTextInputSubmit(it)
                                },
                                placeholderText = stringResource(R.string.search_by_title),
                                onSearchTextChange = onSearchTextInputChange
                            )
                        }
                    }
                }
            },
            content = { innerPadding ->
                BooksVerticalView(
                    layoutMode = state.listLayoutMode.value,
                    // Передаём количество колонок из общего preference
                    gridColumns = state.gridColumns.value,
                    list = state.fetchIterator.list,
                    state = rememberLazyGridState(),
                    error = state.fetchIterator.error,
                    loadState = state.fetchIterator.state,
                    onLoadNext = state.fetchIterator::fetchNext,
                    onBookClicked = onBookClicked,
                    onBookLongClicked = onBookLongClicked,
                    onReload = state.fetchIterator::reloadFailedLastLoad,
                    onCopyError = context::actionCopyToClipboard,
                    onWebViewOpen = onOpenSourceWebPage,
                    innerPadding = innerPadding
                )
            }
        )

        if (state.isFilterSheetOpen.value && state.hasFilters) {
            FilterBottomSheet(
                filterList    = state.filterList.value,
                activeFilters = state.activeFilters.value,
                onApply       = onApplyFilters,
                onDismiss     = { state.isFilterSheetOpen.value = false }
            )
        }
    }
}

private val LocalContentColor: androidx.compose.ui.graphics.Color
    @Composable get() = androidx.compose.material3.LocalContentColor.current
