package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.features.reader.features.LiveTranslationSettingData
import my.noveldokusha.reader.R
import my.noveldokusha.text_translator.domain.TranslationModelState

@Composable
internal fun TranslatorSettingDialog(
    state: LiveTranslationSettingData
) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Toggle ──────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Switch(
                    checked = state.enable.value,
                    onCheckedChange = { state.onEnable(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorAccent(),
                        checkedTrackColor = colorAccent().copy(alpha = 0.3f),
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.live_translation),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.provider_name, getProviderLabel(state.currentProvider.value)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.enable.value) {
                    IconButton(onClick = { state.onRedoTranslation() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.live_translation),
                            tint = colorAccent(),
                        )
                    }
                }
            }

            // ── Provider selection ──────────────────────────────────────
            ProviderSelector(state = state)

            HorizontalDivider()

            // ── Language selection ──────────────────────────────────────
            LanguageSelector(state = state)

            // ── Novel prompt (LLM only) ────────────────────────────────
            NovelPromptSection(state = state)
        }
    }
}

private fun getProviderLabel(key: String): String = when (key) {
    "GOOGLE_PA"   -> "Google (Enhanced)"
    "GOOGLE_FREE" -> "Google (Simple)"
    "GEMINI"      -> "Gemini"
    "OPENAI"      -> "OpenAI"
    else          -> key
}

@Composable
private fun ProviderSelector(state: LiveTranslationSettingData) {
    val providers = listOf(
        Triple("GOOGLE_PA",   R.string.provider_google_pa,   R.string.provider_google_pa_description),
        Triple("GOOGLE_FREE", R.string.provider_google_free, R.string.provider_google_free_description),
        Triple("GEMINI",      R.string.provider_gemini,      R.string.provider_gemini_description),
        Triple("OPENAI",      R.string.provider_openai,      R.string.provider_openai_description),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        providers.forEach { (key, labelRes, descRes) ->
            val selected = state.currentProvider.value == key
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { state.onProviderChange(key) }
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = colorAccent(),
                        unselectedColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSelector(state: LiveTranslationSettingData) {
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.language_selection),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LanguageButton(
                label = state.source.value?.locale?.displayLanguage
                    ?: stringResource(R.string.language_source_empty_text),
                active = state.source.value != null,
                onClick = { showSourceDialog = true },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowRightAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            LanguageButton(
                label = state.target.value?.locale?.displayLanguage
                    ?: stringResource(R.string.language_target_empty_text),
                active = state.target.value != null,
                onClick = { showTargetDialog = true },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showSourceDialog) {
        LanguageSearchDialog(
            languages = state.listOfAvailableModels,
            selected = state.source.value,
            onSelect = { state.onSourceChange(it); showSourceDialog = false },
            onDismiss = { showSourceDialog = false },
        )
    }

    if (showTargetDialog) {
        LanguageSearchDialog(
            languages = state.listOfAvailableModels,
            selected = state.target.value,
            onSelect = { state.onTargetChange(it); showTargetDialog = false },
            onDismiss = { showTargetDialog = false },
        )
    }
}

@Composable
private fun LanguageButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            Icons.Filled.Language,
            contentDescription = null,
            tint = if (active) colorAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LanguageSearchDialog(
    languages: List<TranslationModelState>,
    selected: TranslationModelState?,
    onSelect: (TranslationModelState?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(query, languages) {
        if (query.isBlank()) languages
        else languages.filter {
            it.locale.displayLanguage.contains(query, ignoreCase = true) ||
            it.language.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.language_search),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.language_search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    filtered.forEach { item ->
                        val isSelected = selected?.language == item.language
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = item.available) {
                                    onSelect(if (isSelected) null else item)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                text = "${item.locale.displayLanguage} (${item.language})",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                color = when {
                                    !item.available -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    isSelected -> colorAccent()
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = colorAccent(),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.language_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun NovelPromptSection(state: LiveTranslationSettingData) {
    val isLlmProvider = state.currentProvider.value in listOf("GEMINI", "OPENAI")

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    val isActive = isLlmProvider && state.novelPrompt.value.isNotBlank()
    var showEditor by rememberSaveable { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLlmProvider) Modifier.clickable { showEditor = !showEditor }
                else Modifier
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            Icons.Outlined.Psychology,
            contentDescription = null,
            tint = if (isActive) colorAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.novel_prompt_title),
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) colorAccent() else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isLlmProvider) stringResource(R.string.novel_prompt_description_reader)
                       else stringResource(R.string.novel_prompt_llm_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isActive) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "(${state.novelPrompt.value.length} chars)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLlmProvider) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (isLlmProvider) {
        AnimatedVisibility(visible = showEditor) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                var promptText by remember(state.novelPrompt.value) {
                    mutableStateOf(state.novelPrompt.value)
                }

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = {
                        Text(
                            stringResource(R.string.novel_prompt_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            state.onNovelPromptChange(promptText)
                            showEditor = false
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.novel_prompt_append_mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.novelPromptAppendMode.value,
                        onCheckedChange = { state.onNovelPromptAppendModeChange(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorAccent(),
                            checkedTrackColor = colorAccent().copy(alpha = 0.3f),
                        ),
                    )
                }
            }
        }
    }
}
