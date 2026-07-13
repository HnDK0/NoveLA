package my.noveldokusha.features.reader.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.core.isValidChapterContent
import my.noveldokusha.features.reader.ReaderRepository
import my.noveldokusha.features.reader.domain.ChapterLoaded
import my.noveldokusha.features.reader.domain.ChapterState
import my.noveldokusha.features.reader.domain.ChapterStats
import my.noveldokusha.features.reader.domain.ChapterUrl
import my.noveldokusha.features.reader.domain.InitialPositionChapter
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.domain.ReaderState
import my.noveldokusha.features.reader.domain.ReadingChapterPosStats
import my.noveldokusha.features.reader.domain.indexOfReaderItem
import my.noveldokusha.features.reader.tools.textToItemsConverter
import my.noveldokusha.features.reader.ui.ReaderViewHandlersActions
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterTranslation
import kotlin.coroutines.CoroutineContext

internal class ReaderChaptersLoader(
    private val readerRepository: ReaderRepository,
    private val translatorTranslateOrNull: suspend (text: String) -> String?,
    private val translatorIsActive: () -> Boolean,
    private val translatorSourceLanguageOrNull: () -> String?,
    private val translatorTargetLanguageOrNull: () -> String?,
    private val translatorProvider: () -> String,
    // true = Gemini/OpenAI/online — поабзацный перевод недопустим, только батч
    private val translatorIsOnline: () -> Boolean,
    private val translatorBatchTranslateOrNull: () -> (suspend (List<String>) -> Map<String, String>)?,
    /**
     * Переводит заголовок главы через Google PA → Free, не тратя токены Gemini/OpenAI.
     * Возвращает null если перевод не удался — в этом случае остаётся оригинал.
     */
    private val translatorTranslateTitleOrNull: suspend (title: String, sourceLang: String, targetLang: String) -> String?,
    private val bookUrl: String,
    val orderedChapters: List<Chapter>,
    @Volatile var readerState: ReaderState,
    private val readerViewHandlersActions: ReaderViewHandlersActions,
    private val chapterTranslationDao: ChapterTranslationDao,
    private val regexRulesProvider: () -> List<my.noveldokusha.core.models.RegexRule> = { emptyList() },
) : CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main.immediate

    private sealed interface LoadChapter {
        enum class Type { RestartInitial, Initial, Previous, Next }
        data class RestartInitialChapter(val state: ChapterState) : LoadChapter
        data class Initial(val chapterIndex: Int) : LoadChapter
        data object Previous : LoadChapter
        data object Next : LoadChapter
    }

    val chaptersStats = mutableMapOf<ChapterUrl, ChapterStats>()
    val loadedChapters = mutableSetOf<ChapterUrl>()
    val chapterLoadedFlow = MutableSharedFlow<ChapterLoaded>()
    private val items: MutableList<ReaderItem> = ArrayList()
    private val loaderQueue = mutableSetOf<LoadChapter.Type>()
    private val chapterLoaderFlow = MutableSharedFlow<LoadChapter>(extraBufferCapacity = 1)

    private @Volatile var _hasLoadingError = false
    private var autoResetJob: kotlinx.coroutines.Job? = null
    private var errorRetryCount = 0
    private @Volatile var pendingPruneChapterIndex: Int? = null

    var hasLoadingError: Boolean
        get() = _hasLoadingError
        set(value) {
            _hasLoadingError = value
            autoResetJob?.cancel()
            autoResetJob = null
            if (value) {
                errorRetryCount++
                val delayMs = (5_000L * errorRetryCount).coerceAtMost(60_000L)
                autoResetJob = launch {
                    delay(delayMs)
                    _hasLoadingError = false
                    autoResetJob = null
                    android.util.Log.d(TAG, "Auto-reset hasLoadingError after ${delayMs/1000}s timeout (attempt $errorRetryCount), resuming preload")
                    tryLoadNext()
                }
            } else {
                errorRetryCount = 0
                loaderQueue.remove(LoadChapter.Type.Next)
            }
        }

    init {
        startChapterLoaderWatcher()
    }

    fun getItems(): List<ReaderItem> = items

    fun getItemContext(itemIndex: Int, chapterUrl: String): ReadingChapterPosStats? {
        val item = items.getOrNull(itemIndex) ?: return null
        if (item !is ReaderItem.Position) return null
        val chapterStats = chaptersStats[chapterUrl] ?: return null
        return ReadingChapterPosStats(
            chapterIndex = item.chapterIndex,
            chapterCount = orderedChapters.size,
            chapterItemPosition = item.chapterItemPosition,
            chapterItemsCount = chapterStats.itemsCount,
            chapterTitle = chapterStats.chapter.title,
            chapterUrl = chapterStats.chapter.url,
        )
    }

    fun getItemContext(chapterIndex: Int, chapterItemPosition: Int): ReadingChapterPosStats? {
        val itemIndex = indexOfReaderItem(
            list = items,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition
        )
        val item = items.getOrNull(itemIndex) ?: return null
        if (item !is ReaderItem.Position) return null
        val chapterStats = chaptersStats[item.chapterUrl] ?: return null
        return ReadingChapterPosStats(
            chapterIndex = chapterIndex,
            chapterCount = orderedChapters.size,
            chapterItemPosition = item.chapterItemPosition,
            chapterItemsCount = chapterStats.itemsCount,
            chapterTitle = chapterStats.chapter.title,
            chapterUrl = chapterStats.chapter.url,
        )
    }

    fun isLastChapter(chapterIndex: Int): Boolean = chapterIndex == orderedChapters.lastIndex
    fun isChapterIndexLoaded(chapterIndex: Int): Boolean {
        return orderedChapters.getOrNull(chapterIndex)?.url
            ?.let { loadedChapters.contains(it) }
            ?: false
    }
    fun isChapterIndexValid(chapterIndex: Int) = chapterIndex in 0 until orderedChapters.size

    @Synchronized fun tryLoadInitial(chapterIndex: Int) {
        android.util.Log.d("READER_DEBUG", "tryLoadInitial called, chapterIndex=$chapterIndex, stack=${Thread.currentThread().stackTrace[2]}")
        if (LoadChapter.Type.Initial in loaderQueue) return
        loaderQueue.add(LoadChapter.Type.Initial)
        launch { chapterLoaderFlow.emit(LoadChapter.Initial(chapterIndex = chapterIndex)) }
    }

    @Synchronized fun tryLoadRestartedInitial(chapterLastState: ChapterState) {
        if (LoadChapter.Type.RestartInitial in loaderQueue) return
        loaderQueue.add(LoadChapter.Type.RestartInitial)
        launch { chapterLoaderFlow.emit(LoadChapter.RestartInitialChapter(state = chapterLastState)) }
    }

    @Synchronized fun tryLoadPrevious() {
        if (LoadChapter.Type.Previous in loaderQueue) return
        loaderQueue.add(LoadChapter.Type.Previous)
        launch { chapterLoaderFlow.emit(LoadChapter.Previous) }
    }

    @Synchronized fun tryLoadNext() {
        if (hasLoadingError) {
            android.util.Log.d(TAG, "tryLoadNext: blocked due to previous loading error")
            return
        }
        if (LoadChapter.Type.Next in loaderQueue) return
        loaderQueue.add(LoadChapter.Type.Next)
        launch { chapterLoaderFlow.emit(LoadChapter.Next) }
    }

    fun clearErrorAndLoadNext() {
        hasLoadingError = false
        tryLoadNext()
    }

    fun retryChapter(chapterIndex: Int) {
        if (chapterIndex < 0 || chapterIndex >= orderedChapters.size) {
            android.util.Log.e(TAG, "retryChapter: invalid chapterIndex $chapterIndex, size ${orderedChapters.size}")
            return
        }

        launch(Dispatchers.Main.immediate) {
            val chapterUrl = orderedChapters[chapterIndex].url

            hasLoadingError = false
            chaptersStats.remove(chapterUrl)
            loadedChapters.remove(chapterUrl)
            loaderQueue.remove(LoadChapter.Type.Next)

            items.removeAll { it.chapterIndex == chapterIndex }
            readerViewHandlersActions.doForceUpdateListViewState()

            withContext(Dispatchers.IO) {
                readerRepository.deleteChapterBody(chapterUrl)
            }

            readerState = ReaderState.LOADING

            fun findInsertIndex(): Int {
                val idx = items.indexOfFirst { it.chapterIndex > chapterIndex }
                return if (idx == -1) items.size else idx
            }

        val insert: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                items.add(findInsertIndex(), it)
            }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                items.addAll(findInsertIndex(), it)
            }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                items.remove(it)
            }
        }
            val success = addChapter(chapterIndex = chapterIndex, insert = insert, insertAll = insertAll, remove = remove)
            readerState = ReaderState.IDLE
            flushPendingPrune()

            if (success == true && !hasLoadingError) {
                android.util.Log.d(TAG, "retryChapter: auto-resuming preload for next chapter")
                tryLoadNext()
            }
        }
    }

    fun reload() {
        coroutineContext.cancelChildren()
        loaderQueue.clear()
        launch(Dispatchers.Main.immediate) {
            items.clear()
            readerViewHandlersActions.doForceUpdateListViewState()
            loadedChapters.clear()
            hasLoadingError = false
            readerState = ReaderState.INITIAL_LOAD
            startChapterLoaderWatcher()
        }
    }

    private fun startChapterLoaderWatcher() {
        launch {
            chapterLoaderFlow.collect {
                when (it) {
                    is LoadChapter.Initial -> {
                        loadInitialChapter(chapterIndex = it.chapterIndex)
                        removeQueueItem(LoadChapter.Type.Initial)
                    }
                    is LoadChapter.Next -> {
                        loadNextChapter()
                        removeQueueItem(LoadChapter.Type.Next)
                    }
                    is LoadChapter.Previous -> {
                        loadPreviousChapter()
                        removeQueueItem(LoadChapter.Type.Previous)
                    }
                    is LoadChapter.RestartInitialChapter -> {
                        loadRestartedInitialChapter(chapterLastState = it.state)
                        removeQueueItem(LoadChapter.Type.RestartInitial)
                    }
                }
            }
        }
    }

    @Synchronized private fun removeQueueItem(type: LoadChapter.Type) {
        loaderQueue.remove(type)
    }

    private suspend fun flushPendingPrune() {
        pendingPruneChapterIndex?.let { chapterIndex ->
            pendingPruneChapterIndex = null
            pruneItems(chapterIndex)
        }
    }

    private suspend fun loadRestartedInitialChapter(
        chapterLastState: ChapterState
    ) = withContext(Dispatchers.Main.immediate) {
        readerState = ReaderState.INITIAL_LOAD
        items.clear()
        readerViewHandlersActions.doForceUpdateListViewState()

        val insert: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.add(it) }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.addAll(it) }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.remove(it) }
        }

        val index = orderedChapters.indexOfFirst { it.url == chapterLastState.chapterUrl }
        if (index == -1) {
            readerViewHandlersActions.doShowInvalidChapterDialog()
            return@withContext
        }

        addChapter(chapterIndex = index, insert = insert, insertAll = insertAll, remove = remove,
            maintainPosition = readerViewHandlersActions::doMaintainStartPosition)

        readerViewHandlersActions.doForceUpdateListViewState()
        readerViewHandlersActions.doSetInitialPosition(
            InitialPositionChapter(
                chapterIndex = index,
                chapterItemPosition = chapterLastState.chapterItemPosition,
                chapterItemOffset = chapterLastState.offset
            )
        )
        readerState = ReaderState.IDLE
        chapterLoadedFlow.emit(ChapterLoaded(chapterIndex = index, type = ChapterLoaded.Type.Initial))
    }

    private suspend fun loadInitialChapter(
        chapterIndex: Int
    ) = withContext(Dispatchers.Main.immediate) {
        android.util.Log.d("READER_DEBUG", "loadInitialChapter START, chapterIndex=$chapterIndex")
        readerState = ReaderState.INITIAL_LOAD
        items.clear()
        readerViewHandlersActions.doForceUpdateListViewState()

        if (chapterIndex < 0 || chapterIndex >= orderedChapters.size) {
            readerViewHandlersActions.doShowInvalidChapterDialog()
            return@withContext
        }

        val insert: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.add(it) }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.addAll(it) }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.remove(it) }
        }

        addChapter(
            chapterIndex = chapterIndex,
            insert = insert, insertAll = insertAll, remove = remove,
            maintainPosition = readerViewHandlersActions::doMaintainStartPosition,
        )

        val chapter = orderedChapters[chapterIndex]
        val initialPosition = readerRepository.getInitialChapterItemPosition(
            bookUrl = bookUrl,
            chapterIndex = chapter.position,
            chapter = chapter,
        )
        readerViewHandlersActions.doForceUpdateListViewState()
        readerViewHandlersActions.doSetInitialPosition(initialPosition)
        chapterLoadedFlow.emit(ChapterLoaded(chapterIndex = chapterIndex, type = ChapterLoaded.Type.Initial))
        readerState = ReaderState.IDLE
    }

    private suspend fun loadPreviousChapter() = withContext(Dispatchers.Main.immediate) {
        readerState = ReaderState.LOADING

        val firstItem = items.firstOrNull()
        if (firstItem == null) {
            readerState = ReaderState.IDLE
            return@withContext
        }
        if (firstItem is ReaderItem.BookStart) {
            readerState = ReaderState.IDLE
            return@withContext
        }

        var listIndex = 0
        val insert: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                items.add(listIndex, it); listIndex += 1
            }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                items.addAll(listIndex, it); listIndex += it.size
            }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                if (items.remove(it)) listIndex -= 1
            }
        }

        val previousIndex = firstItem.chapterIndex - 1
        if (previousIndex < 0) {
            readerViewHandlersActions.doMaintainLastVisiblePosition {
                insert(ReaderItem.BookStart(chapterIndex = previousIndex))
                readerViewHandlersActions.doForceUpdateListViewState()
            }
            readerState = ReaderState.IDLE
            flushPendingPrune()
            return@withContext
        }

        addChapter(chapterIndex = previousIndex, insert = insert, insertAll = insertAll, remove = remove,
            maintainPosition = readerViewHandlersActions::doMaintainLastVisiblePosition,
            skipLoadedCheck = true,
            maintainOnSuccess = true)

        chapterLoadedFlow.emit(ChapterLoaded(chapterIndex = previousIndex, type = ChapterLoaded.Type.Previous))
        readerState = ReaderState.IDLE
        flushPendingPrune()
    }

    private suspend fun loadNextChapter() = withContext(Dispatchers.Main.immediate) {
        readerState = ReaderState.LOADING

        val lastChapterIndex = items.maxOfOrNull { it.chapterIndex }

        if (lastChapterIndex == null) {
            readerState = ReaderState.IDLE
            return@withContext
        }

        val insert: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.add(it) }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.addAll(it) }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.remove(it) }
        }

        if (lastChapterIndex >= orderedChapters.lastIndex) {
            if (items.none { it is ReaderItem.BookEnd }) {
                insert(ReaderItem.BookEnd(chapterIndex = lastChapterIndex + 1))
                readerViewHandlersActions.doForceUpdateListViewState()
            }
            readerState = ReaderState.IDLE
            flushPendingPrune()
            return@withContext
        }

        val nextIndex = lastChapterIndex + 1

        addChapter(chapterIndex = nextIndex, insert = insert, insertAll = insertAll, remove = remove)
        chapterLoadedFlow.emit(ChapterLoaded(chapterIndex = nextIndex, type = ChapterLoaded.Type.Next))
        readerState = ReaderState.IDLE
        flushPendingPrune()
    }

    private suspend fun addChapter(
        chapterIndex: Int,
        insert: suspend (ReaderItem) -> Unit,
        insertAll: suspend (Collection<ReaderItem>) -> Unit,
        remove: suspend (ReaderItem) -> Unit,
        maintainPosition: suspend (suspend () -> Unit) -> Unit = { withContext(Dispatchers.Main.immediate) { it() } },
        showLoadingState: Boolean = true,
        skipLoadedCheck: Boolean = false,
        maintainOnSuccess: Boolean = false,
    ): Boolean? = withContext(Dispatchers.Default) {
        val chapter = orderedChapters.getOrNull(chapterIndex) ?: return@withContext null

        synchronized(loadedChapters) {
            if (!skipLoadedCheck && loadedChapters.contains(chapter.url)) {
                android.util.Log.d(TAG, "addChapter: chapter ${chapter.url} already loaded or loading, skipping")
                return@withContext null
            }
            loadedChapters.add(chapter.url)
        }

        try {
            _addChapterInternal(chapter, chapterIndex, insert, insertAll, remove, maintainPosition, showLoadingState, maintainOnSuccess)
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.w(TAG, "addChapter: cancelled for chapter ${chapter.url}, cleaning up")
            synchronized(loadedChapters) {
                loadedChapters.remove(chapter.url)
            }
            null
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "addChapter: unexpected error for chapter ${chapter.url}", e)
            synchronized(loadedChapters) {
                loadedChapters.remove(chapter.url)
            }
            false
        }
    }

    private suspend fun _addChapterInternal(
        chapter: Chapter,
        chapterIndex: Int,
        insert: suspend (ReaderItem) -> Unit,
        insertAll: suspend (Collection<ReaderItem>) -> Unit,
        remove: suspend (ReaderItem) -> Unit,
        maintainPosition: suspend (suspend () -> Unit) -> Unit,
        showLoadingState: Boolean = true,
        maintainOnSuccess: Boolean = false,
    ): Boolean? {

        val itemProgressBar = ReaderItem.Progressbar(chapterIndex = chapterIndex)
        var chapterItemPosition = 0
        val titleOriginal = chapter.title
        var titleTranslated: String = titleOriginal

        // Проверяем перевод заголовка в БД до загрузки тела главы
        if (translatorIsActive()) {
            val sourceLang = translatorSourceLanguageOrNull()
            val targetLang = translatorTargetLanguageOrNull()
            if (sourceLang != null && targetLang != null) {
                val cachedTitle = withContext(Dispatchers.IO) {
                    chapterTranslationDao.getTranslations(
                        chapterUrl = chapter.url,
                        sourceLang = sourceLang,
                        targetLang = targetLang
                    )?.titleTranslation?.takeIf { it.isNotEmpty() }
                }
                if (cachedTitle != null) {
                    titleTranslated = cachedTitle
                    android.util.Log.d(TAG, "Title translation found in DB: '$titleOriginal' -> '$titleTranslated'")
                }
            }
        }

        val itemTitle = ReaderItem.Title(
            chapterUrl = chapter.url,
            chapterIndex = chapterIndex,
            text = titleOriginal,
            chapterItemPosition = chapterItemPosition,
        )
        chapterItemPosition += 1

        if (showLoadingState) {
            android.util.Log.d("READER_DEBUG", "inserting Progressbar for chapterIndex=$chapterIndex")
            maintainPosition {
                insert(ReaderItem.Divider(chapterIndex = chapterIndex))
                insert(itemTitle)
                insert(itemProgressBar)
                readerViewHandlersActions.doForceUpdateListViewState()
            }
        }

        when (val res = readerRepository.downloadChapter(chapter.url)) {
            is Response.Success -> {
                if (!isValidChapterContent(res.data)) {
                    withContext(Dispatchers.Main.immediate) {
                        hasLoadingError = true
                        android.util.Log.w(TAG, "Chapter content invalid (possibly Cloudflare or Login), stopping auto-loading. Preview: ${res.data.take(160)}")
                    }
                    maintainPosition {
                        // 1. Сначала подготавливаем сообщения
                        val preview = res.data.take(400).ifBlank { "<empty body>" }
                        val userMessage = if (java.util.Locale.getDefault().language == "ru")
                            "Ошибка контента: защита Cloudflare, пустая глава или требуется login. Попробуйте открыть в браузере.\n\nКод: ${preview.take(100)}..."
                        else
                            "Content error: Cloudflare, empty chapter, or login required. Try opening in browser.\n\nCode snippet: ${preview.take(100)}..."

                        // 2. Атомарно модифицируем список данных, не дёргая адаптер на каждое удаление
                        // (Предполагается, что remove/insert у тебя работают напрямую с внутренним массивом)
                        val updatedItems = items.toMutableList().apply {
                            remove(itemProgressBar)
                            remove(itemTitle)
                            removeAll { it is ReaderItem.Divider && it.chapterIndex == chapterIndex }
                            // Добавляем ошибку вместо удаленного прогресс-бара
                            add(ReaderItem.Error(chapterIndex = chapterIndex, chapterUrl = chapter.url, text = userMessage))
                        }

                        // 3. Перезаписываем список в адаптере целиком
                        items.clear()
                        items.addAll(updatedItems)

                        // 4. Только теперь уведомляем UI (внутри или сразу после этого блока)
                        readerViewHandlersActions.doForceUpdateListViewState()
                    }
                    return@_addChapterInternal false
                }

                val itemsOriginal = textToItemsConverter(
                    chapterUrl = chapter.url,
                    chapterIndex = chapterIndex,
                    chapterItemPositionDisplacement = chapterItemPosition,
                    text = res.data,
                    userRegexRules = regexRulesProvider(),
                )
                chapterItemPosition += itemsOriginal.size

                val itemTranslationAttribution = if (translatorIsActive()) {
                    ReaderItem.TranslateAttribution(chapterIndex = chapterIndex, provider = translatorProvider())
                } else null

                val itemTranslating = if (translatorIsActive()) {
                    ReaderItem.Translating(
                        chapterIndex = chapterIndex,
                        sourceLang = translatorSourceLanguageOrNull() ?: "",
                        targetLang = translatorTargetLanguageOrNull() ?: "",
                    )
                } else null

                if (itemTranslating != null) {
                    maintainPosition {
                        insert(itemTranslating)
                        readerViewHandlersActions.doForceUpdateListViewState()
                    }
                }

                val items = try {
                    when {
                        translatorIsActive() -> {
                            val sourceLang = translatorSourceLanguageOrNull() ?: "en"
                            val targetLang = translatorTargetLanguageOrNull() ?: "zh"
                            val batchTranslator = translatorBatchTranslateOrNull()

                            if (batchTranslator != null) {
                                val bodyTexts = itemsOriginal.filterIsInstance<ReaderItem.Body>().map { it.text }
                                val bodyTextToIndex = bodyTexts.withIndex().associate { (idx, text) -> text to idx }

                                // Load existing cache entry (single row per chapter+language)
                                val existingEntry = withContext(Dispatchers.IO) {
                                    chapterTranslationDao.getTranslations(
                                        chapterUrl = chapter.url,
                                        sourceLang = sourceLang,
                                        targetLang = targetLang
                                    )
                                }

                                // Parse cached body translations
                                val cachedBody: Map<Int, String> = if (existingEntry != null) {
                                    try {
                                        val arr = org.json.JSONArray(existingEntry.translatedParagraphs)
                                        (0 until arr.length()).associate { it to arr.getString(it) }
                                    } catch (e: org.json.JSONException) {
                                        emptyMap()
                                    }
                                } else {
                                    emptyMap()
                                }

                                var needsSave = false
                                var translatedParagraphsJson = existingEntry?.translatedParagraphs ?: "[]"
                                var titleSaved = existingEntry?.titleTranslation ?: ""

                                val translatedItems = if (cachedBody.isNotEmpty()) {
                                    val missingIndices = bodyTexts.indices.filter { it !in cachedBody }

                                    if (missingIndices.isEmpty()) {
                                        android.util.Log.d(TAG, "Using full DB cache for chapter ${chapter.title} (${cachedBody.size} body translations)")
                                        itemsOriginal.map { item ->
                                            if (item is ReaderItem.Body) {
                                                val idx = bodyTextToIndex[item.text] ?: return@map item
                                                item.copy(textTranslated = cachedBody[idx] ?: item.text)
                                            } else item
                                        }
                                    } else {
                                        val missingTexts = missingIndices.map { bodyTexts[it] }
                                        android.util.Log.d(TAG, "DB cache partial: ${cachedBody.size}/${bodyTexts.size}, translating ${missingTexts.size} missing body paragraphs")
                                        val extraTranslations = withContext(Dispatchers.IO) {
                                            kotlinx.coroutines.withTimeout(60_000L) {
                                                batchTranslator.invoke(missingTexts)
                                            }
                                        }

                                        val fullBody = cachedBody.toMutableMap()
                                        missingIndices.forEach { idx ->
                                            fullBody[idx] = extraTranslations[bodyTexts[idx]] ?: bodyTexts[idx]
                                        }
                                        translatedParagraphsJson = org.json.JSONArray(
                                            bodyTexts.indices.map { fullBody[it] ?: bodyTexts[it] }
                                        ).toString()
                                        needsSave = true

                                        itemsOriginal.map { item ->
                                            if (item is ReaderItem.Body) {
                                                val idx = bodyTextToIndex[item.text] ?: return@map item
                                                item.copy(textTranslated = fullBody[idx] ?: item.text)
                                            } else item
                                        }
                                    }
                                } else {
                                    val result = translateAndCacheBodiesOnly(
                                        itemsOriginal, batchTranslator,
                                        chapter.url, sourceLang, targetLang
                                    )
                                    translatedParagraphsJson = result.second
                                    needsSave = true
                                    result.first
                                }

                                // Translate title if not cached
                                if (titleTranslated == titleOriginal) {
                                    try {
                                        val translated = withContext(Dispatchers.IO) {
                                            translatorTranslateTitleOrNull(titleOriginal, sourceLang, targetLang)
                                        }
                                        if (translated != null) {
                                            titleTranslated = translated
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w(TAG, "Title translation failed (offline?): ${e.message}")
                                    }
                                }
                                if (titleTranslated != titleOriginal && titleSaved != titleTranslated) {
                                    titleSaved = titleTranslated
                                    needsSave = true
                                }

                                // Save combined entry synchronously if anything changed
                                if (needsSave) {
                                    withContext(Dispatchers.IO) {
                                        chapterTranslationDao.insertReplace(
                                            ChapterTranslation(
                                                chapterUrl = chapter.url,
                                                sourceLang = sourceLang,
                                                targetLang = targetLang,
                                                translatedParagraphs = translatedParagraphsJson,
                                                titleTranslation = titleSaved,
                                            )
                                        )
                                    }
                                }

                                translatedItems
                            } else {
                                // Батч недоступен
                                if (translatorIsOnline()) {
                                    android.util.Log.e(TAG, "Batch translator unavailable for online provider — refusing per-paragraph fallback")
                                    throw IllegalStateException(
                                        if (java.util.Locale.getDefault().language == "ru")
                                            "Переводчик ещё не готов. Попробуйте ещё раз."
                                        else
                                            "Translator not ready yet. Please retry."
                                    )
                                }
                                // MLKit — поабзацный перевод допустим, заголовок не трогаем
                                android.util.Log.d(TAG, "Using paragraph-by-paragraph translation (MLKit offline)")
                                itemsOriginal.map {
                                    if (it is ReaderItem.Body) it.copy(textTranslated = translatorTranslateOrNull(it.text))
                                    else it
                                }
                            }
                        }
                        else -> itemsOriginal
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Translation failed for chapter ${chapter.title}: ${e.message}", e)
                    withContext(Dispatchers.Main.immediate) {
                        chaptersStats[chapter.url] = ChapterStats(
                            chapter = chapter,
                            itemsCount = 1,
                            orderedChaptersIndex = chapterIndex
                        )
                        hasLoadingError = true
                    }
                    maintainPosition {
                        remove(itemProgressBar)
                        itemTranslating?.let { remove(it) }
                        remove(itemTitle)
                        items.removeAll { it is ReaderItem.Divider && it.chapterIndex == chapterIndex }
                        val userMessage = if (java.util.Locale.getDefault().language == "ru")
                            "Ошибка перевода: ${e.message ?: "неизвестная ошибка"}\n\nПроверьте настройки переводчика и попробуйте снова."
                        else
                            "Translation error: ${e.message ?: "unknown error"}\n\nCheck your translator settings and try again."
                        insert(ReaderItem.Error(chapterIndex = chapterIndex, chapterUrl = chapter.url, text = userMessage))
                        readerViewHandlersActions.doForceUpdateListViewState()
                    }
                    return@_addChapterInternal false
                }

                withContext(Dispatchers.Main.immediate) {
                    chaptersStats[chapter.url] = ChapterStats(
                        chapter = chapter,
                        itemsCount = items.size,
                        orderedChaptersIndex = chapterIndex
                    )
                }

                val finalItemTitle = itemTitle.copy(textTranslated = titleTranslated)

                // Do NOT use maintainPosition for success block — it would call
                // setSelection(titleIndex) via doMaintainStartPosition and override
                // the scroll position set by setInitialPosition later.
                // When maintainOnSuccess is true (loadPreviousChapter), use it to
                // preserve scroll position after replacing Progressbar with real content.
                if (maintainOnSuccess) {
                    maintainPosition {
                        remove(itemProgressBar)
                        itemTranslating?.let { remove(it) }
                        withContext(Dispatchers.Main.immediate) {
                            val idx = this@ReaderChaptersLoader.items.indexOf(itemTitle)
                            if (idx != -1) this@ReaderChaptersLoader.items[idx] = finalItemTitle
                        }
                        itemTranslationAttribution?.let { insert(it) }
                        insertAll(items)
                        insert(ReaderItem.Divider(chapterIndex = chapterIndex))
                        readerViewHandlersActions.doForceUpdateListViewState()
                    }
                } else {
                    withContext(Dispatchers.Main.immediate) {
                        val backingList = this@ReaderChaptersLoader.items
                        backingList.remove(itemProgressBar)
                        itemTranslating?.let { backingList.remove(it) }
                        val idx = backingList.indexOf(itemTitle)
                        if (idx != -1) backingList[idx] = finalItemTitle
                        itemTranslationAttribution?.let { backingList.add(it) }
                        backingList.addAll(items)
                        backingList.add(ReaderItem.Divider(chapterIndex = chapterIndex))
                        readerViewHandlersActions.forceUpdateListViewState?.invoke()
                    }
                }
                return@_addChapterInternal true
            }
            is Response.Error -> {
                withContext(Dispatchers.Main.immediate) {
                    chaptersStats[chapter.url] = ChapterStats(
                        chapter = chapter,
                        itemsCount = 1,
                        orderedChaptersIndex = chapterIndex
                    )
                    hasLoadingError = true
                    android.util.Log.w(TAG, "Chapter load error: ${res.message}, stopping further auto-loading")
                }
                maintainPosition {
                    remove(itemProgressBar)
                    remove(itemTitle)
                    items.removeAll { it is ReaderItem.Divider && it.chapterIndex == chapterIndex }
                    val rawDetail = res.exception.message?.takeIf { it.isNotBlank() } ?: res.message
                    val detail = Regex(""":\d+\s+(\[?\w.*?)$""", RegexOption.DOT_MATCHES_ALL)
                        .find(rawDetail)?.groupValues?.get(1)?.trim()
                        ?: rawDetail.lines().lastOrNull { it.isNotBlank() }?.trim()
                        ?: rawDetail
                    val userMessage = if (java.util.Locale.getDefault().language == "ru")
                        "Ошибка загрузки: $detail\n\nВозможные причины: защита Cloudflare, требуется авторизация или проблема с источником. Попробуйте открыть в браузере."
                    else
                        "Load error: $detail\n\nPossible causes: Cloudflare protection, login required, or source issue. Try opening in browser."
                    insert(ReaderItem.Error(chapterIndex = chapterIndex, chapterUrl = chapter.url, text = userMessage))
                    readerViewHandlersActions.doForceUpdateListViewState()
                }
                return@_addChapterInternal false
            }
        }
    }

    suspend fun pruneItems(currentChapterIndex: Int) = withContext(Dispatchers.Main.immediate) {
        if (readerState == ReaderState.LOADING) {
            pendingPruneChapterIndex = currentChapterIndex
            return@withContext
        }

        val minKeep = (currentChapterIndex - WINDOW_BEHIND).coerceAtLeast(0)
        val maxKeep = currentChapterIndex + WINDOW_AHEAD

        val toRemoveChapterIndices = mutableSetOf<Int>()
        val toRemoveItems = mutableListOf<ReaderItem>()

        for (item in items) {
            if (item.chapterIndex < minKeep || item.chapterIndex > maxKeep) {
                toRemoveItems.add(item)
                toRemoveChapterIndices.add(item.chapterIndex)
            }
        }

        if (toRemoveItems.isEmpty()) return@withContext

        val listView = readerViewHandlersActions.listView
        val savedFirstVisible = listView?.firstVisiblePosition ?: 0
        val savedTop = listView?.getChildAt(0)?.top ?: 0
        val removedCountFront = toRemoveItems.count { it.chapterIndex < currentChapterIndex }

        items.removeAll(toRemoveItems)
        readerViewHandlersActions.doForceUpdateListViewState()

        if (listView != null && removedCountFront > 0) {
            val newFirstVisible = (savedFirstVisible - removedCountFront).coerceAtLeast(0)
            listView.setSelectionFromTop(newFirstVisible, savedTop)
        }

        for (chapterIndex in toRemoveChapterIndices) {
            val url = orderedChapters.getOrNull(chapterIndex)?.url ?: continue
            loadedChapters.remove(url)
            chaptersStats.remove(url)
        }
    }

    private suspend fun translateAndCacheBodiesOnly(
        itemsOriginal: List<ReaderItem>,
        batchTranslator: suspend (List<String>) -> Map<String, String>,
        chapterUrl: String,
        sourceLang: String,
        targetLang: String,
    ): Pair<List<ReaderItem>, String> {
        val bodyTexts = itemsOriginal.filterIsInstance<ReaderItem.Body>().map { it.text }
        android.util.Log.d(TAG, "translateAndCacheBodiesOnly: ${bodyTexts.size} body paragraphs")
        val translations = batchTranslator.invoke(bodyTexts)

        val bodyJson = org.json.JSONArray(
            bodyTexts.map { translations[it] ?: it }
        ).toString()

        val translatedItems = itemsOriginal.map { item ->
            if (item is ReaderItem.Body) item.copy(textTranslated = translations[item.text] ?: item.text)
            else item
        }
        return Pair(translatedItems, bodyJson)
    }

    @Deprecated("Use translateAndCacheBodiesOnly for body and translatorTranslateTitleOrNull for title")
    private suspend fun translateAndCache(
        itemsOriginal: List<ReaderItem>,
        textsToTranslate: List<String>,
        batchTranslator: suspend (List<String>) -> Map<String, String>,
        chapterUrl: String,
        sourceLang: String,
        targetLang: String,
    ): List<ReaderItem> {
        if (textsToTranslate.isEmpty()) return itemsOriginal

        android.util.Log.d(TAG, "translateAndCache: translating ${textsToTranslate.size} paragraphs")
        val translations = batchTranslator.invoke(textsToTranslate)

        val missing = textsToTranslate.size - translations.size
        if (missing > 0) android.util.Log.w(TAG, "translateAndCache: $missing paragraphs missing, saving original as fallback")

        val bodyJson = org.json.JSONArray(
            textsToTranslate.map { translations[it] ?: it }
        ).toString()

        withContext(Dispatchers.IO) {
            chapterTranslationDao.insertReplace(
                ChapterTranslation(
                    chapterUrl = chapterUrl,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    translatedParagraphs = bodyJson,
                )
            )
            android.util.Log.d(TAG, "translateAndCache: saved translation to DB")
        }

        return itemsOriginal.map {
            if (it is ReaderItem.Body) it.copy(textTranslated = translations[it.text] ?: it.text)
            else it
        }
    }

    companion object {
        private const val TAG = "ReaderChaptersLoader"
        private const val WINDOW_AHEAD = 10
        private const val WINDOW_BEHIND = 10
    }
}