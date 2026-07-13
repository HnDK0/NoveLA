package my.noveldokusha.features.reader.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import androidx.media.session.MediaButtonReceiver
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.core.utils.isServiceRunning
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
internal class NarratorMediaControlsService : Service() {

    companion object {
        // PlaybackStateCompat.ACTION_* не доступны в этом модуле (нет androidx.media:media
        // с PlaybackStateCompat), поэтому дублируем стабильные значения action.
        // buildMediaButtonPendingIntent(Context, long) маппит их в KEYCODE_MEDIA_*.
        private const val MEDIA_ACTION_PLAY = 4L   // PlaybackStateCompat.ACTION_PLAY
        private const val MEDIA_ACTION_PAUSE = 2L  // PlaybackStateCompat.ACTION_PAUSE

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
    private var wasPausedByFocusLoss = false

    private fun dispatchMediaButtonAction(action: Long) {
        try {
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, action).send()
        } catch (_: Exception) {
            Timber.d("dispatchMediaButtonAction failed for $action")
        }
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        wasPausedByFocusLoss = true
                        dispatchMediaButtonAction(MEDIA_ACTION_PAUSE)
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (wasPausedByFocusLoss) {
                            wasPausedByFocusLoss = false
                            dispatchMediaButtonAction(MEDIA_ACTION_PLAY)
                        }
                    }
                }
            }
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
        Timber.d("AudioFocus requested AUDIOFOCUS_GAIN")
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
            Timber.d("AudioFocus abandoned")
        }
    }

    override fun onCreate() {
        super.onCreate()
        requestAudioFocus()

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
        narratorNotification.close()
        super.onDestroy()
    }

    @SuppressLint("MissingSuperCall")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand: action=${intent?.action}")
        narratorNotification.handleCommand(intent)
        if (intent == null) return START_NOT_STICKY
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null
}
