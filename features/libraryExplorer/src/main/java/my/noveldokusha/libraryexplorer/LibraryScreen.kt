package my.noveldokusha.libraryexplorer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.ExperimentalAnimationApi
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.MoreVert
import my.noveldokusha.coreui.components.AnimatedTransition
import my.noveldokusha.coreui.components.ToolbarMode
import my.noveldokusha.coreui.components.TopAppBarSearch
import my.noveldokusha.navigation.NavigationRouteViewModel
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.feature.local_database.BookWithContext
import my.noveldokusha.feature.local_database.tables.Book


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LibraryScreen(
    navigationRouteViewModel: NavigationRouteViewModel = viewModel()
) {
    val libraryModel: LibraryViewModel = viewModel()
    val pageViewModel: LibraryPageViewModel = viewModel()
    
    val uiState by libraryModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val showDropdownMenu = remember { mutableStateOf(false) }
    val lastClickTime = remember { mutableStateOf(0L) }

    val gridColumns = uiState.gridColumns

    // Dialog states
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showMoveToCategoryDialog by remember { mutableStateOf(false) }

    val handleBookClick = remember(context, uiState.isSelectionMode) {
        { book: BookWithContext ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime.value >= 200L) {
                lastClickTime.value = currentTime
                if (uiState.isSelectionMode) {
                    libraryModel.toggleBookSelection(book.book.url)
                } else {
                    navigationRouteViewModel.chapters(
                        context = context,
                        bookMetadata = BookMetadata(
                            title = book.book.title,
                            url = book.book.url
                        )
                    ).let(context::startActivity)
                }
            }
        }
    }

    val handleBookLongClick = remember(uiState.isSelectionMode) {
        { book: BookWithContext ->
            if (!uiState.isSelectionMode) {
                libraryModel.setBookActionsSheetBook(book.book)
            } else {
                libraryModel.toggleBookSelection(book.book.url)
            }
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        title = {
                            Text(
                                text = stringResource(R.string.selected_count, libraryModel.selectedBooks.size),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    val currentBooks = pageViewModel.filteredList.value
                                    libraryModel.selectAllBooks(currentBooks)
                                }
                            ) {
                                Icon(Icons.Filled.SelectAll, stringResource(R.string.select_all))
                            }
                            IconButton(
                                onClick = { showMoveToCategoryDialog = true },
                                enabled = libraryModel.selectedBooks.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Filled.DriveFileRenameOutline,
                                    stringResource(R.string.move_to),
                                    tint = if (libraryModel.selectedBooks.isNotEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                onClick = { libraryModel.deleteSelectedBooks() },
                                enabled = libraryModel.selectedBooks.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    stringResource(R.string.delete),
                                    tint = if (libraryModel.selectedBooks.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(
                                onClick = { libraryModel.fixSelectedBooks() },
                                enabled = libraryModel.selectedBooks.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Filled.Build,
                                    stringResource(R.string.book_fix),
                                    tint = if (libraryModel.selectedBooks.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                            IconButton(onClick = { libraryModel.toggleSelectionMode() }) {
                                Icon(Icons.Filled.CheckCircle, stringResource(R.string.cancel))
                            }
                        }
                    )
                    if (uiState.isFixingBooks) {
                        LinearProgressIndicator(
                            progress = { if (uiState.fixTotal > 0) uiState.fixProgress.toFloat() / uiState.fixTotal.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error,
                            trackColor = MaterialTheme.colorScheme.errorContainer,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    AnimatedTransition(targetState = uiState.toolbarMode) { target ->
                        when (target) {
                            ToolbarMode.MAIN -> TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                title = {
                                    Text(
                                        text = "NoveLA",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                },
                                actions = {
                                    if (pageViewModel.isUpdating) {
                                        IconButton(onClick = { pageViewModel.cancelLibraryUpdates() }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                stringResource(R.string.cancel),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    IconButton(onClick = { libraryModel.setToolbarMode(ToolbarMode.SEARCH) }) {
                                        Icon(Icons.Filled.Search, stringResource(R.string.search_for_title))
                                    }
                                    IconButton(onClick = { libraryModel.setShowBottomSheet(true) }) {
                                        Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.filter))
                                    }
                                    // Category toggle button (like language filter in Finder)
                                    IconButton(
                                        onClick = { libraryModel.toggleShowCategories() }
                                    ) {
                                        Icon(
                                            Icons.Filled.FolderOpen,
                                            stringResource(R.string.categories),
                                            tint = if (uiState.showCategories)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { showDropdownMenu.value = true }) {
                                        Icon(Icons.Filled.MoreVert, stringResource(R.string.options_panel))
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = showDropdownMenu.value,
                                        onDismissRequest = { showDropdownMenu.value = false }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Filled.Checklist, stringResource(R.string.select_books))
                                            },
                                            text = { Text(stringResource(R.string.select_books)) },
                                            onClick = {
                                                showDropdownMenu.value = false
                                                libraryModel.toggleSelectionMode()
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(Icons.Filled.FileOpen, stringResource(id = R.string.import_epub))
                                            },
                                            text = { Text(stringResource(id = R.string.import_epub)) },
                                            onClick = my.noveldokusha.tooling.epub_importer.onDoImportFile()
                                        )
                                    }
                                }
                            )
                            ToolbarMode.SEARCH -> TopAppBarSearch(
                                focusRequester = remember { androidx.compose.ui.focus.FocusRequester() },
                                searchTextInput = pageViewModel.searchQuery,
                                onClose = {
                                    libraryModel.setToolbarMode(ToolbarMode.MAIN)
                                    pageViewModel.updateSearchQuery("")
                                },
                                onTextDone = { },
                                placeholderText = stringResource(R.string.search_here),
                                onSearchTextChange = { pageViewModel.updateSearchQuery(it) }
                            )
                        }
                    }
                }
            }
        },
        content = { innerPadding ->
            LibraryScreenBody(
                innerPadding = innerPadding,
                topAppBarState = null,
                onBookClick = handleBookClick,
                onBookLongClick = handleBookLongClick,
                gridColumns = gridColumns,
                selectedBooks = libraryModel.selectedBooks,
                isSelectionMode = uiState.isSelectionMode,
                pendingRemoval = libraryModel.pendingRemoval.keys.toSet(),
                showCategories = uiState.showCategories,
                customCategories = uiState.customCategories,
                onCreateCategory = { showCreateCategoryDialog = true },
                onDeleteCategory = { libraryModel.removeCategory(it) }
            )
        }
    )

    uiState.bookActionsSheetBook?.let { book ->
        BookActionsBottomSheet(
            book = book,
            onDismiss = { libraryModel.setBookActionsSheetBook(null) },
            onDeleteNovel = { libraryModel.deleteBook(book.url) },
            onCategorySelected = { libraryModel.updateBookCategory(book.url, it) },
            categories = libraryModel.getCategories(),
            onMarkAllChaptersRead = { libraryModel.markAllChaptersAsRead(book.url) },
            onMarkAllChaptersUnread = { libraryModel.markAllChaptersAsUnread(book.url) },
            onMigrate = {
                navigationRouteViewModel.novelMigration(
                    context = context,
                    bookUrl = book.url,
                    bookTitle = book.title
                ).let(context::startActivity)
            },
        )
    }

    LibraryBottomSheet(
        visible = uiState.showBottomSheet,
        onDismiss = { libraryModel.setShowBottomSheet(false) }
    )

    // Create category dialog
    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateCategoryDialog = false },
            onCreate = { name ->
                libraryModel.addCategory(name)
                showCreateCategoryDialog = false
            }
        )
    }

    // Move to category dialog (selection mode)
    if (showMoveToCategoryDialog) {
        MoveToCategoryDialog(
            categories = libraryModel.getCategories(),
            onDismiss = { showMoveToCategoryDialog = false },
            onCategorySelected = { category ->
                libraryModel.moveBooksToCategory(category)
                showMoveToCategoryDialog = false
            }
        )
    }
}