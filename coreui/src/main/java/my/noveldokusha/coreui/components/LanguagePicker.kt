package my.noveldokusha.coreui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.strings.R
import my.noveldokusha.text_translator.domain.TranslationModelState

/**
 * Кнопка выбора языка: иконка + метка. Активное состояние подсвечивается акцентным цветом.
 */
@Composable
fun LanguageButton(
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

/**
 * Диалог выбора языка: поиск, последние пары перевода, избранные и полный список языков.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSearchDialog(
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
