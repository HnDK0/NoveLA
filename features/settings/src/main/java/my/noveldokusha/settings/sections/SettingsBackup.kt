package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SettingsBackup(
    onBackupData: () -> Unit = {},
    onRestoreData: () -> Unit = {},
    // Auto backup
    autoBackupEnabled: Boolean = false,
    onAutoBackupEnabledChange: (Boolean) -> Unit = {},
    autoBackupDirectoryUri: String = "",
    autoBackupDirectoryDisplayName: String = "",
    onAutoBackupSelectDirectory: () -> Unit = {},
    autoBackupMaxCount: Int = 5,
    onAutoBackupMaxCountChange: (Int) -> Unit = {},
    autoBackupIntervalMinutes: Long = 60L,
    onAutoBackupIntervalMinutesChange: (Long) -> Unit = {},
    autoBackupIncludeImages: Boolean = false,
    onAutoBackupIncludeImagesChange: (Boolean) -> Unit = {},
    autoBackupLastTimestamp: Long = 0L,
) {
    Column {
        // ── Manual Backup Section ──
        Text(
            text = stringResource(id = R.string.backup),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = colorAccent()
        )
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.backup_data))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.opens_the_file_explorer_to_select_the_backup_saving_location))
            },
            leadingContent = {
                Icon(Icons.Outlined.Save, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable { onBackupData() }
        )
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.restore_data))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.opens_the_file_explorer_to_select_the_backup_file))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.SettingsBackupRestore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.clickable { onRestoreData() }
        )

        // ── Auto Backup Section ──
        Text(
            text = stringResource(id = R.string.auto_backup),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding().padding(top = 16.dp),
            color = colorAccent()
        )

        // Enable/Disable
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.auto_backup_switch))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.auto_backup_description))
            },
            leadingContent = {
                Icon(Icons.Outlined.Backup, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = autoBackupEnabled,
                    onCheckedChange = onAutoBackupEnabledChange
                )
            }
        )

        // Directory selection
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.auto_backup_directory))
            },
            supportingContent = {
                Text(
                    text = if (autoBackupDirectoryUri.isNotEmpty() && autoBackupDirectoryDisplayName.isNotEmpty())
                        autoBackupDirectoryDisplayName
                    else if (autoBackupDirectoryUri.isNotEmpty())
                        stringResource(R.string.auto_backup_directory_selected)
                    else
                        stringResource(R.string.auto_backup_select_directory)
                )
            },
            leadingContent = {
                Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable(enabled = true) { onAutoBackupSelectDirectory() }
        )

        // Max count
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.auto_backup_max_count))
            },
            supportingContent = {
                Text(text = stringResource(R.string.auto_backup_max_count_value, autoBackupMaxCount))
            },
            leadingContent = {
                Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable {
                val options = listOf(1, 3, 5, 10, 20, 50)
                val nextIndex = (options.indexOf(autoBackupMaxCount) + 1) % options.size
                onAutoBackupMaxCountChange(options[nextIndex])
            }
        )

        // Interval
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.auto_backup_interval))
            },
            supportingContent = {
                Text(text = formatIntervalMinutes(autoBackupIntervalMinutes))
            },
            leadingContent = {
                Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable {
                val options = listOf(60L, 120L, 360L, 720L, 1440L, 4320L, 10080L)
                val nextIndex = (options.indexOf(autoBackupIntervalMinutes) + 1) % options.size
                onAutoBackupIntervalMinutesChange(options[nextIndex])
            }
        )

        // Last backup time
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.auto_backup_last_time))
            },
            supportingContent = {
                Text(
                    text = if (autoBackupLastTimestamp > 0) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        sdf.format(Date(autoBackupLastTimestamp))
                    } else {
                        stringResource(R.string.auto_backup_never)
                    }
                )
            },
            leadingContent = {
                Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )

        // Include images
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.auto_backup_include_images))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.auto_backup_include_images_description))
            },
            leadingContent = {
                Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                Switch(
                    checked = autoBackupIncludeImages,
                    onCheckedChange = onAutoBackupIncludeImagesChange
                )
            }
        )
    }
}

/**
 * Format interval in minutes to human-readable string.
 */
private fun formatIntervalMinutes(minutes: Long): String {
    return when {
        minutes < 60 -> "${minutes} min"
        minutes < 1440 -> "${minutes / 60} h"
        minutes < 10080 -> "${minutes / 1440} days"
        else -> {
            val weeks = minutes / 10080
            if (weeks == 1L) "1 week"
            else "$weeks weeks"
        }
    }
}