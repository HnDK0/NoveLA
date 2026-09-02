package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.noveldokusha.core.appPreferences.TranslationLangPair
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

            // ── Подсказка состояния: переключатель и пара независимы. ──
            when {
                !state.translationGlobalMode.value && !state.enable.value ->
                    Text(
                        text = stringResource(R.string.translation_toggle_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSearchDialog(
    languages: List<TranslationModelState>,
    selected: TranslationModelState?,
    onSelect: (TranslationModelState?) -> Unit,
    onDismiss: () -> Unit,
    favoriteLanguages: List<String>,
    onToggleFavorite: (String) -> Unit,
    recentPairs: List<TranslationLangPair>,
    onApplyRecentPair: (String, String) -> Unit,
    onRemovePair: (TranslationLangPair) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }

    // Стабильные Map-индексы: O(1) поиск по коду языка вместо O(n·m) сканов.
    // Ключ remember — сами Map (стабильные), а не пересоздаваемый список favoriteItems.
    val itemByCode = remember(languages) { languages.associateBy { it.language } }
    // Ключ — СОДЕРЖИМОЕ favoriteLanguages (toList), а не сам SnapshotStateList:
    // у SnapshotStateList equals — по ссылке, поэтому remember по нему никогда не
    // пересчитывался, и переключение избранного не двигало язык между секциями
    // до переоткрытия диалога.
    val favoriteItems = remember(languages, favoriteLanguages.toList()) {
        favoriteLanguages.mapNotNull { itemByCode[it] }
    }
    val favoriteCodes = remember(favoriteItems) { favoriteItems.mapTo(mutableSetOf()) { it.language } }

    // Основной список: исключаем избранные, чтобы не дублировать их в прокрутке.
    // Ключ remember — только query: Map-индексы стабильны, кэш не сбрасывается на рекомпозиции.
    val filtered = remember(query, itemByCode, favoriteCodes) {
        val base = if (query.isBlank()) languages
        else languages.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.language.contains(query, ignoreCase = true)
        }
        base.filter { item -> item.language !in favoriteCodes }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.language_search),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
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

            // Последние пары перевода (порядок — от свежих к старым).
            val displayPairs = recentPairs

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // ── Последние пары перевода (всегда видна) ─────────────
                item(key = "pairs_header") {
                    Text(
                        text = stringResource(R.string.language_recent_pairs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                if (displayPairs.isNotEmpty()) {
                    items(displayPairs, key = { "pair_${it.source}_${it.target}" }) { pair ->
                        val sourceItem = itemByCode[pair.source]
                        val targetItem = itemByCode[pair.target]
                        // Показываем чип только если оба кода есть в списке языков.
                        if (sourceItem != null && targetItem != null) {
                            val available = sourceItem.available && targetItem.available
                            val label = "${sourceItem.displayName} → ${targetItem.displayName}"
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = available) {
                                            onApplyRecentPair(pair.source, pair.target)
                                            onDismiss()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        color = if (available)
                                            MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                    // Удаление пары из списка последних.
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable { onRemovePair(pair) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item(key = "pairs_empty_hint") {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.language_recent_pairs_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // ── Разделитель между парами и избранными языками ──────
                item(key = "divider_1") {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                // ── Избранные языки (закреплены сверху) ────────────────
                if (favoriteItems.isNotEmpty()) {
                    item(key = "fav_header") {
                        Text(
                            text = stringResource(R.string.language_favorites),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(favoriteItems, key = { "fav_${it.language}" }) { item ->
                        val isSelected = selected?.language == item.language
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    else Modifier
                                )
                                .clickable(enabled = item.available) {
                                    onSelect(if (isSelected) null else item)
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                text = "${item.displayName} (${item.language})",
                                style = MaterialTheme.typography.bodyMedium,
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
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { onToggleFavorite(item.language) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (favoriteLanguages.contains(item.language)) Icons.Filled.Star
                                    else Icons.Outlined.StarBorder,
                                    contentDescription = stringResource(
                                        if (favoriteLanguages.contains(item.language))
                                            R.string.language_favorite_remove
                                        else R.string.language_favorite_add
                                    ),
                                    tint = if (favoriteLanguages.contains(item.language))
                                        colorAccent()
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // ── Разделитель между избранными и основным списком ────
                item(key = "divider_2") {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                // ── Основной список ────────────────────────────────────
                item(key = "all_languages_header") {
                    Text(
                        text = stringResource(R.string.language_all_languages),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(filtered, key = { "lang_${it.language}" }) { item ->
                    val isSelected = selected?.language == item.language
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                else Modifier
                            )
                            .clickable(enabled = item.available) {
                                onSelect(if (isSelected) null else item)
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = "${item.displayName} (${item.language})",
                            style = MaterialTheme.typography.bodyMedium,
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
                        // Звезда для добавления/удаления языка из избранного.
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onToggleFavorite(item.language) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (favoriteLanguages.contains(item.language)) Icons.Filled.Star
                                else Icons.Outlined.StarBorder,
                                contentDescription = stringResource(
                                    if (favoriteLanguages.contains(item.language))
                                        R.string.language_favorite_remove
                                    else R.string.language_favorite_add
                                ),
                                tint = if (favoriteLanguages.contains(item.language))
                                    colorAccent()
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "no_results") {
                        Text(
                            text = stringResource(R.string.language_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
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
