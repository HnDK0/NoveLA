package my.noveldokusha.features.reader.domain

import kotlin.math.ceil

/**
 * Only use it on definitions where the primitive data type
 * doesn't convey enough meaning
 */
internal typealias ChapterUrl = String
internal typealias ItemIndex = Int // refers to [items]
internal typealias ChapterIndex = Int // refers to [orderedChapters]

internal enum class ReaderState {
    IDLE,
    LOADING,
    INITIAL_LOAD
}

internal data class ChapterState(
    val chapterUrl: String,
    val chapterItemPosition: Int,
    val offset: Int
)

internal data class ReadingChapterPosStats(
    val chapterIndex: Int,
    val chapterCount: Int,
    val chapterItemPosition: Int,
    val chapterItemsCount: Int,
    val chapterTitle: String,
    val chapterUrl: String,
    /**
     * Доля прокрутки ВНУТРИ текущего элемента (0..1). Для страниц манхвы
     * ряд — целый экран высотой: позиция по элементам не меняется между
     * страницами, и прогресс «застывал». С долей каждая маленькая
     * прокрутка даёт точный процент. Для текстовых глав всегда 0.
     */
    val withinItemFraction: Float = 0f,
)

internal fun ReadingChapterPosStats.chapterReadPercentage() = when (chapterItemsCount) {
    0 -> 100f
    else -> ceil(
        (((chapterItemPosition + withinItemFraction) / chapterItemsCount) * 100f)
            .coerceAtMost(100f)
    )
}
