package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.reader.R

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
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
    ) {
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