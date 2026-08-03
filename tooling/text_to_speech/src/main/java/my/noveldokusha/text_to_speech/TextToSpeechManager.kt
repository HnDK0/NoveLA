package my.noveldokusha.text_to_speech

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections
import me.nanihadesuka.algorithms.delimiterAwareTextSplitter

interface Utterance<T : Utterance<T>> {
    enum class PlayState { PLAYING, FINISHED, LOADING }

    val utteranceId: String
    val playState: PlayState
    fun copyWithState(playState: PlayState): T
}

@Immutable
data class VoiceData(
    val id: String,
    val language: String,
    val needsInternet: Boolean,
    val quality: Int,
    val enginePackage: String,
)

private const val MIN_RANGE_HOLD_MS = 100L
// Реальная частота синтеза каждого куска берётся из onBeginSynthesis (сетевые голоса
// Google проигрывают звук медленнее маркерного темпа, поэтому маркеры приходят раньше,
// чем слово звучит). Отношение реальная_длительность / маркерная измеряется по абзацу:
// (wall onFinished - wall onStart первого куска) делим на маркерную длительность абзаца.
// Fallback на 48000 — только если движок почему-то не прислал onBeginSynthesis для куска.
private const val FALLBACK_SAMPLE_RATE = 48000f
// Стартовый ratio для сетевых голосов без персиста: реальный ~2.0, но seed 1.96 даёт
// ошибку подсветки ~2% уже на первом абзаце, пока регрессия не уточнит точное значение.
private const val INTERNET_SEED_RATIO = 1.96f
// Размер скользящего окна пар (абзацев) для регрессии. Окно короткое, потому что seed
// уже почти точен, а регрессия лишь подстраивает под конкретный голос/скорость.
private const val CALIB_WINDOW = 4
// Минимум пар для расчёта slope (иначе деление на ноль или одна точка без разброса).
private const val CALIB_WINDOW_MIN = 2

// Регрессия wall = slope * marker + tail по (маркерные_мс, реальные_мс) парам завершённых
// абзацев. slope — маркерные мс в единицу реального времени, т.е. ratio (вход уже переведён
// в одинаковые единицы, частота дискретизации в пересчёте не нужна). Постоянный хвост
// абзаца (пауза после последнего слова до onFinished) сидит в intercept и вычитается:
// короткие абзацы не завышают ratio. null при < CALIB_WINDOW_MIN парах или нулевом разбросе frame.
internal fun calibrationRegressionSlope(pairs: List<Pair<Long, Long>>): Float? {
    if (pairs.size < CALIB_WINDOW_MIN) return null
    val n = pairs.size
    val meanFrame = pairs.sumOf { it.first }.toDouble() / n
    val meanWall = pairs.sumOf { it.second }.toDouble() / n
    var numerator = 0.0
    var denominator = 0.0
    for ((frame, wall) in pairs) {
        val dx = frame - meanFrame
        numerator += dx * (wall - meanWall)
        denominator += dx * dx
    }
    if (denominator == 0.0) return null
    return (numerator / denominator).toFloat()
}

class TextToSpeechManager<T : Utterance<T>>(
    private val context: Context,
    private val appTtsEngine: AppTtsEngine,
    initialItemState: T,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "TextToSpeechManager: uncaught exception in scope")
            }
    )
    private val _queueList = mutableMapOf<String, T>()
    // Мапы ниже пишутся из TTS-callback тредов (onStart/onRangeStart/onFinished могут
    // прийти из разных потоков движка) и читаются из корутин на Dispatchers.Default —
    // поэтому обёрнуты в synchronizedMap, чтобы не ловить гонки данных. Исключение —
    // _queueList выше: обычный mutableMapOf, гонка предсуществующая, не тронута.
    private val _queueListItemSize = Collections.synchronizedMap(mutableMapOf<String, Int>())
    // Wall-time старта АБЗАЦА (первого куска): фиксируется один раз и служит якорем
    // для калибровки и для стейл-проверки отложенных показов.
    private val _itemStartWall = Collections.synchronizedMap(mutableMapOf<String, Long>())
    // Wall-time старта каждого ПОД-КУСКА (ключ — полный uniqueID куска): frame в
    // onRangeStart отсчитывается от начала куска, поэтому якорь нужен на каждый кусок.
    private val _sliceStartWall = Collections.synchronizedMap(mutableMapOf<String, Long>())
    // Последний frame каждого под-куска (ключ — полный uniqueID куска): маркерная длина
    // куска = его последний frame (отсчёт от начала куска), переводится в мс с реальной
    // частотой куска и суммируется по кускам в _itemMarkerMs.
    private val _sliceLastFrame = Collections.synchronizedMap(mutableMapOf<String, Int>())
    // Реальная частота синтеза каждого под-куска (ключ — полный uniqueID куска) из
    // onBeginSynthesis: frame в onRangeStart отсчитан в ней, поэтому frame->мс переводится
    // с той же частотой и в подсветке, и в калибровке.
    private val _sliceSampleRate = Collections.synchronizedMap(mutableMapOf<String, Float>())
    // Накопленная маркерная длина АБЗАЦА (сумма переведённых в мс последних frame всех кусков).
    private val _itemMarkerMs = Collections.synchronizedMap(mutableMapOf<String, Long>())
    // Пары (маркерные_мс, wallDelta) завершённых абзацев: полная маркерная длина абзаца против
    // реального времени от onStart первого куска до onFinished последнего. Не очищается
    // в clearAllTracking — окно живёт сквозь паузы/смены глав/новел (ratio — свойство голоса).
    private val _calibPairs = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
    private val _currentTextSpeakFlow = MutableSharedFlow<T>()
    private val _delayedRangeJobs = mutableListOf<Job>()

    // Сериализуем показы подсветки: сетевые движки присылают onRangeStart пачками,
    // и без очереди соседние диапазоны накладываются, а короткие слова «проглатываются».
    private val _rangeMutex = Mutex()
    private var _lastRangeShownWall = 0L

    // frame в onRangeStart отсчитан в реальной частоте синтеза куска (см. _sliceSampleRate),
    // а _audioToMarkerRatio абсорбирует разницу реального темпа воспроизведения
    // (сетевой голос играет медленнее маркерной скорости). Отношение измеряется по
    // завершённым абзацам (регрессия маркерные_мс->wall) и персистится.
    // Ratio пишется из onFinished-треда и читается из других тредов движка (onRangeStart)
    // и из Main (loadRatio) — @Volatile гарантирует видимость между потоками.
    @Volatile
    private var _audioToMarkerRatio = 1f
    private val ttsPrefs by lazy {
        context.getSharedPreferences("tts_preferences", Context.MODE_PRIVATE)
    }

    private fun calibrationKey(): String {
        val voiceId = activeVoice.value?.id ?: currentEnginePackage
        // Флаг сети в ключе: у одного голоса Google needsInternet может меняться (например,
        // после установки данных голоса), и сетевой ratio (~2) не должен применяться к
        // локальной версии того же голоса.
        return "marker_ratio_v2_${voiceId}_${activeVoice.value?.needsInternet}_${voiceSpeed.floatValue}"
    }

    private fun loadRatio() {
        // Без установленного голоса нет осмысленного ключа — чужие значения не применяем
        // (на холодном старте trySetVoiceSpeed может прийти раньше восстановления голоса).
        if (activeVoice.value == null) return
        // Сменился голос/скорость/движок — окно пар регрессии от старого голоса не годится.
        synchronized(_calibPairs) { _calibPairs.clear() }
        val stored = ttsPrefs.getFloat(calibrationKey(), 0f)
        _audioToMarkerRatio = if (stored > 0f) {
            stored.coerceIn(1f, 4f)
        } else if (activeVoice.value?.needsInternet == true) {
            // Нет персиста и голос сетевой: стартуем с почти точного seed (реальный ~2.0),
            // регрессия уточнит за пару абзацев — подсветка не плывёт с первого слова.
            INTERNET_SEED_RATIO
        } else {
            1f
        }
        Timber.d( "calib load ratio=$_audioToMarkerRatio key=${calibrationKey()}")
    }

    private fun saveRatio() {
        ttsPrefs.edit().putFloat(calibrationKey(), _audioToMarkerRatio).apply()
    }

    private fun addCalibrationSample(itemUtteranceId: String, markerMs: Long, wallDelta: Long) {
        synchronized(_calibPairs) {
            _calibPairs += markerMs to wallDelta
            if (_calibPairs.size > CALIB_WINDOW) _calibPairs.removeAt(0)
        }
        calibrateRatio()
    }

    private fun calibrateRatio() {
        // Без установленного голоса нет ни смысла, ни ключа для калибровки (симметрично loadRatio).
        if (activeVoice.value == null) return
        // Локальные голоса играют аудио с маркерным темпом — калибровка им не нужна,
        // ratio навсегда 1.0. Иначе регрессия по завершённым абзацам могла бы увести ratio
        // выше 1.0 (последнее слово слайса не входит в маркерную длину, а wall-время включает
        // хвост/паузы), и подсветка локальных голосов отставала бы от звука.
        if (activeVoice.value?.needsInternet == false) {
            _audioToMarkerRatio = 1f
            return
        }
        val pairs = synchronized(_calibPairs) { _calibPairs.toList() }
        calibrationRegressionSlope(pairs)?.let { slope ->
            // EMA-смешивание вместо перезаписи: одна пара с подбуферизацией сети может дать
            // шумный slope, а персист/seed уже почти точны. Бленд гасит шум без потери сходимости.
            _audioToMarkerRatio = _audioToMarkerRatio * 0.5f + slope.coerceIn(1f, 4f) * 0.5f
            saveRatio()
            Timber.d("calib slope=%.2f ratio=%.2f pairs=%d".format(slope, _audioToMarkerRatio, pairs.size))
        }
    }

    val availableVoices = mutableStateListOf<VoiceData>()
    val voiceSpeed = mutableFloatStateOf(1f)
    val voicePitch = mutableFloatStateOf(1f)
    val activeVoice = mutableStateOf<VoiceData?>(null)
    // serviceLoadedFlow с replay=1 защищает стартовую подписку (ReaderTextToSpeech init):
    // голоса могут эмитнуться раньше, чем take(1)-коллектор зарегистрируется в другой
    // корутине на Dispatchers.Default. Но эта же буферизация ломает ожидание смены движка:
    // там нужен СВЕЖИЙ сигнал о завершении reinit, а не устаревший. Поэтому reinit-путь
    // использует отдельный reinitDoneFlow (replay=0); setVoice ждёт именно его.
    val serviceLoadedFlow = MutableSharedFlow<Unit>(replay = 1)
    val reinitDoneFlow = MutableSharedFlow<Unit>()
    val queueList = _queueList as Map<String, T>
    val currentTextSpeakFlow = _currentTextSpeakFlow.shareIn(
        scope = scope,
        started = SharingStarted.Eagerly
    )

    val currentSpeakingText = mutableStateOf("")
    val spokenWordRange = mutableStateOf<IntRange?>(null)

    private val auxiliaryServices = mutableListOf<TextToSpeech>()

    // Храним enginePackage сами — service.defaultEngine всегда возвращает системный дефолт,
    // независимо от того с каким enginePackage был создан этот конкретный service объект.
    private var currentEnginePackage: String = ""

    lateinit var service: TextToSpeech
        private set

    val currentActiveItemState = mutableStateOf(initialItemState)

    fun init() {
        service = appTtsEngine.getOrCreate(onReady = ::onServiceReady)
        currentEnginePackage = appTtsEngine.getBoundEnginePackage() ?: (service.defaultEngine ?: "")
        onServiceReady()
    }

    private fun onServiceReady() {
        // service.defaultEngine всегда возвращает СИСТЕМНЫЙ дефолт, а не движок, к которому
        // привязан этот инстанс — берём фактический пакет из AppTtsEngine.
        currentEnginePackage = appTtsEngine.getBoundEnginePackage() ?: (service.defaultEngine ?: "")
        Timber.d( "onServiceReady engine=$currentEnginePackage")
        listenToUtterances()
        updateActiveVoice()
        collectVoicesFromAllEngines()
    }

    fun getCurrentEnginePackage(): String = currentEnginePackage

    fun reinitWithEngine(enginePackage: String, voiceId: String) {
        Timber.d( "reinitWithEngine engine=$enginePackage voice=$voiceId")
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
                // updateActiveVoice сама грузит калибровку под фактически активный голос
                // нового движка — отдельный loadRatio здесь не нужен.
                updateActiveVoice()
            }
            listenToUtterances()
            scope.launch { reinitDoneFlow.emit(Unit) }
        }
    }

    private fun collectVoicesFromAllEngines() {
        val engines = service.engines
        var pending = engines.size

        if (engines.isEmpty()) {
            return
        }

        // onServiceReady вызывается дважды (синхронно и из onInit движка) —
        // сбрасываем, чтобы голоса не дублировались в списке.
        availableVoices.clear()
        engines.forEach { engineInfo ->
            if (engineInfo.name == currentEnginePackage) {
                val voices = service.voices
                    ?.map { it.toVoiceData(engineInfo.name) }
                    ?: emptyList()
                availableVoices.addAll(voices)
                // Эмитим «голоса собраны» при завершении сбора (даже пустой список):
                // стартовый восстановитель в ReaderTextToSpeech применяет speed/pitch на
                // первой эмиссии, а голос — на последующей наполненной. Если эмитить
                // только при непустом списке, при сбое инициализации движка take(1)-подписка
                // зависла бы навсегда и speed/pitch не применились.
                if (--pending == 0) {
                    scope.launch { serviceLoadedFlow.emit(Unit) }
                }
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
                    if (--pending == 0) {
                        scope.launch { serviceLoadedFlow.emit(Unit) }
                    }
                }, engineInfo.name)
                auxiliaryServices.add(aux)
            }
        }
    }

    fun stop() {
        Timber.d( "stop() queueSize=${_queueList.size}")
        service.stop()
        cancelDelayedRanges()
        clearAllTracking()
    }

    fun shutdown() {
        runCatching { service.stop() }
        auxiliaryServices.forEach { runCatching { it.shutdown() } }
        auxiliaryServices.clear()
        cancelDelayedRanges()
        clearAllTracking()
        scope.cancel()
    }

    fun clearQueue() {
        Timber.d("clearQueue() queueSize=${_queueList.size}")
        cancelDelayedRanges()
        clearAllTracking()
    }

    private fun clearAllTracking() {
        _queueList.clear()
        _queueListItemSize.clear()
        _itemStartWall.clear()
        _sliceStartWall.clear()
        _sliceLastFrame.clear()
        _sliceSampleRate.clear()
        _itemMarkerMs.clear()
        // _calibPairs НЕ очищаем: пары завершённых абзацев живут сквозь stop/clearQueue,
        // чтобы паузы и смены глав/новел не сбрасывали уже накопленную калибровку голоса.
    }

    private fun cancelDelayedRanges() {
        val jobs = synchronized(_delayedRangeJobs) {
            _delayedRangeJobs.toList().also { _delayedRangeJobs.clear() }
        }
        jobs.forEach { it.cancel() }
        _lastRangeShownWall = 0L
    }

    fun speak(text: String, textSynthesis: T, leadingOffset: Int = 0) {
        val subItems = delimiterAwareTextSplitter(
            fullText = text,
            maxSliceLength = maxStringLengthPerTextUnit(),
            charDelimiter = '.'
        )
        _queueList[textSynthesis.utteranceId] = textSynthesis
        _queueListItemSize[textSynthesis.utteranceId] = subItems.size
        val sliceBounds = buildList {
            var offset = leadingOffset
            subItems.forEach { slice ->
                add(offset until (offset + slice.length))
                offset += slice.length
            }
        }

        Timber.d( "speak id=${textSynthesis.utteranceId} subItems=${subItems.size} queueSize=${_queueList.size} bounds=$sliceBounds wall=${SystemClock.elapsedRealtime()}")
        var enqueueFailed = false
        var currentOffset = 0
        subItems.forEachIndexed { index, textSlice ->
            val uniqueID = "$index|${currentOffset + leadingOffset}|${textSynthesis.utteranceId}"
            val bundle = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uniqueID)
            }
            val result = service.speak(textSlice, TextToSpeech.QUEUE_ADD, bundle, uniqueID)
            if (result != TextToSpeech.SUCCESS) {
                Timber.w( "speak failed id=$uniqueID result=$result")
                enqueueFailed = true
            }
            currentOffset += textSlice.length
        }

        if (!enqueueFailed) {
            currentSpeakingText.value = text
        }

        // ponytail: speak() returning ERROR means none of the slices will play and no
        // callback fires -> the item would stay in the queue forever and freeze reading.
        // Skip it so the session keeps advancing.
        if (enqueueFailed) {
            completeItem(textSynthesis.copyWithState(playState = Utterance.PlayState.FINISHED))
        }
    }

    private fun completeItem(item: T) {
        _queueList.remove(item.utteranceId)
        _queueListItemSize.remove(item.utteranceId)
        _itemStartWall.remove(item.utteranceId)
        _itemMarkerMs.remove(item.utteranceId)
        currentActiveItemState.value = item
        scope.launch { _currentTextSpeakFlow.emit(item) }
    }

    fun setCurrentSpeakState(textSynthesis: T) {
        currentActiveItemState.value = textSynthesis
        scope.launch { _currentTextSpeakFlow.emit(textSynthesis) }
    }

    fun trySetVoiceById(id: String): Boolean {
        val voice = service.voices?.find { it.name == id }
        if (voice == null) {
            Timber.w("trySetVoiceById($id) -> not found in ${service.voices?.size ?: 0} voices")
            return false
        }
        service.voice = voice
        // updateActiveVoice сама грузит калибровку под применённый голос.
        updateActiveVoice()
        Timber.d( "trySetVoiceById($id) -> success")
        return true
    }

    fun trySetVoicePitch(value: Float): Boolean {
        if (value < 0.1 || value > 5) {
            Timber.w( "trySetVoicePitch: invalid $value")
            return false
        }
        val result = service.setPitch(value)
        val success = result == TextToSpeech.SUCCESS
        Timber.d( "trySetVoicePitch($value) -> $success")
        if (success) {
            voicePitch.floatValue = value
            return true
        }
        return false
    }

    fun trySetVoiceSpeed(value: Float): Boolean {
        if (value < 0.1 || value > 5) {
            Timber.w( "trySetVoiceSpeed: invalid $value")
            return false
        }
        val result = service.setSpeechRate(value)
        val success = result == TextToSpeech.SUCCESS
        Timber.d( "trySetVoiceSpeed($value) -> $success")
        if (success) {
            voiceSpeed.floatValue = value
            // Калибровка грузится только под известный голос: на холодном старте скорость
            // применяется до восстановления голоса, и её ratio загрузит сам updateActiveVoice.
            if (activeVoice.value != null) loadRatio()
            return true
        }
        return false
    }

    private fun maxStringLengthPerTextUnit() = TextToSpeech.getMaxSpeechInputLength()

    private fun updateActiveVoice() {
        activeVoice.value = service.voice?.toVoiceData(currentEnginePackage)
        // Калибровка грузится событийно под фактически активный голос (а не по точкам вызова):
        // сетевой голос после рестарта может подгрузиться позже — ratio применится в момент
        // его применения; пока голос не установлен (null), ratio не трогаем.
        if (activeVoice.value != null) loadRatio()
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
                val wall = SystemClock.elapsedRealtime()
                // Якорь для КАЖДОГО под-куска: frame в onRangeStart отсчитывается от
                // начала своего куска, поэтому каждому куску нужен собственный wall-time.
                _sliceStartWall[utteranceId] = wall

                val itemUtteranceIndex = utteranceId
                    .substringBefore('|', "")
                    .toIntOrNull() ?: return
                if (itemUtteranceIndex != 0) return

                val itemUtteranceId = utteranceId.substringAfterLast('|')
                _itemStartWall[itemUtteranceId] = wall
                Timber.d("onStart id=$utteranceId itemId=$itemUtteranceId wall=$wall")
                val res: T = _queueList[itemUtteranceId]
                    ?.copyWithState(playState = Utterance.PlayState.PLAYING)
                    ?: return

                spokenWordRange.value = null
                currentActiveItemState.value = res
                scope.launch { _currentTextSpeakFlow.emit(res) }
            }

            override fun onDone(utteranceId: String?) = onFinished(utteranceId)

            // API 21+ calls this overload; the deprecated onError(String?) below is dead
            // on modern devices. Without it a failed utterance never completes and the
            // whole reading session freezes (highlighted but silent, no progress).
            override fun onError(utteranceId: String?, errorCode: Int) {
                Timber.w( "onError($errorCode) $utteranceId")
                onErrorFinished(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Timber.w( "onError(deprecated) $utteranceId")
                onErrorFinished(utteranceId)
            }

            override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                // Реальная частота синтеза куска: frame в onRangeStart отсчитан в ней,
                // поэтому frame->мс переводится с ней же (в подсветке и в калибровке).
                // Обычно приходит до первого onRangeStart куска, но не гарантировано
                // спецификацией — при отсутствии записи используется FALLBACK_SAMPLE_RATE.
                if (utteranceId != null && sampleRateInHz > 0) {
                    _sliceSampleRate[utteranceId] = sampleRateInHz.toFloat()
                }
                Timber.d("onBeginSynthesis id=$utteranceId sampleRate=$sampleRateInHz channels=$channelCount")
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId == null) return
                val parts = utteranceId.split('|')
                if (parts.size < 3) return
                val offset = parts[1].toIntOrNull() ?: 0
                val itemUtteranceId = parts[2]
                val wall = SystemClock.elapsedRealtime()
                // Якорь текущего под-куска (полный uniqueID): frame отсчитывается от начала
                // куска, поэтому и время его звучания считаем от старта этого куска.
                val sliceWall = _sliceStartWall[utteranceId]
                val range = (start + offset) until (end + offset)
                // Запоминаем последний frame куска: это маркерная длина куска, которая на
                // onFinished последнего куска абзаца переводится в мс и суммируется в _itemMarkerMs.
                _sliceLastFrame[utteranceId] = frame
                // frame — позиция слова в синтезированном аудио (в частоте синтеза куска),
                // но сетевые голоса синтезируют аудио заранее и реально проигрывают его
                // медленнее, поэтому маркер приходит раньше, чем слово звучит.
                // Масштабируем frame->время на измеренный _audioToMarkerRatio и задерживаем показ.
                val delayMs = if (sliceWall != null) {
                    // Считаем дельту маркера отдельно в Long: смешение Long и Float в одном
                    // выражении даёт Float-квантование (ULP 256 мс при wall ~2.5e9), из-за
                    // чего задержка прыгала ступенями вместо непрерывных миллисекунд.
                    val markerMs = (frame / (_sliceSampleRate[utteranceId] ?: FALLBACK_SAMPLE_RATE) * _audioToMarkerRatio * 1000).toLong()
                    (sliceWall + markerMs - wall).coerceAtLeast(0)
                } else {
                    0
                }
                // Стейл-проверка идёт по якорю АБЗАЦА, а не куска: если абзац сменился,
                // отложенные показы старого абзаца не должны проскочить в новый.
                publishRange(itemUtteranceId, _itemStartWall[itemUtteranceId], range, wall + delayMs)
            }

            private fun publishRange(itemUtteranceId: String, startWall: Long?, range: IntRange, targetWall: Long) {
                // Все показы (включая мгновенные, где targetWall <= now) идут через mutex:
                // гарантируем строгий порядок, минимальное время удержания и стейл-проверку
                // даже когда часть колбэков прилетает без задержки.
                val job = scope.launch {
                    try {
                        _rangeMutex.withLock {
                            val now = SystemClock.elapsedRealtime()
                            // Hold между показами нужен сетевым голосам (пачки onRangeStart):
                            // локальные играют с маркерным темпом, и задержка между словами
                            // накопительно сдвигала бы подсветку от звука.
                            val holdMs = if (activeVoice.value?.needsInternet == true) MIN_RANGE_HOLD_MS else 0L
                            val showAt = maxOf(targetWall, _lastRangeShownWall + holdMs)
                            delay((showAt - now).coerceAtLeast(0))
                            // Абзац мог смениться, остановиться или завершиться с ошибкой
                            // (осиротевший слайс: _itemStartWall уже удалён) — показываем
                            // только если это всё ещё тот самый живой абзац.
                            if (startWall != null && _itemStartWall[itemUtteranceId] == startWall) {
                                spokenWordRange.value = range
                                // Hold-пол двигают только реально показанные диапазоны:
                                // отброшенный стейл-показ не должен отталкивать подсветку
                                // следующего слова (серия мусора сдвигала бы всё дальше).
                                _lastRangeShownWall = SystemClock.elapsedRealtime()
                            }
                        }
                    } catch (_: CancellationException) {
                        // Отменено (stop/clearQueue/новый абзац) — запись удалится
                        // в invokeOnCompletion ниже.
                    }
                }
                // Гарантируем удаление job из списка даже если он завершился ДО того,
                // как был добавлен в map (наносекундное окно TOCTOU): invokeOnCompletion
                // срабатывает и для уже завершённой корутины.
                job.invokeOnCompletion {
                    synchronized(_delayedRangeJobs) {
                        _delayedRangeJobs.remove(job)
                    }
                }
                synchronized(_delayedRangeJobs) {
                    _delayedRangeJobs += job
                }
            }

            private fun onErrorFinished(utteranceId: String?) {
                if (utteranceId == null) return
                // Симметричная onFinished чистка per-slice записей: иначе при ошибке
                // слайса его _sliceStartWall/_sliceLastFrame/_sliceSampleRate висят до stop()/clearQueue().
                _sliceStartWall.remove(utteranceId)
                _sliceLastFrame.remove(utteranceId)
                _sliceSampleRate.remove(utteranceId)
                // Skip the broken item regardless of which sub-slice errored so reading
                // continues instead of stalling on a permanently stuck queue entry.
                val itemUtteranceId = utteranceId.substringAfterLast('|')
                val res: T = _queueList[itemUtteranceId]
                    ?.copyWithState(playState = Utterance.PlayState.FINISHED)
                    ?: return
                completeItem(res)
            }

            private fun onFinished(utteranceId: String?) {
                if (utteranceId == null) {
                    Timber.w( "onFinished: null id")
                    return
                }
                val wall = SystemClock.elapsedRealtime()
                Timber.d("onFinished id=$utteranceId wall=$wall")
                val subItemUtteranceIndex = utteranceId
                    .substringBefore('|', "")
                    .toIntOrNull() ?: run {
                        Timber.w( "onFinished: cant parse index from $utteranceId")
                        return
                    }
                val itemUtteranceId = utteranceId.substringAfterLast('|')

                val sliceLastFrame = _sliceLastFrame[utteranceId]
                val sliceSampleRate = _sliceSampleRate[utteranceId] ?: FALLBACK_SAMPLE_RATE
                _sliceStartWall.remove(utteranceId)
                _sliceLastFrame.remove(utteranceId)
                _sliceSampleRate.remove(utteranceId)

                val itemSize = _queueListItemSize[itemUtteranceId]?.minus(1) ?: run {
                    Timber.w( "onFinished: no itemSize for $itemUtteranceId")
                    return
                }
                // Последний кусок абзаца (frame каждого куска отсчитывается от его начала,
                // поэтому накопленная сумма переведённых в мс по кускам = полная маркерная
                // длительность абзаца).
                if (sliceLastFrame != null) {
                    val sliceMarkerMs = (sliceLastFrame / sliceSampleRate * 1000).toLong()
                    _itemMarkerMs[itemUtteranceId] =
                        (_itemMarkerMs[itemUtteranceId] ?: 0L) + sliceMarkerMs
                }
                if (itemSize != subItemUtteranceIndex) return

                // Абзац завершён: (маркерная длительность абзаца, реальное время от onStart до onFinished).
                val wallStart = _itemStartWall[itemUtteranceId]
                val totalMarkerMs = _itemMarkerMs.remove(itemUtteranceId)
                if (totalMarkerMs != null && wallStart != null && totalMarkerMs > 0) {
                    addCalibrationSample(itemUtteranceId, totalMarkerMs, wall - wallStart)
                }

                val res: T = _queueList[itemUtteranceId]
                    ?.copyWithState(playState = Utterance.PlayState.FINISHED)
                    ?: run {
                        Timber.w( "onFinished: no queue entry for $itemUtteranceId")
                        return
                    }

                completeItem(res)
            }
        })
    }
}