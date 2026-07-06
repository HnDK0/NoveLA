package my.noveldokusha.features.reader.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal class FloatingTtsOverlayView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val preferences: FloatingTtsOverlayPreferences,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onPlayPause()
        fun onPrevious()
        fun onNext()
        fun onCloseOverlay()
    }

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var state = FloatingTtsOverlayState(
        liveParagraphEnabled = preferences.liveParagraphEnabled,
        showRemainingTime = preferences.showRemainingTime,
        showPlaybackSpeed = preferences.showPlaybackSpeed,
        opacity = preferences.opacity,
        bubbleSizeDp = preferences.bubbleSizeDp,
        panelSize = preferences.panelSize,
        collapsed = preferences.collapsed,
    )
    private var lastDownRawX = 0f
    private var lastDownRawY = 0f
    private var startWindowX = 0
    private var startWindowY = 0
    private var moved = false
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!moved) callbacks.onCloseOverlay()
    }

    private lateinit var bubble: TextView
    private lateinit var livePreview: LinearLayout
    private lateinit var livePreviewText: TextView
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private lateinit var paragraph: TextView
    private lateinit var speed: TextView
    private lateinit var remaining: TextView
    private lateinit var opacityRow: LinearLayout

    fun show() {
        if (root != null) return
        root = FrameLayout(context).also { container ->
            bubble = TextView(context).apply {
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(0xffffffff.toInt())
                background = rounded(0xff3f51b5.toInt(), 999f)
            }
            livePreview = buildLivePreview()
            panel = buildPanel()
            container.addView(bubble)
            container.addView(livePreview)
            container.addView(panel)
            container.setOnTouchListener(dragAndTapListener())
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.x
            y = preferences.y
        }
        windowManager.addView(root, params)
        render(state)
    }

    fun remove() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
    }

    fun render(nextState: FloatingTtsOverlayState) {
        state = nextState
        val container = root ?: return
        val bubbleSize = state.bubbleSizeDp.dp()
        container.alpha = state.opacity
        bubble.text = if (state.isPlaying) "Ⅱ" else "▶"
        bubble.layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize)

        val panelWidth = state.panelSize.widthDp.dp()
        livePreview.isVisible = state.collapsed && state.liveParagraphEnabled
        livePreview.layoutParams = FrameLayout.LayoutParams(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = bubbleSize + 8.dp()
        }
        livePreviewText.text = state.currentParagraph.take(180)

        panel.isVisible = !state.collapsed
        panel.layoutParams = FrameLayout.LayoutParams(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = bubbleSize + 8.dp()
        }
        status.text = if (state.isPlaying) "Playing" else if (state.isActive) "Paused" else "Inactive"
        paragraph.isVisible = state.liveParagraphEnabled
        paragraph.text = state.currentParagraph
        speed.isVisible = state.showPlaybackSpeed
        speed.text = String.format(Locale.US, "Speed %.2fx", state.speed)
        remaining.isVisible = state.showRemainingTime
        remaining.text = "${formatDuration(state.remainingSeconds)} left"
    }

    private fun buildLivePreview() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
        background = rounded(0xee202124.toInt(), 18f)
        livePreviewText = label("").apply { maxLines = 2 }
        val controls = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(button("◀") { callbacks.onPrevious() })
            addView(button("⏯") { callbacks.onPlayPause() })
            addView(button("▶") { callbacks.onNext() })
        }
        addView(livePreviewText)
        addView(controls)
    }

    private fun buildPanel() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        background = rounded(0xee202124.toInt(), 24f)
        status = label("TTS overlay")
        paragraph = label("").apply { maxLines = 4 }
        speed = label("Speed 1.00x")
        remaining = label("0:00 left")
        val controls = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(button("◀") { callbacks.onPrevious() })
            addView(button("⏯") { callbacks.onPlayPause() })
            addView(button("▶") { callbacks.onNext() })
            addView(button("−") { setCollapsed(true) })
            addView(button("×") { callbacks.onCloseOverlay() })
        }
        val settings = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            addView(button("Text") { toggleLiveParagraph() })
            addView(button("Time") { toggleRemaining() })
            addView(button("Speed") { toggleSpeed() })
            addView(button("Size") { cyclePanelSize() })
            addView(button("Opacity") { opacityRow.isVisible = true })
        }
        opacityRow = LinearLayout(context).apply {
            isVisible = false
            orientation = LinearLayout.VERTICAL
            addView(label("Transparency"))
            addView(SeekBar(context).apply {
                max = 75
                progress = ((preferences.opacity - 0.25f) * 100).roundToInt().coerceIn(0, 75)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        preferences.opacity = 0.25f + progress / 100f
                        render(state.copy(opacity = preferences.opacity))
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        opacityRow.postDelayed({ opacityRow.isVisible = false }, 1200)
                    }
                })
            })
        }
        addView(status)
        addView(paragraph)
        addView(speed)
        addView(remaining)
        addView(controls)
        addView(settings)
        addView(opacityRow)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun dragAndTapListener() = View.OnTouchListener { _, event ->
        val lp = params ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownRawX = event.rawX
                lastDownRawY = event.rawY
                startWindowX = lp.x
                startWindowY = lp.y
                moved = false
                handler.postDelayed(longPressRunnable, 650)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastDownRawX
                val dy = event.rawY - lastDownRawY
                if (abs(dx) > 6 || abs(dy) > 6) {
                    moved = true
                    handler.removeCallbacks(longPressRunnable)
                }
                lp.x = startWindowX + dx.roundToInt()
                lp.y = startWindowY + dy.roundToInt()
                windowManager.updateViewLayout(root, lp)
                true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (moved) {
                    snapToEdge(lp)
                    preferences.x = lp.x
                    preferences.y = lp.y
                } else {
                    setCollapsed(!state.collapsed)
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                true
            }
            else -> false
        }
    }

    private fun setCollapsed(collapsed: Boolean) {
        preferences.collapsed = collapsed
        render(state.copy(collapsed = collapsed))
    }

    private fun toggleLiveParagraph() {
        preferences.liveParagraphEnabled = !preferences.liveParagraphEnabled
        render(state.copy(liveParagraphEnabled = preferences.liveParagraphEnabled))
    }

    private fun toggleRemaining() {
        preferences.showRemainingTime = !preferences.showRemainingTime
        render(state.copy(showRemainingTime = preferences.showRemainingTime))
    }

    private fun toggleSpeed() {
        preferences.showPlaybackSpeed = !preferences.showPlaybackSpeed
        render(state.copy(showPlaybackSpeed = preferences.showPlaybackSpeed))
    }

    private fun cyclePanelSize() {
        preferences.panelSize = preferences.panelSize.next()
        render(state.copy(panelSize = preferences.panelSize))
    }

    private fun snapToEdge(lp: WindowManager.LayoutParams) {
        val displaySize = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getSize(displaySize)
        val rootWidth = root?.width ?: state.bubbleSizeDp.dp()
        lp.x = if (lp.x + rootWidth / 2 < displaySize.x / 2) 0 else (displaySize.x - rootWidth).coerceAtLeast(0)
        lp.y = lp.y.coerceIn(0, (displaySize.y - (root?.height ?: state.bubbleSizeDp.dp())).coerceAtLeast(0))
        windowManager.updateViewLayout(root, lp)
    }

    private fun label(textValue: String) = TextView(context).apply {
        text = textValue
        setTextColor(0xffffffff.toInt())
        textSize = 13f
    }

    private fun button(textValue: String, action: () -> Unit) = Button(context).apply {
        text = textValue
        minWidth = 0
        minimumWidth = 0
        setPadding(8.dp(), 0, 8.dp(), 0)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun Int.dp() = (this * context.resources.displayMetrics.density).roundToInt()
    private fun Float.dp() = (this * context.resources.displayMetrics.density).roundToInt()
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
