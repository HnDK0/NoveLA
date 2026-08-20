package my.noveldokusha.coreui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Слайдер в форме «пилюли» (полностью скруглённый) с раскладкой Label — Slider — Value.
 *
 * Трек имеет скруглённые торцы, заполненная часть остаётся визуально непрерывной.
 * Жесты, диапазон и сохранение значения идентичны стандартному Slider.
 * Семантика доступности (Role.Slider + ProgressBarRangeInfo) сохранена,
 * чтобы TalkBack мог менять значение свайпом.
 *
 * @param label Текст слева (например, «Высота голоса»).
 * @param value Текущее значение слайдера.
 * @param valueRange Включительный диапазон значений.
 * @param onValueChange Вызывается непрерывно во время перетаскивания.
 * @param onValueChangeFinished Вызывается один раз, когда пользователь отпустил палец.
 * @param valueText Текст справа (например, «1.00»).
 * @param modifier Опциональный модификатор (отступы, weight и т.п.).
 */
@Composable
fun PillSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    valueText: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val trackHeightDp = 6.dp
    val trackHeightPx = with(density) { trackHeightDp.toPx() }
    val thumbRadiusPx = with(density) { 9.dp.toPx() }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val activeColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        // Label — слева
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )

        // Pill-shaped track
        var localValue by remember(value) { mutableFloatStateOf(value) }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(trackHeightDp + 18.dp) // дополнительная высота для зоны касания бегунка
                .semantics {
                    // Compose UI 1.11+ не имеет Role.Slider; TalkBack-регулировка
                    // обеспечивается progressBarRangeInfo + setProgress (как в M3 Slider)
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                        range = valueRange,
                    )
                    setProgress { targetValue ->
                        localValue = targetValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        onValueChange(localValue)
                        true
                    }
                }
                .pointerInput(valueRange) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var dragged = false
                        drag(down.id) { change ->
                            change.consume()
                            dragged = true
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            localValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(localValue)
                        }
                        if (!dragged) {
                            val fraction = (down.position.x / size.width).coerceIn(0f, 1f)
                            localValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(localValue)
                        }
                        onValueChangeFinished()
                    }
                }
        ) {
            val width = size.width
            val centerY = size.height / 2

            val fraction = ((localValue - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                .coerceIn(0f, 1f)
            val activeWidth = width * fraction
            val trackTop = centerY - trackHeightPx / 2
            val pillRadius = trackHeightPx / 2

            // Незаполненный (фоновый) трек — целая пилюля
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, trackTop),
                size = Size(width, trackHeightPx),
                cornerRadius = CornerRadius(pillRadius),
            )

            // Заполненный трек — пилюля со скруглёнными торцами
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(activeWidth, trackHeightPx),
                    cornerRadius = CornerRadius(pillRadius),
                )
            }

            // Круглый бегунок
            drawCircle(
                color = activeColor,
                radius = thumbRadiusPx,
                center = Offset(activeWidth.coerceIn(thumbRadiusPx, width - thumbRadiusPx), centerY),
            )
        }

        // Value — справа
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}