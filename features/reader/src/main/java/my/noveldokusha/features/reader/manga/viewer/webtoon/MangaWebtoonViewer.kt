package my.noveldokusha.features.reader.manga.viewer.webtoon

import android.content.Context
import android.graphics.PointF
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.manga.MangaChapter
import my.noveldokusha.features.reader.manga.MangaPage
import my.noveldokusha.features.reader.manga.viewer.Viewer
import my.noveldokusha.features.reader.manga.viewer.ViewerNavigation.NavigationRegion
import my.noveldokusha.features.reader.tools.PageImageLoader
import my.noveldokusha.reader.R
import timber.log.Timber

/**
 * Вебтун-вьюер — мультиглавный порт tachiyomisy WebtoonViewer.
 *
 * Вьюер владеет «окном» резидентных глав ([MangaWebtoonWindow]): текущая +
 * соседние, накопленные вперёд/назад. Лента скроллится БЕСШОВНО через
 * границы глав — пересечение детектируется по [MangaWebtoonWindow.locate]
 * и эмитится в [onChapterChanged] (state-sync без перезагрузки). Соседние
 * главы запрашиваются через [onRequestChapter] (Activity грузит страницы
 * и возвращает их через [appendNextChapter]/[prependPreviousChapter]).
 *
 * [onFirstPageReached]/[onLastPageReached] теперь означают упор в РЕАЛЬНЫЙ
 * край книги (соседей больше нет) — а не границу главы.
 */
internal class MangaWebtoonViewer(
    private val context: Context,
    internal val pageImageLoader: PageImageLoader,
    private val appPreferences: AppPreferences,
) : Viewer {

    private val scope = MainScope()

    /** Конфигурация вьюера (настройки подписаны на AppPreferences). */
    val config = MangaWebtoonConfig(appPreferences, scope)

    /** Лента. */
    internal val recycler = MangaWebtoonRecyclerView(context)

    /** Обёртка ленты (трансляция тачей). */
    private val frame = MangaWebtoonFrame(context)

    /** Дистанция скролла при тапе на край: 3/4 экрана (как в tachiyomisy). */
    private val scrollDistance = context.resources.displayMetrics.heightPixels * 3 / 4

    /** Extra layout space: лента создаёт холдеры на 4 экрана вперёд. Страницы
     *  вебтуна высокие (2-3 экрана), поэтому 2 экрана давали холдер лишь на
     *  одну страницу вперёд — полная картинка (1-5 МБ) не успевала загрузиться
     *  до подхода читателя. 4 экрана — запас в 1.5-2 страницы на загрузку. */
    private val layoutManager =
        MangaWebtoonLayoutManager(context, context.resources.displayMetrics.heightPixels * 4)

    /** Окно резидентных глав + плоский список ленты. */
    private val window = MangaWebtoonWindow()

    private val adapter = MangaWebtoonAdapter(this)

    /**
     * Заглушка для пустой главы (нет страниц): иначе пользователь видит
     * пустой экран, а навигация уходила бы в бесконечный цикл переключения
     * глав через onLastPageReached/onFirstPageReached.
     */
    private val emptyView = TextView(context).apply {
        text = context.getString(R.string.manga_reader_no_pages)
        gravity = Gravity.CENTER
        textSize = 16f
        visibility = View.GONE
    }

    /** Индекс текущей страницы (осевшая в окне: первая полностью видимая,
     *  либо страница выше окна, чей верх ушёл за верхний край). */
    private var currentIndex = -1

    /** Последняя «осевшая» плоская позиция, реально переданная в
     *  [onSettledPosition]: дедуп эмиссий (эталон Kotatsu WebtoonScrollDispatcher).
     *  onScrolled прилетает на КАЖДЫЙ layout-проход от декодирования картинок,
     *  и та же позиция не должна логироваться/обрабатываться повторно. */
    private var lastSettledFlatPos = RecyclerView.NO_POSITION

    /** Плоская позиция, для которой уже прогрет префетч окна (± count страниц):
     *  дедуп prefetchNearbyPages по смене страницы, а не по каждому onScrolled. */
    private var lastPrefetchPos = -1

    /** Видели DRAGGING/SETTLING с последнего IDLE: лента осела после
     *  ПОЛЬЗОВАТЕЛЬСКОГО жеста, а не программного scrollToPosition
     *  (setChapter/appendNextChapter — без touch-состояний, сразу IDLE). */
    private var lastGestureWasUser = false

    /** true, пока активна автопрокрутка: её программный скролл до края окна
     *  должен догружать соседнюю главу (в отличие от prepend-сдвига ленты). */
    private var autoScrollActive = false

    /** Индекс предыдущей главы, запрошенной по жесту/кнопке: после её prepend
     *  и сдвига ленты на свою главу повторный запрос той же главы не нужен.
     *  -1 = активного запроса нет. */
    private var requestedPreviousChapterIndex = -1

    init {
        // FrameLayout.LayoutParams обязательны: frame.onMeasure кастует layoutParams
        // детей к MarginLayoutParams, ViewGroup.LayoutParams → ClassCastException.
        recycler.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.isFocusable = false
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_DRAGGING,
                        RecyclerView.SCROLL_STATE_SETTLING,
                        -> lastGestureWasUser = true

                        RecyclerView.SCROLL_STATE_IDLE -> {
                            if (lastGestureWasUser) {
                                // Жест завершился — осел ли пользователь на краю
                                // ленты? Тогда граница книги/запрос соседа.
                                // Программные scrollToPosition из setChapter
                                // проходят сразу в IDLE (без DRAGGING/SETTLING)
                                // и сюда не попадают.
                                lastGestureWasUser = false
                                checkEdgeReached()
                            }
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateCurrentPage()
                    prefetchNearbyPages()
                }
            },
        )
        recycler.tapListener = { event -> handleTap(event) }
        recycler.doubleTapListener = { event -> onDoubleTap(event) }

        // Отступы/переходы изменились — перепривязать видимые холдеры.
        config.imagePropertyChangedListener = {
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }

        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
        frame.addView(emptyView)

        setupAutoScroll()
    }

    // ── Автопрокрутка (tachiyomisy-стиль: непрерывный плавный скролл) ──

    private var autoScrollJob: Job? = null

    /** Пауза по касанию: любой тач по ленте останавливает скролл. */
    private var autoScrollPaused = false

    private fun setupAutoScroll() {
        // Тач по ленте — пауза (пользователь читает/листает сам).
        recycler.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                // Любой тач по ленте — пауза (пользователь читает/листает сам)
                // и конец автоскролла: дальше только реальные жесты.
                MotionEvent.ACTION_DOWN -> {
                    autoScrollPaused = true
                    autoScrollActive = false
                }

                // Снятие последнего пальца — возобновить автопрокрутку.
                // actionMasked отделяет ACTION_POINTER_UP: пока второй палец
                // ещё держится, скролл остаётся на паузе.
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> autoScrollPaused = false
            }
            false // не перехватываем событие
        }
        appPreferences.MANGA_READER_AUTOSCROLL_ENABLED.flow()
            .onEach { enabled ->
                if (enabled && autoScrollJob == null) {
                    autoScrollPaused = false
                    autoScrollJob = scope.launch { runAutoScroll() }
                } else if (!enabled) {
                    autoScrollJob?.cancel()
                    autoScrollJob = null
                }
            }
            .launchIn(scope)
    }

    /** Цикл: тик каждые 16 мс, мгновенный скролл на накопленные пиксели. */
    private suspend fun CoroutineScope.runAutoScroll() {
        val density = context.resources.displayMetrics.density
        var totalPixels = 0f
        autoScrollActive = true
        while (isActive) {
            if (!autoScrollPaused && recycler.width > 0 && recycler.height > 0) {
                // Читаем скорость каждый тик — смена в настройках применяется на лету.
                val speedPxPerSec = appPreferences.MANGA_READER_AUTOSCROLL_SPEED.value * density
                val (pixels, newTotal) = accumulatedPixels(speedPxPerSec, FRAME_DELTA_MS, totalPixels)
                totalPixels = newTotal
                if (pixels != 0) recycler.scrollBy(0, pixels)
                // Конец ленты: скроллить дальше некуда — цикл завершится сам,
                // а не крутится вхолостую на последней странице.
                // canScrollVertically(1) — нативный признак «снизу контента нет»,
                // не требует чтения адаптера. Job обнуляется до return: иначе
                // guard autoScrollJob == null не позволил бы повторный запуск.
                if (!recycler.canScrollVertically(1)) {
                    autoScrollJob = null
                    // Упор в низ окна: догружаем следующую главу, чтобы
                    // автоскролл мог продолжиться (Activity отбросит запрос
                    // на границе книги).
                    val last = window.chapters.lastOrNull()
                    if (last != null) onRequestChapter?.invoke(last.index + 1)
                    return
                }
            }
            delay(FRAME_DELTA_MS)
        }
    }

    /** Корневой view вьюера. */
    override val view: View get() = frame

    override var onPageChanged: ((Int) -> Unit)? = null
    override var onLastPageReached: (() -> Unit)? = null
    override var onFirstPageReached: (() -> Unit)? = null
    override var pageClickListener: (() -> Unit)? = null

    /**
     * Текущая глава сменилась бесшовно (скролл через границу резидентных
     * глав). Activity/ViewModel ОБНОВЛЯЮТ состояние IN PLACE (chapter.value,
     * currentChapterIndex, currentPage) — НЕ вызывают loadChapter.
     */
    var onChapterChanged: ((chapterUrl: String, chapterIndex: Int, page: Int, pages: List<MangaPage>) -> Unit)? = null

    /**
     * Запрос загрузки соседней главы по индексу в книге. Activity сам решает,
     * существует ли глава (граница книги) и грузит/возвращает её через
     * [appendNextChapter]/[prependPreviousChapter].
     */
    var onRequestChapter: ((chapterIndex: Int) -> Unit)? = null

    override fun setChapter(chapter: MangaChapter, startPage: Int) {
        Timber.d(
            "MangaWebtoon: setChapter url=%s index=%d startPage=%d windowChapters=%d",
            chapter.url, chapter.index, startPage, window.chapters.size,
        )
        window.setChapter(chapter)
        currentIndex = -1
        // Новое окно: сброс дедупов позиции и префетча (плоские позиции
        // пересчитаны с нуля, старое состояние невалидно).
        lastSettledFlatPos = RecyclerView.NO_POSITION
        lastPrefetchPos = -1
        emptyView.visibility = if (chapter.pages.isEmpty()) View.VISIBLE else View.GONE
        rebuildItems()
        moveToPage(startPage)
        // Запрашиваем соседей сразу: к моменту подхода к границе главы
        // сосед уже резидентан — бесшовный скролл без ожидания сети.
        requestNeighbors(requestAdjacent = true)
        prefetchNearbyPages()
        prefetchChapter(chapter)
    }

    /** true, если глава уже резидентна в окне (дубль запроса на prepend/append). */
    fun hasChapter(url: String): Boolean = window.chapters.any { it.url == url }

    /** Глава загружена и добавляется СЛЕВА (предыдущая). */
    fun prependPreviousChapter(chapter: MangaChapter) {
        Timber.d(
            "MangaWebtoon: prependPreviousChapter url=%s index=%d windowChaptersBefore=%d",
            chapter.url, chapter.index, window.chapters.size,
        )
        val wasEmpty = window.chapters.isEmpty()
        if (wasEmpty) {
            window.setChapter(chapter)
        } else if (!window.prependPrevious(chapter)) {
            Timber.w("MangaWebtoon: prependPreviousChapter rejected url=%s", chapter.url)
            return
        }
        emptyView.visibility = View.GONE
        rebuildItems()
        val restore = chapter.pages.size + 1
        // Вставка сверху делает item 0 страницей НОВОЙ главы. Если layout ещё
        // не применял moveToPage (prepend пришёл синхронно из кэша в стеке
        // setChapter) — корректируем pending-скролл: иначе лента оседает на
        // странице 0 ПРЕДЫДУЩЕЙ главы и onSettledPosition(0) откатывает
        // moveChapter. Если лента уже осела — возвращаем её на первую
        // страницу текущей главы (позиция pages + 1 после вставки), но только
        // когда она реально стояла на самом верху окна (позиция 0).
        if (!recycler.adjustPendingScroll(restore) && recycler.currentPagePosition() == 0) {
            recycler.scrollToPage(restore.coerceAtMost(adapter.itemCount - 1))
        }
        // Расширение по краю окна — только если текущая глава у края
        // (читатель реально на границе), prepend-сдвиг сам по себе соседей
        // не запрашивает: иначе каскад до главы 1.
        requestNeighbors(requestAdjacent = false)
        prefetchChapter(chapter, fromEnd = true)
    }

    /** Глава загружена и добавляется СПРАВА (следующая). */
    fun appendNextChapter(chapter: MangaChapter) {
        Timber.d(
            "MangaWebtoon: appendNextChapter url=%s index=%d windowChaptersBefore=%d",
            chapter.url, chapter.index, window.chapters.size,
        )
        val wasEmpty = window.chapters.isEmpty()
        if (wasEmpty) {
            window.setChapter(chapter)
        } else if (!window.appendNext(chapter)) {
            Timber.w("MangaWebtoon: appendNextChapter rejected url=%s", chapter.url)
            return
        }
        emptyView.visibility = View.GONE
        rebuildItems()
        requestNeighbors(requestAdjacent = false)
        prefetchChapter(chapter)
    }

    /** Пересборка плоского списка из окна (delta-обновление через DiffUtil). */
    private fun rebuildItems() {
        val items = window.buildItems()
        Timber.d("MangaWebtoon: rebuildItems itemCount=%d", items.size)
        adapter.setItems(items)
    }

    /**
     * Расширение окна вперёд/назад, не дожидаясь фактического подхода к
     * границе: окно прогревается соседями заранее. [requestAdjacent] —
     * запросить ОБОИХ соседей (при первичной установке главы); иначе —
     * только за краем текущего окна.
     */
    private fun requestNeighbors(requestAdjacent: Boolean) {
        val onRequest = onRequestChapter ?: return
        val chapters = window.chapters
        if (chapters.isEmpty()) return
        Timber.d(
            "MangaWebtoon: requestNeighbors adjacent=%b windowFirst=%d windowLast=%d windowSize=%d",
            requestAdjacent, chapters.first().index, chapters.last().index, chapters.size,
        )
        if (requestAdjacent) {
            onRequest(chapters.first().index - 1)
            onRequest(chapters.last().index + 1)
            return
        }
        // Расширение по краю окна: запрашиваем главу за краем.
        val cur = window.currentChapterIndex
        if (cur == 0) onRequest(chapters.first().index - 1)
        if (cur == chapters.lastIndex) onRequest(chapters.last().index + 1)
    }

    override fun currentPage(): Int {
        val pos = recycler.currentPagePosition()
        return if (pos == RecyclerView.NO_POSITION) currentIndex else pos
    }

    override fun moveToPage(index: Int) {
        // index — страница ВНУТРИ текущей главы окна.
        if (window.chapters.isEmpty()) return
        val flatPos = window.flatPosition(window.currentChapterIndex, index)
        Timber.d(
            "MangaWebtoon: moveToPage page=%d flatPos=%d itemCount=%d currentChapterIndex=%d",
            index, flatPos, adapter.itemCount, window.currentChapterIndex,
        )
        if (flatPos !in 0 until adapter.itemCount) return
        recycler.scrollToPage(flatPos)
        // Индикатор читает страницу внутри главы (не плоскую позицию).
        if (index != currentIndex) {
            currentIndex = index
            onPageChanged?.invoke(index)
        }
    }

    /** Скролл на страницу вверх (назад). */
    override fun prevPage(): Boolean {
        if (adapter.itemCount == 0) return false
        val current = currentPage()
        return if (current > 0) {
            recycler.smoothScrollToPage(current - 1)
            true
        } else {
            // Упор в верх окна: запрашиваем предыдущую главу, если она есть
            // за краем окна (Activity отбросит запрос на границе книги).
            val first = window.chapters.firstOrNull() ?: return false
            onRequestChapter?.invoke(first.index - 1)
            onFirstPageReached?.invoke()
            true
        }
    }

    /** Скролл на страницу вниз (вперёд). */
    override fun nextPage(): Boolean {
        if (adapter.itemCount == 0) return false
        val current = currentPage()
        return if (current < adapter.itemCount - 1) {
            recycler.smoothScrollToPage(current + 1)
            true
        } else {
            val last = window.chapters.lastOrNull() ?: return false
            onRequestChapter?.invoke(last.index + 1)
            onLastPageReached?.invoke()
            true
        }
    }

    override fun destroy() {
        scope.cancel()
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> if (isUp) pageClickListener?.invoke()

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            -> if (isUp) scrollUp()

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            -> if (isUp) scrollDown()

            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean = false

    /**
     * Краевое расширение окна после того, как пользовательский жест осел на
     * краю ленты. canScrollVertically — нативный признак того, что контент
     * закончился; на краю окна (не книги) запрашиваем соседнюю главу — лента
     * расширится вперёд/назад, и пользователь продолжит скролл. На РЕАЛЬНОМ
     * краю книги (глав за окном нет) — фолбэк onFirst/onLastPageReached.
     */
    private fun checkEdgeReached() {
        if (adapter.itemCount == 0) return
        if (!recycler.canScrollVertically(-1)) {
            val first = window.chapters.firstOrNull() ?: return
            onRequestChapter?.invoke(first.index - 1)
            onFirstPageReached?.invoke()
        } else if (!recycler.canScrollVertically(1)) {
            val last = window.chapters.lastOrNull() ?: return
            onRequestChapter?.invoke(last.index + 1)
            onLastPageReached?.invoke()
        }
    }

    /** Обновляет текущую страницу по «осевшей» позиции ленты.
     *
     * Критерий оседания: текущей считается первая полностью видимая страница
     * (findFirstCompletelyVisibleItemPosition). Пока идёт переход — верхняя
     * страница частично ушла за край, следующая ещё не видна целиком —
     * полностью видимых нет, и эмиссии нет: индикатор не мигает на
     * промежуточных позициях автопрокрутки и не откатывается к половине
     * ушедшей странице.
     *
     * Исключение — страница выше окна (типичный вебтун-лист): она никогда
     * не бывает «полностью видимой», но её верх, ушедший за верхний край,
     * означает, что читатель уже на ней.
     */
    private fun updateCurrentPage() {
        val lm = recycler.layoutManager as? LinearLayoutManager ?: return
        val settled = lm.findFirstCompletelyVisibleItemPosition()
        val flatPos =
            if (settled != RecyclerView.NO_POSITION) {
                settled
            } else {
                val topVisible = lm.findFirstVisibleItemPosition()
                if (topVisible == RecyclerView.NO_POSITION) return
                val child = recycler.findViewHolderForAdapterPosition(topVisible)?.itemView ?: return
                // Страница выше окна (типичный вебтун-лист): она никогда
                // не бывает «полностью видимой», но её верх, ушедший за
                // верхний край, означает, что читатель уже на ней.
                if (child.top <= recycler.paddingTop && child.height > recycler.height) topVisible else return
            }
        // Дедуп по позиции: onScrolled прилетает на каждый layout-проход от
        // декодирования картинок — та же позиция не должна эмититься повторно
        // (иначе спам onSettledPosition на каждый ре-лейаут загрузки).
        if (flatPos == lastSettledFlatPos) return
        lastSettledFlatPos = flatPos
        onSettledPosition(flatPos)
    }

    /**
     * Плоская позиция «осела»: обновить currentIndex, эмитить onPageChanged
     * (страница ВНУТРИ главы — индикатор/навигатор читают её, а не плоскую
     * позицию), а при пересечении границы глав — state-sync
     * (onChapterChanged), расширить окно за краем и вытеснить дальние главы.
     */
    private fun onSettledPosition(flatPos: Int) {
        val location = window.locate(flatPos)
        if (location != null) {
            val (chapterPos, page) = location
            val changedChapter = chapterPos != window.currentChapterIndex
            Timber.d(
                "MangaWebtoon: onSettledPosition flatPos=%d chapterPos=%d page=%d changedChapter=%b windowSize=%d",
                flatPos, chapterPos, page, changedChapter, window.chapters.size,
            )
            window.currentChapterIndex = chapterPos
            if (changedChapter) {
                val chapter = window.chapters[chapterPos]
                // Страницы главы передаются вместе с переходом: у VM нет кэша
                // для главы, вернувшейся в окно после prepend-сдвига, и без них
                // syncChapter строил бы заглушку с 0 страниц («X из 0»).
                onChapterChanged?.invoke(chapter.url, chapter.index, page, chapter.pages)
                // Пользователь в последней резидентной главе — запросить
                // следующую; в первой — предыдущую.
                if (chapterPos == 0) onRequestChapter?.invoke(window.chapters.first().index - 1)
                if (chapterPos == window.chapters.lastIndex) {
                    onRequestChapter?.invoke(window.chapters.last().index + 1)
                }
                // Вытеснение дальних глав НИКОГДА не трогает текущую.
                if (window.prune()) {
                    // Позиция текущей страницы могла сместиться — восстановить.
                    val newFlat = window.flatPosition(window.currentChapterIndex, page)
                    // DiffUtil-обновление ЗАПРЕЩЕНО из scroll callback
                    // (IllegalStateException «Cannot call this method in a
                    // scroll callback») — пересборка откладывается на
                    // следующий кадр после завершения скролла/layout.
                    recycler.post {
                        if (!recycler.isAttachedToWindow) return@post
                        rebuildItems()
                        recycler.scrollToPage(newFlat.coerceIn(0, adapter.itemCount - 1))
                    }
                }
            }
            if (page != currentIndex) {
                currentIndex = page
                onPageChanged?.invoke(page)
            }
        } else {
            setCurrentPage(flatPos)
        }
    }

    /**
     * Установка текущей страницы (используется moveToPage, где плоская
     * позиция == страница единственной/первой главы окна).
     */
    private fun setCurrentPage(position: Int) {
        if (position == currentIndex) return
        currentIndex = position
        onPageChanged?.invoke(position)
    }

    /**
     * Прогрев ВСЕЙ главы при открытии/добавлении в окно: семафорный префетч
     * грузит страницы параллельно, и к моменту подхода читателя они уже в
     * кэше — скролл без «затупов». Уважает READER_PAGE_PREFETCH_COUNT
     * (0 = префетч выключен). load() дедуплицирует пересечения с
     * prefetchNearbyPages и мгновенно возвращает уже загруженные страницы.
     */
    private fun prefetchChapter(chapter: MangaChapter, fromEnd: Boolean = false) {
        if (chapter.pages.isEmpty()) return
        val count = appPreferences.READER_PAGE_PREFETCH_COUNT.value
        if (count <= 0) return
        // Префетчим только страницы у края присоединения (лимит из настроек):
        // вся глава целиком = сотни запросов → 429-лавина. При prepend —
        // последние страницы (читатель подойдёт снизу), иначе — первые.
        val pages = if (fromEnd) chapter.pages.takeLast(count) else chapter.pages.take(count)
        pageImageLoader.prefetchPages(chapter.url, pages)
    }

    /**
     * Префетч страниц ВПЕРЁД и НАЗАД относительно текущей при скролле:
     * окно [READER_PAGE_PREFETCH_COUNT] страниц в обе стороны прогревается
     * через PageImageLoader; load() дедуплицирует одновременные запросы.
     * В мультиглавном окне прогреваются страницы ВСЕХ резидентных глав
     * поблизости от текущей позиции (границы не разрывают префетч).
     */
    private fun prefetchNearbyPages() {
        val current = currentPage()
        if (current < 0 || window.chapters.isEmpty()) return
        // Дедуп по смене страницы: onScrolled прилетает на каждый layout-проход
        // от загрузки картинок; без дедупа ре-лейауты повторно сканировали окно
        // и разгоняли декодирование вперёд (лишний GC от битмапов префетча).
        if (current == lastPrefetchPos) return
        lastPrefetchPos = current
        val count = appPreferences.READER_PAGE_PREFETCH_COUNT.value
        if (count <= 0) return
        for ((chapterPos, chapter) in window.chapters.withIndex()) {
            val first = window.firstPagePositionOf(chapterPos)
            // Страницы главы, близкие к текущей позиции (окно в обе стороны).
            val startPage = (current - first - count).coerceIn(0, chapter.pageCount - 1)
            val endPage = (current - first + 1 + count).coerceIn(0, chapter.pageCount)
            if (startPage < endPage) {
                pageImageLoader.prefetchPages(
                    chapter.url,
                    chapter.pages.subList(startPage, endPage),
                )
            }
        }
    }

    /**
     * Тап: зоны из config.navigator (координаты нормализованы 0..1).
     * MENU — переключение тулбара, NEXT — вниз (вперёд), PREV — вверх (назад).
     */
    private fun handleTap(event: MotionEvent) {
        val width = recycler.width
        val height = recycler.height
        if (width <= 0 || height <= 0) {
            pageClickListener?.invoke()
            return
        }
        val pos = PointF(event.x / width, event.y / height)
        when (config.navigator.getAction(pos)) {
            NavigationRegion.MENU -> config.handleMenuTap { pageClickListener?.invoke() }
            NavigationRegion.NEXT -> scrollDown()
            NavigationRegion.PREV -> scrollUp()
        }
    }

    /** Двойной тап: открыть меню в зоне MENU (иначе — жест не наш). */
    private fun onDoubleTap(event: MotionEvent): Boolean {
        val width = recycler.width
        val height = recycler.height
        if (width <= 0 || height <= 0) {
            pageClickListener?.invoke()
            return true
        }
        val pos = PointF(event.x / width, event.y / height)
        if (config.navigator.getAction(pos) == NavigationRegion.MENU) {
            pageClickListener?.invoke()
        } else {
            // Быстрая пара тапов: первый single tap отменён GestureDetector'ом,
            // пробрасываем действие второго (см. MangaPageImageView.onTouchEvent).
            handleTap(event)
        }
        return true
    }

    /** Скролл вверх на 3/4 экрана (плавно при transitionsEnabled). */
    private fun scrollUp() {
        if (config.transitionsEnabled) {
            recycler.smoothScrollBy(0, -scrollDistance)
        } else {
            recycler.scrollBy(0, -scrollDistance)
        }
    }

    /** Скролл вниз на 3/4 экрана (плавно при transitionsEnabled). */
    private fun scrollDown() {
        if (config.transitionsEnabled) {
            recycler.smoothScrollBy(0, scrollDistance)
        } else {
            recycler.scrollBy(0, scrollDistance)
        }
    }
}

/** Каденс автопрокрутки: задержка цикла = окно накопления одного тика. */
private const val FRAME_DELTA_MS = 16L
