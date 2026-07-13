package my.noveldokusha.globalsourcesearch

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.BookImageButtonView
import my.noveldokusha.coreui.components.BookTitlePosition
import my.noveldokusha.coreui.composableActions.ListLoadWatcher
import my.noveldokusha.coreui.modifiers.bounceOnPressed
import my.noveldokusha.coreui.states.IteratorState
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.PreviewThemes
import my.noveldokusha.data.CatalogItem
import my.noveldokusha.mappers.mapToBookMetadata
import my.noveldokusha.core.Response
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.fixtures.fixturesCatalogList
import my.noveldokusha.feature.local_database.BookMetadata

@Composable
internal fun GlobalSourceSearchScreenBody(
    listSources: List<SourceResults>,
    contentPadding: PaddingValues,
    onBookClick: (book: BookMetadata) -> Unit
) {
    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(top = 12.dp, bottom = 240.dp)
    ) {
        items(listSources) { entry ->
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = entry.source.catalog.name
                        ?: if (entry.source.catalog.nameStrId != 0)
                            stringResource(id = entry.source.catalog.nameStrId)
                        else "Unknown",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(start = 16.dp, bottom = 8.dp)
                )
                SourceListView(
                    list = entry.fetchIterator.list,
                    loadState = entry.fetchIterator.state,
                    error = entry.fetchIterator.error?.message,
                    onBookClick = onBookClick,
                    onLoadNext = { entry.fetchIterator.fetchNext() },
                )
            }
        }
    }
}

@Composable
private fun SourceListView(
    list: List<BookResult>,
    loadState: IteratorState,
    error: String?,
    onBookClick: (book: BookMetadata) -> Unit,
    onLoadNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState()

    ListLoadWatcher(listState = state, loadState = loadState, onLoadNext = onLoadNext)

    LazyRow(
        state = state,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 30.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .animateContentSize()
            .fillMaxWidth(),
    ) {
        items(list) {
            BookImageButtonView(
                title = it.title,
                coverImageModel = rememberResolvedBookImagePath(
                    bookUrl = it.url,
                    imagePath = it.coverImageUrl
                ),
                onClick = { onBookClick(it.mapToBookMetadata()) },
                onLongClick = { },
                modifier = Modifier
                    .width(130.dp),
                bookTitlePosition = BookTitlePosition.Outside
            )
        }

        item {
            fun Modifier.topPadding() = padding(top = (130 / 1.45f).dp - 8.dp)

            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                when (loadState) {
                    IteratorState.LOADING -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(36.dp)
                    )

                    IteratorState.CONSUMED -> when {
                        error != null -> Text(
                            text = stringResource(R.string.error_loading),
                            color = MaterialTheme.colorScheme.error,
                            modifier = if (list.isEmpty()) Modifier else Modifier.topPadding()
                        )

                        list.isEmpty() -> Text(
                            text = stringResource(R.string.no_results_found),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        else -> Text(
                            text = stringResource(R.string.no_more_results),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.topPadding()
                        )
                    }

                    IteratorState.IDLE -> {}
                }
            }
        }
    }
}

@PreviewThemes
@Composable
private fun PreviewView() {


    val list = fixturesCatalogList().mapIndexed { index, source ->
        val (catalog, books) = CatalogItem(
            catalog = source,
            pinned = false
        ) to (0..5).map {
            BookResult(
                title = "Book $it",
                url = ""
            )
        }

        val sr = SourceResults(catalog, "", rememberCoroutineScope())
        when (index) {
            0 -> {
                sr.fetchIterator.state = IteratorState.CONSUMED
            }

            1 -> {
                sr.fetchIterator.list.addAll(books.take(2))
                sr.fetchIterator.state = IteratorState.CONSUMED
            }

            2 -> {
                sr.fetchIterator.error = Response.Error("Error here", Exception())
            }

            else -> {
                sr.fetchIterator.list.addAll(books.take(2))
                sr.fetchIterator.state = IteratorState.CONSUMED
                sr.fetchIterator.error = Response.Error("Error here", Exception())
            }
        }
        sr
    }

    InternalTheme {
        GlobalSourceSearchScreenBody(
            listSources = list,
            onBookClick = {},
            contentPadding = PaddingValues(),
        )
    }
}
