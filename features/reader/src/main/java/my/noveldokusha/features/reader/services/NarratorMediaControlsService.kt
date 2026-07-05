package my.noveldokusha.features.reader.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.core.utils.isServiceRunning
import javax.inject.Inject


@AndroidEntryPoint
internal class NarratorMediaControlsService : Service() {

    companion object {
        fun start(ctx: Context) {
            if (!isRunning(ctx))
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, NarratorMediaControlsService::class.java)
                )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, NarratorMediaControlsService::class.java))
        }

        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(NarratorMediaControlsService::class.java)
    }

    @Inject
    lateinit var narratorNotification: NarratorMediaControlsNotification

    private var audioFocusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
        Log.d("MediaService", "AudioFocus requested AUDIOFOCUS_GAIN")
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
            Log.d("MediaService", "AudioFocus abandoned")
        }
    }

    override fun onCreate() {
        super.onCreate()
        requestAudioFocus()

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.registerMediaButtonEventReceiver(
            ComponentName(this, androidx.media.session.MediaButtonReceiver::class.java)
        )

        val notification = narratorNotification.createNotificationMediaControls(this)
        if (notification != null) {
            startForeground(narratorNotification.notificationId, notification)
        } else {
            // Создаем минимальное уведомление, чтобы удовлетворить требования foreground сервиса
            val defaultNotification = narratorNotification.createDefaultNotification(this)
            startForeground(narratorNotification.notificationId, defaultNotification)
        }
    }

    override fun onDestroy() {
        abandonAudioFocus()
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.unregisterMediaButtonEventReceiver(
            ComponentName(this, androidx.media.session.MediaButtonReceiver::class.java)
        )
        narratorNotification.close()
        super.onDestroy()
    }

    @SuppressLint("MissingSuperCall")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MediaService", "onStartCommand: action=${intent?.action}")
        narratorNotification.handleCommand(intent)
        if (intent == null) return START_NOT_STICKY
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null
}
