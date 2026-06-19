package my.noveldokusha.tooling.backup_restore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import my.noveldokusha.coreui.components.MyButton

@Composable
fun onBackupRestore(): () -> Unit {
    val context = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var overwritePlugins by rememberSaveable { mutableStateOf(true) }

    val fileExplorer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null)
                RestoreDataService.start(
                    ctx = context,
                    uri = uri,
                    overwritePlugins = overwritePlugins
                )
        }
    )

    if (showDialog) Dialog(
        onDismissRequest = { showDialog = false },
        content = {
            Card {
                Column {
                    Text(
                        text = stringResource(R.string.restore_options),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clickable { overwritePlugins = !overwritePlugins }
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Checkbox(
                            checked = overwritePlugins,
                            onCheckedChange = null
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.overwrite_plugins),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.overwrite_plugins_description),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    MyButton(
                        text = stringResource(id = R.string.restore_data),
                        textAlign = TextAlign.Center,
                        onClick = {
                            showDialog = false
                            fileExplorer.launch("*/*")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .padding(top = 12.dp)
                    )
                }
            }
        }
    )

    return { showDialog = true }
}