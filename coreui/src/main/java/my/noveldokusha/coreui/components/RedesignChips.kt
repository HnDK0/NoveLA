package my.noveldokusha.coreui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Redesign pill chip (prototype `.chip`):
 *  - idle: transparent fill, 1px hairline border, muted label
 *  - selected: ink fill, surface label
 * Fully custom Surface pill — no Material FilterChip chrome (elevation,
 * oversized min-height, heavy padding) so it matches the prototype exactly.
 */
@Composable
fun NovelaFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingCount: Int? = null,
) {
    val shape = RoundedCornerShape(99.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.outlineVariant
    val fillColor = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = shape,
        color = fillColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .defaultMinSize(minHeight = 30.dp)
                .padding(horizontal = 11.dp, vertical = 5.dp),
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                RowSpacer(5.dp)
            }
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            if (trailingCount != null) {
                RowSpacer(3.dp)
                Text(
                    text = "$trailingCount",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RowSpacer(width: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.width(width))
}

/** Prototype `.chip-row`: full-bleed horizontal row of chips. */
@Composable
fun NovelaChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
