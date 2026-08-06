import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.domain.ImgEntry
import my.noveldokusha.features.reader.domain.firstHighlightItemIndexAtOrAfter
import my.noveldokusha.features.reader.domain.nextHighlightItemIndex
import my.noveldokusha.features.reader.domain.previousHighlightItemIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualHighlightIndexTest {

    private fun text(chapterIndex: Int, chapterItemPosition: Int): ReaderItem.Text =
        ReaderItem.Body(
            chapterUrl = "",
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition,
            text = "",
            location = ReaderItem.Location.MIDDLE,
        )

    // Плоский список без обёрток адаптера (Padding и т.п. вручную не добавляем).
    // Индексы:
    //   0 BookEnd | 1 Divider | 2 Title(0,0) | 3 Attribution | 4 Body(0,1) | 5 Body(0,2)
    //   6 Image(0,3) | 7 Body(0,4) | 8 Divider(1) | 9 Title(1,0) | 10 Body(1,1) | 11 Body(1,2)
    //   12 Image(1,3) | 13 BookEnd
    private val list = listOf<ReaderItem>(
        ReaderItem.BookEnd(chapterIndex = -1),
        ReaderItem.Divider(chapterIndex = 0),
        ReaderItem.Title(chapterUrl = "", chapterIndex = 0, chapterItemPosition = 0, text = ""),
        ReaderItem.GoogleTranslateAttribution(chapterIndex = 0),
        text(chapterIndex = 0, chapterItemPosition = 1),
        text(chapterIndex = 0, chapterItemPosition = 2),
        ReaderItem.Image(chapterUrl = "", chapterIndex = 0, chapterItemPosition = 3, text = "", location = ReaderItem.Location.MIDDLE, image = ImgEntry("", 1f)),
        text(chapterIndex = 0, chapterItemPosition = 4),
        ReaderItem.Divider(chapterIndex = 1),
        ReaderItem.Title(chapterUrl = "", chapterIndex = 1, chapterItemPosition = 0, text = ""),
        text(chapterIndex = 1, chapterItemPosition = 1),
        text(chapterIndex = 1, chapterItemPosition = 2),
        ReaderItem.Image(chapterUrl = "", chapterIndex = 1, chapterItemPosition = 3, text = "", location = ReaderItem.Location.MIDDLE, image = ImgEntry("", 1f)),
        ReaderItem.BookEnd(chapterIndex = 4),
    )

    @Test
    fun firstFromStartSkipsNonText() {
        assertEquals(2, firstHighlightItemIndexAtOrAfter(list, 0))
    }

    @Test
    fun firstFromTextReturnsItself() {
        assertEquals(4, firstHighlightItemIndexAtOrAfter(list, 4))
    }

    @Test
    fun firstFromImageSkipsToNextText() {
        assertEquals(7, firstHighlightItemIndexAtOrAfter(list, 6))
    }

    @Test
    fun firstFromBookEndAtEndIsNull() {
        assertNull(firstHighlightItemIndexAtOrAfter(list, 13))
    }

    @Test
    fun firstBeyondListIsNull() {
        assertNull(firstHighlightItemIndexAtOrAfter(list, 999))
    }

    @Test
    fun firstNegativeIndexClampsToZero() {
        assertEquals(2, firstHighlightItemIndexAtOrAfter(list, -5))
    }

    @Test
    fun firstEmptyListIsNull() {
        assertNull(firstHighlightItemIndexAtOrAfter(emptyList(), 0))
    }

    @Test
    fun nextMovesForward() {
        assertEquals(4, nextHighlightItemIndex(list, 2))
    }

    @Test
    fun nextSkipsImage() {
        assertEquals(7, nextHighlightItemIndex(list, 5))
    }

    @Test
    fun nextCrossesChapterBoundary() {
        assertEquals(9, nextHighlightItemIndex(list, 7))
    }

    @Test
    fun nextAtEndIsNull() {
        assertNull(nextHighlightItemIndex(list, 11))
    }

    @Test
    fun previousMovesBackward() {
        assertEquals(4, previousHighlightItemIndex(list, 5))
    }

    @Test
    fun previousSkipsImage() {
        assertEquals(5, previousHighlightItemIndex(list, 7))
    }

    @Test
    fun previousCrossesChapterBoundary() {
        assertEquals(7, previousHighlightItemIndex(list, 9))
    }

    @Test
    fun previousAtStartIsNull() {
        assertNull(previousHighlightItemIndex(list, 2))
    }

    @Test
    fun previousAtIndexZeroIsNull() {
        assertNull(previousHighlightItemIndex(list, 0))
    }

    @Test
    fun navigationThroughWholeList() {
        // С 0 до конца и обратно: 2 -> 4 -> 5 -> 7 -> 9 -> 10 -> 11 -> (null)
        var index = firstHighlightItemIndexAtOrAfter(list, 0)
        val forward = mutableListOf<Int>()
        while (index != null) {
            forward += index
            index = nextHighlightItemIndex(list, index)
        }
        assertEquals(listOf(2, 4, 5, 7, 9, 10, 11), forward)

        var backIndex: Int? = forward.last()
        val backward = mutableListOf<Int>()
        while (backIndex != null) {
            backward += backIndex
            backIndex = previousHighlightItemIndex(list, backIndex)
        }
        assertEquals(forward.reversed(), backward)
    }
}
