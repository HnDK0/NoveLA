package my.noveldokusha.libraryexplorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.R
import my.noveldokusha.coreui.components.BookImageButtonView
import my.noveldokusha.coreui.theme.ImageBorderShape
import my.noveldokusha.coreui.theme.isLightTheme
import my.noveldokusha.coreui.theme.Grey0
import my.noveldokusha.coreui.theme.Grey75
import my.noveldokusha.coreui.theme.Grey400
import my.noveldokusha.coreui.theme.Grey1000
import my.noveldokusha.coreui.theme.Error300
import my.noveldokusha.core.isLocalUri
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.feature.local_database.BookWithContext

@Composable
internal fun LibraryPageBody(
    list: List<BookWithContext>,
    onClick: (BookWithContext) -> Unit,
    onLongClick: (BookWithContext) -> Unit,
    getSourceName: (String) -> String,
    // Количество колонок: от 2 до 6, дефолт 3
    gridColumns: Int = 3,
    selectedBooks: Map<String, Boolean> = emptyMap(),
    isSelectionMode: Boolean = false,
    pendingRemoval: Set<String> = emptySet(),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(gridColumns.coerceIn(2, 6)),
        contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp, start = 4.dp, end = 4.dp),
    ) {
        items(
            items = list,
            key = { it.book.url },
            contentType = { "book" }
        ) {
            val isSelected = selectedBooks[it.book.url] ?: false
            val isRemoving = it.book.url in pendingRemoval
            AnimatedVisibility(
                visible = !isRemoving,
                exit = fadeOut(animationSpec = tween(300)) + scaleOut(animationSpec = tween(300))
            ) {
            Box {
                val notReadCount = it.chaptersCount - it.chaptersReadCount
                BookImageButtonView(
                    title = it.book.title,
                    coverImageModel = rememberResolvedBookImagePath(
                        bookUrl = it.book.url,
                        imagePath = it.book.coverImageUrl
                    ),
                    onClick = { onClick(it) },
                    onLongClick = { onLongClick(it) },
                    sourceText = getSourceName(it.book.url),
                    topLeftBadge = if (notReadCount != 0) {
                        {
                            Text(
                                text = notReadCount.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 12.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            )
                        }
                    } else null,
                    topRightBadge = if (it.book.url.isLocalUri) {
                        {
                            Text(
                                text = stringResource(R.string.local),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topEnd = 0.dp, bottomStart = 12.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            )
                        }
                    } else null,
                    forceCache = true
                )

                // Selection overlay
                if (isSelectionMode && isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.selected),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                        )
                    }
                }
            }
            }
        }
    }
}


