package my.noveldokusha.extensions

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.core.appPreferences.isComplete
import my.noveldokusha.coreui.components.LanguageButton
import my.noveldokusha.coreui.components.LanguageSearchDialog
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.text_translator.domain.TranslationModelState

/**
 * Диалог настроек перевода для конкретного плагина (источника).
 *
 * ViewModel-геттеры не реактивны (read одним заходом поверх AppPreferences),
 * поэтому значениями управляют локальные Compose-State: мутации пишут в VM и
 * тут же обновляют State, чтобы диалог реагировал мгновенно (P1).
 */
@Composable
fun PluginTranslationSettingsDialog(
    extensionId: String,
    viewModel: ExtensionsManagerViewModel,
    onDismiss: () -> Unit,
) {
    val models = viewModel.translationModels

    val enabled = remember(extensionId) { mutableStateOf(viewModel.translationEnabled(extensionId)) }
    val pair = remember(extensionId) { mutableStateOf(viewModel.translationPair(extensionId)) }
    val provider = remember(extensionId) { mutableStateOf(viewModel.translationProvider(extensionId) ?: "GOOGLE_PA") }
    val scope = remember(extensionId) { mutableStateOf(viewModel.translationScope(extensionId)) }
    val prompt = remember(extensionId) { mutableStateOf(viewModel.translationPrompt(extensionId).orEmpty()) }

    // Реактивные списки избранного и последних пар: clear+addAll после мутации
    // триггерят рекомпозицию диалога (по образцу ReaderLiveTranslation).
    val favoriteLanguages = remember {
        mutableStateListOf<String>().apply { addAll(viewModel.favoriteLanguages()) }
    }
    val recentPairs = remember {
        mutableStateListOf<TranslationLangPair>().apply { addAll(viewModel.recentTranslationPairs()) }
    }
    val refreshFavorite = remember(viewModel) {
        {
            favoriteLanguages.clear()
            favoriteLanguages.addAll(viewModel.favoriteLanguages())
        }
    }
    val refreshRecent = remember(viewModel) {
        {
            recentPairs.clear()
            recentPairs.addAll(viewModel.recentTranslationPairs())
        }
    }

    // Провайдер из префов может быть неизвестным — безопасно откатываемся на GOOGLE_PA.
    val safeProvider = if (PROVIDERS.any { it.first == provider.value }) provider.value else "GOOGLE_PA"

    var showPromptEditor by remember { mutableStateOf(false) }
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var showTargetDialog by rememberSaveable { mutableStateOf(false) }

    val sourceModel = models.firstOrNull { it.language == pair.value.source }
    val targetModel = models.firstOrNull { it.language == pair.value.target }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(my.noveldokusha.strings.R.string.plugin_translation_settings_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Включение перевода плагина ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Switch(
                        checked = enabled.value,
                        onCheckedChange = {
                            enabled.value = it
                            viewModel.setTranslationEnabled(extensionId, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorAccent(),
                            checkedTrackColor = colorAccent().copy(alpha = 0.3f),
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(my.noveldokusha.strings.R.string.plugin_translation_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // ── Провайдер ──
                PROVIDERS.forEach { (key, labelRes) ->
                    val selected = safeProvider == key
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                provider.value = key
                                viewModel.setTranslationProvider(extensionId, key)
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                HorizontalDivider()

                // ── Пара языков (source → target) ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LanguageButton(
                        label = sourceModel?.displayName
                            ?: stringResource(my.noveldokusha.strings.R.string.language_source_empty_text),
                        active = sourceModel != null,
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
                        label = targetModel?.displayName
                            ?: stringResource(my.noveldokusha.strings.R.string.language_target_empty_text),
                        active = targetModel != null,
                        onClick = { showTargetDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (showSourceDialog) {
                    LanguageSearchDialog(
                        languages = models,
                        selected = sourceModel,
                        onSelect = { model ->
                            val newPair = pair.value.copy(source = model?.language ?: "")
                            pair.value = newPair
                            viewModel.setTranslationPair(extensionId, newPair.source, newPair.target)
                            showSourceDialog = false
                        },
                        onDismiss = { showSourceDialog = false },
                        favoriteLanguages = favoriteLanguages,
                        onToggleFavorite = {
                            viewModel.toggleFavoriteLanguage(it)
                            refreshFavorite()
                        },
                        recentPairs = recentPairs,
                        onApplyRecentPair = { s, t ->
                            viewModel.recordRecentTranslationPair(s, t)
                            val newPair = pair.value.copy(source = s, target = t)
                            pair.value = newPair
                            viewModel.setTranslationPair(extensionId, s, t)
                            refreshRecent()
                            showSourceDialog = false
                        },
                        onRemovePair = {
                            viewModel.removeRecentTranslationPair(it)
                            refreshRecent()
                        },
                    )
                }

                if (showTargetDialog) {
                    LanguageSearchDialog(
                        languages = models,
                        selected = targetModel,
                        onSelect = { model ->
                            val newPair = pair.value.copy(target = model?.language ?: "")
                            pair.value = newPair
                            viewModel.setTranslationPair(extensionId, newPair.source, newPair.target)
                            showTargetDialog = false
                        },
                        onDismiss = { showTargetDialog = false },
                        favoriteLanguages = favoriteLanguages,
                        onToggleFavorite = {
                            viewModel.toggleFavoriteLanguage(it)
                            refreshFavorite()
                        },
                        recentPairs = recentPairs,
                        onApplyRecentPair = { s, t ->
                            viewModel.recordRecentTranslationPair(s, t)
                            val newPair = pair.value.copy(source = s, target = t)
                            pair.value = newPair
                            viewModel.setTranslationPair(extensionId, s, t)
                            refreshRecent()
                            showTargetDialog = false
                        },
                        onRemovePair = {
                            viewModel.removeRecentTranslationPair(it)
                            refreshRecent()
                        },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = {
                            val swapped = TranslationLangPair(source = pair.value.target, target = pair.value.source)
                            pair.value = swapped
                            viewModel.setTranslationPair(extensionId, swapped.source, swapped.target)
                        },
                        enabled = pair.value.isComplete,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(my.noveldokusha.strings.R.string.plugin_translation_swap_languages),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(my.noveldokusha.strings.R.string.plugin_translation_swap_languages))
                    }
                }

                // ── Fallback-превью: глобальная пара, когда у плагина пусто ──
                if (!pair.value.isComplete) {
                    val globalSource = viewModel.globalTranslationSource
                    val globalTarget = viewModel.globalTranslationTarget
                    if (globalSource.isNotBlank() && globalTarget.isNotBlank()) {
                        Text(
                            text = "${globalSource} → ${globalTarget}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider()

                // ── Область перевода (STANDARD / FULL) ──
                Text(
                    text = stringResource(my.noveldokusha.strings.R.string.plugin_translation_scope_standard),
                    style = MaterialTheme.typography.titleSmall,
                )
                ScopeOption(
                    selected = scope.value == my.noveldokusha.core.appPreferences.AppPreferences.TRANSLATION_SCOPE_STANDARD,
                    label = stringResource(my.noveldokusha.strings.R.string.plugin_translation_scope_standard),
                    description = stringResource(my.noveldokusha.strings.R.string.plugin_translation_scope_standard_description),
                    onClick = { scope.value = my.noveldokusha.core.appPreferences.AppPreferences.TRANSLATION_SCOPE_STANDARD; viewModel.setTranslationScope(extensionId, my.noveldokusha.core.appPreferences.AppPreferences.TRANSLATION_SCOPE_STANDARD) },
                )
                ScopeOption(
                    selected = scope.value == my.noveldokusha.core.appPreferences.AppPreferences.TRANSLATION_SCOPE_FULL,
                    label = stringResource(my.noveldokusha.strings.R.string.plugin_translation_scope_full),
                    description = stringResource(my.noveldokusha.strings.R.string.plugin_translation_scope_full_description),
                    onClick = { scope.value = my.noveldokusha.core.appPreferences.AppPreferences.TRANSLATION_SCOPE_FULL; viewModel.setTranslationScope(extensionId, my.noveldokusha.core.appPreferences.AppPreferences.TRANSLATION_SCOPE_FULL) },
                )

                // ── Промпт (только Gemini/OpenAI) ──
                if (safeProvider == "GEMINI" || safeProvider == "OPENAI") {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPromptEditor = !showPromptEditor }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(my.noveldokusha.strings.R.string.plugin_translation_prompt_label),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (showPromptEditor) "▾" else "▸",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(visible = showPromptEditor) {
                        var promptText by remember(prompt.value) { mutableStateOf(prompt.value) }
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            placeholder = {
                                Text(
                                    stringResource(my.noveldokusha.strings.R.string.plugin_translation_prompt_hint),
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
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    viewModel.setTranslationPrompt(extensionId, promptText)
                                    prompt.value = promptText
                                    showPromptEditor = false
                                },
                            ) {
                                Text(stringResource(my.noveldokusha.strings.R.string.save))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(my.noveldokusha.strings.R.string.close))
            }
        },
    )
}

/** Провайдеры перевода: ключ → строковый ресурс метки. */
private val PROVIDERS = listOf(
    "GOOGLE_PA" to my.noveldokusha.strings.R.string.provider_google_pa,
    "GOOGLE_FREE" to my.noveldokusha.strings.R.string.provider_google_free,
    "GEMINI" to my.noveldokusha.strings.R.string.provider_gemini,
    "OPENAI" to my.noveldokusha.strings.R.string.provider_openai,
)

@Composable
private fun ScopeOption(
    selected: Boolean,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
