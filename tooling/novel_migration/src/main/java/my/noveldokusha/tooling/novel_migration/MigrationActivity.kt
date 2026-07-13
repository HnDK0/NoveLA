package my.noveldokusha.tooling.novel_migration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.noveldokusha.strings.R
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.core.utils.Extra_Boolean
import my.noveldokusha.core.utils.Extra_String
import my.noveldokusha.core.utils.Extra_StringNullable
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.coreui.BaseActivity
import my.noveldokusha.coreui.components.BookImageButtonView
import my.noveldokusha.coreui.components.BookTitlePosition
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.data.ScraperRepository
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.navigation.NavigationRoutes
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.tooling.novel_migration.data.MigrationOptions
import my.noveldokusha.tooling.novel_migration.data.MigrationRepository
import my.noveldokusha.tooling.novel_migration.ui.*
import javax.inject.Inject

private enum class MigrationStep {
    BOOK_PICKER,
    RESULTS,
}

@AndroidEntryPoint
class MigrationActivity : BaseActivity() {

    class IntentData : Intent {
        var bookUrl by Extra_String()
        var bookTitle by Extra_String()
        var massMigrationSourceUrl by Extra_StringNullable()
        var showHistory by Extra_Boolean()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, bookUrl: String, bookTitle: String) : super(ctx, MigrationActivity::class.java) {
            this.bookUrl = bookUrl
            this.bookTitle = bookTitle
        }
    }

    @Inject
    lateinit var scraperRepository: ScraperRepository

    @Inject
    lateinit var navigationRoutes: NavigationRoutes

    private val singleViewModel: MigrationViewModel by viewModels()
    private val massViewModel: MassMigrationViewModel by viewModels()
    private val historyViewModel: MigrationHistoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentData = IntentData(intent)

        val isMassMigration = intentData.massMigrationSourceUrl?.isNotEmpty() == true
        val isHistory = intentData.showHistory

        if (isHistory) {
            showHistoryScreen(intentData)
            return
        }

        if (isMassMigration) {
            initMassMigration(intentData)
        } else {
            singleViewModel.init(intentData.bookUrl, intentData.bookTitle)
            initSingleMigration(intentData)
        }
    }

    private fun navigateToLibrary() {
        startActivity(
            navigationRoutes.main(this).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
    }

    private fun showHistoryScreen(intentData: IntentData) {
        setContent {
            Theme(themeProvider = themeProvider) {
                val groups by historyViewModel.groups.collectAsStateWithLifecycle()
                MigrationHistoryScreen(
                    groups = groups,
                    onPressBack = { finish() },
                    onDeleteGroup = { oldSourceId, newSourceId -> historyViewModel.deleteGroup(oldSourceId, newSourceId) },
                )
            }
        }
    }

    private fun initSingleMigration(intentData: IntentData) {
        setContent {
            var showOptions by remember { mutableStateOf(false) }

            Theme(themeProvider = themeProvider) {
                val state by singleViewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    if (!state.isSearching && state.searchResults.isEmpty() && state.error == null && !state.isComplete && state.selectedResult == null) {
                        singleViewModel.searchAllSources()
                    }
                }

                when {
                    state.isComplete -> {
                        MigrationResultsScreen(
                            title = state.bookTitle,
                            migratingBooks = emptyList(),
                            isSearchingAll = false,
                            isMigrating = false,
                            isComplete = true,
                            migrateProgress = state.chaptersTotal,
                            migrateTotal = state.chaptersTotal,
                            successCount = if (state.migrationSuccess) 1 else 0,
                            skipCount = 0,
                            failCount = if (state.migrationSuccess) 0 else 1,
                            chaptersMatched = state.chaptersMatched,
                            chaptersTotal = state.chaptersTotal,
                            onPressBack = { navigateToLibrary() },
                            onSkip = { navigateToLibrary() },
                            onMigrateAll = {},
                            onReturnToLibrary = { navigateToLibrary() },
                        )
                    }
                    state.searchResults.isNotEmpty() && state.selectedResult == null -> {
                        PickerScreen(
                            state = state,
                            onResultPicked = { index -> singleViewModel.onResultPicked(index) },
                            onPressBack = { finish() },
                        )
                    }
                    else -> {
                        val migratingBooks = if (state.isSearching || state.searchResults.isNotEmpty() || state.isMatching || state.isMigrating) {
                            listOf(MigratingBook(
                                book = Book(title = state.bookTitle, url = state.bookUrl),
                                result = state.selectedResult,
                                isSearching = state.isSearching || state.isMatching,
                                isDone = state.isComplete,
                                isSkipped = state.error != null && state.searchResults.isEmpty(),
                                error = state.error,
                            ))
                        } else emptyList()

                        MigrationResultsScreen(
                            title = state.bookTitle,
                            migratingBooks = migratingBooks,
                            isSearchingAll = state.isSearching,
                            isMigrating = state.isMigrating,
                            isComplete = state.isComplete,
                            migrateProgress = if (state.isMigrating) 0 else 1,
                            migrateTotal = 1,
                            successCount = 0,
                            skipCount = 0,
                            failCount = 0,
                            onPressBack = { finish() },
                            onSkip = { finish() },
                            onMigrateAll = { showOptions = true },
                        )
                    }
                }

                if (showOptions) {
                    MigrationOptionsDialog(
                        initialOptions = MigrationOptions(),
                        onConfirm = { options ->
                            singleViewModel.startMigration(options)
                            showOptions = false
                        },
                        onDismiss = { showOptions = false },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PickerScreen(
        state: MigrationUiState,
        onResultPicked: (Int) -> Unit,
        onPressBack: () -> Unit,
    ) {
        val groupedResults = remember(state.searchResults) {
            state.searchResults.withIndex()
                .groupBy({ (_, r) -> r.source }, { (idx, r) -> idx to r })
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.bookTitle) },
                    navigationIcon = {
                        IconButton(onClick = onPressBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { padding ->
            if (state.isSearching) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.migration_searching))
                    }
                }
            } else {
                if (state.error != null) {
                    Text(
                        state.error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 240.dp),
                ) {
                    groupedResults.forEach { (source, indexedResults) ->
                        item {
                            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                Text(
                                    text = source.resolveName(this@MigrationActivity),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(start = 12.dp, end = 30.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(indexedResults) { (flatIndex, result) ->
                                        BookImageButtonView(
                                            title = result.book.title,
                                            coverImageModel = rememberResolvedBookImagePath(
                                                bookUrl = result.book.url,
                                                imagePath = result.book.coverImageUrl ?: "",
                                            ),
                                            onClick = { onResultPicked(flatIndex) },
                                            onLongClick = {},
                                            modifier = Modifier.width(130.dp),
                                            bookTitlePosition = BookTitlePosition.Outside,
                                            topLeftBadge = {
                                                if (state.chaptersLoadingIndex == flatIndex) {
                                                    CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp)
                                                } else if (result.isLoaded) {
                                                    Text(
                                                        text = "${result.chapterCount}",
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier
                                                            .background(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 12.dp),
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 8.sp,
                                                        ),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initMassMigration(intentData: IntentData) {
        val sourceUrl = intentData.massMigrationSourceUrl

        setContent {
            var step by remember { mutableStateOf(MigrationStep.BOOK_PICKER) }
            var showOptions by remember { mutableStateOf(false) }
            var selectedSource by remember { mutableStateOf<SourceInterface.Catalog?>(null) }
            val massState by massViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(sourceUrl) {
                val source = scraperRepository.scraper.sourcesList
                    .filterIsInstance<SourceInterface.Catalog>()
                    .find { it.baseUrl == sourceUrl }
                if (source != null) {
                    selectedSource = source
                    massViewModel.setSource(source)
                }
            }

            Theme(themeProvider = themeProvider) {
                when (step) {
                    MigrationStep.BOOK_PICKER -> {
                        MigrationBookPickerScreen(
                            sourceName = selectedSource?.let { it.resolveName(this@MigrationActivity) }                         ?: stringResource(R.string.migration_select_source),
                            books = massState.books,
                            bookChapterCounts = massState.bookChapterCounts,
                            selectedBooks = massState.selectedBooks,
                            targetSource = massState.targetSource,
                            targetSources = massState.targetSources,
                            onPressBack = { finish() },
                            onToggleSelection = { massViewModel.toggleBookSelection(it) },
                            onSelectAll = { massViewModel.selectAllBooks() },
                            onClearSelection = { massViewModel.clearSelection() },
                            onSetTargetSource = { massViewModel.setTargetSource(it) },
                            onStartSearch = {
                                massViewModel.startSearch()
                                step = MigrationStep.RESULTS
                            },
                        )
                    }

                    MigrationStep.RESULTS -> {
                        MigrationResultsScreen(
                            title = selectedSource?.let { it.resolveName(this@MigrationActivity) }                         ?: stringResource(R.string.migration_title),
                            migratingBooks = massState.migratingBooks,
                            isSearchingAll = massState.isSearchingAll,
                            isMigrating = massState.isMigrating,
                            isComplete = massState.isComplete,
                            migrateProgress = massState.migrateProgress,
                            migrateTotal = massState.migrateTotal,
                            successCount = massState.successCount,
                            skipCount = massState.skipCount,
                            failCount = massState.failCount,
                            onPressBack = {
                                if (massState.isComplete) {
                                    navigateToLibrary()
                                } else {
                                    massViewModel.reset()
                                    step = MigrationStep.BOOK_PICKER
                                }
                            },
                            onSkip = { index -> massViewModel.skipBook(index) },
                            onMigrateAll = { showOptions = true },
                            onSelectAlternative = { bookIndex, resultIndex ->
                                massViewModel.setResultForBook(bookIndex, resultIndex)
                            },
                            onReturnToLibrary = { navigateToLibrary() },
                        )
                    }

                }

                if (showOptions) {
                    MigrationOptionsDialog(
                        initialOptions = MigrationOptions(),
                        onConfirm = { options ->
                            massViewModel.migrateAll(options)
                            showOptions = false
                        },
                        onDismiss = { showOptions = false },
                    )
                }
            }
        }
    }
}
