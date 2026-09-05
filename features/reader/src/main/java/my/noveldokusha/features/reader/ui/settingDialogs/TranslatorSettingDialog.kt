package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver.ActiveTranslatorLevel
import my.noveldokusha.coreui.components.LanguageButton
import my.noveldokusha.coreui.components.LanguageSearchDialog
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.features.reader.features.LiveTranslationSettingData
import my.noveldokusha.reader.R

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
            // ── Toggle + режим применения ────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Switch(
                    checked = state.enable.value,
                    enabled = true,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                Spacer(Modifier.width(4.dp))
                ModeToggle(state = state)
            }

            // ── Статус перевода: активный переводчик или «все выключены». ──
            val level = state.activeTranslatorLevel.value
            val levelActive = level != ActiveTranslatorLevel.NONE
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (levelActive) colorAccent().copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = when (level) {
                            ActiveTranslatorLevel.PER_NOVEL -> Icons.Outlined.AutoStories
                            ActiveTranslatorLevel.PLUGIN -> Icons.Outlined.Extension
                            ActiveTranslatorLevel.GLOBAL -> Icons.Outlined.Public
                            ActiveTranslatorLevel.NONE -> Icons.Outlined.Block
                        },
                        contentDescription = null,
                        tint = if (levelActive) colorAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (level) {
                            ActiveTranslatorLevel.PER_NOVEL -> stringResource(
                                R.string.active_translator_label,
                                stringResource(R.string.translation_mode_per_novel),
                            )
                            ActiveTranslatorLevel.PLUGIN -> stringResource(
                                R.string.active_translator_label,
                                state.activePluginName.value ?: "",
                            )
                            ActiveTranslatorLevel.GLOBAL -> stringResource(
                                R.string.active_translator_label,
                                stringResource(R.string.translation_mode_global),
                            )
                            ActiveTranslatorLevel.NONE -> stringResource(R.string.translators_all_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (levelActive) colorAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── Подсказка состояния: переключатель и пара независимы. ──
            // «Перевод выключен» показываем только когда реально не переводит НИКТО
            // (level == NONE): иначе при выключенном пер-новел, но включённом плагине
            // подсказка противоречила бы полоске «Активный переводчик: <плагин>».
            when {
                !state.translationGlobalMode.value && (state.source.value == null || state.target.value == null) ->
                    Text(
                        text = stringResource(R.string.translation_select_pair_to_enable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }

            // ── Provider selection ──────────────────────────────────────
            ProviderSelector(state = state)

            HorizontalDivider()

            // ── Language selection ──────────────────────────────────────
            LanguageSelector(
                state = state,
                onRemovePair = state.onRemovePair,
            )

            // ── Display options ────────────────────────────────────────
            DisplayOptionsSection(state = state)

            // ── Novel prompt (LLM only) ────────────────────────────────
            NovelPromptSection(state = state)
        }
    }
}

@Composable
private fun ModeToggle(state: LiveTranslationSettingData) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeChip(
            selected = !state.translationGlobalMode.value,
            icon = Icons.Outlined.AutoStories,
            description = stringResource(R.string.translation_mode_per_novel),
            onClick = { state.onTranslationGlobalModeChange(false) },
        )
        ModeChip(
            selected = state.translationGlobalMode.value,
            icon = Icons.Outlined.Public,
            description = stringResource(R.string.translation_mode_global),
            onClick = { state.onTranslationGlobalModeChange(true) },
        )
    }
}

@Composable
private fun ModeChip(
    selected: Boolean,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            selectedContainerColor = colorAccent().copy(alpha = 0.15f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = colorAccent(),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = colorAccent(),
            selectedBorderWidth = 1.dp,
        ),
    )
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
private fun LanguageSelector(
    state: LiveTranslationSettingData,
    onRemovePair: (TranslationLangPair) -> Unit = {},
) {
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
                label = state.source.value?.displayName
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
                label = state.target.value?.displayName
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
            favoriteLanguages = state.favoriteLanguages,
            onToggleFavorite = state.onToggleFavorite,
            recentPairs = state.recentPairs,
            onApplyRecentPair = state.onApplyRecentPair,
            onRemovePair = onRemovePair,
        )
    }

    if (showTargetDialog) {
        LanguageSearchDialog(
            languages = state.listOfAvailableModels,
            selected = state.target.value,
            onSelect = { state.onTargetChange(it); showTargetDialog = false },
            onDismiss = { showTargetDialog = false },
            favoriteLanguages = state.favoriteLanguages,
            onToggleFavorite = state.onToggleFavorite,
            recentPairs = state.recentPairs,
            onApplyRecentPair = state.onApplyRecentPair,
            onRemovePair = onRemovePair,
        )
    }
}

@Composable
private fun DisplayOptionsSection(state: LiveTranslationSettingData) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    val enabled = state.parallelEnabled.value
    val order = state.parallelOrder.value
    val active = enabled

    val modeLabel = when {
        !enabled -> ""
        order == "TRANSLATION_FIRST" -> stringResource(R.string.parallel_order_translation_first)
        else -> stringResource(R.string.parallel_order_original_first)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    !enabled -> {
                        state.onParallelEnabledChange(true)
                        state.onParallelOrderChange("TRANSLATION_FIRST")
                    }
                    order == "TRANSLATION_FIRST" -> {
                        state.onParallelOrderChange("ORIGINAL_FIRST")
                    }
                    else -> {
                        state.onParallelEnabledChange(false)
                    }
                }
            }
            .padding(vertical = 4.dp),
    ) {
        Icon(
            if (active) Icons.Filled.ViewColumn else Icons.Outlined.ViewColumn,
            contentDescription = stringResource(R.string.parallel_mode_title),
            tint = if (active) colorAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.parallel_mode_title),
                style = MaterialTheme.typography.titleSmall,
                color = if (active) colorAccent() else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.parallel_mode_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (active) {
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colorAccent(),
            )
        }
    }
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
