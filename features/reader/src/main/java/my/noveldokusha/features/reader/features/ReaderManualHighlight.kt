package my.noveldokusha.features.reader.features

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import my.noveldokusha.features.reader.domain.HighlightPosition
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.domain.firstHighlightItemIndexAtOrAfter
import my.noveldokusha.features.reader.domain.indexOfReaderItem
import my.noveldokusha.features.reader.domain.nextHighlightItemIndex
import my.noveldokusha.features.reader.domain.previousHighlightItemIndex

@Stable
internal data class ManualHighlightSettingData(
    val highlightedItem: State<HighlightPosition?>,
    val next: () -> Unit,
    val previous: () -> Unit,
    val clear: () -> Unit,
)

/**
 * Ручная подсветка абзацев: пользователь сам переключает текущий абзац
 * кнопками вперёд/назад, в отличие от автоматической подсветки TTS.
 */
internal class ReaderManualHighlight(
    private val items: List<ReaderItem>,
    private val scope: CoroutineScope,
    private val stopTextToSpeech: () -> Unit,
) {
    private val _highlightedItem = mutableStateOf<HighlightPosition?>(null)
    val highlightedItem: State<HighlightPosition?> = _highlightedItem

    /** Эмитит позицию, на которую нужно проскроллить после next/previous. */
    val scrollToItem = MutableSharedFlow<HighlightPosition>()

    val state = ManualHighlightSettingData(
        highlightedItem = highlightedItem,
        next = ::next,
        previous = ::previous,
        clear = ::clear,
    )

    /**
     * Подсвечивает первый абзац с [itemIndex] включительно (пропуская нетекстовые
     * элементы) и останавливает TTS, чтобы режимы не пересекались.
     */
    fun startFromItemIndex(itemIndex: Int) {
        val targetIndex = firstHighlightItemIndexAtOrAfter(items, itemIndex) ?: return
        setHighlight(targetIndex)
        stopTextToSpeech()
    }

    fun next() {
        val current = _highlightedItem.value ?: return
        val currentIndex = indexOfReaderItem(
            items, current.chapterIndex, current.chapterItemPosition
        )
        // Абзац выгружен из списка (pruneItems/reload) — панель не должна висеть «мёртвой»
        if (currentIndex == -1) {
            clear()
            return
        }
        val targetIndex = nextHighlightItemIndex(items, currentIndex) ?: return
        moveTo(targetIndex)
    }

    fun previous() {
        val current = _highlightedItem.value ?: return
        val currentIndex = indexOfReaderItem(
            items, current.chapterIndex, current.chapterItemPosition
        )
        // Абзац выгружен из списка (pruneItems/reload) — панель не должна висеть «мёртвой»
        if (currentIndex == -1) {
            clear()
            return
        }
        val targetIndex = previousHighlightItemIndex(items, currentIndex) ?: return
        moveTo(targetIndex)
    }

    fun clear() {
        _highlightedItem.value = null
    }

    private fun setHighlight(itemIndex: Int) {
        val item = items.getOrNull(itemIndex) as? ReaderItem.Text ?: return
        _highlightedItem.value = HighlightPosition(
            chapterIndex = item.chapterIndex,
            chapterItemPosition = item.chapterItemPosition,
        )
    }

    private fun moveTo(itemIndex: Int) {
        setHighlight(itemIndex)
        val position = _highlightedItem.value ?: return
        scope.launch { scrollToItem.emit(position) }
    }
}
