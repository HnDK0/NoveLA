package my.noveldokusha.features.reader.manga.viewer.webtoon

import my.noveldokusha.features.reader.manga.MangaChapter

/**
 * Чистая модель «окна» вебтун-ленты: маппинг плоского списка элементов
 * RecyclerView ↔ глава/страница и дисциплина окна (расширение + вытеснение).
 *
 * Плоский список строится из резидентного списка упорядоченных глав: между
 * каждыми двумя соседними главами вставляется [MangaWebtoonTransition]:
 *
 *   [C0.pages..., T, C1.pages..., T, C2.pages...]
 *
 * Отсюда: плоская позиция ↔ (index-главы, страница-внутри), и наоборот.
 *
 * Инвариант окна (обязателен): вытеснение (prune) НИКОГДА не выкидывает
 * текущую главу [currentIndex] — лишь главы вне активного окна. Иначе
 * ломается честность состояния (индикатор/навигатор/позиция), см. P4.
 */
internal class MangaWebtoonWindow {

    /** Резидентные главы в порядке возрастания (по индексу в книге). */
    val chapters: List<MangaChapter> get() = _chapters
    private var _chapters: List<MangaChapter> = emptyList()

    /** Индекс текущей главы (среди [chapters]). Меняется вьюером при
     *  бесшовном пересечении границы между резидентными главами. */
    var currentChapterIndex: Int = 0
        internal set

    /** Максимум резидентных глав (окно ограничено). */
    var maxChapters: Int = 3

    /** Сброс окна на одну главу (первичная установка / явный переход). */
    fun setChapter(chapter: MangaChapter) {
        _chapters = listOf(chapter)
        currentChapterIndex = 0
    }

    /** Дописать главу СПРАВА (следующую). Возвращает true, если вставлена. */
    fun appendNext(chapter: MangaChapter): Boolean {
        val last = _chapters.lastOrNull() ?: return false
        if (chapter.index != last.index + 1) return false
        // Окно полно — вытесняем самую левую главу: иначе окно раздувается
        // сверх maxChapters, а схлопывание отложится до смены главы
        // (prune вызывается только при changedChapter) — память растёт.
        // Текущую главу не выкидываем: если она на левом краю — prepend/append
        // невозможен без нарушения инварианта, отклоняем вставку.
        if (_chapters.size >= maxChapters) {
            if (currentChapterIndex <= 0) return false
            _chapters = _chapters.drop(1)
            currentChapterIndex -= 1
        }
        _chapters = _chapters + chapter
        return true
    }

    /** Дописать главу СЛЕВА (предыдущую). Возвращает true, если вставлена. */
    fun prependPrevious(chapter: MangaChapter): Boolean {
        val first = _chapters.firstOrNull() ?: return false
        if (chapter.index != first.index - 1) return false
        // Окно полно — вытесняем самую правую главу (см. appendNext).
        if (_chapters.size >= maxChapters) {
            if (currentChapterIndex >= _chapters.lastIndex) return false
            _chapters = _chapters.dropLast(1)
        }
        _chapters = listOf(chapter) + _chapters
        currentChapterIndex += 1
        return true
    }

    /** Общее число плоских элементов (страницы + переходы между главами). */
    val itemCount: Int
        get() = if (_chapters.isEmpty()) 0 else _chapters.sumOf { it.pageCount } + (_chapters.size - 1)

    /** Плоская позиция первой страницы [chapterPos] (без перехода перед ней). */
    fun firstPagePositionOf(chapterPos: Int): Int {
        require(chapterPos in _chapters.indices) { "chapterPos out of range" }
        var acc = 0
        for (i in 0 until chapterPos) acc += _chapters[i].pageCount + 1
        return acc
    }

    /**
     * Вытеснение глав вне окна. НИКОГДА не выкидывает текущую главу
     * [currentChapterIndex]. Возвращает true, если список изменился.
     */
    fun prune(): Boolean {
        if (_chapters.size <= maxChapters) return false
        // Сколько глав можно убрать слева/справа, не трогая текущую.
        val canDropLeft = currentChapterIndex
        val canDropRight = _chapters.size - 1 - currentChapterIndex
        val excess = _chapters.size - maxChapters
        // Убираем поровну, но не трогая текущую главу.
        var dropLeft = minOf(canDropLeft, excess)
        var dropRight = excess - dropLeft
        if (dropRight > canDropRight) {
            // Переносим избыток влево, если справа мест нет.
            val overflow = dropRight - canDropRight
            dropRight = canDropRight
            dropLeft = minOf(canDropLeft, dropLeft + overflow)
        }
        if (dropLeft == 0 && dropRight == 0) return false
        _chapters = _chapters.drop(dropLeft).dropLast(dropRight)
        currentChapterIndex -= dropLeft
        return true
    }

    /**
     * Плоская позиция [flatPos] → (позиция главы, страница внутри).
     * Переход считает себя «внутри» следующей главы (страница 0).
     * Возвращает null для недопустимой позиции.
     */
    fun locate(flatPos: Int): Pair<Int, Int>? {
        if (flatPos !in 0 until itemCount) return null
        var remaining = flatPos
        for (i in _chapters.indices) {
            val pages = _chapters[i].pageCount
            if (remaining < pages) return i to remaining
            remaining -= pages
            if (i < _chapters.size - 1) {
                if (remaining == 0) return (i + 1) to 0 // переход → начало следующей
                remaining -= 1
            }
        }
        return null
    }

    /**
     * (позиция главы, страница) → плоская позиция страницы. Переходы
     * пропускаются: возвращается позиция именно страницы.
     */
    fun flatPosition(chapterPos: Int, page: Int): Int {
        require(chapterPos in _chapters.indices) { "chapterPos out of range" }
        val off = firstPagePositionOf(chapterPos)
        val clamped = page.coerceIn(0, _chapters[chapterPos].pageCount - 1)
        return off + clamped
    }

    /** Элемент плоского списка. */
    sealed interface Item {
        /** Одностраничный: [MangaPage]-индексный курсор (URL страницы + URL главы). */
        data class Page(val chapterUrl: String, val pageUrl: String, val index: Int) : Item
        /** Межглавный переход (вперёд = следующая глава внизу). */
        data class Transition(val isNext: Boolean) : Item
    }

    /**
     * Собирает плоский список элементов для адаптера: страницы глав,
     * разделённые [Item.Transition] между соседями.
     */
    fun buildItems(): List<Item> {
        if (_chapters.isEmpty()) return emptyList()
        val out = ArrayList<Item>(itemCount)
        for (i in _chapters.indices) {
            val ch = _chapters[i]
            for (p in ch.pages) out.add(Item.Page(ch.url, p.url, p.index))
            if (i < _chapters.size - 1) {
                out.add(Item.Transition(isNext = true))
            }
        }
        return out
    }
}