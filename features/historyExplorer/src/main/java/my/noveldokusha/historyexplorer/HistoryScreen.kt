package my.noveldokusha.historyexplorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import my.noveldokusha.core.rememberResolvedBookImagePath
import my.noveldokusha.core.utils.refererFor
import my.noveldokusha.feature.local_database.BookMetadata
import my.noveldokusha.navigation.NavigationRouteViewModel
import my.noveldokusha.strings.R as StringsR
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onClose: (() -> Unit)? = null,
    navigationRouteViewModel: NavigationRouteViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
) {
    val uiState by historyViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val lastClickTime = remember { mutableStateOf(0L) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    // When hosted as the redesign overlay page, show a back arrow
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(StringsR.string.go_back)
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = stringResource(id = StringsR.string.title_history),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    if (uiState is HistoryUiState.Content) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                stringResource(StringsR.string.options_panel)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(Icons.Filled.DeleteSweep, stringResource(StringsR.string.delete))
                                },
                                text = { Text(stringResource(StringsR.string.clear_all_history)) },
                                onClick = {
                                    showMenu = false
                                    historyViewModel.deleteAll()
                                }
                            )
                        }
                    }
                }
            )
        },
        content = { innerPadding ->
            when (val state = uiState) {
                is HistoryUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(StringsR.string.loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is HistoryUiState.Empty -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    // Prototype .empty-state: dashed border, serif title, muted body
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 18.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 18.dp, vertical = 36.dp),
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(StringsR.string.no_history_yet),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                is HistoryUiState.Content -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    // Bottom clearance for the system navigation bar / gesture bar.
                    contentPadding = PaddingValues(
                        bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // — Editorial page heading (prototype .page-heading) —
                    item(key = "heading") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                text = stringResource(StringsR.string.history_eyebrow).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.4.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = StringsR.string.title_history),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(StringsR.string.history_page_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.items, key = { it.bookUrl }) { item ->
                        Column {
                            HistoryItemCard(
                                item = item,
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastClickTime.value >= 200L) {
                                        lastClickTime.value = now
                                        navigationRouteViewModel.chapters(
                                            context = context,
                                            bookMetadata = BookMetadata(
                                                title = item.bookTitle,
                                                url = item.bookUrl,
                                                coverImageUrl = item.bookCoverUrl,
                                            )
                                        ).let(context::startActivity)
                                    }
                                },
                                onLongClick = { historyViewModel.delete(item.bookUrl) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                thickness = 1.dp,
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemCard(
    item: HistoryItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val coverImageModel = rememberResolvedBookImagePath(item.bookUrl, item.bookCoverUrl)
        val referer = (coverImageModel as? String)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(::refererFor)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverImageModel)
                .apply {
                    if (!referer.isNullOrEmpty()) {
                        httpHeaders(
                            NetworkHeaders.Builder()
                                .set("Referer", referer)
                                .build()
                        )
                    }
                }
                .build(),
            contentDescription = item.bookTitle,
            modifier = Modifier
                .size(width = 48.dp, height = 72.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.bookTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!item.sourceName.isNullOrBlank()) {
                Text(
                    text = item.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            if (!item.lastReadChapterTitle.isNullOrBlank()) {
                Text(
                    text = item.lastReadChapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }

            if (item.totalChapters > 0) {
                LinearProgressIndicator(
                    progress = { item.readChapters.toFloat() / item.totalChapters.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.readChapters}/${item.totalChapters} · ${if (item.totalChapters > 0) item.readChapters * 100 / item.totalChapters else 0}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = relativeTime(item.lastReadEpochTimeMilli),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun relativeTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        minutes < 1 -> stringResource(StringsR.string.time_just_now)
        minutes < 60 -> stringResource(StringsR.string.time_minutes_ago, minutes)
        hours < 24 -> stringResource(StringsR.string.time_hours_ago, hours)
        days < 7 -> stringResource(StringsR.string.time_days_ago, days)
        days < 30 -> stringResource(StringsR.string.time_weeks_ago, days / 7)
        days < 365 -> stringResource(StringsR.string.time_months_ago, days / 30)
        else -> stringResource(StringsR.string.time_years_ago, days / 365)
    }
}


