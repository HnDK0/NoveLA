package my.noveldokusha.libraryexplorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.appPreferences.SourceStripPosition
import my.noveldokusha.core.utils.refererFor
import my.noveldokusha.coreui.R
import my.noveldokusha.coreui.components.BookImageButtonView
import my.noveldokusha.coreui.components.BookRatingBadge
import my.noveldokusha.coreui.components.toContentTypeBadgeIcon
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.feature.local_database.BookWithContext

/**
 * Grid entry: either a full-span source group header or a book card.
 * Used when the library is sorted by source to render shelf dividers.
 */
private data class LibraryGridEntry(val header: String?, val book: BookWithContext?, val key: String)

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
    // When true (SOURCE sort), insert full-span shelf headers per source group.
    groupBySource: Boolean = false,
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
            contentPadding = PaddingValues(
                top = 4.dp,
                // Clear the docked bottom-nav bar + system navigation insets.
                bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                start = 4.dp,
                end = 4.dp,
            ),
        ) {
            // Shelf headers: O(n) single pass, recomputed only when the list changes.
            // (list is memoized upstream; getSourceName hits a ConcurrentHashMap cache.)
            val gridEntries = if (!groupBySource) {
                list.map { LibraryGridEntry(null, it, it.book.url) }
            } else {
                buildList {
                    var lastSource: String? = null
                    for (b in list) {
                        val s = getSourceName(b.book.url)
                        if (s != lastSource) {
                            add(LibraryGridEntry(s, null, "hdr_${s.lowercase()}"))
                            lastSource = s
                        }
                        add(LibraryGridEntry(null, b, b.book.url))
                    }
                }
            }
            items(
                items = gridEntries,
                key = { it.key },
                contentType = { if (it.header != null) "header" else "book" },
                span = { entry -> GridItemSpan(if (entry.header != null) maxLineSpan else 1) }
            ) { entry ->
                val header = entry.header
                if (header != null) {
                    // Prototype shelf divider: eyebrow-caps source name + hairline
                    Text(
                        text = header.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.4.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                    )
                } else {
                    val it = entry.book!!
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
                                sourceStripPosition = sourceStripPosition,
                                fadeInDurationMillis = 250,
                                topLeftBadge = {
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
        // Prefetch next images while scrolling to avoid decode spikes
        // ponytail: track all disposables — previous code leaked 4 of 5 prefetch requests
        val context = LocalContext.current
        val imageLoader = SingletonImageLoader.get(context)
        val appFileResolver = remember(context) { AppFileResolver(context) }
        LaunchedEffect(gridState, list) {
            val pendingPrefetch = mutableListOf<coil3.request.Disposable>()
            snapshotFlow {
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }.collect { lastVisibleIndex ->
                pendingPrefetch.forEach { it.dispose() }
                pendingPrefetch.clear()
                val prefetchCount = 5
                val startIndex = lastVisibleIndex + 1
                val endIndex = minOf(startIndex + prefetchCount, list.size)
                for (i in startIndex until endIndex) {
                    val book = list[i]
                    val request = ImageRequest.Builder(context)
                        .data(appFileResolver.resolvedBookImagePath(
                            bookUrl = book.book.url,
                            imagePath = book.book.coverImageUrl
                        ))
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .apply {
                            val url = book.book.coverImageUrl
                            val referer = url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::refererFor)
                            if (!referer.isNullOrEmpty()) {
                                httpHeaders(
                                    NetworkHeaders.Builder()
                                        .set("Referer", referer)
                                        .build()
                                )
                            }
                        }
                        .build()
                    pendingPrefetch.add(imageLoader.enqueue(request))
                }
            }
        }
    }
}


