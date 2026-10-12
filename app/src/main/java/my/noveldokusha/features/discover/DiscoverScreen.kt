@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package my.noveldokusha.features.discover

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.coreui.components.ImageView

private val RAIL_CARD_WIDTH = 99.dp
private val RAIL_CARD_COMPACT_WIDTH = 87.dp

/**
 * Redesign home page (prototype pages/discover) — the NEW homepage.
 * Old homepage = Library tab; this screen is the new editorial home.
 */
@Composable
fun DiscoverScreen(
    onSwitchTab: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTheme: () -> Unit,
    viewModel: DiscoverViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val content = state.content
    var showFeedSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        DiscoverTopBar(onOpenSearch = onOpenSearch, onOpenTheme = onOpenTheme)

        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Bottom clearance for the docked bottom-nav bar + system navigation insets.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = 96.dp + androidx.compose.foundation.layout.WindowInsets.navigationBars
                    .asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            // — Intro (prototype .discover-intro) —
            item(key = "intro") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("YOUR READING DESK", startPadding = 0)
                        Text(
                            text = "A story for\nthe in-between.",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 29.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Thoughtful finds from the sources you follow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Prototype: the quiet-round button opens the homepage feed settings.
                    QuietRoundButton(onClick = { showFeedSheet = true }) {
                        Text(
                            text = "⇄",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // — Category shortcuts (prototype .feed-tabs, repurposed):
            // tap a category to jump to its catalog row below —
            item(key = "feed-shortcuts") {
                FeedShortcutsRow(
                    categories = state.sections.map { it.feed },
                    onSelect = { feed ->
                        scope.launch {
                            listState.animateScrollToItem(feedSectionIndex(state, feed))
                        }
                    },
                )
            }

            // — Featured story (prototype .featured-story) —
            val featured = content?.featured
            if (featured != null) {
                item(key = "featured") {
                    FeaturedStoryCard(
                        book = featured,
                        onClick = { openChapters(context, featured) },
                    )
                }
            }

            // — Continue reading —
            item(key = "sec-continue") {
                SectionHead(
                    eyebrow = "YOUR CURRENT CHAPTERS",
                    title = "Continue reading",
                    action = "See all",
                    onAction = { onSwitchTab(TAB_LIBRARY) },
                )
            }
            val continueReading = content?.continueReading.orEmpty()
            if (continueReading.isEmpty()) {
                item(key = "continue-empty") {
                    NovelaEmptyState(
                        title = "Your next chapter starts here",
                        body = "Open a story from your library to keep reading.",
                    )
                }
            } else {
                item(key = "continue-rail") {
                    BookRail(books = continueReading, compact = true)
                }
            }

            // — Browse genres —
            item(key = "sec-browse") {
                SectionHead(
                    eyebrow = "BROWSE",
                    title = "Find a new reading mood",
                    action = "All genres",
                    onAction = { onSwitchTab(TAB_FINDER) },
                )
            }

            // — Homepage feeds: ALL configured categories at once, one source's
            // real catalog per category row (For you / Trending / Latest / Recently updated) —
            state.sections.forEach { section ->
                item(key = "feedsec-" + section.feed.name) {
                    FeedCatalogSection(
                        section = section,
                        onOpenCatalog = section.sourceBaseUrl?.let { url ->
                            {
                                context.startActivity(
                                    my.noveldokusha.sourceexplorer.SourceCatalogActivity.IntentData(
                                        context,
                                        url,
                                    )
                                )
                            }
                        },
                        onRetry = {
                            section.sourceBaseUrl?.let(viewModel::retryRail)
                        },
                    )
                }
            }

            // — Trending this week —
            if (!content?.trending.isNullOrEmpty()) {
                item(key = "sec-trending") {
                    SectionHead(
                        eyebrow = "THIS WEEK · ACROSS SOURCES",
                        title = "Trending this week",
                        action = "Top 100 ↗",
                        onAction = { onSwitchTab(TAB_FINDER) },
                    )
                    BookRail(books = content.trending)
                }
            }

            // — Because you read X —
            val byLabel = content?.becauseYouReadLabel
            if (byLabel != null && !content.becauseYouRead.isNullOrEmpty()) {
                item(key = "sec-because") {
                    SectionHead(
                        eyebrow = byLabel.uppercase(),
                        title = "More like it",
                        action = "See all",
                        onAction = { onSwitchTab(TAB_FINDER) },
                    )
                    BookRail(books = content.becauseYouRead, compact = true)
                }
            }

            // — Picked for you: real per-source catalog rails above —

            // — New chapters, quietly arriving —
            item(key = "sec-updates") {
                SectionHead(
                    eyebrow = "FROM YOUR SHELF",
                    title = "New chapters, quietly arriving",
                    action = "Your shelf",
                    onAction = { onSwitchTab(TAB_LIBRARY) },
                )
            }
            val updates = content?.updates.orEmpty()
            if (updates.isEmpty()) {
                item(key = "updates-empty") {
                    NovelaEmptyState(
                        title = "All caught up",
                        body = "New chapter alerts from followed sources will appear here.",
                    )
                }
            } else {
                item(key = "updates-card") {
                    UpdatesCard(
                        entries = updates,
                        onOpen = { openChapters(context, it) },
                    )
                }
            }

            // — Popular across sources —
            if (!content?.popular.isNullOrEmpty()) {
                item(key = "sec-popular") {
                    SectionHead(
                        eyebrow = "POPULAR ACROSS SOURCES",
                        title = "Well-loved, wherever you read",
                        action = "Catalog",
                        onAction = { onSwitchTab(TAB_FINDER) },
                    )
                    BookRail(books = content.popular, compact = true)
                }
            }

            // — Finished, not forgotten —
            if (!content?.finished.isNullOrEmpty()) {
                item(key = "sec-finished") {
                    SectionHead(
                        eyebrow = "A LITTLE DIFFERENT",
                        title = "Finished, not forgotten",
                        action = "Browse",
                        onAction = { onSwitchTab(TAB_FINDER) },
                    )
                    BookRail(books = content.finished, compact = true)
                }
            }

            // — Manage sources card (prototype .manage-sources-card) —
            item(key = "manage-sources") {
                ManageSourcesCard(onClick = { onSwitchTab(TAB_FINDER) })
            }

            item(key = "footnote") {
                Text(
                    text = "Independent sources · Your library stays yours",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    // — Homepage feed settings sheet (prototype "Home feed & sources") —
    if (showFeedSheet) {
        DiscoverFeedSheet(
            state = state,
            onDismiss = { showFeedSheet = false },
            onSetFeedSource = viewModel::setFeedSource,
            onSetVisibleFeed = viewModel::setVisibleFeed,
        )
    }
}

/**
 * LazyColumn index of a homepage category row. Item order above the rows is:
 * intro, shortcuts, [featured], sec-continue, continue body, sec-browse.
 * Keep in sync with the item list above.
 */
private fun feedSectionIndex(state: DiscoverUiState, feed: HomeFeed): Int {
    val featured = state.content?.featured
    val prefix = (if (featured != null) 3 else 2) + 3
    val sectionIdx = state.sections.indexOfFirst { it.feed == feed }
    return if (sectionIdx < 0) prefix else prefix + sectionIdx
}

internal const val TAB_LIBRARY = 1
internal const val TAB_FINDER = 2

internal fun openChapters(context: android.content.Context, book: DiscoverBook) {
    val intent = my.noveldokusha.features.chapterslist.ChaptersActivity.IntentData(
        context,
        my.noveldokusha.feature.local_database.BookMetadata(
            title = book.title,
            url = book.url,
        )
    )
    context.startActivity(intent)
}

// — App bar (prototype .appbar: real NoveLA logo + title + icon buttons) —

@Composable
private fun DiscoverTopBar(onOpenSearch: () -> Unit, onOpenTheme: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Real NoveLA app logo (adaptive-icon foreground artwork).
                        Image(
                            painter = painterResource(my.noveldokusha.coreui.R.drawable.ic_logo),
                            contentDescription = "NoveLA",
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            text = stringResource(my.noveldokusha.strings.R.string.title_home),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            painter = painterResource(my.noveldokusha.coreui.R.drawable.ic_baseline_search_24),
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenTheme) {
                        Icon(
                            painter = painterResource(my.noveldokusha.R.drawable.ic_redesign_theme_24),
                            contentDescription = "Appearance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// — Small building blocks —

@Composable
internal fun Eyebrow(text: String, startPadding: Int = 16) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = startPadding.dp),
    )
}

@Composable
private fun QuietRoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun SectionHead(
    eyebrow: String,
    title: String,
    action: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 21.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Eyebrow(eyebrow, startPadding = 0)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun FeedShortcutsRow(
    categories: List<HomeFeed>,
    onSelect: (HomeFeed) -> Unit,
) {
    Column {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(categories) { feed ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .clickable { onSelect(feed) }
                        .padding(horizontal = 2.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = feed.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "  ↓",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun FeaturedStoryCard(book: DiscoverBook, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(208.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(0.61f)
                    .fillMaxSize()
                    .padding(start = 15.dp, end = 4.dp, top = 16.dp, bottom = 14.dp),
            ) {
                Text(
                    text = (book.sourceName?.uppercase() ?: "FROM YOUR SOURCES").let {
                        "CURRENTLY READING · $it"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        letterSpacing = 1.2.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 24.sp,
                        lineHeight = 25.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = book.status.ifBlank { "Chapter ${book.chaptersReadCount + 1} of ${book.chaptersCount}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                    ) {
                        Text(
                            text = "Continue the story  ↗",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(0.39f)
                    .fillMaxSize(),
            ) {
                val coverModel = rememberResolvedBookImagePath(
                    bookUrl = book.url,
                    imagePath = book.coverUrl,
                )
                ImageView(
                    imageModel = coverModel,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    error = my.noveldokusha.coreui.R.drawable.default_book_cover,
                )
            }
        }
    }
}

@Composable
internal fun BookRail(books: List<DiscoverBook>, compact: Boolean = false) {
    val context = LocalContext.current
    val cardWidth = if (compact) RAIL_CARD_COMPACT_WIDTH else RAIL_CARD_WIDTH
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(books, key = { it.url }) { book ->
            DiscoverBookCard(book = book, width = cardWidth)
        }
    }
}

@Composable
internal fun DiscoverBookCard(book: DiscoverBook, width: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(width)
            .clickable { openChapters(context, book) },
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxWidth()
                .height(width * 1.45f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val coverModel = rememberResolvedBookImagePath(
                bookUrl = book.url,
                imagePath = book.coverUrl,
            )
            ImageView(
                imageModel = coverModel,
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                error = my.noveldokusha.coreui.R.drawable.default_book_cover,
            )
            if (book.hasProgress && book.chaptersCount > 0) {
                val fraction = (book.chaptersReadCount.toFloat() / book.chaptersCount).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp, vertical = 6.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp),
        )
        val meta = listOfNotNull(
            book.rating.takeIf { it.isNotBlank() }?.let { "★ ${book.rating}" },
            book.sourceName,
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

/**
 * One homepage category row: the category label (For you / Trending / …),
 * the single source chosen for it, and that source's real catalog rail —
 * or a clear "not set up" hint when no source is chosen yet.
 */
@Composable
private fun FeedCatalogSection(
    section: FeedSectionState,
    onOpenCatalog: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 21.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow(section.feed.label.uppercase(), startPadding = 0)
                Text(
                    text = section.sourceName ?: "No source chosen",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                    color = if (section.sourceName != null) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onOpenCatalog != null) {
                Text(
                    text = "Catalog ↗",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onOpenCatalog)
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }
        val rail = section.rail
        when {
            rail == null -> Text(
                text = "Pick one source for this category in Home feed & sources.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )
            rail.loading -> RailPlaceholderRow()
            rail.error != null -> RailErrorRow(message = rail.error, onRetry = onRetry)
            rail.books.isEmpty() -> RailErrorRow(message = "No books found", onRetry = null)
            else -> BookRail(books = rail.books)
        }
    }
}

/** Skeleton rail shown while a source's catalog is fetching. */
@Composable
private fun RailPlaceholderRow() {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(4) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .width(RAIL_CARD_WIDTH)
                    .height(RAIL_CARD_WIDTH * 1.45f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun RailErrorRow(message: String, onRetry: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            Text(
                text = "Retry",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onRetry)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
internal fun NovelaEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun UpdatesCard(entries: List<UpdateEntry>, onOpen: (DiscoverBook) -> Unit) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                val book = entry.book
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(book) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 35.dp, height = 47.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        val coverModel = rememberResolvedBookImagePath(
                            bookUrl = book.url,
                            imagePath = book.coverUrl,
                        )
                        ImageView(
                            imageModel = coverModel,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            error = my.noveldokusha.coreui.R.drawable.default_book_cover,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 9.dp),
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${book.sourceName ?: "Library"} · Chapter ${book.chaptersReadCount + 1} is ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "+${entry.unread}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
                if (index != entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageSourcesCard(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 21.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(27.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "＋",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = "Find your next source",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Built-in catalogues, local files and Lua extensions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
