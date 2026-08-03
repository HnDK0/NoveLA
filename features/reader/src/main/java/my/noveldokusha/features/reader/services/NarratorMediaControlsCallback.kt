package my.noveldokusha.features.reader.services

import android.content.Intent
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import my.noveldokusha.features.reader.features.ReaderTextToSpeech
import timber.log.Timber

internal class NarratorMediaControlsCallback(
    private val readerTextToSpeech: ReaderTextToSpeech
) : MediaSessionCompat.Callback() {

    // Media-кнопки (в отличие от UI, где уже есть debouncedAction) не имеют дебаунса:
    // быстрые повторные нажатия вызывают гонку stop()/start() и переполнение очереди
    // TTS. Троттлим только однотипные команды (Next,Prev — из них и приходит шторм),
    // по отдельным ключам: общий троттлинг резал бы легитимный системный onPause
    // (потеря аудиофокуса / вынутые наушники), приходящий сразу после кнопки.
    private val lastActionTimes = mutableMapOf<String, Long>()

    private fun throttled(key: String, action: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        val last = lastActionTimes[key] ?: 0L
        if (now - last < 450) return
        lastActionTimes[key] = now
        action()
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        @Suppress("DEPRECATION")
        val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            ?: return super.onMediaButtonEvent(mediaButtonEvent)

        Timber.d("onMediaButtonEvent: action=${keyEvent.action} keyCode=${keyEvent.keyCode}")
        if (keyEvent.action == KeyEvent.ACTION_DOWN) {
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    onSkipToPrevious()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    onRewind()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    onPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    onPlay()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                    val isPlaying = readerTextToSpeech.isSpeaking.value
                    if (isPlaying) onPause() else onPlay()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    onFastForward()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    onSkipToNext()
                    return true
                }
            }
        }
        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    override fun onPause() {
        Timber.d("onPause()")
        // Системная пауза (потеря аудиофокуса / вынутые наушники) приходит через
        // narratorNotification.pause() с уже выставленным isSystemPauseTrigger —
        // её троттлить нельзя: потеря фокуса часто случается сразу после кнопки,
        // и дроп паузы оставит TTS говорить. Троттлим только ручную (кнопочную)
        // паузу. pausedBySystem при системной паузе сохраняется — по нему
        // NarratorMediaControlsNotification.maybeAutoResume решает, возобновлять ли.
        // Флаг сбрасывается после setPlaying(false): setPlaying читает его ДО сброса
        // (при системной паузе видит true и не трогает pausedBySystem), а после сброса
        // следующая ручная пауза не выглядит системной (иначе userPaused не выставится
        // и maybeAutoResume самовозобновит чтение после ручной паузы).
        if (ReaderTextToSpeech.isSystemPauseTrigger) {
            readerTextToSpeech.state.setPlaying(false)
            ReaderTextToSpeech.isSystemPauseTrigger = false
            return
        }
        throttled("pause") {
            ReaderTextToSpeech.pausedBySystem = false
            readerTextToSpeech.state.setPlaying(false)
        }
    }

    override fun onPlay() {
        Timber.d("onPlay()")
        throttled("play") {
            ReaderTextToSpeech.pausedBySystem = false
            NarratorMediaControlsService.reacquireFocus()
            NarratorMediaControlsService.reassertActive()
            readerTextToSpeech.state.setPlaying(true)
        }
    }

    override fun onSkipToNext() {
        Timber.d("TTS-JUMP media: onSkipToNext")
        throttled("next") { readerTextToSpeech.state.playNextItem() }
    }

    override fun onSkipToPrevious() {
        Timber.d("TTS-JUMP media: onSkipToPrevious")
        throttled("prev") { readerTextToSpeech.state.playPreviousItem() }
    }

    override fun onRewind() {
        Timber.d("TTS-JUMP media: onRewind")
        throttled("prev") { readerTextToSpeech.state.playPreviousItem() }
    }

    override fun onFastForward() {
        Timber.d("TTS-JUMP media: onFastForward")
        throttled("next") { readerTextToSpeech.state.playNextItem() }
    }
}