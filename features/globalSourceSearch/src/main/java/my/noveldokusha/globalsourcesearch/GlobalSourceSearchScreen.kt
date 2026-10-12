@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package my.noveldokusha.globalsourcesearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.coreui.components.ImageView
import my.noveldokusha.coreui.components.LibraryBadgeMaps
import my.noveldokusha.coreui.components.LibraryBadgeState
import my.noveldokusha.coreui.components.NovelaFilterChip
import my.noveldokusha.coreui.states.IteratorState
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.mappers.mapToBookMetadata
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.strings.R as StringsR

/**
 * Redesigned global search (prototype pages/finder search):
 * editorial page-heading + integrated search-box + live per-source result
 * sections rendered as compact rows — one flat LazyColumn, no nested scrolls.
 */
@Composable
internal fun GlobalSourceSearchScreen(
    searchInput: String,
    listSources: List<SourceResults>,
    onSearchInputChange: (String) -> Unit,
    onSearchInputSubmit: (String) -> Unit,
    onBookClick: (book: BookMetadata) -> Unit,
    onPressBack: () -> Unit,
    getLibraryBadge: (String, String) -> LibraryBadgeState? = { _, _ -> null },
    libraryBadgeData: State<LibraryBadgeMaps> = androidx.compose.runtime.mutableStateOf(LibraryBadgeMaps()),
    selectedScope: String = "",
    onScopeChange: (String) -> Unit = {},
) {
    // Reactive progress: how many sources finished answering (state reads observed).
    val finishedCount by remember {
        derivedStateOf { listSources.count { it.fetchIterator.hasFinished } }
    }
    val totalCount = listSources.size
    val allDone = finishedCount >= totalCount && totalCount > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Edge-to-edge hardening: keep chrome clear of the status bar and the
            // system navigation bar on every device (gesture bar, 3-button nav,
            // cutouts); imePadding lifts the search box above the keyboard.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        // — Page heading (prototype .page-heading) —
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPressBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(StringsR.string.go_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (allDone) stringResource(StringsR.string.search_results_title)
                    else stringResource(StringsR.string.global_search_eyebrow, finishedCount, totalCount.coerceAtLeast(1)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = searchInput.ifBlank { stringResource(StringsR.string.global_search) },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // — Search box (prototype .search-box: hairline border, surface, ↵ submit) —
        SearchBox(
            query = searchInput,
            onQueryChange = onSearchInputChange,
            onSubmit = { onSearchInputSubmit(searchInput) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        // — Content scope chips —
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            val scopeOptions = listOf(
                "" to stringResource(StringsR.string.all_categories),
                "novel" to stringResource(StringsR.string.content_type_novel),
                "manga" to stringResource(StringsR.string.content_type_manga),
                "video" to stringResource(StringsR.string.content_type_video),
            )
            scopeOptions.forEach { (value, label) ->
                NovelaFilterChip(
                    selected = selectedScope == value,
                    onClick = { onScopeChange(value) },
                    label = label,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // — Thin progress hairline while sources answer —
        if (!allDone && totalCount > 0) {
            LinearProgressIndicator(
                progress = { finishedCount.toFloat() / totalCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )

        // — Live result sections: one horizontal scroll row per source —
        // (prototype cover-rail pattern: every source reachable at a glance,
        // no scrolling past a wall of results to reach the next source)
        GlobalSearchResultsBody(
            listSources = listSources,
            onBookClick = onBookClick,
            getLibraryBadge = getLibraryBadge,
            libraryBadgeData = libraryBadgeData,
        )
    }
}

/**
 * Prototype `.search-box`: 42dp rounded-12 field, hairline border, surface fill,
 * leading stroke icon, trailing accent "↵" submit square.
 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Icon(
                painter = painterResource(my.noveldokusha.coreui.R.drawable.ic_baseline_search_24),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(StringsR.string.search_all_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(9.dp))
            // Accent submit square (prototype .search-submit)
            Surface(
                onClick = onSubmit,
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(25.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↵",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Per-source result sections: an eyebrow header + one horizontal scrolling
 * cover-rail per source (prototype `.cover-rail`). Every source stays reachable
 * at a glance — no scrolling past a wall of results to find the next source.
 * The trailing rail card is the pagination trigger for that source.
 */
@Composable
private fun GlobalSearchResultsBody(
    listSources: List<SourceResults>,
    onBookClick: (BookMetadata) -> Unit,
    getLibraryBadge: (String, String) -> LibraryBadgeState?,
    libraryBadgeData: State<LibraryBadgeMaps>,
) {
    // Reactive dependency: library badge updates recompose rows.
    libraryBadgeData.value

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        listSources.forEachIndexed { entryIndex, entry ->
            val sourceKey = entry.source.catalog.id
            item(key = "hdr_$sourceKey", contentType = { "header" }) {
                SectionHeader(entry)
            }
            item(key = "row_$sourceKey", contentType = { "rail" }) {
                SourceResultRail(
                    entry = entry,
                    onBookClick = onBookClick,
                    getLibraryBadge = getLibraryBadge,
                )
            }
        }
    }
}

/** Header for one source row: eyebrow-caps name + results-so-far count. */
@Composable
private fun SectionHeader(entry: SourceResults) {
    val name = entry.source.catalog.name
        ?: if (entry.source.catalog.nameStrId != 0)
            stringResource(id = entry.source.catalog.nameStrId)
        else stringResource(StringsR.string.sources)
    val results = entry.fetchIterator.list.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Text(
            text = name.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (results > 0) {
            Text(
                text = "$results",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** Horizontal cover rail for one source + trailing status/pagination card. */
@Composable
private fun SourceResultRail(
    entry: SourceResults,
    onBookClick: (BookMetadata) -> Unit,
    getLibraryBadge: (String, String) -> LibraryBadgeState?,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // ponytail: list is a SnapshotStateList; size read is observed -> pages append live.
        val bookCount = entry.fetchIterator.list.size
        items(count = bookCount, key = { "${entry.source.catalog.id}_${entry.fetchIterator.list[it].url}" }) { bookIndex ->
            val book = entry.fetchIterator.list[bookIndex]
            val badge = getLibraryBadge(book.url, book.title)
            GlobalSearchResultCard(
                title = book.title,
                coverImage = book.coverImageUrl,
                bookUrl = book.url,
                rating = book.rating,
                inLibrary = badge != null,
                onClick = { onBookClick(book.mapToBookMetadata()) },
            )
        }
        item(key = "${entry.source.catalog.id}_status") {
            RailStatusCard(entry)
        }
    }
}

/** Compact cover card (prototype `.book-card`): cover, title below, rating meta. */
@Composable
private fun GlobalSearchResultCard(
    title: String,
    coverImage: String,
    bookUrl: String,
    rating: String?,
    inLibrary: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            val coverModel = rememberResolvedBookImagePath(
                bookUrl = bookUrl,
                imagePath = coverImage,
            )
            ImageView(
                imageModel = coverModel,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 136.dp)
                    .clip(RoundedCornerShape(8.dp)),
                error = my.noveldokusha.coreui.R.drawable.default_book_cover,
            )
            if (inLibrary) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopEnd),
                )
            }
            if (!rating.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                    modifier = Modifier
                        .padding(5.dp)
                        .align(Alignment.BottomStart),
                ) {
                    Text(
                        text = "★ $rating",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
        )
    }
}

/**
 * Trailing rail card = per-source status + pagination trigger: when it scrolls
 * into the viewport the next page is requested.
 */
@Composable
private fun RailStatusCard(entry: SourceResults) {
    val state = entry.fetchIterator.state
    val error = entry.fetchIterator.error
    val list = entry.fetchIterator.list

    LaunchedEffect(entry.source.catalog.id, list.size) {
        if (state == IteratorState.IDLE && error == null) {
            entry.fetchIterator.fetchNext()
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
        modifier = Modifier.size(width = 96.dp, height = 136.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
            when (state) {
                IteratorState.LOADING -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(StringsR.string.loading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                IteratorState.CONSUMED -> if (error != null) {
                    Text(
                        text = stringResource(StringsR.string.error_loading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                } else if (list.isEmpty()) {
                    Text(
                        text = stringResource(StringsR.string.no_results_found),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = "· · ·",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IteratorState.IDLE -> {}
            }
        }
    }
}

private fun PreviewBodyPlaceholder() {
    // Kept for parity with previews in other screens; not rendered in release.
}

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun PreviewView() {
    InternalTheme {
        GlobalSourceSearchScreen(
            searchInput = "Lord of the Mysteries",
            listSources = listOf(),
            onSearchInputChange = { },
            onSearchInputSubmit = { },
            onBookClick = { },
            onPressBack = { },
        )
    }
}
