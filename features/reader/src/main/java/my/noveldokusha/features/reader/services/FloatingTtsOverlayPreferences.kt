package my.noveldokusha.features.reader.services

import kotlinx.coroutines.flow.Flow
import my.noveldokusha.core.appPreferences.AppPreferences
import javax.inject.Inject

internal class FloatingTtsOverlayPreferences @Inject constructor(
    private val appPreferences: AppPreferences,
) {
    fun enabledFlow(): Flow<Boolean> = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY.flow()
    var enabled: Boolean
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY.value
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY.value = value }

    var liveParagraphEnabled: Boolean
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_LIVE_PARAGRAPH.value
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_LIVE_PARAGRAPH.value = value }

    var showRemainingTime: Boolean
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_SHOW_REMAINING_TIME.value
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_SHOW_REMAINING_TIME.value = value }

    var showPlaybackSpeed: Boolean
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_SHOW_PLAYBACK_SPEED.value
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_SHOW_PLAYBACK_SPEED.value = value }

    var opacity: Float
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_OPACITY.value.coerceIn(0.25f, 1f)
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_OPACITY.value = value.coerceIn(0.25f, 1f) }

    var bubbleSizeDp: Float
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_BUBBLE_SIZE.value.coerceIn(44f, 88f)
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_BUBBLE_SIZE.value = value.coerceIn(44f, 88f) }

    var panelSize: FloatingTtsOverlayPanelSize
        get() = runCatching { FloatingTtsOverlayPanelSize.valueOf(appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_PANEL_SIZE.value) }
            .getOrDefault(FloatingTtsOverlayPanelSize.Medium)
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_PANEL_SIZE.value = value.name }

    var collapsed: Boolean
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_COLLAPSED.value
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_COLLAPSED.value = value }

    var x: Int
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_X.value
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_X.value = value }

    var y: Int
        get() = appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_Y.value
        set(value) { appPreferences.READER_TEXT_TO_SPEECH_FLOATING_OVERLAY_Y.value = value }
}
