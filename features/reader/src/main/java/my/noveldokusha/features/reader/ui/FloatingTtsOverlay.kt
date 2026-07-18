package my.noveldokusha.features.reader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.features.reader.features.TextToSpeechSettingData
import my.noveldokusha.reader.R

@Composable
internal fun FloatingTtsOverlayContent(
    state: TextToSpeechSettingData,
    showText: Boolean,
    opacity: Float,
    onClose: () -> Unit,
    onToggleExpand: () -> Unit,
    isExpanded: Boolean,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    panelWidth: Float = 300f,
    onPanelWidthChange: ((Float) -> Unit)? = null,
    opacityValue: Float = 0.95f,
    onOpacityChange: ((Float) -> Unit)? = null,
    showTextToggle: Boolean = true,
    onShowTextToggle: (() -> Unit)? = null,
    paragraphMode: String = "tts",
    onParagraphModeChange: ((String) -> Unit)? = null,
    ttsHighlightEnabled: Boolean = false,
    ttsHighlightColor: String = "FFFF6D00",
    menuHidden: Boolean = false,
    onToggleMenuHidden: (() -> Unit)? = null,
) {
    if (isExpanded) {
        TtsMiniPlayer(
            state = state,
            onClose = onClose,
            onStartHere = { state.playFirstVisibleItem() },
            showParagraphText = showText,
            opacity = opacity,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            panelWidth = panelWidth,
            onPanelWidthChange = onPanelWidthChange,
            opacityValue = opacityValue,
            onOpacityChange = onOpacityChange,
            showTextToggle = showTextToggle,
            onShowTextToggle = onShowTextToggle,
            paragraphMode = paragraphMode,
            onParagraphModeChange = onParagraphModeChange,
            ttsHighlightEnabled = ttsHighlightEnabled,
            ttsHighlightColor = ttsHighlightColor,
            menuHidden = menuHidden,
            onToggleMenuHidden = onToggleMenuHidden,
        )
    } else {
        FloatingBubble(
            isPlaying = state.isPlaying.value,
            onClick = onToggleExpand,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
    }
}

@Composable
private fun FloatingBubble(
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .alpha(0.5f)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEnd
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            label = ""
        ) { playing ->
            when (playing) {
                true -> Icon(
                    Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.media_control_pause),
tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    false -> Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.media_control_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
