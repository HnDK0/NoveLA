package my.noveldokusha.features.reader

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.coreui.BaseViewModel
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.utils.StateExtra_Boolean
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.features.reader.manager.ReaderManager
import my.noveldokusha.features.reader.ui.ReaderScreenState
import my.noveldokusha.features.reader.ui.ReaderViewHandlersActions
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.properties.Delegates

interface ReaderStateBundle {
    var bookUrl: String
    var chapterUrl: String
    var introScrollToSpeaker: Boolean
}

@HiltViewModel
internal class ReaderViewModel @Inject constructor(
    stateHandler: SavedStateHandle,
    appPreferences: AppPreferences,
    private val readerManager: ReaderManager,
    readerViewHandlersActions: ReaderViewHandlersActions,
) : BaseViewModel(), ReaderStateBundle {

    override var bookUrl by StateExtra_String(stateHandler)
    override var chapterUrl by StateExtra_String(stateHandler)
    override var introScrollToSpeaker by StateExtra_Boolean(stateHandler)

    private val readerSession = readerManager.initiateOrGetSession(
        bookUrl = bookUrl,
        chapterUrl = chapterUrl
    )

    private val readingPosStats = readerSession.readingStats

    val state = ReaderScreenState(
        showReaderInfo = mutableStateOf(false),
        readerInfo = ReaderScreenState.CurrentInfo(
            chapterTitle = derivedStateOf {
                readingPosStats.value?.chapterTitle ?: ""
            },
            chapterCurrentNumber = derivedStateOf {
                readingPosStats.value?.run { chapterIndex + 1 } ?: 0
            },
            chapterPercentageProgress = readerSession.readingChapterProgressPercentage,
            chaptersCount = derivedStateOf { readingPosStats.value?.chapterCount ?: 0 },
            chapterUrl = derivedStateOf { readingPosStats.value?.chapterUrl ?: "" }
        ),
        settings = ReaderScreenState.Settings(
            selectedSetting = mutableStateOf(ReaderScreenState.Settings.Type.None),
            isTextSelectable = appPreferences.READER_SELECTABLE_TEXT.state(viewModelScope),
            keepScreenOn = appPreferences.READER_KEEP_SCREEN_ON.state(viewModelScope),
            textToSpeech = readerSession.readerTextToSpeech.state,
            liveTranslation = readerSession.readerLiveTranslation.state,
            fullScreen = appPreferences.READER_FULL_SCREEN.state(viewModelScope),
            isSingleTapToOpenSettings = appPreferences.READER_SINGLE_TAP_TO_OPEN_SETTINGS.state(viewModelScope),
            style = ReaderScreenState.Settings.StyleSettingsData(
                currentDarkMode = mutableStateOf(DarkMode.SYSTEM).also { state ->
                    viewModelScope.launch {
                        appPreferences.THEME_DARK_MODE.flow()
                            .map {
                                try { DarkMode.valueOf(it) }
                                catch (_: Exception) { DarkMode.SYSTEM }
                            }
                            .collect { withContext(Dispatchers.Main) { state.value = it } }
                    }
                },
                currentAppTheme = mutableStateOf(AppTheme.DEFAULT).also { state ->
                    viewModelScope.launch {
                        appPreferences.APP_THEME.flow()
                            .map {
                                try { AppTheme.valueOf(it) }
                                catch (_: Exception) { AppTheme.DEFAULT }
                            }
                            .collect { withContext(Dispatchers.Main) { state.value = it } }
                    }
                },
                textFont = appPreferences.READER_FONT_FAMILY.state(viewModelScope),
                textSize = appPreferences.READER_FONT_SIZE.state(viewModelScope),
                lineHeight = appPreferences.READER_LINE_HEIGHT.state(viewModelScope),
                paragraphSpacing = appPreferences.READER_PARAGRAPH_SPACING.state(viewModelScope),
            )
        ),
        showInvalidChapterDialog = mutableStateOf(false)
    )

    init {
        readerViewHandlersActions.showInvalidChapterDialog = {
            withContext(Dispatchers.Main) {
                state.showInvalidChapterDialog.value = true
            }
        }

        viewModelScope.launch {
            snapshotFlow { readingPosStats.value?.chapterUrl }
                .collectLatest { newChapterUrl ->
                    if (!newChapterUrl.isNullOrEmpty()) {
                        chapterUrl = newChapterUrl
                    }
                }
        }

    }

    val items = readerSession.items
    val chaptersLoader = readerSession.readerChaptersLoader
    val readerSpeaker = readerSession.readerTextToSpeech
    var readingCurrentChapter by Delegates.observable(readerSession.currentChapter) { _, _, new ->
        readerSession.currentChapter = new
    }
    val onTranslatorChanged = readerSession.readerLiveTranslation.onTranslatorChanged
    val ttsScrolledToTheTop = readerSession.readerTextToSpeech.scrolledToTheTop
    val ttsScrolledToTheBottom = readerSession.readerTextToSpeech.scrolledToTheBottom

    fun onCloseManually() {
        readerManager.close()
    }

    fun startSpeaker(itemIndex: Int) =
        readerSession.startSpeaker(itemIndex = itemIndex)

    fun reloadReader() {
        val currentChapter = readingCurrentChapter.copy()
        readerSession.reloadReader()
        chaptersLoader.tryLoadRestartedInitial(currentChapter)
    }

    fun updateInfoViewTo(itemIndex: Int, userHasScrolled: Boolean = false) =
        readerSession.updateInfoViewTo(itemIndex = itemIndex, userHasScrolled = userHasScrolled)

    fun markChapterStartAsSeen(chapterUrl: String) =
        readerSession.markChapterStartAsSeen(chapterUrl = chapterUrl)

    fun markChapterEndAsSeen(chapterUrl: String) =
        readerSession.markChapterEndAsSeen(chapterUrl = chapterUrl)

    fun saveCurrentReadingPosition() {
        readerSession.saveCurrentPosition(readingCurrentChapter)
    }
}