package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.settings.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsOpenAITranslation(
    baseUrl: String,
    apiKeys: String,
    model: String,
    onBaseUrlChange: (String) -> Unit,
    onApiKeysChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    val predefinedModels = listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-3.5-turbo",
        "mistral-small-latest",
        "mistral-large-latest",
        "meta-llama/llama-3.3-70b-instruct:free",
        "google/gemini-2.0-flash-exp:free",
        "deepseek/deepseek-chat",
        "qwen/qwen-2.5-72b-instruct",
        "microsoft/phi-4",
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
        cursorColor = MaterialTheme.colorScheme.onSurface
    )

    // Local state for text fields — NOT synced back from props to avoid
    // resetting the cursor while the user is typing.
    var baseUrlText by rememberSaveable { mutableStateOf(baseUrl) }
    var apiKeysText by rememberSaveable { mutableStateOf(apiKeys) }

    // Model field: plain independent state, no LaunchedEffect reset.
    var modelText by rememberSaveable { mutableStateOf(model) }

    val focusManager = LocalFocusManager.current

    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        // ── Base URL ──────────────────────────────────────────────────────────
        SlimListItem(
            headlineContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.openai_base_url),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrlText,
                        onValueChange = {
                            baseUrlText = it
                            onBaseUrlChange(it)
                        },
                        label = {
                            Text(
                                stringResource(R.string.openai_base_url_label),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        placeholder = { Text("https://api.openai.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = textFieldColors
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.openai_base_url_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // ── API Keys ──────────────────────────────────────────────────────────
        SlimListItem(
            headlineContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.openai_api_keys),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeysText,
                        onValueChange = {
                            apiKeysText = it
                            onApiKeysChange(it)
                        },
                        label = {
                            Text(
                                stringResource(R.string.openai_api_keys_label),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        placeholder = { Text(stringResource(R.string.openai_api_keys_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        colors = textFieldColors
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.openai_api_keys_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // ── Model ─────────────────────────────────────────────────────────────
        SlimListItem(
            headlineContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.openai_model),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))

                    // Plain text field — no dropdown wrapper stealing events
                    OutlinedTextField(
                        value = modelText,
                        onValueChange = {
                            modelText = it
                            onModelChange(it)
                        },
                        label = {
                            Text(
                                stringResource(R.string.openai_model_label),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        placeholder = { Text(predefinedModels.first()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = textFieldColors
                    )

                    Spacer(Modifier.height(8.dp))

                    // Preset chips — tap to fill the field instantly
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        predefinedModels.forEach { preset ->
                            FilterChip(
                                selected = modelText == preset,
                                onClick = {
                                    modelText = preset
                                    onModelChange(preset)
                                    focusManager.clearFocus()
                                },
                                label = {
                                    Text(
                                        text = preset,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.openai_model_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}