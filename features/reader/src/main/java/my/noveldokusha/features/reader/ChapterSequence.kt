package my.noveldokusha.features.reader

import my.noveldokusha.feature.local_database.tables.Chapter

/**
 * Последовательность глав книги: идентичность по URL, индексы,
 * hasPrev/hasNext. Глава не найдена в списке → [InvalidChapter] —
 * без фолбэка на индекс 0 (Bug1a: «не могу открыть нескачанную главу,
 * отправляет на 1»). Чистая логика, без зависимостей, покрыта тестами.
 */
sealed interface ChapterSequence {

    /** Глава найдена в списке книги. */
    data class Found(
        val chapters: List<Chapter>,
        val index: Int,
    ) : ChapterSequence {
        val chapter: Chapter get() = chapters[index]
        val hasPrev: Boolean get() = index > 0
        val hasNext: Boolean get() = index < chapters.lastIndex

        /** Соседняя глава на [delta] позиций; null — выход за границы книги. */
        fun neighbor(delta: Int): Chapter? = chapters.getOrNull(index + delta)
    }

    /** Глава не найдена в списке книги — вызывающий показывает диалог. */
    data object InvalidChapter : ChapterSequence

    companion object {
        /** Идентичность главы — по URL; при дублях берётся первое вхождение. */
        fun of(chapters: List<Chapter>, chapterUrl: String): ChapterSequence {
            val index = chapters.indexOfFirst { it.url == chapterUrl }
            return if (index >= 0) Found(chapters, index) else InvalidChapter
        }
    }
}