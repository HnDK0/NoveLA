package my.noveldokusha.features.reader.manager

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.data.AppRepository
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.features.reader.ReaderRepository
import my.noveldokusha.features.reader.domain.ChapterLoaded
import my.noveldokusha.features.reader.domain.ChapterState
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.domain.ReaderState
import my.noveldokusha.features.reader.domain.ReadingChapterPosStats
import my.noveldokusha.features.reader.domain.chapterReadPercentage
import my.noveldokusha.features.reader.features.ReaderChaptersLoader
import my.noveldokusha.features.reader.features.ReaderLiveTranslation
import my.noveldokusha.features.reader.features.ReaderTextToSpeech
import my.noveldokusha.features.reader.services.NarratorMediaControlsService
import my.noveldokusha.features.reader.tools.ChaptersIsReadRoutine
import my.noveldokusha.features.reader.ui.ReaderViewHandlersActions
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_to_speech.Utterance
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.feature.local_database.tables.Chapter
import kotlin.properties.Delegates


internal class ReaderSession(
    val bookUrl: String,
    initialChapterUrl: String,
    private val appRepository: AppRepository,
    private val appPreferences: AppPreferences,
    private val readerRepository: ReaderRepository,
    readerViewHandlersActions: ReaderViewHandlersActions,
    @ApplicationContext private val context: Context,
    translationManager: TranslationManager,
    private val chapterTranslationDao: ChapterTranslationDao,
) {
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("ReaderSession")
    )

    private var chapterUrl: String = initialChapterUrl

    private val readRoutine = ChaptersIsReadRoutine(appRepository)
    private val orderedChapters = mutableListOf<Chapter>()

    private var lastChapterIndex: Int = -1
    private var preloadTriggeredForChapter = -1
    private val sessionCreatedTime = System.currentTimeMillis()

    var bookTitle: String? = null
    var bookCoverUrl: String? = null

    var currentChapter: ChapterState by Delegates.observable(
        ChapterState(
            chapterUrl = chapterUrl,
            chapterItemPosition = 0,
            offset = 0
        )
    ) { _, old, new ->
        chapterUrl = new.chapterUrl
        readerLiveTranslation.currentChapterUrl = new.chapterUrl
        if (
            old.chapterUrl != new.chapterUrl &&
            savePositionMode.value == SavePositionMode.Reading
        ) {
            readerRepository.saveBookLastReadPositionState(bookUrl, new, old)
        }
    }

    private enum class SavePositionMode { Reading, Speaking }

    private val savePositionMode = derivedStateOf {
        if (readerTextToSpeech.isSpeaking.value) SavePositionMode.Speaking else SavePositionMode.Reading
    }

    val readingStats = mutableStateOf<ReadingChapterPosStats?>(null)
    val readingChapterProgressPercentage = derivedStateOf {
        readingStats.value?.chapterReadPercentage() ?: 0f
    }

    val speakerStats = derivedStateOf {
        val item = readerTextToSpeech.currentTextPlaying.value.itemPos
        readerChaptersLoader.getItemContext(
            chapterIndex = item.chapterIndex,
            chapterItemPosition = item.chapterItemPosition
        )
    }

    val readerLiveTranslation = ReaderLiveTranslation(
        translationManager = translationManager,
        appPreferences = appPreferences,
        chapterTranslationDao = chapterTranslationDao,
        bookUrl = bookUrl,
    ).also { it.currentChapterUrl = chapterUrl }

    val readerChaptersLoader = ReaderChaptersLoader(
        readerRepository = readerRepository,
        translatorTranslateOrNull = { readerLiveTranslation.translatorState?.translate?.invoke(it) },
        translatorIsActive = { readerLiveTranslation.translatorState != null },
        translatorSourceLanguageOrNull = { readerLiveTranslation.translatorState?.sourceLocale?.language },
        translatorTargetLanguageOrNull = { readerLiveTranslation.translatorState?.targetLocale?.language },
        translatorProvider = {
            // Определяем провайдер по настройке, а не по флагу isUsingOnlineTranslation
            when (appPreferences.TRANSLATION_PROVIDER.value) {
                "GEMINI" -> "gemini"
                "OPENAI" -> "openai"
                "GOOGLE_FREE" -> "google_free"
                else -> "google"
            }
        },
        translatorIsOnline = { readerLiveTranslation.isUsingOnlineTranslation() },
        translatorBatchTranslateOrNull = { readerLiveTranslation.getBatchTranslator() },
        translatorTranslateTitleOrNull = { title, sourceLang, targetLang ->
            translationManager.translateTitle(title, sourceLang, targetLang)
        },
        bookUrl = bookUrl,
        orderedChapters = orderedChapters,
        readerState = ReaderState.INITIAL_LOAD,
        readerViewHandlersActions = readerViewHandlersActions,
        chapterTranslationDao = chapterTranslationDao,
        regexRulesProvider = { appPreferences.USER_REGEX_CLEANUP_RULES.value },
    )

    val items = readerChaptersLoader.getItems()

    private var ttsCurrentChapterIndex: Int = -1

    lateinit var readerTextToSpeech: ReaderTextToSpeech

    init {
        readerTextToSpeech = ReaderTextToSpeech(
            coroutineScope = scope,
            context = context,
            items = items,
            chapterLoadedFlow = readerChaptersLoader.chapterLoadedFlow,
            isChapterIndexLoaded = readerChaptersLoader::isChapterIndexLoaded,
            isChapterIndexValid = readerChaptersLoader::isChapterIndexValid,
            tryLoadPreviousChapter = readerChaptersLoader::tryLoadPrevious,
            loadNextChapter = readerChaptersLoader::tryLoadNext,
            customSavedVoices = appPreferences.READER_TEXT_TO_SPEECH_SAVED_PREDEFINED_LIST.state(scope),
            setCustomSavedVoices = {
                appPreferences.READER_TEXT_TO_SPEECH_SAVED_PREDEFINED_LIST.value = it
            },
            getPreferredVoiceId = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ID.value },
            setPreferredVoiceId = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ID.value = it },
            getPreferredVoiceEngine = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ENGINE.value },
            setPreferredVoiceEngine = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ENGINE.value = it },
            getPreferredVoiceSpeed = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_SPEED.value },
            setPreferredVoiceSpeed = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_SPEED.value = it },
            getPreferredVoicePitch = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_PITCH.value },
            setPreferredVoicePitch = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_PITCH.value = it },
            getPreferredVoiceIdForOriginal = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ID_ORIGINAL.value },
            setPreferredVoiceIdForOriginal = { appPreferences.READER_TEXT_TO_SPEECH_VOICE_ID_ORIGINAL.value = it },
            getParallelEnabled = appPreferences.TRANSLATION_PARALLEL_ENABLED.state(scope)::value,
            getParallelOrder = appPreferences.TRANSLATION_PARALLEL_ORDER.state(scope)::value,
            onBufferLow = {
                val currentChapterIndex = ttsCurrentChapterIndex
                val sessionAge = System.currentTimeMillis() - sessionCreatedTime
                if (currentChapterIndex < 0) return@ReaderTextToSpeech
                if (sessionAge < 3000) return@ReaderTextToSpeech
                if (
                    readerTextToSpeech.isSpeaking.value &&
                    !readerChaptersLoader.hasLoadingError &&
                    !readerChaptersLoader.isLastChapter(currentChapterIndex)
                ) {
                    val nextChapterIndex = currentChapterIndex + 1
                    if (!readerChaptersLoader.isChapterIndexLoaded(nextChapterIndex)) {
                        readerChaptersLoader.tryLoadNext()
                    }
                }
            },
        )

        scope.launch {
            readerLiveTranslation.onDisplaySettingsChanged.collect {
                readerTextToSpeech.onParallelModeOrderChanged()
            }
        }
    }

    fun init() {
        initLoadData()
        scope.launch {
            appRepository.libraryBooks.updateLastReadEpochTimeMilli(
                bookUrl,
                System.currentTimeMillis()
            )
        }
        initReaderTTSObservers()
    }

    private fun initLoadData() {
        scope.launch {
            val book = async(Dispatchers.IO) { appRepository.libraryBooks.get(bookUrl) }
            val chapter = async(Dispatchers.IO) { appRepository.bookChapters.get(chapterUrl) }
            val chaptersList = async(Dispatchers.Default) {
                orderedChapters.also { it.addAll(appRepository.bookChapters.chapters(bookUrl)) }
            }
            val chapterIndex = async(Dispatchers.Default) {
                chaptersList.await().indexOfFirst { it.url == chapterUrl }
            }

            chaptersList.await()

            // ВАЖНО: translator должен быть готов ДО первой загрузки главы.
            // Иначе getBatchTranslator() вернёт null (translatorState ещё не создан)
            // и онлайн-провайдер (Gemini/OpenAI) упадёт в поабзацный перевод или ошибку.
            readerLiveTranslation.init()

            bookCoverUrl = book.await()?.coverImageUrl
            bookTitle = book.await()?.title
            readerLiveTranslation.bookTitle = bookTitle ?: ""
            currentChapter = ChapterState(
                chapterUrl = chapterUrl,
                chapterItemPosition = chapter.await()?.lastReadPosition ?: 0,
                offset = chapter.await()?.lastReadOffset ?: 0,
            )

            // Загружаем главу только после того как translatorState готов
            readerChaptersLoader.tryLoadInitial(chapterIndex = chapterIndex.await())
        }
    }

    private fun initReaderTTSObservers() {
        scope.launch {
            readerTextToSpeech
                .currentReaderItem
                .collect { item ->
                    ttsCurrentChapterIndex = item.itemPos.chapterIndex
                }
        }

        scope.launch {
            readerTextToSpeech.reachedChapterEndFlowChapterIndex.collect { chapterIndex ->
                withContext(Dispatchers.Main.immediate) {
                    if (readerChaptersLoader.isLastChapter(chapterIndex)) return@withContext
                    if (readerChaptersLoader.hasLoadingError) return@withContext
                    val nextChapterIndex = chapterIndex + 1
                    val chapterItem = readerChaptersLoader.orderedChapters[nextChapterIndex]
                    if (readerChaptersLoader.loadedChapters.contains(chapterItem.url)) {
                        readerTextToSpeech.readChapterStartingFromStart(
                            chapterIndex = nextChapterIndex
                        )
                    } else launch {
                        readerChaptersLoader.tryLoadNext()
                        readerChaptersLoader.chapterLoadedFlow
                            .filter { it.type == ChapterLoaded.Type.Next }
                            .take(1)
                            .collect {
                                readerTextToSpeech.readChapterStartingFromStart(
                                    chapterIndex = nextChapterIndex
                                )
                            }
                    }
                }
            }
        }

        scope.launch(Dispatchers.Main.immediate) {
            snapshotFlow { readerTextToSpeech.isActive.value }
                .filter { it }
                .collectLatest {
                    NarratorMediaControlsService.start(context)
                }
        }

        scope.launch(Dispatchers.Main.immediate) {
            readerTextToSpeech
                .currentReaderItem
                .filter { it.playState == Utterance.PlayState.PLAYING || it.playState == Utterance.PlayState.LOADING }
                .filter { savePositionMode.value == SavePositionMode.Speaking }
                .collect { saveLastReadPositionStateSpeaker(it.itemPos) }
        }

        scope.launch(Dispatchers.Main.immediate) {
            readerTextToSpeech
                .currentReaderItem
                .filter { it.playState == Utterance.PlayState.PLAYING || it.playState == Utterance.PlayState.LOADING }
                .filter { savePositionMode.value == SavePositionMode.Speaking }
                .collect {
                    val item = it.itemPos
                    if (item !is ReaderItem.ParagraphLocation) return@collect
                    when (item.location) {
                        ReaderItem.Location.FIRST -> markChapterStartAsSeen(chapterUrl = item.chapterUrl)
                        ReaderItem.Location.LAST -> markChapterEndAsSeen(chapterUrl = item.chapterUrl)
                        ReaderItem.Location.MIDDLE -> Unit
                    }
                }
        }

        scope.launch(Dispatchers.Main.immediate) {
            readerTextToSpeech
                .currentReaderItem
                .filter { it.playState == Utterance.PlayState.PLAYING }
                .collect { item ->
                    val stats = readerChaptersLoader.getItemContext(
                        chapterIndex = item.itemPos.chapterIndex,
                        chapterItemPosition = item.itemPos.chapterItemPosition
                    )
                    if (stats != null) {
                        readingStats.value = stats
                    }
                }
        }
    }

    fun startSpeaker(itemIndex: Int) {
        val startingItem = items.getOrNull(itemIndex) ?: return
        readerTextToSpeech.start()
        scope.launch {
            readerTextToSpeech.readChapterStartingFromItemIndex(
                itemIndex = itemIndex,
                chapterIndex = startingItem.chapterIndex
            )
        }
    }

    fun close() {
        readerChaptersLoader.coroutineContext.cancelChildren()
        if (readerTextToSpeech.isActive.value) {
            saveLastReadPositionStateSpeaker(
                item = readerTextToSpeech.currentTextPlaying.value.itemPos
            )
        } else {
            when (savePositionMode.value) {
                SavePositionMode.Reading -> readerRepository.saveBookLastReadPositionState(
                    bookUrl,
                    currentChapter
                )
                SavePositionMode.Speaking -> saveLastReadPositionStateSpeaker(
                    item = readerTextToSpeech.currentTextPlaying.value.itemPos
                )
            }
        }
        readerTextToSpeech.stop()
        scope.coroutineContext.cancelChildren()
        NarratorMediaControlsService.stop(context)
    }

    fun reloadReader() {
        readerChaptersLoader.reload()
        readerTextToSpeech.stop()
    }

    fun updateInfoViewTo(itemIndex: Int, userHasScrolled: Boolean = false) {
        val sessionAge = System.currentTimeMillis() - sessionCreatedTime
        if (sessionAge < 3000) return

        val stats = readerChaptersLoader.getItemContext(
            itemIndex = itemIndex,
            chapterUrl = chapterUrl
        ) ?: return
        readingStats.value = stats

        val progress = stats.chapterReadPercentage()
        val chapterIndex = stats.chapterIndex

        if (chapterIndex != lastChapterIndex) {
            if (lastChapterIndex != -1 && readerChaptersLoader.hasLoadingError) {
                readerChaptersLoader.hasLoadingError = false
                android.util.Log.d("ReaderSession", "Reset hasLoadingError on chapter change: $lastChapterIndex -> $chapterIndex")
            }
            lastChapterIndex = chapterIndex
            preloadTriggeredForChapter = -1
            scope.launch {
                readerChaptersLoader.pruneItems(chapterIndex)
            }
        }

        if (
            userHasScrolled &&
            preloadTriggeredForChapter != chapterIndex &&
            progress >= 80f &&
            !readerChaptersLoader.isLastChapter(chapterIndex) &&
            !readerChaptersLoader.hasLoadingError
        ) {
            val nextChapterIndex = chapterIndex + 1
            if (!readerChaptersLoader.isChapterIndexLoaded(nextChapterIndex)) {
                preloadTriggeredForChapter = chapterIndex
                scope.launch {
                    readerChaptersLoader.tryLoadNext()
                }
            }
        }
    }

    fun markChapterStartAsSeen(chapterUrl: String) {
        readRoutine.setReadStart(chapterUrl = chapterUrl)
    }

    fun markChapterEndAsSeen(chapterUrl: String) {
        readRoutine.setReadEnd(chapterUrl = chapterUrl)
    }

    fun saveCurrentPosition(currentChapter: ChapterState) {
        readerRepository.saveBookLastReadPositionState(
            bookUrl = bookUrl,
            newChapter = currentChapter
        )
    }

    private fun saveLastReadPositionStateSpeaker(item: ReaderItem.Position) {
        readerRepository.saveBookLastReadPositionState(
            bookUrl = bookUrl,
            newChapter = ChapterState(
                chapterUrl = item.chapterUrl,
                chapterItemPosition = item.chapterItemPosition,
                offset = 0
            )
        )
    }
}