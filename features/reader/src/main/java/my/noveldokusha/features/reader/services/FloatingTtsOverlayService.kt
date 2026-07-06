package my.noveldokusha.features.reader.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.view.WindowManager
import androidx.compose.runtime.snapshotFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import my.noveldokusha.features.reader.domain.ReaderItem
import my.noveldokusha.features.reader.manager.ReaderManager
import javax.inject.Inject

@AndroidEntryPoint
internal class FloatingTtsOverlayService : Service(), FloatingTtsOverlayView.Callbacks {

    companion object {
        fun start(context: Context) {
            if (FloatingTtsOverlayPermissionManager.canDrawOverlays(context)) {
                context.startService(Intent(context, FloatingTtsOverlayService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTtsOverlayService::class.java))
        }
    }

    @Inject lateinit var preferences: FloatingTtsOverlayPreferences
    @Inject lateinit var readerManager: ReaderManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayView: FloatingTtsOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        if (!FloatingTtsOverlayPermissionManager.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        overlayView = FloatingTtsOverlayView(
            context = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager,
            preferences = preferences,
            callbacks = this,
        ).also { it.show() }
        observeOverlayState()
    }

    override fun onDestroy() {
        overlayView?.remove()
        overlayView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onPlayPause() {
        readerManager.session?.readerTextToSpeech?.state?.let { it.setPlaying(!it.isPlaying.value) }
    }

    override fun onPrevious() {
        readerManager.session?.readerTextToSpeech?.state?.playPreviousItem?.invoke()
    }

    override fun onNext() {
        readerManager.session?.readerTextToSpeech?.state?.playNextItem?.invoke()
    }

    override fun onCloseOverlay() {
        preferences.enabled = false
        stopSelf()
    }

    private fun observeOverlayState() {
        scope.launch {
            preferences.enabledFlow().collectLatest { enabled ->
                if (!enabled) stopSelf()
            }
        }
        scope.launch {
            combine(
                snapshotFlow { readerManager.session?.readerTextToSpeech?.state?.isPlaying?.value },
                snapshotFlow { readerManager.session?.readerTextToSpeech?.currentTextPlaying?.value },
                snapshotFlow { readerManager.session?.readerTextToSpeech?.state?.voiceSpeed?.value },
                snapshotFlow { readerManager.session?.readerTextToSpeech?.state?.estimatedRemainingSeconds?.value },
            ) { isPlaying, currentItem, speed, remaining ->
                val state = readerManager.session?.readerTextToSpeech?.state
                FloatingTtsOverlayState(
                    isPlaying = isPlaying == true,
                    isActive = state?.isThereActiveItem?.value == true,
                    speed = speed ?: 1f,
                    remainingSeconds = remaining ?: 0,
                    currentParagraph = currentItem?.itemPos?.let(::paragraphText).orEmpty(),
                    liveParagraphEnabled = preferences.liveParagraphEnabled,
                    showRemainingTime = preferences.showRemainingTime,
                    showPlaybackSpeed = preferences.showPlaybackSpeed,
                    opacity = preferences.opacity,
                    bubbleSizeDp = preferences.bubbleSizeDp,
                    panelSize = preferences.panelSize,
                    collapsed = preferences.collapsed,
                )
            }.collectLatest { overlayView?.render(it) }
        }
    }


    private fun paragraphText(item: ReaderItem.Position): String = when (item) {
        is ReaderItem.Text -> item.textToDisplay
        else -> ""
    }
}
