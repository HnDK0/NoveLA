package my.noveldokusha.features.searchsettings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.scraper.SourceInterface

/**
 * Global-search source priority (settings): a drag-to-reorder list of sources.
 * Global search renders one row per source, ordered by this list — drag
 * wtr-lab and 69shuba to the top and they become the first two result rows.
 */
data class PrioritySource(
    val baseUrl: String,
    val name: String,
    val language: String,
)

@HiltViewModel
class SearchPriorityViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val scraper: Scraper,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sourcesTick = MutableStateFlow(0)

    private val namesCache = HashMap<String, String>()

    /** All installed catalog sources, ordered by the user's dragged priority. */
    val sources: StateFlow<List<PrioritySource>> = MutableStateFlow<List<PrioritySource>>(emptyList()).also { out ->
        viewModelScope.launch {
            combine(sourcesTick) { _ -> buildList() }.collect { out.value = it }
        }
    }

    private fun buildList(): List<PrioritySource> {
        val natural = scraper.sourcesList
            .filterIsInstance<SourceInterface.Catalog>()
            .filter { !it.isLocalSource }
            .map { source ->
                PrioritySource(
                    baseUrl = source.baseUrl.trimEnd('/'),
                    name = namesCache.getOrPut(source.baseUrl) { source.resolveName(context) },
                    language = source.language?.iso639_1.orEmpty(),
                )
            }
            .sortedBy { it.name.lowercase() }
            .distinctBy { it.baseUrl }
        val priority = appPreferences.GLOBAL_SEARCH_SOURCE_ORDER.value
        // Priority entries first (in dragged order), then the rest alphabetically.
        val byUrl = natural.associateBy { it.baseUrl }
        val prioritized = priority.mapNotNull { byUrl[it] }
        val prioritySet = priority.toSet()
        val rest = natural.filter { it.baseUrl !in prioritySet }
        return prioritized + rest
    }

    /** Persists the dragged order (list of source baseUrls). */
    fun persistOrder(baseUrls: List<String>) {
        viewModelScope.launch {
            appPreferences.GLOBAL_SEARCH_SOURCE_ORDER.value = baseUrls
        }
    }

    /** Clears the personal priority — global search falls back to the natural order. */
    fun resetOrder() {
        viewModelScope.launch {
            appPreferences.GLOBAL_SEARCH_SOURCE_ORDER.value = emptyList()
            sourcesTick.value += 1
        }
    }

    fun refresh() {
        sourcesTick.value += 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourcePrioritySheet(
    onDismiss: () -> Unit,
    viewModel: SearchPriorityViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(
                text = "Search source priority",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Drag sources to decide the global search order — the first source here is the first result row.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "LONG-PRESS AND DRAG \u22ee TO REORDER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { viewModel.resetOrder() }
                        .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (sources.isEmpty()) {
                Text(
                    text = "No book sources installed yet. Install extensions in Finder first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            } else {
                ReorderablePriorityList(
                    items = sources,
                    onMove = { from, to ->
                        viewModel.persistOrder(
                            SearchSourceOrderBridge.moved(
                                sources.map { it.baseUrl }, from, to,
                            ),
                        )
                        viewModel.refresh()
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** Bridge to the pure move logic (kept symmetrical with the global-search module). */
private object SearchSourceOrderBridge {
    fun moved(list: List<String>, from: Int, to: Int): List<String> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}

@Composable
private fun ReorderablePriorityList(
    items: List<PrioritySource>,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    // The dragged row is tracked by its stable item key (baseUrl) — row indices
    // shift as items swap, but keys never do, so the gesture survives reorders.
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(items.size, key = { items[it].baseUrl }) { index ->
            val source = items[index]
            val isDragging = draggingKey == source.baseUrl
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = if (isDragging) draggingOffset else 0f
                    }
                    .zIndex(if (isDragging) 1f else 0f)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (index < 3) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (source.language.isNotBlank()) {
                        Text(
                            text = source.language.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Drag handle — long-press and drag to reorder.
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Reorder",
                    tint = if (isDragging) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                        .pointerInput(source.baseUrl) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingKey = source.baseUrl
                                    draggingOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingKey == source.baseUrl) {
                                        draggingOffset += dragAmount.y
                                        resolveSwap(
                                            listState = listState,
                                            draggedKey = source.baseUrl,
                                            draggedOffset = draggingOffset,
                                            onSwap = { target -> onMove(currentIndexOf(listState, source.baseUrl), target) },
                                        )?.let { delta -> draggingOffset += delta }
                                    }
                                },
                                onDragEnd = {
                                    draggingKey = null
                                    draggingOffset = 0f
                                },
                                onDragCancel = {
                                    draggingKey = null
                                    draggingOffset = 0f
                                },
                            )
                        },
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}

/** Current positional index of the item with [key], or -1 when not visible. */
private fun currentIndexOf(listState: LazyListState, key: Any): Int =
    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.index ?: -1

/**
 * Finds the neighbour crossed by the dragged row's bounds.
 * Returns the anchor-correction delta (computed from pre-move offsets), or null
 * when the drag hasn't crossed a boundary yet. [onSwap] receives the target index.
 */
private fun resolveSwap(
    listState: LazyListState,
    draggedKey: Any,
    draggedOffset: Float,
    onSwap: (Int) -> Unit,
): Float? {
    val visible = listState.layoutInfo.visibleItemsInfo
    val dragged = visible.firstOrNull { it.key == draggedKey } ?: return null
    if (dragged.index < 0) return null
    val start = dragged.offset + draggedOffset
    val end = start + dragged.size
    val hovered = visible.firstOrNull { info ->
        info.key != draggedKey &&
            start < info.offset + info.size &&
            end > info.offset
    } ?: return null
    onSwap(hovered.index)
    // Pre-move offset difference keeps the row under the finger after the swap.
    return (dragged.offset - hovered.offset).toFloat()
}
