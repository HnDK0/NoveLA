package my.noveldokusha.tooling.novel_migration.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.coreui.components.BookImageButtonView
import my.noveldokusha.coreui.components.BookTitlePosition
import my.noveldokusha.coreui.components.MyButton
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.displayName
import my.noveldokusha.strings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationBookPickerScreen(
    sourceName: String,
    books: List<Book>,
    bookChapterCounts: Map<String, Int>,
    selectedBooks: Set<String>,
    targetSource: SourceInterface.Catalog?,
    targetSources: List<SourceInterface.Catalog>,
    onPressBack: () -> Unit,
    onToggleSelection: (Book) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSetTargetSource: (SourceInterface.Catalog?) -> Unit,
    onStartSearch: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.migration_select_books, sourceName)) },
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
            if (targetSources.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.migration_migrate_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = targetSource == null,
                        onClick = { onSetTargetSource(null) },
                        label = { Text(stringResource(R.string.all_sources), style = MaterialTheme.typography.labelSmall) },
                    )
                    targetSources.forEach { source ->
                        FilterChip(
                            selected = targetSource == source,
                            onClick = { onSetTargetSource(source) },
                            label = {
                                Text(
                                    source.displayName(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.migration_books_count, books.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MyButton(
                        text = stringResource(R.string.select_all),
                        onClick = onSelectAll,
                        outerPadding = 0.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        minHeight = 32.dp,
                    )
                    if (selectedBooks.isNotEmpty()) {
                        MyButton(
                            text = stringResource(R.string.migration_clear_selection),
                            onClick = onClearSelection,
                            outerPadding = 0.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            minHeight = 32.dp,
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(books, key = { it.url }) { book ->
                    val isSelected = book.url in selectedBooks
                    val chapterCount = bookChapterCounts[book.url] ?: 0
                    BookImageButtonView(
                        title = book.title,
                        coverImageModel = rememberResolvedBookImagePath(
                            bookUrl = book.url,
                            imagePath = book.coverImageUrl,
                        ),
                        onClick = { onToggleSelection(book) },
                        onLongClick = {},
                        modifier = Modifier.width(110.dp),
                        bookTitlePosition = BookTitlePosition.Outside,
                        topLeftBadge = {
                            if (chapterCount > 0) {
                                Text(
                                    text = "$chapterCount",
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
                        topRightBadge = {
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp),
                                ) {
                                    Text(
                                        text = "\u2713",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            MyButton(
                text = stringResource(R.string.migration_search_matches),
                onClick = onStartSearch,
                enabled = selectedBooks.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
