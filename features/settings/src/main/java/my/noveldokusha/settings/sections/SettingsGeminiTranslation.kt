package my.noveldokusha.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsGeminiTranslation(
    translationProvider: String,
    geminiApiKey: String,
    geminiModel: String,
    googlePaApiKeys: String,
    openAiBaseUrl: String,
    openAiApiKeys: String,
    openAiModel: String,
    activeSystemPrompt: String,
    promptPresets: List<Pair<String, String>>,
    promptUseEnglishLocale: Boolean,
    onTranslationProviderChange: (String) -> Unit,
    onGeminiApiKeyChange: (String) -> Unit,
    onGeminiModelChange: (String) -> Unit,
    onGooglePaApiKeysChange: (String) -> Unit,
    onOpenAiBaseUrlChange: (String) -> Unit,
    onOpenAiApiKeysChange: (String) -> Unit,
    onOpenAiModelChange: (String) -> Unit,
    onActiveSystemPromptChange: (String) -> Unit,
    onPromptUseEnglishLocaleChange: (Boolean) -> Unit,
    onSavePreset: (name: String, prompt: String) -> Unit,
    onDeletePreset: (name: String) -> Unit,
    // LLM batch / token settings (shown only for Gemini and OpenAI)
    llmBatchSize: Int,
    llmMaxOutputTokens: Int,
    onLlmBatchSizeChange: (Int) -> Unit,
    onLlmMaxOutputTokensChange: (Int) -> Unit,
    // Novel-specific prompts
    novelPromptCount: Int,
    onNovelPromptsClick: () -> Unit,
) {
    val predefinedGeminiModels = listOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash-exp",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-1.0-pro"
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
        cursorColor          = MaterialTheme.colorScheme.onSurface
    )

    // rememberSaveable — survives rotation; no LaunchedEffect reset so typing isn't interrupted
    var apiKeyText by rememberSaveable { mutableStateOf(geminiApiKey) }
    var modelText  by rememberSaveable { mutableStateOf(geminiModel) }
    var paKeysText by rememberSaveable { mutableStateOf(googlePaApiKeys) }

    val focusManager = LocalFocusManager.current

    Column {
        Text(
            text     = stringResource(R.string.translation_services),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
        color    = colorAccent()
        )

        // ── Provider picker ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            ProviderRadioOption(
                label       = stringResource(R.string.provider_google_pa),
                description = stringResource(R.string.provider_google_pa_description),
                selected    = translationProvider == "GOOGLE_PA",
                onClick     = { onTranslationProviderChange("GOOGLE_PA") }
            )
            Spacer(Modifier.height(4.dp))
            ProviderRadioOption(
                label       = stringResource(R.string.provider_google_free),
                description = stringResource(R.string.provider_google_free_description),
                selected    = translationProvider == "GOOGLE_FREE",
                onClick     = { onTranslationProviderChange("GOOGLE_FREE") }
            )
            Spacer(Modifier.height(4.dp))
            ProviderRadioOption(
                label       = stringResource(R.string.provider_gemini),
                description = stringResource(R.string.provider_gemini_description),
                selected    = translationProvider == "GEMINI",
                onClick     = { onTranslationProviderChange("GEMINI") }
            )
            Spacer(Modifier.height(4.dp))
            ProviderRadioOption(
                label       = stringResource(R.string.provider_openai),
                description = stringResource(R.string.provider_openai_description),
                selected    = translationProvider == "OPENAI",
                onClick     = { onTranslationProviderChange("OPENAI") }
            )
        }

        // ── Google PA config ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = translationProvider == "GOOGLE_PA",
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                SlimListItem(
                    headlineContent = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text  = stringResource(R.string.google_pa_api_keys),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value         = paKeysText,
                                onValueChange = { paKeysText = it; onGooglePaApiKeysChange(it) },
                                label         = { Text(stringResource(R.string.enter_google_pa_api_keys), color = MaterialTheme.colorScheme.onSurface) },
                                placeholder   = { Text(stringResource(R.string.google_pa_api_keys_placeholder)) },
                                modifier      = Modifier.fillMaxWidth(),
                                minLines      = 2,
                                maxLines      = 5,
                                colors        = textFieldColors
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = stringResource(R.string.google_pa_api_keys_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }

        // ── Gemini config ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = translationProvider == "GEMINI",
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                // API Key
                SlimListItem(
                    headlineContent = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier          = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text  = stringResource(R.string.gemini_api_key),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value         = apiKeyText,
                                onValueChange = { apiKeyText = it; onGeminiApiKeyChange(it) },
                                label         = { Text(stringResource(R.string.enter_gemini_api_keys), color = MaterialTheme.colorScheme.onSurface) },
                                placeholder   = { Text(stringResource(R.string.gemini_api_keys_placeholder)) },
                                modifier      = Modifier.fillMaxWidth(),
                                minLines      = 2,
                                maxLines      = 5,
                                colors        = textFieldColors
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = stringResource(R.string.get_free_api_key) + "\n" +
                                        stringResource(R.string.api_key_tip) + "\n\n" +
                                        stringResource(R.string.no_api_key_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                // Model
                SlimListItem(
                    headlineContent = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text  = stringResource(R.string.gemini_model),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(8.dp))

                            // Plain TextField — no ExposedDropdownMenuBox stealing events
                            OutlinedTextField(
                                value         = modelText,
                                onValueChange = {
                                    modelText = it
                                    onGeminiModelChange(it)
                                },
                                placeholder   = { Text(predefinedGeminiModels.first()) },
                                label         = { Text(stringResource(R.string.model_name), color = MaterialTheme.colorScheme.onSurface) },
                                modifier      = Modifier.fillMaxWidth(),
                                singleLine    = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                colors        = textFieldColors
                            )

                            Spacer(Modifier.height(8.dp))

                            // Preset chips
                            FlowRow(
                                modifier             = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement  = Arrangement.spacedBy(4.dp)
                            ) {
                                predefinedGeminiModels.forEach { preset ->
                                    FilterChip(
                                        selected = modelText == preset,
                                        onClick  = {
                                            modelText = preset
                                            onGeminiModelChange(preset)
                                            focusManager.clearFocus()
                                        },
                                        label    = {
                                            Text(
                                                text  = preset,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = stringResource(R.string.default_gemini_model) + "\n" +
                                        stringResource(R.string.model_examples),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                // Prompt manager для Gemini
                SettingsPromptManager(
                    activeSystemPrompt             = activeSystemPrompt,
                    promptPresets                  = promptPresets,
                    promptUseEnglishLocale         = promptUseEnglishLocale,
                    onActiveSystemPromptChange     = onActiveSystemPromptChange,
                    onPromptUseEnglishLocaleChange = onPromptUseEnglishLocaleChange,
                    onSavePreset                   = onSavePreset,
                    onDeletePreset                 = onDeletePreset,
                )
            }
        }

        // ── OpenAI-compatible config ──────────────────────────────────────────
        AnimatedVisibility(
            visible = translationProvider == "OPENAI",
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Column {
                SettingsOpenAITranslation(
                    baseUrl         = openAiBaseUrl,
                    apiKeys         = openAiApiKeys,
                    model           = openAiModel,
                    onBaseUrlChange = onOpenAiBaseUrlChange,
                    onApiKeysChange = onOpenAiApiKeysChange,
                    onModelChange   = onOpenAiModelChange,
                )
                SettingsPromptManager(
                    activeSystemPrompt             = activeSystemPrompt,
                    promptPresets                  = promptPresets,
                    promptUseEnglishLocale         = promptUseEnglishLocale,
                    onActiveSystemPromptChange     = onActiveSystemPromptChange,
                    onPromptUseEnglishLocaleChange = onPromptUseEnglishLocaleChange,
                    onSavePreset                   = onSavePreset,
                    onDeletePreset                 = onDeletePreset,
                )
            }
        }

        // ── Novel-specific prompts — Gemini and OpenAI only ──────────────────
        AnimatedVisibility(
            visible = translationProvider == "GEMINI" || translationProvider == "OPENAI",
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNovelPromptsClick() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.novel_prompts_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (novelPromptCount > 0) {
                            Text(
                                text = stringResource(R.string.novel_prompts_count, novelPromptCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── LLM batch / token settings — Gemini and OpenAI only ──────────────
        AnimatedVisibility(
            visible = translationProvider == "GEMINI" || translationProvider == "OPENAI",
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            SettingsLlmBatchOptions(
                batchSize         = llmBatchSize,
                maxOutputTokens   = llmMaxOutputTokens,
                onBatchSizeChange = onLlmBatchSizeChange,
                onMaxTokensChange = onLlmMaxOutputTokensChange,
            )
        }
    }
}

@Composable
private fun ProviderRadioOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick  = null,
            colors   = RadioButtonDefaults.colors(
                selectedColor   = colorAccent(),
                unselectedColor = MaterialTheme.colorScheme.onSurface,
            )
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = label,       style = MaterialTheme.typography.bodyLarge)
            Text(text = description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Batch size and max output tokens — shown only for Gemini and OpenAI providers.
 * Google PA and Free use character-based chunking and ignore these settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsLlmBatchOptions(
    batchSize: Int,
    maxOutputTokens: Int,
    onBatchSizeChange: (Int) -> Unit,
    onMaxTokensChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
        cursorColor          = MaterialTheme.colorScheme.onSurface
    )

    var batchText  by rememberSaveable(batchSize)       { mutableStateOf(batchSize.toString()) }
    var tokensText by rememberSaveable(maxOutputTokens) { mutableStateOf(maxOutputTokens.toString()) }

    val batchPresets  = listOf(20, 40, 60, 80, 100, 120)
    val tokensPresets = listOf(0, 1024, 2048, 4096, 8192, 16384, 32768)

    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

        // ── Batch size ────────────────────────────────────────────────────────
        SlimListItem(
            headlineContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text  = stringResource(R.string.llm_batch_size),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = batchText,
                        onValueChange = { raw ->
                            batchText = raw
                            raw.toIntOrNull()?.coerceAtLeast(1)?.let(onBatchSizeChange)
                        },
                        label           = { Text(stringResource(R.string.llm_batch_size_label), color = MaterialTheme.colorScheme.onSurface) },
                        modifier        = Modifier.fillMaxWidth(),
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors          = textFieldColors
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp)
                    ) {
                        batchPresets.forEach { preset ->
                            FilterChip(
                                selected = batchText == preset.toString(),
                                onClick  = { batchText = preset.toString(); onBatchSizeChange(preset); focusManager.clearFocus() },
                                label    = { Text(preset.toString(), style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = stringResource(R.string.llm_batch_size_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        // ── Max output tokens ─────────────────────────────────────────────────
        SlimListItem(
            headlineContent = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text  = stringResource(R.string.llm_max_output_tokens),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = tokensText,
                        onValueChange = { raw ->
                            tokensText = raw
                            raw.toIntOrNull()?.coerceAtLeast(0)?.let(onMaxTokensChange)
                        },
                        label           = { Text(stringResource(R.string.llm_max_output_tokens_label), color = MaterialTheme.colorScheme.onSurface) },
                        modifier        = Modifier.fillMaxWidth(),
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors          = textFieldColors
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp)
                    ) {
                        tokensPresets.forEach { preset ->
                            val label = if (preset == 0) stringResource(R.string.llm_tokens_auto) else preset.toString()
                            FilterChip(
                                selected = tokensText == preset.toString(),
                                onClick  = { tokensText = preset.toString(); onMaxTokensChange(preset); focusManager.clearFocus() },
                                label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = stringResource(R.string.llm_max_output_tokens_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}