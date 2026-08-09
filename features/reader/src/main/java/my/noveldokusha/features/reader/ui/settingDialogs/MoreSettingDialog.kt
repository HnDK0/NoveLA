package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.reader.R

private val ttsHighlightColors = listOf(
    "FFFF6D00",  // Deep Orange
    "FFFF1744",  // Red Accent
    "FFFF4081",  // Pink Accent
    "FFE040FB",  // Purple Accent
    "FF7C4DFF",  // Deep Purple Accent
    "FF536DFE",  // Indigo Accent
    "FF448AFF",  // Blue Accent
    "FF40C4FF",  // Light Blue Accent
    "FF18FFFF",  // Cyan Accent
    "FF64FFDA",  // Teal Accent
    "FF69F0AE",  // Green Accent
    "FFFFD740",  // Amber Accent
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoreSettingDialog(
    allowTextSelection: Boolean,
    onAllowTextSelectionChange: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    fullScreen: Boolean,
    onFullScreen: (Boolean) -> Unit,
    singleTapToOpenSettings: Boolean,
    onSingleTapToOpenSettingsChange: (Boolean) -> Unit,
    ttsHighlightEnabled: Boolean,
    onTtsHighlightEnabledChange: (Boolean) -> Unit,
    ttsHighlightColor: String,
    onTtsHighlightColorChange: (String) -> Unit,
    manualHighlightEnabled: Boolean = false,
    onManualHighlightEnabledChange: (Boolean) -> Unit = {},
    imageQuality: String = "high",
    onImageQualityChange: (String) -> Unit = {},
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
    ) {
        // Manual highlight
        SlimListItem(
            modifier = Modifier
                .clickable { onManualHighlightEnabledChange(!manualHighlightEnabled) },
            headlineContent = {
                Text(text = stringResource(id = R.string.manual_highlight))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.Highlight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = manualHighlightEnabled,
                    onCheckedChange = onManualHighlightEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                    )
                )
            }
        )
        // TTS Highlight
        SlimListItem(
            modifier = Modifier
                .clickable { onTtsHighlightEnabledChange(!ttsHighlightEnabled) },
            headlineContent = {
                Text(text = stringResource(id = R.string.tts_highlight))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.FormatPaint,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = ttsHighlightEnabled,
                    onCheckedChange = onTtsHighlightEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                    )
                )
            }
        )
        if (ttsHighlightEnabled) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ttsHighlightColors.forEach { hexColor ->
                    val isSelected = hexColor == ttsHighlightColor
                    val color = try {
                        Color(android.graphics.Color.parseColor("#$hexColor"))
                    } catch (_: Exception) {
                        Color(android.graphics.Color.parseColor("#FFFF6D00"))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { onTtsHighlightColorChange(hexColor) }
                    )
                }
            }
        }
        // Allow text selection
        SlimListItem(
            modifier = Modifier
                .clickable { onAllowTextSelectionChange(!allowTextSelection) },
            headlineContent = {
                Text(text = stringResource(id = R.string.allow_text_selection))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.TouchApp,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = allowTextSelection,
                    onCheckedChange = onAllowTextSelectionChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                    )
                )
            }
        )
        // Keep screen on
        SlimListItem(
            modifier = Modifier
                .clickable { onKeepScreenOn(!keepScreenOn) },
            headlineContent = {
                Text(text = stringResource(R.string.keep_screen_on))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.LightMode,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                    )
                )
            }
        )
        // Full screen
        SlimListItem(
            modifier = Modifier
                .clickable { onFullScreen(!fullScreen) },
            headlineContent = {
                Text(text = stringResource(R.string.features_reader_full_screen))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.Fullscreen,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = fullScreen,
                    onCheckedChange = onFullScreen,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                    )
                )
            }
        )
        // Image quality (manga/manhwa pages)
        SlimListItem(
            modifier = Modifier,
            headlineContent = {
                Text(text = stringResource(id = R.string.image_quality))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.Highlight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Text(
                    text = stringResource(
                        id = when (imageQuality) {
                            "balanced" -> R.string.image_quality_balanced
                            "saver" -> R.string.image_quality_saver
                            "low" -> R.string.image_quality_low
                            else -> R.string.image_quality_high
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "high" to R.string.image_quality_high,
                "balanced" to R.string.image_quality_balanced,
                "saver" to R.string.image_quality_saver,
                "low" to R.string.image_quality_low,
            ).forEach { (value, labelRes) ->
                val isSelected = value == imageQuality
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isSelected) colorAccent()
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onImageQualityChange(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(id = labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Single tap to open settings
        SlimListItem(
            modifier = Modifier
                .clickable { onSingleTapToOpenSettingsChange(!singleTapToOpenSettings) },
            headlineContent = {
                Text(text = stringResource(id = R.string.single_tap_to_open_settings))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.TouchApp,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = singleTapToOpenSettings,
                    onCheckedChange = onSingleTapToOpenSettingsChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                    )
                )
            }
        )
    }
}