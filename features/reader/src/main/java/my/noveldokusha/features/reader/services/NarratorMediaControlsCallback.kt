package my.noveldokusha.features.reader.services

import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import android.util.Log
import my.noveldokusha.features.reader.features.ReaderTextToSpeech

internal class NarratorMediaControlsCallback(
    private val readerTextToSpeech: ReaderTextToSpeech
) : MediaSessionCompat.Callback() {

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        @Suppress("DEPRECATION")
        val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            ?: return super.onMediaButtonEvent(mediaButtonEvent)

        Log.d("MediaCallback", "onMediaButtonEvent: action=${keyEvent.action} keyCode=${keyEvent.keyCode}")
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

    override fun onPlay() {
        Log.d("MediaCallback", "onPlay()")
        readerTextToSpeech.state.setPlaying(true)
    }

    override fun onPause() {
        Log.d("MediaCallback", "onPause()")
        readerTextToSpeech.state.setPlaying(false)
    }

    override fun onSkipToNext() {
        readerTextToSpeech.state.playNextItem()
    }

    override fun onSkipToPrevious() {
        readerTextToSpeech.state.playPreviousItem()
    }

    override fun onRewind() {
        readerTextToSpeech.state.playPreviousItem()
    }

    override fun onFastForward() {
        readerTextToSpeech.state.playNextItem()
    }
}