package my.noveldokusha.text_to_speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import me.nanihadesuka.algorithms.delimiterAwareTextSplitter

interface Utterance<T : Utterance<T>> {
    enum class PlayState { PLAYING, FINISHED, LOADING }

    val utteranceId: String
    val playState: PlayState
    fun copyWithState(playState: PlayState): T
}

data class VoiceData(
    val id: String,
    val language: String,
    val needsInternet: Boolean,
    val quality: Int,
    val enginePackage: String,
)

class TextToSpeechManager<T : Utterance<T>>(
    private val context: Context,
    private val appTtsEngine: AppTtsEngine,
    initialItemState: T,
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _queueList = mutableMapOf<String, T>()
    private val _queueListItemSize = mutableMapOf<String, Int>()
    private val _currentTextSpeakFlow = MutableSharedFlow<T>()

    val availableVoices = mutableStateListOf<VoiceData>()
    val voiceSpeed = mutableFloatStateOf(1f)
    val voicePitch = mutableFloatStateOf(1f)
    val activeVoice = mutableStateOf<VoiceData?>(null)
    val serviceLoadedFlow = MutableSharedFlow<Unit>(replay = 0)
    val queueList = _queueList as Map<String, T>
    val currentTextSpeakFlow = _currentTextSpeakFlow.shareIn(
        scope = scope,
        started = SharingStarted.Eagerly
    )

    private val auxiliaryServices = mutableListOf<TextToSpeech>()

    // Храним enginePackage сами — service.defaultEngine всегда возвращает системный дефолт,
    // независимо от того с каким enginePackage был создан этот конкретный service объект.
    private var currentEnginePackage: String = ""

    lateinit var service: TextToSpeech
        private set

    val currentActiveItemState = mutableStateOf(initialItemState)

    fun init() {
        service = appTtsEngine.getOrCreate(onReady = ::onServiceReady)
        onServiceReady()
    }

    private fun onServiceReady() {
        currentEnginePackage = service.defaultEngine ?: ""
        Log.d("TTS", "onServiceReady engine=$currentEnginePackage")
        listenToUtterances()
        updateActiveVoice()
        collectVoicesFromAllEngines()
    }

    fun getCurrentEnginePackage(): String = currentEnginePackage

    fun reinitWithEngine(enginePackage: String, voiceId: String) {
        Log.d("TTS", "reinitWithEngine engine=$enginePackage voice=$voiceId")
        auxiliaryServices.forEach { runCatching { it.shutdown() } }
        auxiliaryServices.clear()

        service.stop()

        val savedSpeed = voiceSpeed.floatValue
        val savedPitch = voicePitch.floatValue

        appTtsEngine.reinit(enginePackage) {
            service = appTtsEngine.getOrCreate()
            currentEnginePackage = enginePackage
            service.setSpeechRate(savedSpeed)
            service.setPitch(savedPitch)
            val voice = service.voices?.find { it.name == voiceId }
            if (voice != null) {
                service.voice = voice
                updateActiveVoice()
            }
            listenToUtterances()
            scope.launch { serviceLoadedFlow.emit(Unit) }
        }
    }

    private fun collectVoicesFromAllEngines() {
        val engines = service.engines
        var pending = engines.size

        if (engines.isEmpty()) {
            return
        }

        engines.forEach { engineInfo ->
            if (engineInfo.name == service.defaultEngine) {
                val voices = service.voices
                    ?.map { it.toVoiceData(engineInfo.name) }
                    ?: emptyList()
                availableVoices.addAll(voices)
                if (--pending == 0) scope.launch { serviceLoadedFlow.emit(Unit) }
            } else {
                var aux: TextToSpeech? = null
                aux = TextToSpeech(context, { auxStatus ->
                    if (auxStatus == TextToSpeech.SUCCESS) {
                        val voices = aux?.voices
                            ?.map { it.toVoiceData(engineInfo.name) }
                            ?: emptyList()
                        availableVoices.addAll(voices)
                    }
                    runCatching { aux?.shutdown() }
                    auxiliaryServices.remove(aux)
                    if (--pending == 0) scope.launch { serviceLoadedFlow.emit(Unit) }
                }, engineInfo.name)
                auxiliaryServices.add(aux)
            }
        }
    }

    fun stop() {
        Log.d("TTS", "stop() queueSize=${_queueList.size}")
        service.stop()
        _queueList.clear()
        _queueListItemSize.clear()
    }

    fun clearQueue() {
        Log.d("TTS", "clearQueue() queueSize=${_queueList.size}")
        _queueList.clear()
        _queueListItemSize.clear()
    }

    fun speak(text: String, textSynthesis: T) {
        val subItems = delimiterAwareTextSplitter(
            fullText = text,
            maxSliceLength = maxStringLengthPerTextUnit(),
            charDelimiter = '.'
        )
        _queueList[textSynthesis.utteranceId] = textSynthesis
        _queueListItemSize[textSynthesis.utteranceId] = subItems.size

        Log.d("TTS", "speak id=${textSynthesis.utteranceId} subItems=${subItems.size} queueSize=${_queueList.size}")
        subItems.forEachIndexed { index, textSlice ->
            val uniqueID = "$index|${textSynthesis.utteranceId}"
            val bundle = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueID)
            }
            service.speak(textSlice, TextToSpeech.QUEUE_ADD, bundle, uniqueID)
        }
    }

    fun setCurrentSpeakState(textSynthesis: T) {
        currentActiveItemState.value = textSynthesis
        scope.launch { _currentTextSpeakFlow.emit(textSynthesis) }
    }

    fun trySetVoiceById(id: String): Boolean {
        val voice = service.voices?.find { it.name == id } ?: return false
        service.voice = voice
        updateActiveVoice()
        Log.d("TTS", "trySetVoiceById($id) -> success")
        return true
    }

    fun trySetVoicePitch(value: Float): Boolean {
        if (value < 0.1 || value > 5) {
            Log.w("TTS", "trySetVoicePitch: invalid $value")
            return false
        }
        val result = service.setPitch(value)
        val success = result == TextToSpeech.SUCCESS
        Log.d("TTS", "trySetVoicePitch($value) -> $success")
        if (success) {
            voicePitch.floatValue = value
            return true
        }
        return false
    }

    fun trySetVoiceSpeed(value: Float): Boolean {
        if (value < 0.1 || value > 5) {
            Log.w("TTS", "trySetVoiceSpeed: invalid $value")
            return false
        }
        val result = service.setSpeechRate(value)
        val success = result == TextToSpeech.SUCCESS
        Log.d("TTS", "trySetVoiceSpeed($value) -> $success")
        if (success) {
            voiceSpeed.floatValue = value
            return true
        }
        return false
    }

    private fun maxStringLengthPerTextUnit() = TextToSpeech.getMaxSpeechInputLength()

    private fun updateActiveVoice() {
        activeVoice.value = service.voice?.toVoiceData(currentEnginePackage)
    }

    private fun Voice.toVoiceData(enginePackage: String) = VoiceData(
        id = name,
        language = locale.displayLanguage,
        needsInternet = isNetworkConnectionRequired,
        quality = quality,
        enginePackage = enginePackage,
    )

    private fun listenToUtterances() {
        service.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null) return
                val itemUtteranceIndex = utteranceId
                    .substringBefore('|', "")
                    .toIntOrNull() ?: return
                if (itemUtteranceIndex != 0) return

                val itemUtteranceId = utteranceId.substringAfter('|')
                val res: T = _queueList[itemUtteranceId]
                    ?.copyWithState(playState = Utterance.PlayState.PLAYING)
                    ?: return

                currentActiveItemState.value = res
                scope.launch { _currentTextSpeakFlow.emit(res) }
            }

            override fun onDone(utteranceId: String?) = onFinished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onFinished(utteranceId)

            private fun onFinished(utteranceId: String?) {
                if (utteranceId == null) {
                    Log.w("TTS", "onFinished: null id")
                    return
                }
                val subItemUtteranceIndex = utteranceId
                    .substringBefore('|', "")
                    .toIntOrNull() ?: run {
                        Log.w("TTS", "onFinished: cant parse index from $utteranceId")
                        return
                    }
                val itemUtteranceId = utteranceId.substringAfter('|')

                val itemSize = _queueListItemSize[itemUtteranceId]?.minus(1) ?: run {
                    Log.w("TTS", "onFinished: no itemSize for $itemUtteranceId")
                    return
                }
                if (itemSize != subItemUtteranceIndex) return

                val res: T = _queueList[itemUtteranceId]
                    ?.copyWithState(playState = Utterance.PlayState.FINISHED)
                    ?: run {
                        Log.w("TTS", "onFinished: no queue entry for $itemUtteranceId")
                        return
                    }

                _queueList.remove(itemUtteranceId)
                _queueListItemSize.remove(itemUtteranceId)

                Log.d("TTS", "onFinished -> FINISHED $itemUtteranceId queueSize=${_queueList.size}")
                currentActiveItemState.value = res
                scope.launch { _currentTextSpeakFlow.emit(res) }
            }
        })
    }
}