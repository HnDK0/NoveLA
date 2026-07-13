package my.noveldokusha.catalogexplorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import my.noveldokusha.strings.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.noveldokusha.coreui.components.ChipOption
import my.noveldokusha.coreui.components.LanguageFilterChips
import my.noveldokusha.navigation.NavigationRouteViewModel
import my.noveldokusha.catalogexplorer.AddByUrlDialog
import my.noveldokusha.extensions.ExtensionsScreen
import my.noveldokusha.extensions.ExtensionsManagerViewModel
import my.noveldokusha.extensions.ExtensionsScreenEvent
import my.noveldokusha.tooling.novel_migration.ui.MigrationTabContent

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

    val onMigrationSourceClick = remember(context) {
        { source: my.noveldokusha.scraper.SourceInterface.Catalog ->
            navigationRouteViewModel.massMigration(
                context,
                sourceBaseUrl = source.baseUrl
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
                                text = stringResource(R.string.title_finder),
                                style = MaterialTheme.typography.headlineMedium
                            )
                    },
                    actions = {
                        // Show different actions based on selected tab
                        when (uiState.selectedTabIndex) {
                            0 -> BrowseTabActions(
                                onAddByUrlClick = { viewModel.setShowAddByUrlDialog(true) },
                                onGlobalSearchClick = onGlobalSearchClick,
                                onToggleLanguageChips = viewModel::toggleLanguageChips,
                            )
                            1 -> ExtensionsTabActions(
                                onRefresh = { extensionsViewModel.onEvent(ExtensionsScreenEvent.OnRefresh) },
                                onShowRepositoryDialog = { extensionsViewModel.onEvent(ExtensionsScreenEvent.OnShowRepositoryDialog) },
                                onToggleLanguageChips = { extensionsChipsVisible = !extensionsChipsVisible },
                            )
                            2 -> MigrationTabActions(
                                onHistoryClick = {
                                    navigationRouteViewModel.migrationHistory(context).let(context::startActivity)
                                },
                                onGlobalSearchClick = onGlobalSearchClick,
                            )
                        }
                    }
                )

                // Tab row with icons
                val selectedColor = MaterialTheme.colorScheme.onSurface
                val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                val tabTitles = listOf(
                    R.string.title_browse to Icons.Outlined.Explore,
                    R.string.title_extensions to Icons.Outlined.Extension,
                    R.string.migration_tab to Icons.AutoMirrored.Outlined.AltRoute,
                )
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
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(4.dp)
                                )
                                .zIndex(-1f)
                        )
                    },
                    divider = {},
                ) {
                    tabTitles.forEachIndexed { index, (titleRes, icon) ->
                        Tab(
                            selected = uiState.selectedTabIndex == index,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                            onClick = { viewModel.setTabIndex(index) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    icon, null, Modifier.size(18.dp),
                                    tint = if (uiState.selectedTabIndex == index) selectedColor else unselectedColor
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    stringResource(titleRes),
                                    modifier = Modifier.weight(1f, fill = false),
                                    color = if (uiState.selectedTabIndex == index) selectedColor else unselectedColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
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
                2 -> {
                    // Migration tab content
                    MigrationTabContent(
                        innerPadding = innerPadding,
                        onSourceClick = onMigrationSourceClick,
                        onHistoryClick = {
                            navigationRouteViewModel.migrationHistory(context).let(context::startActivity)
                        },
                        onGlobalSearchClick = onGlobalSearchClick,
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
                contentDescription = stringResource(R.string.add_by_url)
            )
        }
        IconButton(onClick = onGlobalSearchClick) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.search)
            )
        }

        // Language filter toggle button
        IconButton(onClick = onToggleLanguageChips) {
            Icon(
                painter = painterResource(id = my.noveldokusha.coreui.R.drawable.ic_baseline_languages_24),
                contentDescription = stringResource(R.string.languages),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MigrationTabActions(
    onHistoryClick: () -> Unit,
    onGlobalSearchClick: () -> Unit,
) {
    Row {
        IconButton(onClick = onHistoryClick) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(R.string.migration_history),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onGlobalSearchClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.AltRoute,
                contentDescription = stringResource(R.string.search),
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
                contentDescription = stringResource(R.string.refresh),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Repository settings button
        IconButton(onClick = onShowRepositoryDialog) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.repository_settings),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Language filter toggle button
        IconButton(onClick = onToggleLanguageChips) {
            Icon(
                painter = painterResource(id = my.noveldokusha.coreui.R.drawable.ic_baseline_languages_24),
                contentDescription = stringResource(R.string.languages),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}