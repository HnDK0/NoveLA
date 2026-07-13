package my.noveldokusha.tooling.novel_migration.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.MyButton
import my.noveldokusha.strings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationResultsScreen(
    title: String,
    migratingBooks: List<MigratingBook>,
    isSearchingAll: Boolean,
    isMigrating: Boolean,
    isComplete: Boolean,
    migrateProgress: Int,
    migrateTotal: Int,
    successCount: Int,
    skipCount: Int,
    failCount: Int,
    chaptersMatched: Int = 0,
    chaptersTotal: Int = 0,
    onPressBack: () -> Unit,
    onSkip: (Int) -> Unit,
    onMigrateAll: () -> Unit,
    onSelectResult: ((ScoredSearchResult) -> Unit)? = null,
    onSelectAlternative: ((bookIndex: Int, resultIndex: Int) -> Unit)? = null,
    onReturnToLibrary: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onPressBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (isSearchingAll) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.migration_searching))
                        val searchingIndex = migratingBooks.indexOfFirst { it.isSearching }
                        if (searchingIndex >= 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.migration_searching_progress, searchingIndex + 1, migratingBooks.size, migratingBooks[searchingIndex].book.title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (isComplete) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.migration_complete),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            if (chaptersTotal > 0) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.migration_chapters_matched, chaptersMatched, chaptersTotal),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.migration_success, successCount, skipCount, failCount),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(24.dp))
                            MyButton(
                                text = stringResource(R.string.migration_return_to_library),
                                onClick = onReturnToLibrary ?: onPressBack,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        if (isMigrating) {
                            LinearProgressIndicator(
                                progress = {
                                    if (migrateTotal > 0) migrateProgress.toFloat() / migrateTotal else 0f
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.migration_progress, migrateProgress, migrateTotal),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    itemsIndexed(migratingBooks) { index, mb ->
                        MigrationResultItem(
                            migratingBook = mb,
                            bookIndex = index,
                            onSkip = { onSkip(index) },
                            onClick = onSelectResult?.let { { mb.result?.let(it) } },
                            onSelectAlternative = onSelectAlternative,
                        )
                    }
                }

                if (!isMigrating) {
                    val canMigrate = migratingBooks.any { it.result != null && !it.isSkipped && !it.isDone }
                    Surface(
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MyButton(
                            text = stringResource(R.string.migration_migrate_all),
                            onClick = onMigrateAll,
                            enabled = canMigrate,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MigrationResultItem(
    migratingBook: MigratingBook,
    bookIndex: Int,
    onSkip: () -> Unit,
    onClick: (() -> Unit)? = null,
    onSelectAlternative: ((bookIndex: Int, resultIndex: Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(onClick = onClick ?: {}) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    migratingBook.book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            val errorMsg = migratingBook.error
            if (migratingBook.isDone && migratingBook.isSkipped) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            } else if (migratingBook.isDone) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else if (migratingBook.result != null && errorMsg != null) {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(errorMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else if (migratingBook.result != null) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        migratingBook.result!!.book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        migratingBook.result!!.source.resolveName(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.migration_chapters_count, migratingBook.result!!.chapters.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.migration_skip)) },
                            onClick = { showMenu = false; onSkip() },
                        )
                        if (onSelectAlternative != null) {
                            migratingBook.results.forEachIndexed { idx, alt ->
                                if (idx != migratingBook.selectedIndex) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.migration_source_with_chapters, alt.source.resolveName(context), alt.chapters.size)
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onSelectAlternative(bookIndex, idx)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.migration_no_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
