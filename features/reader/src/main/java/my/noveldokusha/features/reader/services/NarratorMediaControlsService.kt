package my.noveldokusha.features.reader.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import androidx.media.session.MediaButtonReceiver
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.features.reader.features.ReaderTextToSpeech
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

        private var serviceInstance: NarratorMediaControlsService? = null

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

        fun reacquireFocus() {
            serviceInstance?.requestAudioFocus()
        }

        private fun isRunning(context: Context): Boolean =
            context.isServiceRunning(NarratorMediaControlsService::class.java)
    }

    @Inject
    lateinit var narratorNotification: NarratorMediaControlsNotification

    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPausedByFocusLoss = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("ACTION_AUDIO_BECOMING_NOISY received")
            wasPausedByFocusLoss = true
            ReaderTextToSpeech.isSystemPauseTrigger = true
            ReaderTextToSpeech.pausedBySystem = true
            dispatchMediaButtonAction(MEDIA_ACTION_PAUSE)
        }
    }

    private fun dispatchMediaButtonAction(action: Long) {
        try {
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, action).send()
        } catch (_: Exception) {
            Timber.d("dispatchMediaButtonAction failed for $action")
        }
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioFocusRequest == null) {
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
                            ReaderTextToSpeech.isSystemPauseTrigger = true
                            ReaderTextToSpeech.pausedBySystem = true
                            dispatchMediaButtonAction(MEDIA_ACTION_PAUSE)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            if (wasPausedByFocusLoss && ReaderTextToSpeech.pausedBySystem) {
                                wasPausedByFocusLoss = false
                                ReaderTextToSpeech.pausedBySystem = false
                                dispatchMediaButtonAction(MEDIA_ACTION_PLAY)
                            } else {
                                wasPausedByFocusLoss = false
                            }
                        }
                    }
                }
                .build()
            audioFocusRequest = request
        }
        audioManager.requestAudioFocus(audioFocusRequest!!)
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
        serviceInstance = this
        registerReceiver(
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        )
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
        serviceInstance = null
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        abandonAudioFocus()
        narratorNotification.close()
        super.onDestroy()
    }

    @SuppressLint("MissingSuperCall")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand: action=${intent?.action}")
        if (intent == null && !narratorNotification.isMediaSessionReady) {
            stopSelf()
            return START_NOT_STICKY
        }
        narratorNotification.handleCommand(intent)
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null
}
