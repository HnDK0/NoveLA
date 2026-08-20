package my.noveldokusha.features.chapterslist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DoneOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PublishedWithChanges
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import my.nanihadesuka.compose.InternalLazyColumnScrollbar
import my.noveldokusha.coreui.theme.isAtTop
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.chapterslist.R
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.strings.R as StringsR
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersScreen(
    state: ChaptersScreenState,
    onLibraryToggle: () -> Unit,
    onSearchBookInDatabase: () -> Unit,
    onResumeReading: () -> Unit,
    onPressBack: () -> Unit,
    onSelectedDeleteDownloads: () -> Unit,
    onSelectedDownload: () -> Unit,
    onSelectedTranslate: () -> Unit = {},
    onSelectedDeleteTranslations: () -> Unit = {},
    onSelectedSetRead: () -> Unit,
    onSelectedSetUnread: () -> Unit,
    onSelectedSetReadUpToChapterRead: () -> Unit,
    onSelectedSetReadUpToChapterUnread: () -> Unit,
    onSelectedInvertSelection: () -> Unit,
    onSelectAllChapters: () -> Unit,
    onCloseSelectionBar: () -> Unit,
    onChapterClick: (chapter: ChapterWithContext) -> Unit,
    onChapterLongClick: (chapter: ChapterWithContext) -> Unit,
    onSelectionModeChapterClick: (chapter: ChapterWithContext) -> Unit,
    onSelectionModeChapterLongClick: (chapter: ChapterWithContext) -> Unit,
    onChapterDownload: (chapter: ChapterWithContext) -> Unit,
    onPullRefresh: () -> Unit,
    onCoverLongClick: () -> Unit,
    onChangeCover: () -> Unit,
    onOpenInBrowser: (url: String) -> Unit,
    onGlobalSearchClick: (input: String) -> Unit,
    onDownloadNext100Chapters: () -> Unit,
    onDownloadAllChapters: () -> Unit,
    onExport: (bookUrl: String, bookTitle: String) -> Unit,
    onExportContentChosen: (mode: String, sourceLang: String, targetLang: String) -> Unit,
    onExportDirectorySaved: (uri: String) -> Unit,
    onExportDialogDismiss: () -> Unit,
    exportDialogState: ExportDialogState,
    exportMessage: String?,
    onExportMessageShown: () -> Unit,
    onMigrateBook: () -> Unit = {},
    onDeleteTranslations: () -> Unit = {},
    onFixBook: () -> Unit = {},
    categories: () -> List<String>,
    onUpdateCategory: (String) -> Unit,
    translatedTitle: String?,
    translatedDescription: String?,
    isTranslatingInfo: Boolean,
    onTranslateBookInfo: () -> Unit,
    onClearBookInfoTranslation: () -> Unit,
    scraper: Scraper,
) {
    var showDropDown by rememberSaveable { mutableStateOf(false) }
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Folder picker launcher for export
    val directoryPicker = rememberLauncherForActivityResult(
        contract = OpenDocumentTreeReadPersistent()
    ) { uri ->
        if (uri == null) {
            // Пользователь закрыл пикер: если ждали папку для экспорта — закрываем
            // диалог (поток возвращается в Hidden); если меняли папку из
            // ContentChoice — остаёмся в нём, выбор контента не теряем.
            if (exportDialogState is ExportDialogState.NeedDirectory) {
                onExportDialogDismiss()
            }
        } else {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Отказ в persistable permission — не блокируем экспорт:
                // каталог всё равно запомним, следующий экспорт переспросит.
                Timber.w("Не удалось получить persistable permission для $uri: ${e.message}")
            }
            onExportDirectorySaved(uri.toString())
        }
    }

    // One-shot export message
    LaunchedEffect(exportMessage) {
        if (exportMessage != null) {
            snackbarHostState.showSnackbar(exportMessage)
            onExportMessageShown()
        }
    }
    val areSelectedChaptersRead by remember {
        derivedStateOf {
            val readUrls = state.chapters.asSequence()
                .filter { it.chapter.read }
                .map { it.chapter.url }
                .toHashSet()
            state.selectedChaptersUrl.keys.all { it in readUrls }
        }
    }

    if (state.isInSelectionMode.value) BackHandler {
        onCloseSelectionBar()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val isAtTop by lazyListState.isAtTop(threshold = 40.dp)
            val alpha by animateFloatAsState(targetValue = if (isAtTop) 0f else 1f, label = "")
            val backgroundColor by animateColorAsState(
                targetValue = MaterialTheme.colorScheme.background.copy(alpha = alpha),
                label = ""
            )
            val titleColor by animateColorAsState(
                targetValue = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                label = ""
            )
            Surface(color = backgroundColor) {
                Column {
                    TopAppBar(
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                        title = {
                            Text(
                                text = state.book.value.title,
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = titleColor
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onPressBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                            }
                        },
                        actions = {
                            IconButton(onClick = onLibraryToggle) {
                                Icon(
                                    if (state.book.value.inLibrary) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                    stringResource(R.string.open_the_web_view),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { showBottomSheet = !showBottomSheet }) {
                                Icon(
                                    Icons.Filled.FilterList,
                                    stringResource(R.string.filter),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showDropDown = !showDropDown }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    stringResource(R.string.options_panel)
                                )
                                DropdownMenu(
                                    expanded = showDropDown,
                                    onDismissRequest = { showDropDown = false }) {
                                    ChaptersDropDown(
                                        isLocalSource = state.isLocalSource.value,
                                        bookUrl = state.book.value.url,
                                        bookTitle = state.book.value.title,
                                        openInBrowser = {
                                            if (!state.book.value.url.isLocalUri) {
                                                onOpenInBrowser(state.book.value.url)
                                            }
                                        },
                                        onSearchBookInDatabase = onSearchBookInDatabase,
                                        onResumeReading = onResumeReading,
                                        onChangeCover = onChangeCover,
                                        onDownloadNext100Chapters = onDownloadNext100Chapters,
                                        onDownloadAllChapters = onDownloadAllChapters,
                                        onExport = onExport,
                                        onMigrateBook = onMigrateBook,
                                        onDeleteTranslations = onDeleteTranslations,
                                        onFixBook = onFixBook,
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider(Modifier.alpha(alpha))
                }
            }
            AnimatedVisibility(
                visible = state.isInSelectionMode.value,
                enter = expandVertically(initialHeight = { it / 8 }, expandFrom = Alignment.Top)
                        + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(targetHeight = { it / 8 }, shrinkTowards = Alignment.Top)
                        + fadeOut(animationSpec = tween(120)),
            ) {
                Surface(color = MaterialTheme.colorScheme.primary) {
                    TopAppBar(
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            scrolledContainerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        title = {
                            Text(
                                text = state.selectedChaptersUrl.size.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.animateContentSize()
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onCloseSelectionBar) {
                                Icon(
                                    Icons.Outlined.Close,
                                    stringResource(id = R.string.close_selection_bar)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onSelectAllChapters) {
                                Icon(
                                    Icons.Outlined.SelectAll,
                                    stringResource(id = R.string.select_all_chapters)
                                )
                            }
                            IconButton(onClick = onSelectedInvertSelection) {
                                Icon(
                                    Icons.Outlined.PublishedWithChanges,
                                    stringResource(id = R.string.close_selection_bar)
                                )
                            }
                        }
                    )
                }
            }
        },
        content = { innerPadding ->
            ChaptersScreenBody(
                state = state,
                lazyListState = lazyListState,
                innerPadding = innerPadding,
                translatedTitle = translatedTitle,
                translatedDescription = translatedDescription,
                isTranslating = isTranslatingInfo,
                onTranslateClick = onTranslateBookInfo,
                onClearTranslationClick = onClearBookInfoTranslation,
                onChapterClick = if (state.isInSelectionMode.value) onSelectionModeChapterClick else onChapterClick,
                onChapterLongClick = if (state.isInSelectionMode.value) onSelectionModeChapterLongClick else onChapterLongClick,
                onChapterDownload = onChapterDownload,
                onPullRefresh = onPullRefresh,
                onCoverLongClick = onCoverLongClick,
                onGlobalSearchClick = onGlobalSearchClick,
                bookCategory = state.book.value.category,
                categories = categories,
                onCategoryClick = { showCategoryPicker = true },
                scraper = scraper,
            )
            Box(Modifier.padding(innerPadding)) {
                InternalLazyColumnScrollbar(state = lazyListState)
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.isInSelectionMode.value,
                enter = expandVertically(initialHeight = { it / 8 }) + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(targetHeight = { it / 8 }) + fadeOut(animationSpec = tween(120)),
            ) {
                BottomAppBar(
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!state.isLocalSource.value) {
                            IconButton(onClick = onSelectedDeleteDownloads) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    stringResource(id = R.string.remove_selected_chapters_downloads)
                                )
                            }
                            IconButton(onClick = onSelectedDownload) {
                                Icon(
                                    Icons.Outlined.CloudDownload,
                                    stringResource(id = R.string.download_selected_chapters)
                                )
                            }
                        }
                        IconButton(onClick = onSelectedTranslate) {
                            Icon(
                                Icons.Outlined.Translate,
                                stringResource(id = R.string.translate_selected_chapters)
                            )
                        }
                        if (state.isLocalSource.value) {
                            IconButton(onClick = onSelectedDeleteTranslations) {
                                Icon(
                                    Icons.Outlined.DeleteSweep,
                                    stringResource(id = R.string.delete_selected_chapters_translations)
                                )
                            }
                        }
                        if (areSelectedChaptersRead) {
                            IconButton(onClick = onSelectedSetUnread) {
                                Icon(
                                    Icons.Filled.DoneOutline,
                                    stringResource(id = R.string.set_as_not_read_selected_chapters)
                                )
                            }
                        } else {
                            IconButton(onClick = onSelectedSetRead) {
                                Icon(
                                    Icons.Filled.Done,
                                    stringResource(id = R.string.set_as_read_selected_chapters)
                                )
                            }
                        }
                        if (state.selectedChaptersUrl.size <= 1) {
                            if (areSelectedChaptersRead) {
                                IconButton(onClick = onSelectedSetReadUpToChapterUnread) {
                                    Icon(
                                        Icons.Filled.RemoveDone,
                                        stringResource(id = R.string.set_as_Unread_up_to_selected_chapter)
                                    )
                                }
                            } else {
                                IconButton(onClick = onSelectedSetReadUpToChapterRead) {
                                    Icon(
                                        Icons.Filled.DoneAll,
                                        stringResource(id = R.string.set_as_read_up_to_selected_chapter)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onResumeReading
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.textPadding()
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(id = R.string.open_last_read_chapter),

                    )
                    AnimatedVisibility(visible = lazyListState.isAtTop(threshold = 100.dp).value) {
                        Text(
                            text = stringResource(id = R.string.resume_reading),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    )

    ChaptersBottomSheet(
        visible = showBottomSheet,
        onDismiss = { showBottomSheet = false },
        state = state
    )

    if (showCategoryPicker) {
        val categoryList = categories().let { cats ->
            cats.map { category ->
                val label = when (category) {
                    "" -> stringResource(R.string.reading)
                    "Completed" -> stringResource(R.string.completed)
                    else -> category
                }
                category to label
            }
        }
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = {
                Text(text = stringResource(R.string.category_name))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categoryList.forEach { (category, label) ->
                        val isSelected = state.book.value.category == category
                        if (isSelected) {
                            FilledTonalButton(
                                onClick = {},
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onUpdateCategory(category)
                                    showCategoryPicker = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCategoryPicker = false }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Export content choice dialog
    when (val ds = exportDialogState) {
        is ExportDialogState.ContentChoice -> {
            var selectedMode by rememberSaveable { mutableStateOf("original") }
            var selectedSourceLang by rememberSaveable { mutableStateOf("") }
            var selectedTargetLang by rememberSaveable { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = onExportDialogDismiss,
                title = {
                    Text(text = stringResource(StringsR.string.export))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Original option
                        ExportContentOption(
                            text = stringResource(StringsR.string.export_original),
                            selected = selectedMode == "original",
                            onClick = {
                                selectedMode = "original"
                                selectedSourceLang = ""
                                selectedTargetLang = ""
                            }
                        )

                        // Translation options
                        ds.availableTranslations.forEach { pair ->
                            val modeKey = "translation_${pair.sourceLang}_${pair.targetLang}"
                            val isSelected = selectedMode == modeKey
                            ExportContentOption(
                                text = stringResource(
                                    StringsR.string.export_translation,
                                    pair.sourceLang,
                                    pair.targetLang
                                ),
                                selected = isSelected,
                                onClick = {
                                    selectedMode = modeKey
                                    selectedSourceLang = pair.sourceLang
                                    selectedTargetLang = pair.targetLang
                                }
                            )
                        }

                        // Folder line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(
                                    StringsR.string.export_folder,
                                    ds.exportDirectoryName ?: stringResource(StringsR.string.not_set)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { directoryPicker.launch(null) }) {
                                Text(text = stringResource(StringsR.string.export_change_folder))
                            }
                        }

                        // Stats — счётчик выбранного режима: скачанные тела для
                        // оригинала, переведённые главы для выбранной пары.
                        val (shownDownloaded, shownTotal) = if (selectedMode == "original") {
                            ds.downloadedChapters to ds.totalChapters
                        } else {
                            val pair = ds.availableTranslations.firstOrNull {
                                it.sourceLang == selectedSourceLang &&
                                    it.targetLang == selectedTargetLang
                            }
                            (pair?.translatedChapters ?: ds.downloadedChapters) to ds.totalChapters
                        }
                        Text(
                            text = stringResource(
                                R.string.chapter_x_over_n,
                                shownDownloaded,
                                shownTotal
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onExportDialogDismiss,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(android.R.string.cancel),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                val mode = if (selectedMode == "original") "original" else "translation"
                                val source = if (selectedMode == "original") "" else selectedSourceLang
                                val target = if (selectedMode == "original") "" else selectedTargetLang
                                onExportContentChosen(mode, source, target)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(StringsR.string.export),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                },
                dismissButton = {}
            )
        }
        ExportDialogState.NeedDirectory -> {
            // Trigger folder picker, no dialog
            LaunchedEffect(Unit) {
                directoryPicker.launch(null)
            }
        }
        ExportDialogState.Hidden -> { /* no-op */ }
    }
}

private class OpenDocumentTreeReadPersistent : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input)
            .addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
    }
}

@Composable
private fun ExportContentOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = text, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Check, contentDescription = null)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = text)
        }
    }
}