package my.noveldokusha.features.reader.domain

/**
 * Позиция подсвеченного абзаца в плоском списке [ReaderItem].
 * Уникальна по паре (chapterIndex, chapterItemPosition), аналогично [ReaderItem.Position].
 */
internal data class HighlightPosition(
    val chapterIndex: Int,
    val chapterItemPosition: Int,
)

/** Подсвечиваемыми считаем только текстовые элементы (Title и Body). */
private fun ReaderItem.isHighlightable(): Boolean = this is ReaderItem.Text

/**
 * Индекс первого подсвечиваемого элемента с [fromIndex] включительно.
 * Пропускает Image/Divider/BookStart/BookEnd/Error/Padding/Progressbar и т.п.
 * Возвращает null, если подходящих элементов нет.
 */
internal fun firstHighlightItemIndexAtOrAfter(
    items: List<ReaderItem>,
    fromIndex: Int,
): Int? {
    val start = fromIndex.coerceAtLeast(0)
    val relative = items
        .asSequence()
        .drop(start)
        .indexOfFirst { it.isHighlightable() }
    return if (relative == -1) null else start + relative
}

/** Следующий подсвечиваемый элемент строго после [currentIndex]. null — за концом. */
internal fun nextHighlightItemIndex(
    items: List<ReaderItem>,
    currentIndex: Int,
): Int? = firstHighlightItemIndexAtOrAfter(items, currentIndex + 1)

/** Предыдущий подсвечиваемый элемент строго до [currentIndex]. null — до начала. */
internal fun previousHighlightItemIndex(
    items: List<ReaderItem>,
    currentIndex: Int,
): Int? {
    for (index in (currentIndex - 1) downTo 0) {
        if (items[index].isHighlightable()) return index
    }
    return null
}
