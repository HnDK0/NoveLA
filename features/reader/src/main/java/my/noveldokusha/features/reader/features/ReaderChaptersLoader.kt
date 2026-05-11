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

    var hasLoadingError: Boolean
        get() = _hasLoadingError
        set(value) {
            _hasLoadingError = value
            autoResetJob?.cancel()
            autoResetJob = null
            if (value) {
                autoResetJob = launch {
                    delay(30_000L)
                    _hasLoadingError = false
                    autoResetJob = null
                    android.util.Log.d(TAG, "Auto-reset hasLoadingError after 30s timeout, resuming preload")
                    tryLoadNext()
                }
            } else {
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
                    readerViewHandlersActions.doForceUpdateListViewState()
                }
            }
            val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
                withContext(Dispatchers.Main.immediate) {
                    items.addAll(findInsertIndex(), it)
                    readerViewHandlersActions.doForceUpdateListViewState()
                }
            }
            val remove: suspend (ReaderItem) -> Unit = {
                withContext(Dispatchers.Main.immediate) {
                    items.remove(it)
                    readerViewHandlersActions.doForceUpdateListViewState()
                }
            }
            val success = addChapter(chapterIndex = chapterIndex, insert = insert, insertAll = insertAll, remove = remove)
            readerState = ReaderState.IDLE

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
        readerState = ReaderState.INITIAL_LOAD
        items.clear()
        readerViewHandlersActions.doForceUpdateListViewState()

        if (chapterIndex < 0 || chapterIndex >= orderedChapters.size) {
            readerViewHandlersActions.doShowInvalidChapterDialog()
            return@withContext
        }

        val insert: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.add(it); readerViewHandlersActions.doForceUpdateListViewState() }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.addAll(it); readerViewHandlersActions.doForceUpdateListViewState() }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.remove(it); readerViewHandlersActions.doForceUpdateListViewState() }
        }

        addChapter(chapterIndex = chapterIndex, insert = insert, insertAll = insertAll, remove = remove,
            maintainPosition = readerViewHandlersActions::doMaintainStartPosition)

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
                readerViewHandlersActions.doForceUpdateListViewState()
            }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                items.addAll(listIndex, it); listIndex += it.size
                readerViewHandlersActions.doForceUpdateListViewState()
            }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) {
                if (items.remove(it)) listIndex -= 1
                readerViewHandlersActions.doForceUpdateListViewState()
            }
        }

        val previousIndex = firstItem.chapterIndex - 1
        if (previousIndex < 0) {
            readerViewHandlersActions.doMaintainLastVisiblePosition {
                insert(ReaderItem.BookStart(chapterIndex = previousIndex))
                readerViewHandlersActions.doForceUpdateListViewState()
            }
            readerState = ReaderState.IDLE
            return@withContext
        }

        addChapter(chapterIndex = previousIndex, insert = insert, insertAll = insertAll, remove = remove,
            maintainPosition = readerViewHandlersActions::doMaintainLastVisiblePosition)

        chapterLoadedFlow.emit(ChapterLoaded(chapterIndex = previousIndex, type = ChapterLoaded.Type.Previous))
        readerState = ReaderState.IDLE
    }

    private suspend fun loadNextChapter() = withContext(Dispatchers.Main.immediate) {
        readerState = ReaderState.LOADING

        val lastChapterIndex = items.maxOfOrNull { it.chapterIndex }

        if (lastChapterIndex == null) {
            readerState = ReaderState.IDLE
            return@withContext
        }

        val insert: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.add(it); readerViewHandlersActions.doForceUpdateListViewState() }
        }
        val insertAll: suspend (Collection<ReaderItem>) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.addAll(it); readerViewHandlersActions.doForceUpdateListViewState() }
        }
        val remove: suspend (ReaderItem) -> Unit = {
            withContext(Dispatchers.Main.immediate) { items.remove(it); readerViewHandlersActions.doForceUpdateListViewState() }
        }

        if (lastChapterIndex >= orderedChapters.lastIndex) {
            if (items.none { it is ReaderItem.BookEnd }) {
                insert(ReaderItem.BookEnd(chapterIndex = lastChapterIndex + 1))
                readerViewHandlersActions.doForceUpdateListViewState()
            }
            readerState = ReaderState.IDLE
            return@withContext
        }

        val nextIndex = lastChapterIndex + 1

        addChapter(chapterIndex = nextIndex, insert = insert, insertAll = insertAll, remove = remove)
        chapterLoadedFlow.emit(ChapterLoaded(chapterIndex = nextIndex, type = ChapterLoaded.Type.Next))
        readerState = ReaderState.IDLE
    }

    private suspend fun addChapter(
        chapterIndex: Int,
        insert: suspend (ReaderItem) -> Unit,
        insertAll: suspend (Collection<ReaderItem>) -> Unit,
        remove: suspend (ReaderItem) -> Unit,
        maintainPosition: suspend (suspend () -> Unit) -> Unit = { it() },
    ): Boolean? = withContext(Dispatchers.Default) {
        val chapter = orderedChapters.getOrNull(chapterIndex) ?: return@withContext null

        synchronized(loadedChapters) {
            if (loadedChapters.contains(chapter.url)) {
                android.util.Log.d(TAG, "addChapter: chapter ${chapter.url} already loaded or loading, skipping")
                return@withContext null
            }
            loadedChapters.add(chapter.url)
        }

        try {
            _addChapterInternal(chapter, chapterIndex, insert, insertAll, remove, maintainPosition)
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
    ): Boolean? {

        val itemProgressBar = ReaderItem.Progressbar(chapterIndex = chapterIndex)
        var chapterItemPosition = 0
        val titleOriginal = chapter.title
        var titleTranslated: String = titleOriginal

        val itemTitle = ReaderItem.Title(
            chapterUrl = chapter.url,
            chapterIndex = chapterIndex,
            text = titleOriginal,
            chapterItemPosition = chapterItemPosition,
        ).copy(textTranslated = titleTranslated)
        chapterItemPosition += 1

        maintainPosition {
            insert(ReaderItem.Divider(chapterIndex = chapterIndex))
            insert(itemTitle)
            insert(itemProgressBar)
            readerViewHandlersActions.doForceUpdateListViewState()
        }

        when (val res = readerRepository.downloadChapter(chapter.url)) {
            is Response.Success -> {
                if (!isValidChapterContent(res.data)) {
                    withContext(Dispatchers.Main.immediate) {
                        hasLoadingError = true
                        android.util.Log.w(TAG, "Chapter content invalid, stopping auto-loading. Preview: ${res.data.take(200)}")
                    }
                    maintainPosition {
                        remove(itemProgressBar)
                        remove(itemTitle)
                        items.removeAll { it is ReaderItem.Divider && it.chapterIndex == chapterIndex }
                        val preview = res.data.take(300).ifBlank { "<null>" }
                        val userMessage = if (java.util.Locale.getDefault().language == "ru")
                            "Ошибка контента: защита Cloudflare, пустая глава или требуется авторизация. Попробуйте открыть в браузере, чтобы пройти проверку.\n\nПолучено: $preview"
                        else
                            "Invalid content: Cloudflare protection, empty chapter, or login required. Try opening in browser to pass the check.\n\nReceived: $preview"
                        insert(ReaderItem.Error(chapterIndex = chapterIndex, chapterUrl = chapter.url, text = userMessage))
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
                                val dbTranslations = withContext(Dispatchers.IO) {
                                    chapterTranslationDao.getTranslations(
                                        chapterUrl = chapter.url,
                                        sourceLang = sourceLang,
                                        targetLang = targetLang
                                    ).associate { it.originalText to it.translatedText }
                                }

                                val bodyTexts = itemsOriginal.filterIsInstance<ReaderItem.Body>().map { it.text }
                                val allTextsToTranslate = buildList {
                                    if (!dbTranslations.containsKey(titleOriginal)) add(titleOriginal)
                                    addAll(bodyTexts)
                                }

                                val result = if (dbTranslations.isNotEmpty()) {
                                    val missingFromDb = allTextsToTranslate.filter { !dbTranslations.containsKey(it) }

                                    if (missingFromDb.isEmpty()) {
                                        android.util.Log.d(TAG, "Using full DB cache for chapter ${chapter.title} (${dbTranslations.size} translations)")
                                        titleTranslated = dbTranslations[titleOriginal] ?: titleOriginal
                                        itemsOriginal.map {
                                            if (it is ReaderItem.Body) it.copy(textTranslated = dbTranslations[it.text] ?: it.text)
                                            else it
                                        }
                                    } else {
                                        android.util.Log.d(TAG, "DB cache partial: ${dbTranslations.size}/${allTextsToTranslate.size}, translating ${missingFromDb.size} missing")
                                        
                                        val extraTranslations = mutableMapOf<String, String>()
                                        val uniqueMissing = missingFromDb.distinct()
                                        uniqueMissing.chunked(25).forEach { chunk ->
                                            try {
                                                val chunkResults = batchTranslator.invoke(chunk)
                                                extraTranslations.putAll(chunkResults)
                                            } catch (e: Exception) {
                                                android.util.Log.e(TAG, "Partial chunk translation failed: ${e.message}")
                                            }
                                        }

                                        if (extraTranslations.isNotEmpty()) {
                                            launch(Dispatchers.IO) {
                                                val entities = extraTranslations.map { (original, translated) ->
                                                    ChapterTranslation(
                                                        chapterUrl = chapter.url,
                                                        sourceLang = sourceLang,
                                                        targetLang = targetLang,
                                                        originalText = original,
                                                        translatedText = translated
                                                    )
                                                }
                                                chapterTranslationDao.insertReplace(entities)
                                            }
                                        }

                                        val fullTranslations = dbTranslations + extraTranslations
                                        titleTranslated = fullTranslations[titleOriginal] ?: titleOriginal
                                        itemsOriginal.map {
                                            if (it is ReaderItem.Body) it.copy(textTranslated = fullTranslations[it.text] ?: it.text)
                                            else it
                                        }
                                    }
                                } else {
                                    val allTranslations = translateAndCacheWithTitle(
                                        itemsOriginal, titleOriginal, batchTranslator,
                                        chapter.url, sourceLang, targetLang
                                    )
                                    titleTranslated = allTranslations.second
                                    allTranslations.first
                                }
                                result
                            } else {
                                // Батч недоступен
                                if (translatorIsOnline()) {
                                    // Для онлайн-провайдеров (Gemini/OpenAI) поабзацный перевод недопустим:
                                    // сожрёт лимиты и токены. Бросаем ошибку — пользователь увидит
                                    // ReaderItem.Error и сможет сделать retry когда translator готов.
                                    android.util.Log.e(TAG, "Batch translator unavailable for online provider — refusing per-paragraph fallback")
                                    throw IllegalStateException(
                                        if (java.util.Locale.getDefault().language == "ru")
                                            "Переводчик ещё не готов. Попробуйте ещё раз."
                                        else
                                            "Translator not ready yet. Please retry."
                                    )
                                }
                                // MLKit — поабзацный перевод допустим
                                android.util.Log.d(TAG, "Using paragraph-by-paragraph translation (MLKit offline)")
                                titleTranslated = try {
                                    translatorTranslateOrNull(titleOriginal) ?: titleOriginal
                                } catch (e: Exception) {
                                    android.util.Log.w(TAG, "Title translation failed, using original: ${e.message}")
                                    titleOriginal
                                }
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
                if (translatorIsActive() && titleTranslated != titleOriginal) {
                    val sourceLang = translatorSourceLanguageOrNull()
                    val targetLang = translatorTargetLanguageOrNull()
                    if (sourceLang != null && targetLang != null) {
                        launch(Dispatchers.IO) {
                            chapterTranslationDao.insertReplace(listOf(
                                ChapterTranslation(
                                    chapterUrl = chapter.url,
                                    sourceLang = sourceLang,
                                    targetLang = targetLang,
                                    originalText = titleOriginal,
                                    translatedText = titleTranslated,
                                )
                            ))
                        }
                    }
                }

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

    private suspend fun translateAndCacheWithTitle(
        itemsOriginal: List<ReaderItem>,
        titleOriginal: String,
        batchTranslator: suspend (List<String>) -> Map<String, String>,
        chapterUrl: String,
        sourceLang: String,
        targetLang: String,
    ): Pair<List<ReaderItem>, String> {
        val bodyTexts = itemsOriginal.filterIsInstance<ReaderItem.Body>().map { it.text }
        val allTexts = buildList {
            add(titleOriginal)
            addAll(bodyTexts)
        }.distinct() // Avoid translating identical strings multiple times

        android.util.Log.d(TAG, "translateAndCacheWithTitle: ${allTexts.size} unique texts (from 1 title + ${bodyTexts.size} paragraphs)")
        
        val translations = mutableMapOf<String, String>()
        allTexts.chunked(25).forEach { chunk ->
            try {
                val chunkResults = batchTranslator.invoke(chunk)
                translations.putAll(chunkResults)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Chunk translation failed: ${e.message}")
            }
        }

        if (translations.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val entities = translations.map { (original, translated) ->
                    ChapterTranslation(
                        chapterUrl = chapterUrl,
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        originalText = original,
                        translatedText = translated
                    )
                }
                chapterTranslationDao.insertReplace(entities)
                android.util.Log.d(TAG, "translateAndCacheWithTitle: saved ${entities.size} translations to DB")
            }
        }

        val translatedItems = itemsOriginal.map {
            if (it is ReaderItem.Body) it.copy(textTranslated = translations[it.text] ?: it.text)
            else it
        }
        val translatedTitle = translations[titleOriginal] ?: titleOriginal
        return Pair(translatedItems, translatedTitle)
    }

    @Deprecated("Use translateAndCacheWithTitle to include title in the batch")
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

        withContext(Dispatchers.IO) {
            val entities = textsToTranslate.map { original ->
                ChapterTranslation(
                    chapterUrl = chapterUrl,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    originalText = original,
                    translatedText = translations[original] ?: original
                )
            }
            chapterTranslationDao.insertReplace(entities)
            android.util.Log.d(TAG, "translateAndCache: saved ${entities.size} translations to DB")
        }

        return itemsOriginal.map {
            if (it is ReaderItem.Body) it.copy(textTranslated = translations[it.text] ?: it.text)
            else it
        }
    }

    companion object {
        private const val TAG = "ReaderChaptersLoader"
    }
}