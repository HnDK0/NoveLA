package my.noveldokusha.features.chapterslist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.components.ErrorView
import my.noveldokusha.chapterslist.R
import my.noveldokusha.feature.local_database.ChapterWithContext
import my.noveldokusha.scraper.Scraper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersScreenBody(
    state: ChaptersScreenState,
    lazyListState: LazyListState,
    innerPadding: PaddingValues,
    translatedTitle: String?,
    translatedDescription: String?,
    isTranslating: Boolean,
    onTranslateClick: () -> Unit,
    onClearTranslationClick: () -> Unit,
    onChapterClick: (chapter: ChapterWithContext) -> Unit,
    onChapterLongClick: (chapter: ChapterWithContext) -> Unit,
    onChapterDownload: (chapter: ChapterWithContext) -> Unit,
    onPullRefresh: () -> Unit,
    onCoverLongClick: () -> Unit,
    onGlobalSearchClick: (input: String) -> Unit,
    bookCategory: String,
    categories: () -> List<String>,
    onCategoryClick: () -> Unit,
    scraper: Scraper,
    modifier: Modifier = Modifier,
) {
    var isRefreshingDelayed by remember { mutableStateOf(state.isRefreshing.value) }
    LaunchedEffect(Unit) {
        snapshotFlow { state.isRefreshing.value }
            .distinctUntilChanged()
            .collectLatest {
                if (it) delay(200)
                isRefreshingDelayed = it
            }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    var highlightedChapterUrl by remember { mutableStateOf<String?>(null) }

    val scrollOffset = -350

    suspend fun smoothScrollToIndex(index: Int) {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val firstVisible = lazyListState.firstVisibleItemIndex
        val isNearby = index in (firstVisible - 5)..(firstVisible + visibleItems.size + 5)
        if (!isNearby) {
            val jumpTo = if (index > firstVisible) index - 3 else index + 3
            lazyListState.scrollToItem(jumpTo.coerceIn(0, lazyListState.layoutInfo.totalItemsCount - 1))
        }
        lazyListState.animateScrollToItem(index, scrollOffset)
    }

    val lastReadChapterIndex = remember(state.book.value.lastReadChapter, state.chapters.size) {
        val url = state.book.value.lastReadChapter ?: return@remember null
        val idx = state.chapters.indexOfFirst { it.chapter.url == url }
        if (idx == -1) null else idx + 1
    }

    val onScrollToLastRead: (() -> Unit)? = lastReadChapterIndex?.let { index ->
        {
            coroutineScope.launch {
                val url = state.book.value.lastReadChapter
                smoothScrollToIndex(index)
                highlightedChapterUrl = url
                delay(1500)
                highlightedChapterUrl = null
            }
        }
    }

    var showGoToChapterDialog by rememberSaveable { mutableStateOf(false) }

    if (showGoToChapterDialog) {
        GoToChapterDialog(
            chapters = state.chapters,
            onChapterSelected = { index, url ->
                coroutineScope.launch {
                    smoothScrollToIndex(index)
                    highlightedChapterUrl = url
                    delay(1500)
                    highlightedChapterUrl = null
                }
            },
            onDismiss = { showGoToChapterDialog = false }
        )
    }

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshingDelayed,
        onRefresh = onPullRefresh,
        state = pullToRefreshState,
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 300.dp),
        ) {
            item(
                key = "header",
                contentType = { 0 },
            ) {
                ChaptersScreenHeader(
                    bookState = state.book.value,
                    genres = state.genres.value,
                    sourceCatalogName = if (state.sourceCatalogNameStrRes.value == 0) {
                        val source = scraper.getCompatibleSource(state.book.value.url)
                        source?.name ?: stringResource(R.string.invalid_source)
                    } else {
                        stringResource(id = state.sourceCatalogNameStrRes.value ?: R.string.invalid_source)
                    },
                    numberOfChapters = state.chapters.size,
                    paddingValues = innerPadding,
                    modifier = Modifier,
                    translatedTitle = translatedTitle,
                    translatedDescription = translatedDescription,
                    isTranslating = isTranslating,
                    onTranslateClick = onTranslateClick,
                    onClearTranslationClick = onClearTranslationClick,
                    onCoverLongClick = onCoverLongClick,
                    onGlobalSearchClick = onGlobalSearchClick,
                    onScrollToLastRead = onScrollToLastRead,
                    onScrollToChapter = { showGoToChapterDialog = true },
                    bookCategory = bookCategory,
                    categories = categories,
                    onCategoryClick = onCategoryClick,
                )
            }

            items(
                items = state.chapters,
                key = { "_" + it.chapter.url },
                contentType = { 1 }
            ) {
                ChaptersScreenChapterItem(
                    chapterWithContext = it,
                    translatedTitle = state.translatedChapterTitles.value[it.chapter.url],
                    selected = state.selectedChaptersUrl.containsKey(it.chapter.url),
                    isLocalSource = state.isLocalSource.value,
                    highlighted = it.chapter.url == highlightedChapterUrl,
                    onClick = { onChapterClick(it) },
                    onLongClick = { onChapterLongClick(it) },
                    onDownload = { onChapterDownload(it) }
                )
            }

            if (state.error.value.isNotBlank()) item(
                key = "error",
                contentType = { 2 }
            ) {
                ErrorView(error = state.error.value)
            }
        }
    }
}