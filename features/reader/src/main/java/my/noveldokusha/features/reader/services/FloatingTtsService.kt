package my.noveldokusha.features.reader.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.reader.R
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.features.reader.features.TextToSpeechSettingData
import my.noveldokusha.features.reader.manager.ReaderManager
import my.noveldokusha.features.reader.ui.FloatingTtsOverlayContent
import my.noveldokusha.core.utils.isServiceRunning
import javax.inject.Inject

@AndroidEntryPoint
internal class FloatingTtsService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "floating_tts_channel"
        const val ACTION_STOP = "my.noveldokusha.features.reader.services.FloatingTtsService.ACTION_STOP"

        var ttsState = mutableStateOf<TextToSpeechSettingData?>(null)
        var showText = mutableStateOf(false)
        var showOutsideApp = mutableStateOf(true)
        var opacity = mutableFloatStateOf(0.6f)
        var panelWidth by mutableFloatStateOf(300f)
        var paragraphMode by mutableStateOf("tts")
        var activityWindowToken: IBinder? = null

        private var isExpanded = mutableStateOf(false)
        private var bubblePosX = mutableFloatStateOf(0f)
        private var bubblePosY = mutableFloatStateOf(0f)
        private var panelPosX = mutableFloatStateOf(0f)
        private var panelPosY = mutableFloatStateOf(0f)
        private var positionInitialized = mutableStateOf(false)

        private var instance: FloatingTtsService? = null
        @Volatile
        private var explicitStop = false

        fun start(ctx: Context) {
            if (!isRunning(ctx)) {
                val intent = Intent(ctx, FloatingTtsService::class.java)
                ctx.startForegroundService(intent)
            }
        }

        fun stop(ctx: Context) {
            explicitStop = true
            ctx.stopService(Intent(ctx, FloatingTtsService::class.java))
        }

        fun isRunning(context: Context): Boolean =
            context.isServiceRunning(FloatingTtsService::class.java)

        fun recreateOverlay() {
            instance?.recreateOverlayInternal()
        }

        fun setOverlayHidden(hidden: Boolean) {
            instance?.composeView?.visibility = if (hidden) View.GONE else View.VISIBLE
        }
    }

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var readerManager: ReaderManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null
    private var displayDensity = 1f
    private var displayWidth = 0
    private var displayHeight = 0

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appPreferences.FLOATING_TTS_ENABLED.value = false
            readerManager.session?.requestTtsStop()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        explicitStop = false
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, IntentFilter(ACTION_STOP))
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        displayDensity = resources.displayMetrics.density

        val wm = windowManager!!
        displayWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.widthPixels
        }
        displayHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.heightPixels
        }

        instance = this
        loadSavedState()

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        if (ttsState.value != null) {
            showOverlay()
            appPreferences.FLOATING_TTS_ENABLED.value = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        // Защита: сервис запущен без живой сессии (процесс перезапущен) — не держим
        // зомби-оверлей, сразу останавливаемся.
        if (ttsState.value == null) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        val wasExplicit = explicitStop
        explicitStop = false
        instance = null
        removeOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (!wasExplicit) {
            runCatching { appPreferences.FLOATING_TTS_ENABLED.value = false }
            runCatching { readerManager.session?.requestTtsStop() }
        }
        super.onDestroy()
    }

    private fun loadSavedState() {
        val savedBubbleX = appPreferences.FLOATING_TTS_POS_X.value
        val savedBubbleY = appPreferences.FLOATING_TTS_POS_Y.value
        val savedPanelX = appPreferences.FLOATING_TTS_PANEL_POS_X.value
        val savedPanelY = appPreferences.FLOATING_TTS_PANEL_POS_Y.value
        panelWidth = appPreferences.FLOATING_TTS_PANEL_WIDTH.value

        val bubbleSizePx = (32 * displayDensity).toInt()
        val marginPx = (16 * displayDensity).toInt()
        val panelSizePx = (panelWidth * displayDensity).toInt()

        val navBarHeight = try {
            @Suppress("DiscouragedApi")
            val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) resources.getDimensionPixelSize(id) else 0
        } catch (_: Exception) { 0 }

        val defaultBubbleX = (displayWidth - bubbleSizePx - marginPx).toFloat()
        val defaultBubbleY = (displayHeight - bubbleSizePx - marginPx - navBarHeight).toFloat()

        val bubbleValid = savedBubbleX >= 0f && savedBubbleY >= 0f &&
                savedBubbleX + bubbleSizePx <= displayWidth &&
                savedBubbleY + bubbleSizePx <= displayHeight
        if (bubbleValid) {
            bubblePosX.floatValue = savedBubbleX
            bubblePosY.floatValue = savedBubbleY
        } else {
            bubblePosX.floatValue = defaultBubbleX
            bubblePosY.floatValue = defaultBubbleY
        }

        val panelValid = savedPanelX >= 0f && savedPanelY >= 0f &&
                savedPanelX + panelSizePx <= displayWidth &&
                savedPanelY + bubbleSizePx <= displayHeight
        if (panelValid) {
            panelPosX.floatValue = savedPanelX
            panelPosY.floatValue = savedPanelY
        } else {
            panelPosX.floatValue = bubblePosX.floatValue
            panelPosY.floatValue = bubblePosY.floatValue
        }

        positionInitialized.value = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val startPosX = if (isExpanded.value) panelPosX.floatValue else bubblePosX.floatValue
        val startPosY = if (isExpanded.value) panelPosY.floatValue else bubblePosY.floatValue

        val initialWidth = if (isExpanded.value) (panelWidth * displayDensity).toInt()
        else (32 * displayDensity).toInt()

        val layoutParams = WindowManager.LayoutParams(
            initialWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = startPosX.toInt()
            y = startPosY.toInt()
        }
        currentLayoutParams = layoutParams

        paragraphMode = appPreferences.FLOATING_TTS_PARAGRAPH_MODE.value

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingTtsService)
            setViewTreeSavedStateRegistryOwner(this@FloatingTtsService)
            setContent {
                val scope = rememberCoroutineScope()
                val darkModeState = remember { appPreferences.THEME_DARK_MODE.state(scope) }
                val appThemeState = remember { appPreferences.APP_THEME.state(scope) }

                val state = ttsState.value ?: return@setContent

                val darkModeStr by darkModeState
                val appThemeStr by appThemeState

                val darkMode = remember(darkModeStr) {
                    try { DarkMode.valueOf(darkModeStr) } catch (_: Exception) { DarkMode.SYSTEM }
                }

                InternalTheme(
                    appTheme = try { AppTheme.valueOf(appThemeStr) } catch (_: Exception) { AppTheme.DEFAULT },
                    isDark = when (darkMode) {
                        DarkMode.LIGHT -> false
                        DarkMode.DARK, DarkMode.BLACK -> true
                        DarkMode.SYSTEM -> isSystemInDarkTheme()
                    },
                    isAmoled = darkMode == DarkMode.BLACK
                ) {
                    FloatingTtsOverlayContent(
                        state = state,
                        showText = showText.value,
                        opacity = opacity.floatValue,
                        onClose = {
                            isExpanded.value = false
                            val lp = currentLayoutParams ?: return@FloatingTtsOverlayContent
                            lp.width = (32 * displayDensity).toInt()
                            val bubbleW = lp.width
                            lp.x = bubblePosX.floatValue
                                .coerceIn(0f, (displayWidth - bubbleW).coerceAtLeast(0).toFloat())
                                .toInt()
                            lp.y = bubblePosY.floatValue
                                .coerceIn(0f, (displayHeight - bubbleW).coerceAtLeast(0).toFloat())
                                .toInt()
                            try {
                                windowManager?.updateViewLayout(composeView, lp)
                            } catch (_: Exception) {}
                        },
                        onToggleExpand = {
                            val wasExpanded = isExpanded.value
                            isExpanded.value = !wasExpanded
                            val lp = currentLayoutParams ?: return@FloatingTtsOverlayContent
                            if (!wasExpanded) {
                                lp.width = (panelWidth * displayDensity).toInt()
                                val panelW = lp.width
                                lp.x = panelPosX.floatValue
                                    .coerceIn(0f, (displayWidth - panelW).coerceAtLeast(0).toFloat())
                                    .toInt()
                                lp.y = panelPosY.floatValue
                                    .coerceIn(0f, (displayHeight - 150).coerceAtLeast(0).toFloat())
                                    .toInt()
                            } else {
                                lp.width = (32 * displayDensity).toInt()
                                val bubbleW = lp.width
                                lp.x = bubblePosX.floatValue
                                    .coerceIn(0f, (displayWidth - bubbleW).coerceAtLeast(0).toFloat())
                                    .toInt()
                                lp.y = bubblePosY.floatValue
                                    .coerceIn(0f, (displayHeight - bubbleW).coerceAtLeast(0).toFloat())
                                    .toInt()
                            }
                            try {
                                windowManager?.updateViewLayout(composeView, lp)
                            } catch (_: Exception) {}
                        },
                        isExpanded = isExpanded.value,
                        onDrag = { dx, dy ->
                            val lp = currentLayoutParams ?: return@FloatingTtsOverlayContent
                            if (isExpanded.value) {
                                val panelW = (panelWidth * displayDensity).toInt()
                                panelPosX.floatValue = (panelPosX.floatValue + dx)
                                    .coerceIn(0f, (displayWidth - panelW).coerceAtLeast(0).toFloat())
                                panelPosY.floatValue = (panelPosY.floatValue + dy)
                                    .coerceIn(0f, (displayHeight - 150).coerceAtLeast(0).toFloat())
                                lp.x = panelPosX.floatValue.toInt()
                                lp.y = panelPosY.floatValue.toInt()
                            } else {
                                lp.width = (32 * displayDensity).toInt()
                                val bubbleW = lp.width
                                bubblePosX.floatValue = (bubblePosX.floatValue + dx)
                                    .coerceIn(0f, (displayWidth - bubbleW).coerceAtLeast(0).toFloat())
                                bubblePosY.floatValue = (bubblePosY.floatValue + dy)
                                    .coerceIn(0f, (displayHeight - bubbleW).coerceAtLeast(0).toFloat())
                                lp.x = bubblePosX.floatValue.toInt()
                                lp.y = bubblePosY.floatValue.toInt()
                            }
                            try {
                                windowManager?.updateViewLayout(composeView, lp)
                            } catch (_: Exception) {}
                        },
                        onDragEnd = {
                            if (isExpanded.value) {
                                appPreferences.FLOATING_TTS_PANEL_POS_X.value = panelPosX.floatValue
                                appPreferences.FLOATING_TTS_PANEL_POS_Y.value = panelPosY.floatValue
                            } else {
                                appPreferences.FLOATING_TTS_POS_X.value = bubblePosX.floatValue
                                appPreferences.FLOATING_TTS_POS_Y.value = bubblePosY.floatValue
                            }
                        },
                        panelWidth = panelWidth,
                        onPanelWidthChange = { newWidth ->
                            panelWidth = newWidth
                            appPreferences.FLOATING_TTS_PANEL_WIDTH.value = newWidth
                            val lp = currentLayoutParams
                            if (lp != null) {
                                lp.width = (newWidth * displayDensity).toInt()
                                try {
                                    windowManager?.updateViewLayout(composeView, lp)
                                } catch (_: Exception) {}
                            }
                        },
                        opacityValue = opacity.floatValue,
                        onOpacityChange = { newOpacity ->
                            opacity.floatValue = newOpacity
                            appPreferences.FLOATING_TTS_OPACITY.value = newOpacity
                        },
                        showTextToggle = showText.value,
                        onShowTextToggle = {
                            showText.value = !showText.value
                        },
                        paragraphMode = paragraphMode,
                        onParagraphModeChange = { newMode ->
                            paragraphMode = newMode
                            appPreferences.FLOATING_TTS_PARAGRAPH_MODE.value = newMode
                        },
                    )
                }
            }
        }

        if (composeView?.isAttachedToWindow != true) {
            windowManager?.addView(composeView, layoutParams)
        }
    }

    private fun recreateOverlayInternal() {
        val view = composeView ?: return
        val wm = windowManager ?: return

        try { wm.removeView(view) } catch (_: Exception) {}

        val currentWidth = if (isExpanded.value) (panelWidth * displayDensity).toInt()
        else (32 * displayDensity).toInt()

        val lp = WindowManager.LayoutParams(
            currentWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (isExpanded.value) panelPosX.floatValue.toInt() else bubblePosX.floatValue.toInt()
            y = if (isExpanded.value) panelPosY.floatValue.toInt() else bubblePosY.floatValue.toInt()
        }
        currentLayoutParams = lp

        try { wm.addView(view, lp) } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        composeView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        composeView = null
    }

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tts_floating),
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.tts_floating_channel_description)
        }
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tts_floating))
            .setContentText(getString(R.string.tts_floating_overlay_active))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDeleteIntent(stopPendingIntent)
            .addAction(
                R.drawable.ic_media_control_stop,
                getString(R.string.close),
                stopPendingIntent
            )
            .build()
    }
}
