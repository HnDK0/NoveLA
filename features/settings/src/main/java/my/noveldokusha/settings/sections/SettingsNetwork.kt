package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R

@Composable
internal fun SettingsNetwork(
    scraperUserAgent: MutableState<String>,
    cloudflareBypassEnabled: MutableState<Boolean>,
    @Suppress("UNUSED_PARAMETER") cloudflareChallengeTimeoutSeconds: MutableState<Int>,
    massAddDelayMs: State<Long>,
    onMassAddDelayChange: (Long) -> Unit,
    downloadDelayMs: State<Long>,
    onDownloadDelayChange: (Long) -> Unit,
    @Suppress("UNUSED_PARAMETER") onCloudflareBypassChanged: (() -> Unit)? = null
) {
    Column {
        Text(
            text = stringResource(id = R.string.network),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = colorAccent()
        )

        // User-Agent setting
        var showUserAgentDialog by remember { mutableStateOf(false) }
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.scraper_user_agent))
            },
            supportingContent = {
                Text(text = if (scraperUserAgent.value.isBlank())
                    stringResource(R.string.default_user_agent)
                else
                    scraperUserAgent.value.take(50) + if (scraperUserAgent.value.length > 50) "..." else "")
            },
            leadingContent = {
                Icon(Icons.Outlined.Http, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onClick = { showUserAgentDialog = true }
        )

        if (showUserAgentDialog) {
            var tempUserAgent by remember { mutableStateOf(scraperUserAgent.value) }
            val hasInvalidChars = tempUserAgent.isNotBlank() && tempUserAgent.any { it.code > 127 }
            Dialog(onDismissRequest = { showUserAgentDialog = false }) {
                androidx.compose.material3.Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.scraper_user_agent),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = tempUserAgent,
                            onValueChange = { tempUserAgent = it },
                            label = { Text(stringResource(R.string.user_agent), color = MaterialTheme.colorScheme.onSurface) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = hasInvalidChars,
                            supportingText = {
                                if (hasInvalidChars) {
                                    Text(
                                        text = stringResource(R.string.user_agent_invalid_chars),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(stringResource(R.string.leave_empty_for_default))
                                }
                            },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (hasInvalidChars) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = if (hasInvalidChars) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showUserAgentDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    scraperUserAgent.value = tempUserAgent.trim()
                                    showUserAgentDialog = false
                                },
                                enabled = !hasInvalidChars,
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    }
                }
            }
        }

        // Cloudflare bypass toggle
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.cloudflare_bypass))
            },
            supportingContent = {
                Text(text = stringResource(R.string.cloudflare_bypass_description))
            },
            leadingContent = {
                Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingContent = {
                androidx.compose.material3.Switch(
                    checked = cloudflareBypassEnabled.value,
                    onCheckedChange = {
                        cloudflareBypassEnabled.value = it
                        // Note: Requires app restart to take effect
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                    )
                )
            }
        )

        // Cloudflare timeout setting - DISABLED (not used yet)
        /*
        if (cloudflareBypassEnabled.value) {
            var showTimeoutDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = {
                    Text(text = stringResource(R.string.cloudflare_challenge_timeout))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.seconds_format, cloudflareChallengeTimeoutSeconds.value))
                },
                leadingContent = {
                    Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.clickable { showTimeoutDialog = true }
            )

            if (showTimeoutDialog) {
                var tempTimeout by remember { mutableStateOf(cloudflareChallengeTimeoutSeconds.value.toString()) }
                Dialog(onDismissRequest = { showTimeoutDialog = false }) {
                    androidx.compose.material3.Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.cloudflare_challenge_timeout),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = tempTimeout,
                                onValueChange = {
                                    tempTimeout = it.filter { char -> char.isDigit() }
                                },
                                label = { Text(stringResource(R.string.timeout_seconds), color = MaterialTheme.colorScheme.onSurface) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showTimeoutDialog = false },
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                                ) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                                TextButton(
                                    onClick = {
                                        val timeout = tempTimeout.toIntOrNull()?.coerceIn(30, 600) ?: 120
                                        cloudflareChallengeTimeoutSeconds.value = timeout
                                        showTimeoutDialog = false
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            }
                        }
                    }
                }
            }
        }
        */



        // Download delay setting
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.download_delay))
            },
            supportingContent = {
                Column {
                    Text(text = stringResource(R.string.download_delay_description))
                    Text(
                        text = "${downloadDelayMs.value / 1000}.${(downloadDelayMs.value % 1000) / 100} s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Icon(Icons.Outlined.Http, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable {
                val currentDelay = downloadDelayMs.value
                val newDelay = when (currentDelay) {
                    500L -> 1000L
                    1000L -> 2000L
                    2000L -> 3000L
                    3000L -> 5000L
                    5000L -> 10000L
                    else -> 500L
                }
                onDownloadDelayChange(newDelay)
            }
        )

        // Add delay setting
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.add_delay))
            },
            supportingContent = {
                Column {
                    Text(text = stringResource(R.string.add_delay_description, massAddDelayMs.value / 1000))
                }
            },
            leadingContent = {
                Icon(Icons.Outlined.Http, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            modifier = Modifier.clickable {
                // Cycle through common delay values: 0.5s, 1s, 2s, 3s, 5s
                val currentDelay = massAddDelayMs.value
                val newDelay = when (currentDelay) {
                    500L -> 1000L
                    1000L -> 2000L
                    2000L -> 3000L
                    3000L -> 5000L
                    else -> 500L // Default to 0.5s if unknown value
                }
                onMassAddDelayChange(newDelay)
            }
        )
    }
}