package my.noveldokusha.features.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import my.noveldokusha.features.reader.domain.HighlightPosition
import my.noveldokusha.reader.R

/**
 * Плавающая кнопка ручной подсветки абзацев. Стартовая позиция — по центру
 * снизу области чтения, перетаскивается и сохраняет позицию через [onPositionChange].
 * Неактивна — компактный полупрозрачный маркер, активна — кнопки ‹ › ×.
 */
@Composable
internal fun ManualHighlightPill(
    areaSize: IntSize,
    highlightedItem: HighlightPosition?,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClear: () -> Unit,
    initialPosition: Pair<Float, Float>?,
    onPositionChange: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    // Стартовый отступ от низа: выше нижней панели (прогресс/глава) при первом показе.
    val defaultBottomOffset = with(density) { 96.dp.toPx() }
    val squareSize = with(density) { 36.dp.toPx() }

    // remember (не saveable): переинициализация от сохранённой позиции при
    // пересоздании композиции, а последнее положение всегда лежит в prefs.
    // offsetX/offsetY — позиция квадрата (маркера); раскрытие/сворачивание
    // панели её не трогают, поэтому после × квадрат возвращается ровно на место.
    var offsetX by remember(initialPosition) { mutableFloatStateOf(initialPosition?.first ?: -1f) }
    var offsetY by remember(initialPosition) { mutableFloatStateOf(initialPosition?.second ?: -1f) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }

    val expanded = highlightedItem != null
    val panelWidth = if (expanded) pillSize.width.toFloat() else squareSize
    val panelHeight = if (expanded) pillSize.height.toFloat() else squareSize
    val maxPanelX = (areaSize.width - panelWidth).coerceAtLeast(0f)
    val maxPanelY = (areaSize.height - panelHeight).coerceAtLeast(0f)

    // Стартовая позиция «центр снизу» либо clamp сохранённой позиции
    // квадрата (защита от изменения границ при повороте/полном экране).
    LaunchedEffect(areaSize) {
        val maxSquareX = (areaSize.width - squareSize).coerceAtLeast(0f)
        val maxSquareY = (areaSize.height - squareSize).coerceAtLeast(0f)
        if (maxSquareX <= 0f || maxSquareY <= 0f) return@LaunchedEffect
        if (offsetX < 0f || offsetY < 0f) {
            offsetX = maxSquareX / 2f
            offsetY = (maxSquareY - defaultBottomOffset).coerceAtLeast(0f)
        } else {
            offsetX = offsetX.coerceIn(0f, maxSquareX)
            offsetY = offsetY.coerceIn(0f, maxSquareY)
        }
    }

    // Отображаемая позиция: центр панели над центром квадрата, clamp в границы.
    // Пока размер не измерен (первый кадр) — рисуем прямо по offset.
    val panelX = if (pillSize.width == 0) offsetX
    else (offsetX + (squareSize - panelWidth) / 2f).coerceIn(0f, maxPanelX)
    val panelY = if (pillSize.height == 0) offsetY
    else (offsetY + (squareSize - panelHeight) / 2f).coerceIn(0f, maxPanelY)

    Box(
        modifier = Modifier
            .offset { IntOffset(panelX.toInt(), panelY.toInt()) }
            .onSizeChanged { pillSize = it }
            .pointerInput(maxPanelX, maxPanelY, panelWidth, panelHeight) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (maxPanelX <= 0f || maxPanelY <= 0f) return@detectDragGestures
                        // Маркер сдвигается ровно на ту дельту, на которую реально
                        // сдвинулась отображаемая панель (старт — её клампированная
                        // позиция): у края экрана панель не прыгает в начале драга,
                        // а сохранённая позиция маркера не «телепортируется» внутрь
                        // экрана (иначе после × квадрат возвращался бы левее края).
                        offsetX += manualHighlightDragDelta(
                            startOffset = offsetX,
                            squareSize = squareSize,
                            panelSize = panelWidth,
                            maxPanel = maxPanelX,
                            dragAmount = dragAmount.x,
                        )
                        offsetY += manualHighlightDragDelta(
                            startOffset = offsetY,
                            squareSize = squareSize,
                            panelSize = panelHeight,
                            maxPanel = maxPanelY,
                            dragAmount = dragAmount.y,
                        )
                    },
                    onDragEnd = { onPositionChange(offsetX, offsetY) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (highlightedItem != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        stringResource(R.string.manual_highlight_stop),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.manual_highlight_previous),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        stringResource(R.string.manual_highlight_next),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    // Цвет и прозрачность как у буббла FloatingTTS (primary + onPrimary,
                    // alpha 0.5) без обводки: рамка secondaryContainer+primary бросалась
                    // в глаза, когда включены обе плавающие фичи.
                    .alpha(0.5f)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Highlight,
                    stringResource(R.string.manual_highlight_start),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Дельта сдвига маркера при драге панели на [dragAmount] по одной оси.
 *
 * Стартовая точка — отображаемая (клампированная в [maxPanel]) позиция панели:
 * [startOffset] это левый край маркера, а панель шире маркера и центрируется
 * на нём, пока влезает в границы. Драг применяется к позиции ПАНЕЛИ, а маркер
 * двигается на получившуюся дельту. Так у края экрана:
 * - панель не прыгает в начале драга (старт — её фактическая позиция);
 * - маркер не «телепортируется» к центру клампированной панели (баг №117:
 *   после × квадрат у края возвращался левее на (panelSize - squareSize)/2).
 */
internal fun manualHighlightDragDelta(
    startOffset: Float,
    squareSize: Float,
    panelSize: Float,
    maxPanel: Float,
    dragAmount: Float,
): Float {
    val startPanel = (startOffset + (squareSize - panelSize) / 2f).coerceIn(0f, maxPanel)
    val newPanel = (startPanel + dragAmount).coerceIn(0f, maxPanel)
    return newPanel - startPanel
}
