package my.noveldokusha.features.reader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import my.noveldokusha.coreui.composableActions.debouncedAction
import my.noveldokusha.reader.R
import my.noveldokusha.features.reader.features.TextToSpeechSettingData

@Composable
internal fun TtsMiniPlayer(
    state: TextToSpeechSettingData,
    onClose: () -> Unit,
    onStartHere: () -> Unit,
    chapterCurrentNumber: Int = 0,
    chaptersCount: Int = 0,
    showParagraphText: Boolean = false,
    opacity: Float = 0.95f,
    onDrag: ((Float, Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
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
    FloatingTtsMiniPlayer(
        state = state,
        showParagraphText = showParagraphText,
        opacity = opacity,
        onClose = onClose,
        onDrag = onDrag,
        onDragEnd = onDragEnd,
        onStartHere = onStartHere,
        chapterCurrentNumber = chapterCurrentNumber,
        chaptersCount = chaptersCount,
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
}

@Composable
private fun MiniPlayerControls(
    state: TextToSpeechSettingData,
    onClose: () -> Unit,
    onStartHere: () -> Unit,
    chapterCurrentNumber: Int,
    chaptersCount: Int,
    animatedProgress: Float,
    remaining: Int,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 26.dp,
    iconCircleSize: Dp = 28.dp,
    playButtonSize: Dp = 40.dp,
    playIconSize: Dp = 36.dp,
    progressHeight: Dp = 8.dp,
    extraAction: @Composable () -> Unit = {},
    trailingAction: @Composable () -> Unit = {},
) {
    val badgeHorizPad = 6.dp
    val badgeVertPad = 2.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.padding(start = 2.dp, end = 8.dp, top = 1.dp, bottom = 1.dp)
    ) {
        IconButton(
            onClick = debouncedAction(waitMillis = 100, action = onClose),
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(iconSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        }
        if (chaptersCount > 0) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "Ch. $chapterCurrentNumber/$chaptersCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = badgeHorizPad, vertical = badgeVertPad)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .height(progressHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = badgeHorizPad, vertical = badgeVertPad)
            ) {
                Icon(
                    Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatDuration(remaining),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        IconButton(
            onClick = debouncedAction(waitMillis = 100) { state.playPreviousItem() },
            enabled = state.isThereActiveItem.value,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.NavigateBefore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(iconCircleSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        }
        IconButton(
            onClick = { state.setPlaying(!state.isPlaying.value) },
            modifier = Modifier.size(playButtonSize)
        ) {
            AnimatedContent(
                targetState = state.isPlaying.value,
                modifier = Modifier
                    .size(playIconSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                label = ""
            ) { target ->
                when (target) {
                    true -> Icon(
                        Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    false -> Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        IconButton(
            onClick = debouncedAction(waitMillis = 100) { state.playNextItem() },
            enabled = state.isThereActiveItem.value,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(iconCircleSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        }

        extraAction()

        IconButton(
            onClick = debouncedAction(waitMillis = 100, action = onStartHere),
            enabled = state.isThereActiveItem.value,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                Icons.Filled.CenterFocusWeak,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(iconCircleSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        }

        trailingAction()
    }
}

@Composable
private fun FloatingTtsMiniPlayer(
    state: TextToSpeechSettingData,
    showParagraphText: Boolean,
    opacity: Float,
    onClose: () -> Unit,
    onDrag: ((Float, Float) -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onStartHere: () -> Unit,
    chapterCurrentNumber: Int,
    chaptersCount: Int,
    panelWidth: Float,
    onPanelWidthChange: ((Float) -> Unit)?,
    opacityValue: Float,
    onOpacityChange: ((Float) -> Unit)?,
    showTextToggle: Boolean,
    onShowTextToggle: (() -> Unit)?,
    paragraphMode: String = "tts",
    onParagraphModeChange: ((String) -> Unit)? = null,
    ttsHighlightEnabled: Boolean = false,
    ttsHighlightColor: String = "FFFF6D00",
    menuHidden: Boolean = false,
    onToggleMenuHidden: (() -> Unit)? = null,
) {
    val total = state.estimatedTotalSeconds.value
    val remaining = state.estimatedRemainingSeconds.value
    val progress = if (total > 0) (total - remaining).toFloat() / total else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = ""
    )

    val ttsParagraph = state.currentParagraphText.value
    val inverseParagraph = state.alternateParagraphText.value
    val hasInverse = inverseParagraph.isNotBlank()
    val displayText = when {
        paragraphMode == "inverse" && hasInverse -> inverseParagraph
        else -> ttsParagraph
    }
    val isBothMode = paragraphMode == "both" && hasInverse
    val hasParagraphText = showParagraphText && (displayText.isNotBlank() || isBothMode)

    val density = LocalDensity.current
    var showOpacitySlider by remember { mutableStateOf(false) }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val minWidth = 100f
    val maxWidth = screenWidthDp - 16f
    val ratio = ((panelWidth - minWidth) / (maxWidth - minWidth)).coerceIn(0f, 1f)

    fun lerpf(a: Float, b: Float, t: Float) = a + (b - a) * t

    val buttonSize = lerpf(20f, 28f, ratio).dp
    val iconSize = lerpf(14f, 22f, ratio).dp
    val iconCircleSize = lerpf(16f, 24f, ratio).dp
    val playButtonSize = lerpf(24f, 34f, ratio).dp
    val playIconSize = lerpf(20f, 30f, ratio).dp
    val progressHeight = lerpf(3f, 6f, ratio).dp
    val paragraphFontSize = lerpf(9f, 13f, ratio).sp

    val dragModifier = if (onDrag != null) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                },
                onDragEnd = { onDragEnd?.invoke() }
            )
        }
    } else {
        Modifier
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = opacity),
        shadowElevation = 0.dp,
        modifier = Modifier
            .then(dragModifier)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            AnimatedVisibility(
                visible = !menuHidden,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) {
                Column {
                    MiniPlayerControls(
                        state = state,
                        onClose = onClose,
                        onStartHere = onStartHere,
                        chapterCurrentNumber = chapterCurrentNumber,
                        chaptersCount = chaptersCount,
                        animatedProgress = animatedProgress,
                        remaining = remaining,
                        buttonSize = buttonSize,
                        iconSize = iconSize,
                        iconCircleSize = iconCircleSize,
                        playButtonSize = playButtonSize,
                        playIconSize = playIconSize,
                        progressHeight = progressHeight,
                        extraAction = {
                            if (onOpacityChange != null) {
                                IconButton(
                                    onClick = { showOpacitySlider = !showOpacitySlider },
                                    modifier = Modifier.size(buttonSize)
                                ) {
                                    Icon(
                                        Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = if (showOpacitySlider) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(iconSize)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                                    )
                                }
                            }
                        },
                        trailingAction = {
                            if (onShowTextToggle != null) {
                                IconButton(
                                    onClick = onShowTextToggle,
                                    modifier = Modifier.size(buttonSize)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.TextSnippet,
                                        contentDescription = null,
                                        tint = if (showTextToggle) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(iconSize)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                                    )
                                }
                            }
                        },
                    )

                    if (onOpacityChange != null && showOpacitySlider) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.tts_floating_opacity),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                                Slider(
                                    value = opacityValue,
                                    onValueChange = { onOpacityChange(it) },
                                    valueRange = 0.3f..1f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                    )
                                )
                            }
                            if (onPanelWidthChange != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.tts_floating_width),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Slider(
                                        value = panelWidth,
                                        onValueChange = { onPanelWidthChange(it) },
                                        valueRange = minWidth..maxWidth,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                        )
                                    )
                                }
                            }
                            if (onParagraphModeChange != null && state.parallelEnabled.value) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.tts_floating_paragraph),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.tts_voice),
                                        color = if (paragraphMode == "tts") MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (paragraphMode == "tts") FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .clickable { onParagraphModeChange("tts") }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                    Text(
                                        text = "/",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = stringResource(R.string.tts_both),
                                        color = if (paragraphMode == "both") MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (paragraphMode == "both") FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .clickable { onParagraphModeChange("both") }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                    Text(
                                        text = "/",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = stringResource(R.string.inverse),
                                        color = if (paragraphMode == "inverse") MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (paragraphMode == "inverse") FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .clickable { onParagraphModeChange("inverse") }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (hasParagraphText) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { onToggleMenuHidden?.invoke() }
                            )
                        }
                ) {
                    if (isBothMode) {
                        val spokenRange = state.spokenWordRange.value
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            if (ttsHighlightEnabled && spokenRange != null) {
                                HighlightedText(
                                    text = ttsParagraph,
                                    range = spokenRange,
                                    highlightColorHex = ttsHighlightColor,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = paragraphFontSize),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = ttsParagraph,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = paragraphFontSize),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = inverseParagraph,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = paragraphFontSize * 0.85f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    } else {
                        val spokenRange = state.spokenWordRange.value
                        val isInverse = paragraphMode == "inverse" && hasInverse
                        if (ttsHighlightEnabled && spokenRange != null && !isInverse) {
                            HighlightedText(
                                text = displayText,
                                range = spokenRange,
                                highlightColorHex = ttsHighlightColor,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = paragraphFontSize),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        } else {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = paragraphFontSize),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "${m}:${s.toString().padStart(2, '0')}"
    }
}

internal fun formatDurationCompact(seconds: Int): String {
    if (seconds <= 0) return "0s"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (m > 0 || h > 0) append("${m}m ")
        if (s > 0 && h == 0) append("${s}s")
    }.trimEnd()
}

@Composable
private fun HighlightedText(
    text: String,
    range: IntRange,
    highlightColorHex: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val bgColor = remember(highlightColorHex) {
        try {
            Color(android.graphics.Color.parseColor("#$highlightColorHex"))
        } catch (_: Exception) {
            Color(android.graphics.Color.parseColor("#FFFF6D00"))
        }
    }
    val annotated = remember(text, range) {
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(0, text.length)
        buildAnnotatedString {
            if (start > 0) append(text.substring(0, start))
            if (start < end) {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                    append(text.substring(start, end))
                }
            }
            if (end < text.length) append(text.substring(end))
        }
    }
    Text(
        text = annotated,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val l = layout ?: return@drawBehind
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(0, text.length)
            if (start >= end) return@drawBehind

            val sLine = l.getLineForOffset(start)
            val eLine = l.getLineForOffset(end - 1).coerceAtLeast(sLine)
            for (line in sLine..eLine) {
                val lineStartChar = l.getLineStart(line)
                val lineEndChar = (l.getLineEnd(line) - 1).coerceAtLeast(lineStartChar)
                val left = if (line == sLine) l.getBoundingBox(start).left else l.getBoundingBox(lineStartChar).left
                val right = if (line == eLine) l.getBoundingBox(end - 1).right else l.getBoundingBox(lineEndChar).right
                drawRoundRect(
                    color = bgColor.copy(alpha = 0.5f),
                    topLeft = Offset(left, l.getLineTop(line)),
                    size = Size(right - left, l.getLineBottom(line) - l.getLineTop(line)),
                    cornerRadius = CornerRadius(6f)
                )
            }
        }
    )
}
