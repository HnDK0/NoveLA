package my.noveldokusha.features.reader.features

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.VoicePredefineState
import my.noveldokusha.features.reader.domain.ChapterIndex
import my.noveldokusha.features.reader.domain.ChapterLoaded
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.domain.indexOfReaderItem
import my.noveldokusha.text_to_speech.TextToSpeechManager
import my.noveldokusha.text_to_speech.Utterance
import my.noveldokusha.text_to_speech.VoiceData

@Stable
internal data class TextToSpeechSettingData(
    val isPlaying: MutableState<Boolean>,
    val isLoadingChapter: MutableState<Boolean>,
    val activeVoice: State<VoiceData?>,
    val voiceSpeed: State<Float>,
    val voicePitch: State<Float>,
    val availableVoices: SnapshotStateList<VoiceData>,
    val currentActiveItemState: State<TextSynthesis>,
    val isThereActiveItem: State<Boolean>,
    val customSavedVoices: State<List<VoicePredefineState>>,
    val setCustomSavedVoices: (List<VoicePredefineState>) -> Unit,
    val setPlaying: (Boolean) -> Unit,
    val playFirstVisibleItem: () -> Unit,
    val playPreviousItem: () -> Unit,
    val playPreviousChapter: () -> Unit,
    val playNextItem: () -> Unit,
    val playNextChapter: () -> Unit,
    val scrollToActiveItem: () -> Unit,
    val setVoiceId: (voiceId: String) -> Unit,
    val setVoiceSpeed: (Float) -> Unit,
    val setVoicePitch: (Float) -> Unit,
    val chapterWordCount: State<Int>,
    val remainingWordCount: State<Int>,
    val estimatedWpm: State<Int>,
    val estimatedTotalSeconds: State<Int>,
    val estimatedRemainingSeconds: State<Int>,
)

internal data class TextSynthesis(
    val itemPos: ReaderItem.Position,
    override val playState: Utterance.PlayState
) : Utterance<TextSynthesis> {
    override val utteranceId = "${itemPos.chapterItemPosition}-${itemPos.chapterIndex}"
    override fun copyWithState(playState: Utterance.PlayState) = copy(playState = playState)
}

internal class ReaderTextToSpeech(
    private val coroutineScope: CoroutineScope,
    context: Context,
    private val items: List<ReaderItem>,
    private val chapterLoadedFlow: Flow<ChapterLoaded>,
    customSavedVoices: State<List<VoicePredefineState>>,
    setCustomSavedVoices: (List<VoicePredefineState>) -> Unit,
    private val isChapterIndexValid: (chapterIndex: Int) -> Boolean,
    private val isChapterIndexLoaded: (chapterIndex: Int) -> Boolean,
    private val tryLoadPreviousChapter: () -> Unit,
    private val loadNextChapter: () -> Unit,
    private val getPreferredVoiceId: () -> String,
    private val setPreferredVoiceId: (voiceId: String) -> Unit,
    private val getPreferredVoiceEngine: () -> String,
    private val setPreferredVoiceEngine: (enginePackage: String) -> Unit,
    private val getPreferredVoicePitch: () -> Float,
    private val setPreferredVoicePitch: (voiceId: Float) -> Unit,
    private val getPreferredVoiceSpeed: () -> Float,
    private val setPreferredVoiceSpeed: (voiceId: Float) -> Unit,
    private val onBufferLow: (() -> Unit)? = null,
) {
    private val DECORATIVE_CHARS = """\-=*_~+#·•°─-┿"""
    private val SEPARATOR_ONLY = Regex("""^\s*[$DECORATIVE_CHARS]{3,}\s*$""")
    private val LEADING_DECORATIVE = Regex("""^[$DECORATIVE_CHARS]{3,}\s*""")
    private val TRAILING_DECORATIVE = Regex("""\s*[$DECORATIVE_CHARS]{3,}$""")

    private val halfBuffer = 5
    private var updateJob: Job? = null
    private val manager = TextToSpeechManager(
        context = context,
        initialItemState = TextSynthesis(
            itemPos = ReaderItem.Title(
                chapterUrl = "",
                chapterIndex = -1,
                chapterItemPosition = 0,
                text = ""
            ),
            playState = Utterance.PlayState.FINISHED
        )
    )

    val scrolledToTheTop = MutableSharedFlow<Unit>()
    val scrolledToTheBottom = MutableSharedFlow<Unit>()
    val currentReaderItem = manager.currentTextSpeakFlow
    val currentTextPlaying = manager.currentActiveItemState as State<TextSynthesis>
    val reachedChapterEndFlowChapterIndex = MutableSharedFlow<ChapterIndex>()
    val startReadingFromFirstVisibleItem = MutableSharedFlow<Unit>()
    val scrollToReaderItem = MutableSharedFlow<ReaderItem>()
    val scrollToChapterTop = MutableSharedFlow<ChapterIndex>()

    private val baseCharactersPerSecond = mutableStateOf(13.0f)

    val chapterWordCount = derivedStateOf {
        val currentChapterIndex = currentTextPlaying.value.itemPos.chapterIndex
        if (isChapterIndexValid(currentChapterIndex)) {
            items.filterIsInstance<ReaderItem.Text>()
                .filter { it.chapterIndex == currentChapterIndex }
                .sumOf { it.textToDisplay.wordCount() }
        } else 0
    }

    val remainingWordCount = derivedStateOf {
        val currentChapterIndex = currentTextPlaying.value.itemPos.chapterIndex
        val currentItemPos = currentTextPlaying.value.itemPos.chapterItemPosition
        if (isChapterIndexValid(currentChapterIndex)) {
            items.filterIsInstance<ReaderItem.Text>()
                .filter { it.chapterIndex == currentChapterIndex && it.chapterItemPosition >= currentItemPos }
                .sumOf { it.textToDisplay.wordCount() }
        } else 0
    }

    val chapterCharacterCount = derivedStateOf {
        val currentChapterIndex = currentTextPlaying.value.itemPos.chapterIndex
        if (isChapterIndexValid(currentChapterIndex)) {
            items.filterIsInstance<ReaderItem.Text>()
                .filter { it.chapterIndex == currentChapterIndex }
                .sumOf { it.textToDisplay.length }
        } else 0
    }

    val remainingCharacterCount = derivedStateOf {
        val currentChapterIndex = currentTextPlaying.value.itemPos.chapterIndex
        val currentItemPos = currentTextPlaying.value.itemPos.chapterItemPosition
        if (isChapterIndexValid(currentChapterIndex)) {
            items.filterIsInstance<ReaderItem.Text>()
                .filter { it.chapterIndex == currentChapterIndex && it.chapterItemPosition >= currentItemPos }
                .sumOf { it.textToDisplay.length }
        } else 0
    }

    val estimatedWpm = derivedStateOf {
        val currentSpeed = manager.voiceSpeed.floatValue
        (baseCharactersPerSecond.value * currentSpeed * 12.0f).toInt().coerceAtLeast(30)
    }

    val estimatedTotalSeconds = derivedStateOf {
        val currentSpeed = manager.voiceSpeed.floatValue
        val cps = baseCharactersPerSecond.value * currentSpeed
        if (cps > 0f) (chapterCharacterCount.value / cps).toInt() else 0
    }

    val estimatedRemainingSeconds = derivedStateOf {
        val currentSpeed = manager.voiceSpeed.floatValue
        val cps = baseCharactersPerSecond.value * currentSpeed
        if (cps > 0f) (remainingCharacterCount.value / cps).toInt() else 0
    }

    val state = TextToSpeechSettingData(
        isPlaying = mutableStateOf(false),
        isLoadingChapter = mutableStateOf(false),
        activeVoice = manager.activeVoice as State<VoiceData?>,
        availableVoices = manager.availableVoices,
        currentActiveItemState = manager.currentActiveItemState,
        isThereActiveItem = derivedStateOf {
            isChapterIndexValid(manager.currentActiveItemState.value.itemPos.chapterIndex)
        },
        voicePitch = manager.voicePitch,
        voiceSpeed = manager.voiceSpeed,
        customSavedVoices = customSavedVoices,
        setCustomSavedVoices = setCustomSavedVoices,
        setVoiceId = ::setVoice,
        playFirstVisibleItem = ::playFirstVisibleItem,
        playNextChapter = ::playNextChapter,
        playPreviousChapter = ::playPreviousChapter,
        playNextItem = ::playNextItem,
        playPreviousItem = ::playPreviousItem,
        setPlaying = ::setPlaying,
        scrollToActiveItem = ::scrollToActiveItem,
        setVoicePitch = ::setVoicePitch,
        setVoiceSpeed = ::setVoiceSpeed,
        chapterWordCount = chapterWordCount,
        remainingWordCount = remainingWordCount,
        estimatedWpm = estimatedWpm,
        estimatedTotalSeconds = estimatedTotalSeconds,
        estimatedRemainingSeconds = estimatedRemainingSeconds,
    )

    val isActive = derivedStateOf { state.isThereActiveItem.value || state.isPlaying.value }
    val isSpeaking = derivedStateOf { state.isThereActiveItem.value && state.isPlaying.value }

    init {
        coroutineScope.launch {
            // Ждём пока все голоса собраны (один раз при старте)
            manager.serviceLoadedFlow.take(1).collect {
                manager.trySetVoicePitch(getPreferredVoicePitch())
                manager.trySetVoiceSpeed(getPreferredVoiceSpeed())

                val preferredVoiceId = getPreferredVoiceId()
                val preferredEngine = getPreferredVoiceEngine()
                val defaultEngine = manager.service.defaultEngine ?: ""

                // Ищем голос в availableVoices — там все движки с правильным enginePackage
                val voiceData = manager.availableVoices.find { it.id == preferredVoiceId }
                val targetEngine = voiceData?.enginePackage ?: preferredEngine

                if (targetEngine.isNotEmpty() && targetEngine != defaultEngine) {
                    // Голос из другого движка — переключаем service для воспроизведения
                    manager.reinitWithEngine(
                        enginePackage = targetEngine,
                        voiceId = preferredVoiceId
                    )
                } else {
                    manager.trySetVoiceById(preferredVoiceId)
                }
            }
        }

        // Калибровка скорости чтения в реальном времени
        coroutineScope.launch {
            val paragraphStartTimes = mutableMapOf<String, Long>()
            manager.currentTextSpeakFlow.collect { utterance ->
                val utteranceId = utterance.utteranceId
                when (utterance.playState) {
                    Utterance.PlayState.PLAYING -> {
                        paragraphStartTimes[utteranceId] = System.currentTimeMillis()
                    }
                    Utterance.PlayState.FINISHED -> {
                        val startTime = paragraphStartTimes.remove(utteranceId)
                        if (startTime != null) {
                            val durationMs = System.currentTimeMillis() - startTime
                            val currentChapterIndex = utterance.itemPos.chapterIndex
                            val currentItemPos = utterance.itemPos.chapterItemPosition

                            val itemIndex = indexOfReaderItem(
                                list = items,
                                chapterIndex = currentChapterIndex,
                                chapterItemPosition = currentItemPos,
                            )
                            val item = items.getOrNull(itemIndex) as? ReaderItem.Text
                            val text = item?.textToDisplay ?: ""
                            val charCount = text.length

                            if (charCount > 10 && durationMs > 200) {
                                val measuredCps = (charCount * 1000.0f) / durationMs
                                val currentSpeed = manager.voiceSpeed.floatValue
                                if (currentSpeed > 0f) {
                                    val baseCps = measuredCps / currentSpeed
                                    if (baseCps in 3.0f..40.0f) {
                                        baseCharactersPerSecond.value = 0.2f * baseCps + 0.8f * baseCharactersPerSecond.value
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun claimMediaSession() {
        try {
            val sampleRate = 44100
            val durationSec = 0.1
            val bufferSize = (sampleRate * durationSec).toInt()
            val audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSize * 2,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            val silence = ShortArray(bufferSize)
            audioTrack.write(silence, 0, silence.size)
            audioTrack.play()
            coroutineScope.launch {
                delay(200)
                runCatching { audioTrack.stop() }
                runCatching { audioTrack.release() }
            }
            Log.d("TTS", "claimMediaSession OK")
        } catch (e: Exception) {
            Log.w("TTS", "claimMediaSession failed: $e")
        }
    }

    @Synchronized
    fun start() {
        Log.d("TTS", "start()")
        claimMediaSession()
        state.isPlaying.value = true
        updateJob?.cancel()
        updateJob = coroutineScope.launch {
            manager
                .currentTextSpeakFlow
                .filter { it.playState == Utterance.PlayState.FINISHED }
                .collect {
                    Log.d("TTS", "collect FINISHED queueSize=${manager.queueList.size}")
                    withContext(Dispatchers.Main) {
                        when (manager.queueList.size) {
                            halfBuffer -> {
                                val lastUtterance = manager
                                    .queueList
                                    .asSequence()
                                    .last().value
                                readChapterNextChunk(
                                    chapterIndex = lastUtterance.itemPos.chapterIndex,
                                    chapterItemPosition = lastUtterance.itemPos.chapterItemPosition,
                                    quantity = halfBuffer
                                )
                                onBufferLow?.invoke()
                            }
                            0 -> {
                                launch {
                                    reachedChapterEndFlowChapterIndex.emit(it.itemPos.chapterIndex)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
        }
    }

    @Synchronized
    fun stop() {
        Log.d("TTS", "stop()")
        state.isPlaying.value = false
        updateJob?.cancel()
        manager.stop()
    }

    fun onClose() {
        stop()
        manager.service.shutdown()
    }

    suspend fun readChapterStartingFromStart(
        chapterIndex: Int
    ) = withContext(Dispatchers.Main.immediate) {
        readChapterStartingFromChapterItemPosition(
            chapterIndex = chapterIndex,
            chapterItemPosition = 0
        )
    }

    private suspend fun readChapterStartingFromChapterItemPosition(
        chapterIndex: Int,
        chapterItemPosition: Int,
    ) = withContext(Dispatchers.Main.immediate) {
        val itemIndex = indexOfReaderItem(
            list = items,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition
        )
        if (itemIndex == -1) {
            reachedChapterEndFlowChapterIndex.emit(chapterIndex)
            return@withContext
        }
        readChapterStartingFromItemIndex(
            itemIndex = itemIndex,
            chapterIndex = chapterIndex
        )
    }

    suspend fun readChapterStartingFromItemIndex(
        itemIndex: Int,
        chapterIndex: Int,
    ) = withContext(Dispatchers.Main.immediate) {
        val nextItems = getChapterNextItems(
            itemIndex = itemIndex,
            chapterIndex = chapterIndex,
            quantity = halfBuffer * 2
        )

        if (nextItems.isEmpty()) {
            reachedChapterEndFlowChapterIndex.emit(chapterIndex)
            return@withContext
        }

        val firstItem = nextItems.first()
        manager.setCurrentSpeakState(
            TextSynthesis(
                itemPos = firstItem,
                playState = Utterance.PlayState.LOADING
            )
        )

        nextItems.forEach(::speakItem)
    }

    @Synchronized
    private fun scrollToActiveItem() {
        coroutineScope.launch {
            val currentItemPos = state.currentActiveItemState.value.itemPos
            val itemIndex = indexOfReaderItem(
                list = items,
                chapterIndex = currentItemPos.chapterIndex,
                chapterItemPosition = currentItemPos.chapterItemPosition,
            )
            val item = items.getOrNull(itemIndex) ?: return@launch
            scrollToReaderItem.emit(item)
        }
    }

    @Synchronized
    fun scrollToCurrentSpeakingItem() {
        coroutineScope.launch {
            val currentItemPos = currentTextPlaying.value.itemPos
            val itemIndex = indexOfReaderItem(
                list = items,
                chapterIndex = currentItemPos.chapterIndex,
                chapterItemPosition = currentItemPos.chapterItemPosition,
            )
            val item = items.getOrNull(itemIndex) ?: return@launch
            scrollToReaderItem.emit(item)
        }
    }

    /**
     * Returns the actual current playing position directly from the TTS manager.
     * Unlike currentTextPlaying.value.itemPos which may be stale when app is backgrounded,
     * this always returns the up-to-date position from the underlying utterance state.
     *
     * Returns null if there is no actively playing/loading item (i.e. TTS is
     * between chapters or has finished all content). Callers should fall back
     * to waiting for the next currentReaderItem emission.
     */
    fun getActualPlayingPosition(): ReaderItem.Position? {
        // All items in the queue are non-finished — the first one is the current
        val playingItem = manager.queueList.values
            .firstOrNull { it.playState == Utterance.PlayState.PLAYING }
        if (playingItem != null) {
            return playingItem.itemPos
        }

        // Queue is empty — check if currentActiveItemState is still active
        val currentState = currentTextPlaying.value
        if (currentState.playState != Utterance.PlayState.FINISHED) {
            return currentState.itemPos
        }

        // Queue empty and state is FINISHED: TTS is between items/chapters.
        // Don't return a stale position — let the caller wait for the next emission.
        return null
    }

    /**
     * Forces the currentActiveItemState to reflect the actual playing position
     * from the TTS queue. This ensures that after screen unlock, the LiveData
     * observer receives the up-to-date position instead of a stale one.
     */
    fun forceUpdateCurrentItemState() {
        val playingItem = manager.queueList.values
            .firstOrNull { it.playState == Utterance.PlayState.PLAYING }
            ?: manager.queueList.values
                .firstOrNull { it.playState == Utterance.PlayState.LOADING }
        if (playingItem != null) {
            manager.setCurrentSpeakState(playingItem)
        }
    }

    @Synchronized
    private fun playFirstVisibleItem() {
        stop()
        start()
        coroutineScope.launch {
            startReadingFromFirstVisibleItem.emit(Unit)
        }
    }

    @Synchronized
    private fun setPlaying(playing: Boolean) {
        if (!playing) {
            stop()
            return
        }
        start()
        val state = state.currentActiveItemState.value

        if (isChapterIndexValid(state.itemPos.chapterIndex)) {
            coroutineScope.launch {
                readChapterStartingFromChapterItemPosition(
                    chapterIndex = state.itemPos.chapterIndex,
                    chapterItemPosition = state.itemPos.chapterItemPosition
                )
            }
        } else {
            coroutineScope.launch {
                startReadingFromFirstVisibleItem.emit(Unit)
            }
        }
    }

    @Synchronized
    private fun playNextItem() {
        if (!state.isThereActiveItem.value) return

        coroutineScope.launch {
            val currentItemPos = state.currentActiveItemState.value.itemPos
            val itemIndex = indexOfReaderItem(
                list = items,
                chapterIndex = currentItemPos.chapterIndex,
                chapterItemPosition = currentItemPos.chapterItemPosition,
            )
            if (itemIndex <= -1 || itemIndex >= items.lastIndex) return@launch
            val nextItemRelativeIndex = items
                .subList(itemIndex + 1, items.size)
                .indexOfFirst { it is ReaderItem.Position }
            if (nextItemRelativeIndex == -1) return@launch
            val nextItemIndex = itemIndex + 1 + nextItemRelativeIndex
            val nextItem = items.getOrNull(nextItemIndex) as? ReaderItem.Position ?: return@launch
            stop()
            start()
            readChapterStartingFromItemIndex(
                itemIndex = nextItemIndex,
                chapterIndex = nextItem.chapterIndex
            )
            scrollToReaderItem.emit(nextItem)
        }
    }

    @Synchronized
    private fun playPreviousItem() {
        if (!state.isThereActiveItem.value) return

        coroutineScope.launch {
            val currentItemPos = state.currentActiveItemState.value.itemPos
            val itemIndex = indexOfReaderItem(
                list = items,
                chapterIndex = currentItemPos.chapterIndex,
                chapterItemPosition = currentItemPos.chapterItemPosition,
            )
            if (itemIndex <= 0) return@launch
            val previousItemRelativeIndex = items
                .subList(0, itemIndex)
                .asReversed()
                .indexOfFirst { it is ReaderItem.Position }
            if (previousItemRelativeIndex == -1) return@launch
            val previousItemIndex = itemIndex - 1 - previousItemRelativeIndex
            val previousItem = items.getOrNull(previousItemIndex) ?: return@launch
            stop()
            start()
            readChapterStartingFromItemIndex(
                itemIndex = previousItemIndex,
                chapterIndex = previousItem.chapterIndex
            )
            scrollToReaderItem.emit(previousItem)
        }
    }

    @Synchronized
    private fun playNextChapter() {
        if (!state.isThereActiveItem.value) return

        val currentState = state.currentActiveItemState.value
        val nextChapterIndex = currentState.itemPos.chapterIndex + 1
        stop()
        if (!isChapterIndexValid(nextChapterIndex)) {
            coroutineScope.launch {
                val item = items.findLast {
                    it is ReaderItem.Position && it.chapterIndex == currentState.itemPos.chapterIndex
                } as? ReaderItem.Position ?: return@launch

                manager.currentActiveItemState.value = currentState.copy(
                    playState = Utterance.PlayState.FINISHED,
                    itemPos = item
                )
                scrolledToTheBottom.emit(Unit)
            }
            return
        }
        start()
        coroutineScope.launch {
            if (!isChapterIndexLoaded(nextChapterIndex)) {
                state.isLoadingChapter.value = true
                loadNextChapter()
                chapterLoadedFlow
                    .filter { it.chapterIndex == nextChapterIndex }
                    .take(1)
                    .collect()
                state.isLoadingChapter.value = false
            }
            readChapterStartingFromStart(nextChapterIndex)
            scrollToChapterTop.emit(nextChapterIndex)
        }
    }

    @Synchronized
    private fun playPreviousChapter() {
        if (!state.isThereActiveItem.value) return

        val currentItemState = state.currentActiveItemState.value
        val targetChapterIndex = when (currentItemState.itemPos is ReaderItem.Title) {
            true -> currentItemState.itemPos.chapterIndex - 1
            false -> currentItemState.itemPos.chapterIndex
        }
        stop()
        if (!isChapterIndexValid(targetChapterIndex)) {
            coroutineScope.launch {
                manager.currentActiveItemState.value = currentItemState.copy(
                    playState = Utterance.PlayState.FINISHED
                )
                scrolledToTheTop.emit(Unit)
            }
            return
        }
        start()
        coroutineScope.launch {
            if (!isChapterIndexLoaded(targetChapterIndex)) {
                state.isLoadingChapter.value = true
                tryLoadPreviousChapter()
                chapterLoadedFlow
                    .filter { it.chapterIndex == targetChapterIndex }
                    .take(1)
                    .collect()
                state.isLoadingChapter.value = false
            }
            readChapterStartingFromStart(targetChapterIndex)
            scrollToChapterTop.emit(targetChapterIndex)
        }
    }

    private fun setVoice(voiceId: String) {
        val voiceData = manager.availableVoices.find { it.id == voiceId }
        // Берём движок из найденного голоса, иначе из текущего service
        val targetEngine = voiceData?.enginePackage ?: (manager.service.defaultEngine ?: "")
        val currentEngine = manager.getCurrentEnginePackage()

        if (targetEngine.isNotEmpty() && targetEngine != currentEngine) {
            // Голос из другого движка — пересоздаём service для воспроизведения
            val wasPlaying = state.isPlaying.value
            stop()
            setPreferredVoiceId(voiceId)
            setPreferredVoiceEngine(targetEngine)
            manager.reinitWithEngine(
                enginePackage = targetEngine,
                voiceId = voiceId,
            )
            if (wasPlaying) {
                coroutineScope.launch {
                    manager.serviceLoadedFlow.take(1).collect()
                    start()
                    val currentState = manager.currentActiveItemState.value
                    if (isChapterIndexValid(currentState.itemPos.chapterIndex)) {
                        readChapterStartingFromChapterItemPosition(
                            chapterIndex = currentState.itemPos.chapterIndex,
                            chapterItemPosition = currentState.itemPos.chapterItemPosition
                        )
                    }
                }
            }
        } else {
            val success = manager.trySetVoiceById(id = voiceId)
            if (success) {
                setPreferredVoiceId(voiceId)
                if (voiceData != null) setPreferredVoiceEngine(voiceData.enginePackage)
                resumeFromCurrentState()
            }
        }
    }

    private fun setVoicePitch(value: Float) {
        Log.d("TTS", "setVoicePitch($value)")
        val success = manager.trySetVoicePitch(value)
        if (success) {
            setPreferredVoicePitch(value)
            resumeFromCurrentState()
        }
    }

    private fun setVoiceSpeed(value: Float) {
        Log.d("TTS", "setVoiceSpeed($value)")
        val success = manager.trySetVoiceSpeed(value)
        if (success) {
            setPreferredVoiceSpeed(value)
            resumeFromCurrentState()
        }
    }

    private fun resumeFromCurrentState() {
        Log.d("TTS", "resumeFromCurrentState isPlaying=${state.isPlaying.value}")
        if (!state.isPlaying.value) return
        stop()
        start()
        val currentState = manager.currentActiveItemState.value
        Log.d("TTS", "resumeFromCurrentState chapterIndex=${currentState.itemPos.chapterIndex}")
        if (currentState.itemPos.chapterIndex >= 0) {
            coroutineScope.launch {
                readChapterStartingFromChapterItemPosition(
                    chapterIndex = currentState.itemPos.chapterIndex,
                    chapterItemPosition = currentState.itemPos.chapterItemPosition
                )
            }
        }
    }

    private fun readChapterNextChunk(
        chapterIndex: Int,
        chapterItemPosition: Int,
        quantity: Int
    ) {
        val itemIndex = indexOfReaderItem(
            list = items,
            chapterIndex = chapterIndex,
            chapterItemPosition = chapterItemPosition
        )
        if (itemIndex == -1) return
        val nextItems = getChapterNextItems(
            itemIndex = itemIndex + 1,
            chapterIndex = chapterIndex,
            quantity = quantity
        )
        if (nextItems.isEmpty()) return
        nextItems.forEach(::speakItem)
    }

    private fun getChapterNextItems(
        itemIndex: Int,
        chapterIndex: Int,
        quantity: Int
    ): List<ReaderItem.Position> {
        return items
            .subList(itemIndex.coerceAtMost(items.lastIndex), items.size)
            .asSequence()
            .filter { it is ReaderItem.Title || it is ReaderItem.Body }
            .filterIsInstance<ReaderItem.Position>()
            .takeWhile { it.chapterIndex == chapterIndex }
            .take(quantity)
            .toList()
    }

    private fun isOnlyDecorators(text: String): Boolean {
        if (text.isBlank()) return true
        return text.lines().all { line ->
            line.isBlank() || SEPARATOR_ONLY.matches(line)
        }
    }

    private fun cleanTextForTts(text: String): String {
        return text.lines().joinToString("\n") { line ->
            line.replace(LEADING_DECORATIVE, "")
                .replace(TRAILING_DECORATIVE, "")
                .trim()
        }
    }

    private fun speakItem(item: ReaderItem) {
        when (item) {
            is ReaderItem.Text -> {
                val displayText = item.textToDisplay
                if (isOnlyDecorators(displayText)) return

                val cleanText = cleanTextForTts(displayText)
                if (cleanText.isBlank()) return

                manager.speak(
                    text = cleanText,
                    textSynthesis = TextSynthesis(
                        itemPos = item,
                        playState = Utterance.PlayState.PLAYING
                    )
                )
            }
            else -> Unit
        }
    }
}

private fun String.wordCount(): Int {
    if (this.isEmpty()) return 0
    return this.split(Regex("\\s+")).count { it.isNotEmpty() }
}