package my.noveldokusha.tooling.novel_migration.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.strings.R
import my.noveldokusha.tooling.novel_migration.data.MigrationOptions

@Composable
fun MigrationOptionsDialog(
    initialOptions: MigrationOptions,
    onConfirm: (MigrationOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var transferProgress by remember { mutableStateOf(initialOptions.transferProgress) }
    var transferBodies by remember { mutableStateOf(initialOptions.transferBodies) }
    var transferTranslations by remember { mutableStateOf(initialOptions.transferTranslations) }
    var deleteOldBook by remember { mutableStateOf(initialOptions.deleteOldBook) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.migration_title)) },
        text = {
            Column {
                CheckboxRow(
                    label = stringResource(R.string.migration_reading_progress),
                    checked = transferProgress,
                    onCheckedChange = { transferProgress = it },
                )
                Spacer(Modifier.height(4.dp))
                CheckboxRow(
                    label = stringResource(R.string.migration_downloaded_chapters),
                    checked = transferBodies,
                    onCheckedChange = { transferBodies = it },
                )
                Spacer(Modifier.height(4.dp))
                CheckboxRow(
                    label = stringResource(R.string.migration_translations),
                    checked = transferTranslations,
                    onCheckedChange = { transferTranslations = it },
                )
                Spacer(Modifier.height(4.dp))
                CheckboxRow(
                    label = stringResource(R.string.migration_delete_old_book),
                    checked = deleteOldBook,
                    onCheckedChange = { deleteOldBook = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(MigrationOptions(
                    transferProgress = transferProgress,
                    transferBodies = transferBodies,
                    transferTranslations = transferTranslations,
                    deleteOldBook = deleteOldBook,
                ))
            }) {
                Text(stringResource(R.string.migration_migrate_all))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
