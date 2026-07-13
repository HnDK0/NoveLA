package my.noveldokusha.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.R
import my.noveldokusha.coreui.composableActions.ListGridLoadWatcher
import my.noveldokusha.coreui.states.IteratorState
import my.noveldokusha.core.Response
import my.noveldokusha.core.appPreferences.ListLayoutMode
import my.noveldokusha.core.domain.CloudfareVerificationBypassFailedException
import my.noveldokusha.core.domain.WebViewCookieManagerInitializationFailedException
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.feature.local_database.BookMetadata

@Composable
fun BooksVerticalView(
    list: List<BookMetadata>,
    state: LazyGridState,
    error: Response.Error?,
    loadState: IteratorState,
    layoutMode: ListLayoutMode,
    onLoadNext: () -> Unit,
    onBookClicked: (book: BookMetadata) -> Unit,
    onBookLongClicked: (bookItem: BookMetadata) -> Unit,
    onReload: () -> Unit = {},
    onCopyError: (String) -> Unit = {},
    onWebViewOpen: () -> Unit = {},
    // Количество колонок из общего preference BOOKS_GRID_COLUMNS (2..6, дефолт 3)
    gridColumns: Int = 3,
    innerPadding: PaddingValues = PaddingValues(),
) {

    val columns by remember(layoutMode, gridColumns) {
        derivedStateOf {
            when (layoutMode) {
                ListLayoutMode.VerticalList -> GridCells.Fixed(1)
                ListLayoutMode.VerticalGrid -> GridCells.Fixed(gridColumns.coerceIn(2, 6))
            }
        }
    }

    ListGridLoadWatcher(
        listState = state,
        loadState = loadState,
        onLoadNext = onLoadNext
    )

    LazyVerticalGrid(
        columns = columns,
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 160.dp)
    ) {
        items(list) {
            when (layoutMode) {
                ListLayoutMode.VerticalList -> MyButton(
                    text = it.title,
                    onClick = { onBookClicked(it) },
                    onLongClick = { onBookLongClicked(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                )
                ListLayoutMode.VerticalGrid -> BookImageButtonView(
                    title = it.title,
                    coverImageModel = rememberResolvedBookImagePath(
                        bookUrl = it.url,
                        imagePath = it.coverImageUrl
                    ),
                    onClick = { onBookClicked(it) },
                    onLongClick = { onBookLongClicked(it) }
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                when (loadState) {
                    IteratorState.LOADING -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.loading),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IteratorState.CONSUMED -> Text(
                        text = when {
                            list.isEmpty() -> stringResource(R.string.no_results_found)
                            else -> stringResource(R.string.no_more_results)
                        },
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    else -> Unit
                }
            }
        }

        when (error?.exception) {
            is WebViewCookieManagerInitializationFailedException -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ClickableOption(
                        title = stringResource(R.string.cloudfare_firewall_detected),
                        subtitle = stringResource(R.string.please_open_web_view_and_try_verify_then_reload),
                        onClick = onWebViewOpen,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            is CloudfareVerificationBypassFailedException -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ClickableOption(
                        title = stringResource(R.string.cloudfare_firewall_failed_bypass),
                        subtitle = stringResource(R.string.tried_but_failed_to_bypass_cloudfare_firewall),
                        onClick = onWebViewOpen,
                        modifier = Modifier.padding(12.dp),
                        isButton = true
                    )
                }
            }
            null -> Unit
        }

        if (error != null) item(span = { GridItemSpan(maxLineSpan) }) {
            ErrorView(error = error.message, onReload = onReload, onCopyError = onCopyError)
        }
    }
}