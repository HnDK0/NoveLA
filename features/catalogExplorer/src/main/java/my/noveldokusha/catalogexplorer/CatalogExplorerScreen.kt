package my.noveldokusha.catalogexplorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.noveldokusha.coreui.components.ChipOption
import my.noveldokusha.coreui.components.LanguageFilterChips
import my.noveldokusha.navigation.NavigationRouteViewModel
import my.noveldokusha.catalogexplorer.AddByUrlDialog
import my.noveldokusha.extensions.ExtensionsScreen
import my.noveldokusha.extensions.ExtensionsManagerViewModel
import my.noveldokusha.extensions.ExtensionsScreenEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogExplorerScreen(
    navigationRouteViewModel: NavigationRouteViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val viewModel: CatalogExplorerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableLanguages by viewModel.availableLanguages.collectAsStateWithLifecycle()

    val extensionsViewModel = hiltViewModel<ExtensionsManagerViewModel>()
    val extensionsState by extensionsViewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var extensionsChipsVisible by rememberSaveable { mutableStateOf(false) }

    val onDatabaseClick = remember(context) {
        { database: my.noveldokusha.scraper.DatabaseInterface ->
            navigationRouteViewModel.databaseSearch(
                context,
                databaseBaseUrl = database.baseUrl
            ).let(context::startActivity)
        }
    }

    val onSourceClick = remember(context) {
        { source: my.noveldokusha.scraper.SourceInterface ->
            navigationRouteViewModel.sourceCatalog(
                context,
                sourceBaseUrl = source.baseUrl
            ).let(context::startActivity)
        }
    }

    val onGlobalSearchClick = remember(context) {
        {
            navigationRouteViewModel.globalSearch(
                context,
                text = ""
            ).let(context::startActivity)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Text(
                            text = "Finder",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
                    actions = {
                        // Show different actions based on selected tab
                        if (uiState.selectedTabIndex == 0) {
                            BrowseTabActions(
                                onAddByUrlClick = { viewModel.setShowAddByUrlDialog(true) },
                                onGlobalSearchClick = onGlobalSearchClick,
                                onToggleLanguageChips = viewModel::toggleLanguageChips,
                            )
                        } else {
                            ExtensionsTabActions(
                                onRefresh = { extensionsViewModel.onEvent(ExtensionsScreenEvent.OnRefresh) },
                                onShowRepositoryDialog = { extensionsViewModel.onEvent(ExtensionsScreenEvent.OnShowRepositoryDialog) },
                                onToggleLanguageChips = { extensionsChipsVisible = !extensionsChipsVisible },
                            )
                        }
                    }
                )

                // Tab Row - Browse and Extensions tabs (same style as Library)
                TabRow(
                    selectedTabIndex = uiState.selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    indicator = {
                        val tabPos = it[uiState.selectedTabIndex]
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPos)
                                .fillMaxSize()
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, my.noveldokusha.coreui.theme.shapes.small)
                                .zIndex(-1f)
                        )
                    },
                    divider = {}
                ) {
                    val selectedColor = MaterialTheme.colorScheme.onSurface
                    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    Tab(
                        selected = uiState.selectedTabIndex == 0,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                        onClick = { viewModel.setTabIndex(0) },
                        text = {
                            Text(
                                text = "Browse",
                                color = if (uiState.selectedTabIndex == 0) selectedColor else unselectedColor,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedTabIndex == 1,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                        onClick = { viewModel.setTabIndex(1) },
                        text = {
                            Text(
                                text = "Extensions",
                                color = if (uiState.selectedTabIndex == 1) selectedColor else unselectedColor,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }

                // Language filter chips row
                if (uiState.selectedTabIndex == 0) {
                    LanguageFilterChips(
                        selected = uiState.selectedLanguages,
                        all = availableLanguages.map { ChipOption(id = it.code, label = it.name) },
                        onToggle = viewModel::toggleSourceLanguage,
                        onClearAll = viewModel::clearLanguageFilter,
                        visible = uiState.showLanguageChips,
                    )
                } else {
                    LanguageFilterChips(
                        selected = extensionsState.selectedLanguages,
                        all = extensionsState.availableLanguages.map { ChipOption(id = it.code, label = it.name, count = it.count) },
                        onToggle = { code -> extensionsViewModel.onEvent(ExtensionsScreenEvent.OnLanguageFilterToggle(code)) },
                        onClearAll = { extensionsViewModel.onEvent(ExtensionsScreenEvent.OnLanguageFilterClear(null)) },
                        visible = extensionsChipsVisible,
                    )
                }
            }
        },
        content = { innerPadding ->
            when (uiState.selectedTabIndex) {
                0 -> {
                    // Browse tab content
                    val filteredSources = remember(uiState.sourcesList, uiState.selectedLanguages) {
                        if (uiState.selectedLanguages.isEmpty()) {
                            uiState.sourcesList
                        } else {
                            uiState.sourcesList.filter {
                                it.catalog.isLocalSource || it.catalog.languageTag in uiState.selectedLanguages
                            }
                        }
                    }
                    CatalogList(
                        innerPadding = innerPadding,
                        databasesList = uiState.databaseList,
                        sourcesList = filteredSources,
                        onDatabaseClick = onDatabaseClick,
                        onSourceClick = onSourceClick,
                        onSourceSetPinned = viewModel::onSourceSetPinned
                    )
                }
                1 -> {
                    // Extensions tab content
                    ExtensionsScreen(
                        innerPadding = innerPadding,
                        onBackPressed = null,
                        showExtensionsLanguageFilter = false,
                        onExtensionsLanguageFilterDismiss = { },
                        onRefresh = {
                            // Extensions screen handles its own refresh
                        }
                    )
                }
            }
        }
    )

    // Add by URL dialog
    if (uiState.showAddByUrlDialog) {
        AddByUrlDialog(
            onDismiss = { viewModel.setShowAddByUrlDialog(false) },
            onConfirm = { urls ->
                viewModel.addNovelsByUrls(urls)
                viewModel.setShowAddByUrlDialog(false)
            },
            scraper = viewModel.scraperRepository.scraper
        )
    }
}

@Composable
private fun BrowseTabActions(
    onAddByUrlClick: () -> Unit,
    onGlobalSearchClick: () -> Unit,
    onToggleLanguageChips: () -> Unit,
) {
    Row {
        IconButton(onClick = onAddByUrlClick) {
            Icon(
                Icons.Filled.AddLink,
                contentDescription = "Add by URL"
            )
        }
        IconButton(onClick = onGlobalSearchClick) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search"
            )
        }

        // Language filter toggle button
        IconButton(onClick = onToggleLanguageChips) {
            Icon(
                painter = painterResource(id = my.noveldokusha.coreui.R.drawable.ic_baseline_languages_24),
                contentDescription = "Languages",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ExtensionsTabActions(
    onRefresh: () -> Unit,
    onShowRepositoryDialog: () -> Unit,
    onToggleLanguageChips: () -> Unit,
) {
    Row {
        // Refresh button
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Repository settings button
        IconButton(onClick = onShowRepositoryDialog) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Repository Settings",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Language filter toggle button
        IconButton(onClick = onToggleLanguageChips) {
            Icon(
                painter = painterResource(id = my.noveldokusha.coreui.R.drawable.ic_baseline_languages_24),
                contentDescription = "Languages",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}