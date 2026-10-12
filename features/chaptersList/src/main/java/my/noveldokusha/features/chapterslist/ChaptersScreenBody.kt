package my.noveldokusha.features.chapterslist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
    showTranslateButton: Boolean,
    onTranslateClick: () -> Unit,
    onClearTranslationClick: () -> Unit,
    onResumeReading: () -> Unit,
    onOpenInBrowser: (url: String) -> Unit,
    onDownloadAllChapters: () -> Unit,
    onDownloadNext100Chapters: () -> Unit,
    onChapterClick: (chapter: ChapterWithContext) -> Unit,
    onChapterLongClick: (chapter: ChapterWithContext) -> Unit,
    onChapterDownload: (chapter: ChapterWithContext) -> Unit,
    onPauseDownload: (chapter: ChapterWithContext) -> Unit,
    onResumeDownload: (chapter: ChapterWithContext) -> Unit,
    onCancelDownload: (chapter: ChapterWithContext) -> Unit,
    onPullRefresh: () -> Unit,
    onCoverLongClick: () -> Unit,
    onGlobalSearchClick: (input: String, contentType: String) -> Unit,
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

    // Список элементов LazyColumn: главы + заголовки групп по volume.
    // Порядок глав не меняется, заголовки вставляются только на границе групп.
    val chapterListItems by remember { derivedStateOf { buildChapterListItems(state.chapters) } }

    val lastReadChapterIndex = remember(state.book.value.lastReadChapter, chapterListItems) {
        val url = state.book.value.lastReadChapter ?: return@remember null
        lazyIndexForChapterUrl(chapterListItems, url).takeIf { it != -1 }
    }

    val readChapters by remember { derivedStateOf { state.chapters.count { it.chapter.read } } }

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
            // Диалог ищет главу по url; индекс LazyColumn считаем по списку
            // с заголовками групп (позиция диалога не учитывает их).
            onChapterSelected = { _, url ->
                coroutineScope.launch {
                    val index = lazyIndexForChapterUrl(chapterListItems, url)
                    if (index != -1) smoothScrollToIndex(index)
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
                    // Referer обложки: у Body нет доступа к ChaptersViewModel — считаем через scraper.
                    coverReferer = scraper.coverReferer(state.book.value.url),
                    genres = state.genres.value,
                    rating = state.rating.value,
                    status = state.status.value,
                    lastUpdateDate = state.lastUpdateDate.value,
                    sourceCatalogName = if (state.sourceCatalogNameStrRes.value == 0) {
                        val source = scraper.getCompatibleSource(state.book.value.url)
                        source?.name ?: stringResource(R.string.invalid_source)
                    } else {
                        stringResource(id = state.sourceCatalogNameStrRes.value ?: R.string.invalid_source)
                    },
                    numberOfChapters = state.chapters.size,
                    readChapters = readChapters,
                    paddingValues = innerPadding,
                    modifier = Modifier,
                    translatedTitle = translatedTitle,
                    translatedDescription = translatedDescription,
                    isTranslating = isTranslating,
                    showTranslateButton = showTranslateButton,
                    onTranslateClick = onTranslateClick,
                    onClearTranslationClick = onClearTranslationClick,
                    onCoverLongClick = onCoverLongClick,
                    onGlobalSearchClick = onGlobalSearchClick,
                    onResumeReading = onResumeReading,
                    onOpenInBrowser = onOpenInBrowser,
                    onDownloadAllChapters = onDownloadAllChapters,
                    onDownloadNext100Chapters = onDownloadNext100Chapters,
                    onScrollToLastRead = onScrollToLastRead,
                    onScrollToChapter = { showGoToChapterDialog = true },
                    bookCategory = bookCategory,
                    categories = categories,
                    onCategoryClick = onCategoryClick,
                )
            }

            chapterListItems.forEach { entry ->
                when (entry) {
                    // Заголовок сезона/тома: прилипает к верху при скролле группы.
                    is ChapterListItem.VolumeHeader -> stickyHeader(key = entry.key) {
                        ChapterVolumeHeader(volume = entry.volume)
                    }

                    is ChapterListItem.Chapter -> item(
                        key = "_" + entry.data.chapter.url,
                        contentType = { 1 }
                    ) {
                        ChaptersScreenChapterItem(
                            chapterWithContext = entry.data,
                            translatedTitle = state.translatedChapterTitles.value[entry.data.chapter.url],
                            chapterSize = state.chapterSizes.value[entry.data.chapter.url],
                            downloadUi = state.chapterDownloads.value[entry.data.chapter.url],
                            selected = state.selectedChaptersUrl.containsKey(entry.data.chapter.url),
                            isLocalSource = state.isLocalSource.value,
                            showCancelAction = state.isVideoBook.value,
                            highlighted = entry.data.chapter.url == highlightedChapterUrl,
                            onClick = { onChapterClick(entry.data) },
                            onLongClick = { onChapterLongClick(entry.data) },
                            onDownload = { onChapterDownload(entry.data) },
                            onPauseDownload = { onPauseDownload(entry.data) },
                            onResumeDownload = { onResumeDownload(entry.data) },
                            onCancelDownload = { onCancelDownload(entry.data) }
                        )
                    }
                }
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

/** Элемент списка глав: глава либо заголовок группы (сезон/том из volume). */
internal sealed interface ChapterListItem {
    /** Заголовок группы. Ключ содержит порядковый номер: volume может повторяться. */
    data class VolumeHeader(val volume: String, val key: String) : ChapterListItem

    data class Chapter(val data: ChapterWithContext) : ChapterListItem
}

/**
 * Группировка глав по volume с сохранением порядка входного списка.
 * Заголовок ставится только на границе группы: у глав с null/пустым volume
 * заголовка нет, у подряд идущих глав с одинаковым volume — один общий.
 */
internal fun buildChapterListItems(chapters: List<ChapterWithContext>): List<ChapterListItem> {
    val items = ArrayList<ChapterListItem>(chapters.size)
    val headerOrdinals = HashMap<String, Int>()
    var previousVolume: String? = null
    for (chapter in chapters) {
        val volume = chapter.chapter.volume?.takeIf { it.isNotBlank() }
        if (volume != null && volume != previousVolume) {
            val ordinal = (headerOrdinals[volume] ?: 0) + 1
            headerOrdinals[volume] = ordinal
            items.add(ChapterListItem.VolumeHeader(volume = volume, key = "vol_${volume}_$ordinal"))
        }
        previousVolume = volume
        items.add(ChapterListItem.Chapter(chapter))
    }
    return items
}

/**
 * Индекс элемента LazyColumn для главы с данным url
 * (позиция 0 занимает хедер книги); -1, если глава не найдена.
 */
internal fun lazyIndexForChapterUrl(items: List<ChapterListItem>, url: String): Int {
    val index = items.indexOfFirst { it is ChapterListItem.Chapter && it.data.chapter.url == url }
    return if (index == -1) -1 else index + 1
}

/**
 * Заголовок группы глав. Непрозрачный фон — при sticky главы не просвечивают.
 * Визуально отделён от списка (капитель + разделитель), чтобы не читался
 * как обычная неактивная глава. Отступ старта (24.dp) выровнен с названием главы.
 */
@Composable
private fun ChapterVolumeHeader(volume: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = volume.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}