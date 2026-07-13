package my.noveldokusha.libraryexplorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import my.noveldokusha.coreui.R
import my.noveldokusha.strings.R as StringsR
import my.noveldokusha.coreui.components.ImageView
import my.noveldokusha.coreui.theme.ImageBorderShape
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.feature.local_database.tables.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookActionsBottomSheet(
    book: Book,
    categories: List<String>,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onMarkAllChaptersRead: () -> Unit,
    onMarkAllChaptersUnread: () -> Unit,
    onDeleteNovel: () -> Unit,
    onMigrate: () -> Unit = {},
) {
    val buttonTextSize = 12.sp
    val buttonShape = RoundedCornerShape(8.dp)
    var selectedCategory by remember { mutableStateOf(book.category) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Header: cover + title ────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ImageView(
                    imageModel = rememberResolvedBookImagePath(
                        bookUrl = book.url,
                        imagePath = book.coverImageUrl
                    ),
                    error = R.drawable.default_book_cover,
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(1 / 1.45f)
                        .clip(ImageBorderShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // ── Category selection: buttons for each category ─────────────────
            Text(
                text = stringResource(R.string.category_name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Build category list with labels
            val categoryLabels = categories.map { category ->
                val label = when (category) {
                    "" -> stringResource(R.string.reading)
                    "Completed" -> stringResource(R.string.completed)
                    else -> category
                }
                category to label
            }

            // Show 2 columns of buttons for categories
            categoryLabels.chunked(2).forEach { chunk ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    chunk.forEach { (category, label) ->
                        val isSelected = selectedCategory == category
                        if (isSelected) {
                            FilledTonalButton(
                                onClick = {},
                                shape = buttonShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = buttonTextSize,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedCategory = category
                                    onCategorySelected(category)
                                },
                                shape = buttonShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = buttonTextSize,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    // If chunk had only 1 item, add spacer for alignment
                    if (chunk.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Mark all chapters as read ────────────────────────────────────
            FilledTonalButton(
                onClick = {
                    onMarkAllChaptersRead()
                    onDismiss()
                },
                shape = buttonShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.mark_all_chapters_read),
                    fontSize = buttonTextSize,
                    textAlign = TextAlign.Center
                )
            }

            // ── Mark all chapters as unread ──────────────────────────────────
            FilledTonalButton(
                onClick = {
                    onMarkAllChaptersUnread()
                    onDismiss()
                },
                shape = buttonShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.mark_all_chapters_unread),
                    fontSize = buttonTextSize,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Migrate to another source ────────────────────────────────────
            FilledTonalButton(
                onClick = {
                    onMigrate()
                    onDismiss()
                },
                shape = buttonShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.AltRoute,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(StringsR.string.migration_migrate_src),
                    fontSize = buttonTextSize,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Delete novel ─────────────────────────────────────────────────
            FilledTonalButton(
                onClick = {
                    onDeleteNovel()
                    onDismiss()
                },
                shape = buttonShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.delete_novel),
                    fontSize = buttonTextSize,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}