package my.noveldokusha.settings.sections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R
import my.noveldokusha.text_translator.BUILT_IN_PROMPTS

/**
 * Единый менеджер промптов для LLM-провайдеров (Gemini и OpenAI-compatible).
 * Показывается внутри блока настроек провайдера, когда выбран Gemini или OpenAI.
 */
@Composable
internal fun SettingsPromptManager(
    activeSystemPrompt: String,
    promptPresets: List<Pair<String, String>>,
    promptUseEnglishLocale: Boolean,
    onActiveSystemPromptChange: (String) -> Unit,
    onPromptUseEnglishLocaleChange: (Boolean) -> Unit,
    onSavePreset: (name: String, prompt: String) -> Unit,
    onDeletePreset: (name: String) -> Unit,
) {
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface,
        cursorColor          = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor    = MaterialTheme.colorScheme.onSurface,
        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    var promptText by remember(activeSystemPrompt) { mutableStateOf(activeSystemPrompt) }
    var showSavePresetDialog by rememberSaveable { mutableStateOf(false) }
    var presetNameInput by rememberSaveable { mutableStateOf("") }

    // Заголовок секции в едином стиле с остальными секциями настроек
    Text(
        text     = stringResource(R.string.llm_prompt_section_title),
        style    = MaterialTheme.typography.titleMedium,
        modifier = Modifier.textPadding(),
        color    = colorAccent(),
    )

    // ── System Prompt field ───────────────────────────────────────────────────
    SlimListItem(
        headlineContent = {
            // Отступ сверху чтобы label OutlinedTextField не обрезался анимацией
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.llm_prompt_label),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = promptText,
                    onValueChange = { promptText = it; onActiveSystemPromptChange(it) },
                    modifier      = Modifier.fillMaxWidth(),
                    minLines      = 5,
                    maxLines      = 12,
                    colors        = textFieldColors,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = stringResource(R.string.llm_prompt_placeholders_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                // ── Preset chips ──────────────────────────────────────────────
                val allPresets: List<Pair<String, String>> = buildList {
                    addAll(BUILT_IN_PROMPTS)
                    addAll(promptPresets)
                }
                val builtInNames = BUILT_IN_PROMPTS.map { it.first }.toSet()

                Row(
                    modifier              = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allPresets.forEach { (presetName, presetPrompt) ->
                        val isBuiltIn  = presetName in builtInNames
                        val isSelected = promptText == presetPrompt

                        // Для пользовательских чипов: при нажатии на уже выбранный
                        // показываем DropdownMenu с опциями «Применить» / «Удалить»
                        var showChipMenu by remember { mutableStateOf(false) }

                        if (isBuiltIn) {
                            // Встроенный пресет — простой клик без меню
                            FilterChip(
                                selected = isSelected,
                                onClick  = {
                                    promptText = presetPrompt
                                    onActiveSystemPromptChange(presetPrompt)
                                },
                                label    = { Text(presetName) },
                            )
                        } else {
                            // Пользовательский пресет — клик открывает меню
                            // (и применить, и удалить — без конфликта касания)
                            Column {
                                FilterChip(
                                    selected = isSelected,
                                    onClick  = { showChipMenu = true },
                                    label    = { Text(presetName) },
                                )
                                DropdownMenu(
                                    expanded        = showChipMenu,
                                    onDismissRequest = { showChipMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text    = { Text(stringResource(R.string.apply)) },
                                        onClick = {
                                            promptText = presetPrompt
                                            onActiveSystemPromptChange(presetPrompt)
                                            showChipMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text    = {
                                            Text(
                                                text  = stringResource(R.string.delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            onDeletePreset(presetName)
                                            showChipMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Save preset button ────────────────────────────────────────
                OutlinedButton(
                    onClick  = {
                        presetNameInput = ""
                        showSavePresetDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border   = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.llm_save_preset),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    )

    // ── English locale toggle ─────────────────────────────────────────────────
    SlimListItem(
        headlineContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.Translate,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text  = stringResource(R.string.llm_prompt_use_english_locale),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text  = stringResource(R.string.llm_prompt_use_english_locale_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked         = promptUseEnglishLocale,
                        onCheckedChange = onPromptUseEnglishLocaleChange,
                        colors          = SwitchDefaults.colors(
                            checkedThumbColor = colorAccent(),
                            checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        }
    )

    // ── Save preset dialog ────────────────────────────────────────────────────
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title            = {
                Text(
                    text  = stringResource(R.string.llm_save_preset),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text             = {
                OutlinedTextField(
                    value         = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label         = {
                        Text(
                            text  = stringResource(R.string.llm_preset_name),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth(),
                    colors     = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor          = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor    = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            },
            confirmButton    = {
                TextButton(
                    onClick  = {
                        val name = presetNameInput.trim()
                        if (name.isNotEmpty()) {
                            onSavePreset(name, promptText)
                            showSavePresetDialog = false
                        }
                    },
                    enabled  = presetNameInput.trim().isNotEmpty(),
                    colors   = ButtonDefaults.textButtonColors(
                        contentColor         = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    ),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton    = {
                TextButton(
                    onClick = { showSavePresetDialog = false },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}