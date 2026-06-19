package my.noveldokusha.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.core.models.RegexRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexCleanupSettingsScreen(
    viewModel: RegexCleanupSettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState
    val filteredRules = viewModel.filteredRules
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── TopBar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = stringResource(id = R.string.regex_cleanup_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                onClick = { viewModel.onAddRule() },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.heightIn(min = 28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.add_new_rule),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Search Bar ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            placeholder = {
                Text(
                    text = stringResource(id = R.string.search_patterns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(bottom = 8.dp)
        )

        // ── Rules List ─────────────────────────────────────────────────────
        if (filteredRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.no_regex_rules_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                itemsIndexed(
                    items = filteredRules,
                    key = { index, rule -> "rule_${rule.pattern}" }
                ) { index, rule ->
                    RegexRuleRow(
                        rule = rule,
                        isLast = index == filteredRules.lastIndex,
                        onEdit = {
                            val realIndex = state.rules.indexOf(rule)
                            if (realIndex >= 0) viewModel.onEditRule(realIndex)
                        },
                        onDelete = { viewModel.onDeleteRule(rule.pattern) },
                        onToggle = {
                            val realIndex = state.rules.indexOf(rule)
                            if (realIndex >= 0) viewModel.onToggleRule(realIndex)
                        },
                        onMoveUp = {
                            val realIndex = state.rules.indexOf(rule)
                            if (realIndex > 0) viewModel.onMoveRule(realIndex, realIndex - 1)
                        },
                        onMoveDown = {
                            val realIndex = state.rules.indexOf(rule)
                            if (realIndex < state.rules.size - 1) viewModel.onMoveRule(realIndex, realIndex + 1)
                        }
                    )
                }
            }
        }

        // ── Modal Bottom Sheet ─────────────────────────────────────────────
        if (state.isBottomSheetOpen && state.editingRule != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.onDismissBottomSheet() },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                RegexRuleBottomSheetContent(
                    rule = state.editingRule!!,
                    validationError = state.validationError?.let { pattern ->
                        stringResource(id = R.string.invalid_regex_pattern_with, pattern)
                    },
                    previewText = state.previewText.ifEmpty {
                        stringResource(id = R.string.preview_sample_text)
                    },
                    onSave = { pattern, replacement, enabled, desc ->
                        viewModel.onSaveRule(pattern, replacement, enabled, desc)
                    },
                    onPreviewChange = { viewModel.updatePreview(it) },
                    onDismiss = { viewModel.onDismissBottomSheet() }
                )
            }
        }

        // ── Delete Confirmation Dialog ─────────────────────────────────────
        if (state.deleteConfirmationPattern != null) {
            AlertDialog(
                onDismissRequest = { viewModel.onDismissDelete() },
                title = {
                    Text(
                        text = stringResource(id = R.string.delete_rule),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = stringResource(
                            id = R.string.delete_rule_confirmation,
                            state.deleteConfirmationPattern!!
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.onConfirmDelete() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text(stringResource(id = R.string.delete))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.onDismissDelete() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(id = R.string.cancel))
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single Rule Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RegexRuleRow(
    rule: RegexRule,
    isLast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val alphaModifier = if (rule.isEnabled) Modifier else Modifier.alpha(0.35f)

    Row(
        modifier = alphaModifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pattern column
        Text(
            text = rule.pattern,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Enabled/Disabled badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (rule.isEnabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (rule.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = if (rule.isEnabled) stringResource(id = R.string.rule_enabled)
                       else stringResource(id = R.string.rule_disabled),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        // Actions column
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(
                onClick = onMoveUp,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    if (!isLast) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Bottom Sheet Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RegexRuleBottomSheetContent(
    rule: RegexRule,
    validationError: String?,
    previewText: String,
    onSave: (pattern: String, replacement: String, enabled: Boolean, description: String) -> Unit,
    onPreviewChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pattern by remember { mutableStateOf(rule.pattern) }
    var replacement by remember { mutableStateOf(rule.replacement) }
    var description by remember { mutableStateOf(rule.description) }
    var isEnabled by remember { mutableStateOf(rule.isEnabled) }
    var localPreviewText by remember { mutableStateOf(previewText) }
    var isPatternValid by remember { mutableStateOf(true) }
    var showError by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(pattern) {
        isPatternValid = try {
            if (pattern.isNotEmpty()) Regex(pattern)
            true
        } catch (e: Exception) {
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Handle bar
        Surface(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        ) {}

        Spacer(modifier = Modifier.height(12.dp))

        // Title
        Text(
            text = if (rule.pattern.isEmpty()) stringResource(id = R.string.new_rule) else stringResource(id = R.string.edit_rule),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Pattern field
        Text(
            text = stringResource(id = R.string.pattern).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = pattern,
            onValueChange = {
                pattern = it
                showError = false
            },
            placeholder = {
                Text(
                    text = stringResource(id = R.string.enter_regex_pattern),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            isError = showError && pattern.isNotEmpty() && !isPatternValid,
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )
        if (showError && pattern.isNotEmpty() && !isPatternValid) {
            Text(
                text = stringResource(id = R.string.invalid_regex_pattern),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Replacement field
        Text(
            text = stringResource(id = R.string.replacement).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = replacement,
            onValueChange = { replacement = it },
            placeholder = {
                Text(
                    text = stringResource(id = R.string.enter_replacement_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description field
        Text(
            text = stringResource(id = R.string.description).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = {
                Text(
                    text = stringResource(id = R.string.description_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Enable toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(id = R.string.enabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preview
        Text(
            text = stringResource(id = R.string.preview).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = localPreviewText,
            onValueChange = {
                localPreviewText = it
                onPreviewChange(it)
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Preview result card
        Card(
            shape = RoundedCornerShape(7.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = stringResource(id = R.string.preview_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                PreviewText(
                    text = localPreviewText,
                    pattern = pattern,
                    replacement = replacement,
                    isEnabled = isEnabled
                )
            }
        }

        // Validation error
        if (validationError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = validationError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = stringResource(id = R.string.cancel),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    if (pattern.isNotEmpty() && isPatternValid) {
                        onSave(pattern, replacement, isEnabled, description)
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier.weight(2f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = stringResource(id = R.string.save).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview Text with strikethrough — matches reader logic: text.replace(Regex(pattern), replacement)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreviewText(
    text: String,
    pattern: String,
    replacement: String,
    isEnabled: Boolean
) {
    if (!isEnabled || pattern.isEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Compute result using the same logic as the reader: text.replace(Regex(pattern), replacement)
    val resultText = remember(pattern, replacement, text) {
        try {
            text.replace(Regex(pattern), replacement)
        } catch (e: Exception) {
            text
        }
    }

    if (resultText == text) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Pre-compute match ranges outside buildAnnotatedString (no try-catch inside composable)
    val matches = remember(pattern, text) {
        try {
            Regex(pattern).findAll(text).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val annotated = buildAnnotatedString {
        var lastEnd = 0
        for (match in matches) {
            if (match.range.first > lastEnd) {
                append(text.substring(lastEnd, match.range.first))
            }
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.error
                )
            )
            append(match.value)
            pop()
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
