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
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.core.utils.isServiceRunning
import my.noveldokusha.features.reader.manager.ReaderManager
import timber.log.Timber
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

    @Inject
    lateinit var readerManager: ReaderManager

    private var audioFocusRequest: AudioFocusRequest? = null
    private var callInterrupted = false

    private val interruptionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> pauseForDeviceDisconnect()
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> handlePhoneState(intent)
                Intent.ACTION_NEW_OUTGOING_CALL -> pauseForCall()
            }
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
            .setOnAudioFocusChangeListener(::handleAudioFocusChange)
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


    private fun registerInterruptionReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(Intent.ACTION_NEW_OUTGOING_CALL)
        }
        ContextCompat.registerReceiver(
            this,
            interruptionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        Timber.d("AudioFocus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> readerManager.session?.readerTextToSpeech?.resumeAfterInterruption()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> readerManager.session?.readerTextToSpeech?.pauseForInterruption()
            AudioManager.AUDIOFOCUS_LOSS -> readerManager.session?.readerTextToSpeech?.stopForPermanentAudioLoss()
        }
    }

    private fun pauseForDeviceDisconnect() {
        readerManager.session?.readerTextToSpeech?.pauseForInterruption()
    }

    private fun pauseForCall() {
        val tts = readerManager.session?.readerTextToSpeech ?: return
        if (tts.state.isPlaying.value) {
            callInterrupted = true
            tts.pauseForInterruption()
        }
    }

    private fun handlePhoneState(intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> pauseForCall()
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (callInterrupted) {
                    callInterrupted = false
                    readerManager.session?.readerTextToSpeech?.resumeAfterInterruption()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        requestAudioFocus()
        registerInterruptionReceiver()

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
        runCatching { unregisterReceiver(interruptionReceiver) }
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
