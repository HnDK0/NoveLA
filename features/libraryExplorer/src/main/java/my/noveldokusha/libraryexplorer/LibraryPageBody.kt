package my.noveldokusha.libraryexplorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.core.appPreferences.SourceStripPosition
import my.noveldokusha.coreui.R
import my.noveldokusha.coreui.components.BookImageButtonView
import my.noveldokusha.coreui.components.BookRatingBadge
import my.noveldokusha.coreui.components.toContentTypeBadgeIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.theme.ImageBorderShape
import my.noveldokusha.coreui.theme.isLightTheme
import my.noveldokusha.coreui.theme.Grey0
import my.noveldokusha.coreui.theme.Grey75
import my.noveldokusha.coreui.theme.Grey400
import my.noveldokusha.coreui.theme.Grey1000
import my.noveldokusha.coreui.theme.Error300
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
    // Позиция полосы источника: кромка обложки (OnCover) или плашка под обложкой (BelowCover)
    sourceStripPosition: SourceStripPosition = SourceStripPosition.BelowCover,
    selectedBooks: Map<String, Boolean> = emptyMap(),
    isSelectionMode: Boolean = false,
    pendingRemoval: Set<String> = emptySet(),
    gridState: LazyGridState = rememberLazyGridState(),
    // bookUrl → переведённое название новеллы (берётся из BookTranslation).
    // Пустая/отсутствующая запись = показываем оригинал.
    translatedTitles: Map<String, String> = emptyMap(),
) {
    if (list.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Book,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_books_in_library),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
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
                    val notReadCount = (it.chaptersCount - it.chaptersReadCount).coerceAtLeast(0)
                    BookImageButtonView(
                        title = translatedTitles[it.book.url]?.takeIf { t -> t.isNotBlank() } ?: it.book.title,
                        coverImageModel = rememberResolvedBookImagePath(
                            bookUrl = it.book.url,
                            imagePath = it.book.coverImageUrl
                        ),
                        onClick = { onClick(it) },
                        onLongClick = { onLongClick(it) },
                        sourceStripUnreadCount = notReadCount,
                        sourceStripSourceName = getSourceName(it.book.url),
                        sourceStripOnCover = sourceStripPosition == SourceStripPosition.OnCover,
                        topLeftBadge = {
                            // Маленький полупрозрачный «бабл» — иконка типа контента читается на любой обложке
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(it.book.contentType.toContentTypeBadgeIcon()),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        topRightBadge = { BookRatingBadge(rating = it.book.rating) },
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
}


