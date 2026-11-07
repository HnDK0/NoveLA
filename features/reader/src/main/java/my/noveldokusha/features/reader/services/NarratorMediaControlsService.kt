package my.noveldokusha.features.reader.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.features.reader.features.ReaderTextToSpeech
import my.noveldokusha.features.reader.manager.ReaderManager
import my.noveldokusha.core.utils.isServiceRunning
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
internal class NarratorMediaControlsService : Service() {

    companion object {
        const val ACTION_STOP_NARRATOR = "my.noveldokusha.STOP_NARRATOR"
        private var serviceInstance: NarratorMediaControlsService? = null

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, NarratorMediaControlsService::class.java)
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, NarratorMediaControlsService::class.java))
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

    @Inject
    lateinit var readerManager: ReaderManager

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Timber.d("ACTION_AUDIO_BECOMING_NOISY received")
            ReaderTextToSpeech.isSystemPauseTrigger = true
            ReaderTextToSpeech.pausedBySystem = true
            narratorNotification.pause()
        }
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_NARRATOR) {
                Timber.d("dismissReceiver: STOP_NARRATOR — closing reader")
                narratorNotification.close()
                readerManager.closeReader()
            }
        }
    }

    // Coexistence mode: the narrator never requests exclusive or ducking audio
    // focus. This keeps music apps playing at their own selected volume.
    // Android system TTS engines may have independent focus policies.
    // Headphone-disconnect handling (ACTION_AUDIO_BECOMING_NOISY) is preserved.

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this

        val notification = narratorNotification.createNotificationMediaControls(this)
        startForeground(narratorNotification.notificationId, notification ?: narratorNotification.createDefaultNotification(this))

        registerReceiver(
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            Context.RECEIVER_NOT_EXPORTED
        )
        registerReceiver(
            dismissReceiver,
            IntentFilter(ACTION_STOP_NARRATOR),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        serviceInstance = null
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        runCatching { unregisterReceiver(dismissReceiver) }
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
