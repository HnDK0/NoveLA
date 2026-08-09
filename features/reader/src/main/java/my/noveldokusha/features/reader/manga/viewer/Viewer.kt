package my.noveldokusha.features.reader.manga.viewer

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import my.noveldokusha.features.reader.manga.MangaChapter

/**
 * Адаптация tachiyomisy Viewer под NoveLA: одна глава за раз,
 * без ViewerChapters (переключение глав делает Activity).
 */
internal interface Viewer {

    /** Корневой view вьюера. */
    val view: View

    /** Активная глава. */
    fun setChapter(chapter: MangaChapter, startPage: Int = chapter.startPage)

    /** Индекс текущей страницы. */
    fun currentPage(): Int

    fun moveToPage(index: Int)

    /** @return true, если жест обработан (переход или граница главы). */
    fun prevPage(): Boolean

    /** @return true, если жест обработан (переход или граница главы). */
    fun nextPage(): Boolean

    fun destroy() {}

    fun handleKeyEvent(event: KeyEvent): Boolean = false

    fun handleGenericMotionEvent(event: MotionEvent): Boolean = false

    /** Страница сменилась (индекс). */
    var onPageChanged: ((Int) -> Unit)?

    /** Попытка уйти за последнюю страницу (Activity переключает главу). */
    var onLastPageReached: (() -> Unit)?

    /** Попытка уйти за первую страницу (Activity переключает главу). */
    var onFirstPageReached: (() -> Unit)?

    /** Тап в зону MENU (или вне зон) — показать/скрыть тулбар. */
    var pageClickListener: (() -> Unit)?
}