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
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.features.reader.features.ReaderTextToSpeech
import my.noveldokusha.core.utils.isServiceRunning
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
internal class NarratorMediaControlsService : Service() {

    companion object {
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

        fun reassertActive() {
            serviceInstance?.narratorNotification?.reassertActive()
        }

        fun maybeAutoResume() {
            serviceInstance?.narratorNotification?.maybeAutoResume()
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
            narratorNotification.pause()
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
                .setAcceptsDelayedFocusGain(true)
                // Речь (TTS): при запросе фокуса с duck'ом ставим паузу,
                // а не понижаем громкость (чинит паузу для Telegram-голосовых).
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            // Любая потеря фокуса -> пауза.
                            // - Постоянная (LOSS): нас выкинуло из стека фокуса.
                            //   НЕ перезапрашиваем здесь — правило "последний запрос
                            //   побеждает" заставило бы нас мгновенно украсть фокус
                            //   обратно у только что взявшего его приложения
                            //   (борьба за фокус). Перехват делается позже, по воле
                            //   пользователя: при onPlay()/возврате в приложение
                            //   (onResume), где setAcceptsDelayedFocusGain даёт DELAYED
                            //   и корректное ожидание GAIN без кражи.
                            // - Временная (TRANSIENT / CAN_DUCK): система сама вернёт
                            //   фокус через стек при релизе захватившего приложения,
                            //   перезапрос не нужен. setWillPauseWhenDucked(true) =>
                            //   при duck тоже пауза, а не понижение громкости.
                            wasPausedByFocusLoss = true
                            ReaderTextToSpeech.isSystemPauseTrigger = true
                            ReaderTextToSpeech.pausedBySystem = true
                            narratorNotification.pause()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> onFocusGained()
                    }
                }
                .build()
            audioFocusRequest = request
        }
        audioManager.requestAudioFocus(audioFocusRequest!!)
        Timber.d("AudioFocus requested AUDIOFOCUS_GAIN")
    }

    private fun onFocusGained() {
        narratorNotification.reassertActive()
        if (wasPausedByFocusLoss && ReaderTextToSpeech.pausedBySystem) {
            wasPausedByFocusLoss = false
            ReaderTextToSpeech.pausedBySystem = false
            narratorNotification.play()
        } else {
            wasPausedByFocusLoss = false
        }
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
