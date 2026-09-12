package my.noveldokusha.sourceexplorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.scraper.ActiveFilters
import my.noveldokusha.scraper.FilterHistoryEntry
import my.noveldokusha.scraper.FilterPreset
import my.noveldokusha.scraper.LuaFilter


enum class TriStateValue { NEUTRAL, INCLUDED, EXCLUDED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterBottomSheet(
    filterList: List<LuaFilter>,
    activeFilters: ActiveFilters,
    onApply: (ActiveFilters) -> Unit,
    onDismiss: () -> Unit,
    presets: List<FilterPreset>,
    textHistory: List<FilterHistoryEntry>,
    onPresetSave: (String) -> Unit,
    onPresetLoad: (FilterPreset) -> Unit,
    onPresetDelete: (FilterPreset) -> Unit,
    onTextHistoryAdd: (filterKey: String, value: String) -> Unit,
    onTextHistoryRemove: (filterKey: String, value: String) -> Unit = { _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState()

    val sortValues    = remember { mutableStateMapOf<String, String>() }
    val sortAscending = remember { mutableStateMapOf<String, Boolean>() }
    val selectValues  = remember { mutableStateMapOf<String, String>() }
    val checkboxState = remember { mutableStateMapOf<String, Set<String>>() }
    val tristateState = remember { mutableStateMapOf<String, Map<String, TriStateValue>>() }
    val switchValues  = remember { mutableStateMapOf<String, Boolean>() }
    val textValues    = remember { mutableStateMapOf<String, String>() }
    val tagInputValues = remember { mutableStateMapOf<String, MutableList<String>>() }

    LaunchedEffect(filterList) {
        filterList.forEach { filter ->
            when (filter) {
                is LuaFilter.Sort -> {
                    sortValues[filter.key] = activeFilters.sortValues[filter.key] ?: filter.defaultValue
                    sortAscending[filter.key] = activeFilters.sortAscending[filter.key] ?: filter.defaultAscending
                }
                is LuaFilter.Select ->
                    selectValues[filter.key] = activeFilters.selectValues[filter.key] ?: filter.defaultValue
                is LuaFilter.CheckboxGroup ->
                    checkboxState[filter.key] = activeFilters.checkboxIncluded[filter.key]?.toSet() ?: emptySet()
                is LuaFilter.TriState -> {
                    val inc = activeFilters.triIncluded[filter.key] ?: emptyList()
                    val exc = activeFilters.triExcluded[filter.key] ?: emptyList()
                    tristateState[filter.key] = filter.options.associate { opt ->
                        opt.value to when {
                            opt.value in inc -> TriStateValue.INCLUDED
                            opt.value in exc -> TriStateValue.EXCLUDED
                            else             -> TriStateValue.NEUTRAL
                        }
                    }
                }
                is LuaFilter.Switch ->
                    switchValues[filter.key] = activeFilters.switchValues[filter.key] ?: filter.defaultValue
                is LuaFilter.TextInput ->
                    textValues[filter.key] = activeFilters.textValues[filter.key] ?: filter.defaultValue
                is LuaFilter.TagInput ->
                    tagInputValues[filter.key] = (activeFilters.tagInputValues[filter.key] ?: emptyList()).toMutableList()
            }
        }
    }

    fun resetToDefaults() {
        filterList.forEach { filter ->
            when (filter) {
                is LuaFilter.Sort -> {
                    sortValues[filter.key] = filter.defaultValue
                    sortAscending[filter.key] = filter.defaultAscending
                }
                is LuaFilter.Select -> selectValues[filter.key] = filter.defaultValue
                is LuaFilter.CheckboxGroup -> checkboxState[filter.key] = emptySet()
                is LuaFilter.TriState -> tristateState[filter.key] = filter.options.associate { it.value to TriStateValue.NEUTRAL }
                is LuaFilter.Switch -> switchValues[filter.key] = filter.defaultValue
                is LuaFilter.TextInput -> textValues[filter.key] = filter.defaultValue
                is LuaFilter.TagInput -> tagInputValues[filter.key] = mutableListOf()
            }
        }
    }

    fun buildActiveFilters(): ActiveFilters {
        val triInc = mutableMapOf<String, List<String>>()
        val triExc = mutableMapOf<String, List<String>>()
        tristateState.forEach { (key, map) ->
            triInc[key] = map.filter { it.value == TriStateValue.INCLUDED }.keys.toList()
            triExc[key] = map.filter { it.value == TriStateValue.EXCLUDED }.keys.toList()
        }
        return ActiveFilters(
            sortValues       = sortValues.toMap(),
            sortAscending    = sortAscending.toMap(),
            selectValues     = selectValues.toMap(),
            checkboxIncluded = checkboxState.mapValues { it.value.toList() },
            triIncluded      = triInc,
            triExcluded      = triExc,
            switchValues     = switchValues.toMap(),
            textValues       = textValues.toMap(),
            tagInputValues   = tagInputValues.mapValues { it.value.toList() },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, tint = colorAccent())
                    Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { resetToDefaults() }) {
                    Text(stringResource(R.string.filters_reset), color = colorAccent())
                }
            }

            PresetSection(
                presets = presets,
                onLoad = onPresetLoad,
                onDelete = onPresetDelete,
                onSave = onPresetSave,
            )

            HorizontalDivider()

            // Filter sections
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (filterList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colorAccent())
                    }
                }
                filterList.forEach { filter ->
                    when (filter) {
                        is LuaFilter.Sort -> SortSection(filter, sortValues[filter.key] ?: filter.defaultValue, sortAscending[filter.key] ?: filter.defaultAscending, { sortValues[filter.key] = it }, { sortAscending[filter.key] = it })
                        is LuaFilter.Select -> SelectSection(filter, selectValues[filter.key] ?: filter.defaultValue) { selectValues[filter.key] = it }
                        is LuaFilter.CheckboxGroup -> CheckboxSection(filter, checkboxState[filter.key] ?: emptySet()) { value ->
                            val current = checkboxState[filter.key] ?: emptySet()
                            checkboxState[filter.key] = if (!filter.multiselect) {
                                if (value in current) emptySet() else setOf(value)
                            } else {
                                if (value in current) current - value else current + value
                            }
                        }
                        is LuaFilter.TriState -> TriStateSection(filter, tristateState[filter.key] ?: emptyMap()) { value ->
                            val current = tristateState[filter.key] ?: emptyMap()
                            tristateState[filter.key] = current + (value to when (current[value] ?: TriStateValue.NEUTRAL) {
                                TriStateValue.NEUTRAL  -> TriStateValue.INCLUDED
                                TriStateValue.INCLUDED -> TriStateValue.EXCLUDED
                                TriStateValue.EXCLUDED -> TriStateValue.NEUTRAL
                            })
                        }
                        is LuaFilter.Switch -> SwitchSection(filter, switchValues[filter.key] ?: filter.defaultValue) { switchValues[filter.key] = it }
                        is LuaFilter.TextInput -> TextSection(
                            filter = filter,
                            value = textValues[filter.key] ?: filter.defaultValue,
                            onChange = { textValues[filter.key] = it },
                            history = textHistory,
                            onHistoryAdd = { onTextHistoryAdd(filter.key, it) },
                            onHistoryRemove = { onTextHistoryRemove(filter.key, it) }
                        )
                        is LuaFilter.TagInput -> TagInputSection(
                            filter = filter,
                            selectedValues = tagInputValues[filter.key] ?: emptyList(),
                            onToggle = { value ->
                                val current = tagInputValues[filter.key]?.toMutableList() ?: mutableListOf()
                                if (value in current) current.remove(value) else current.add(value)
                                tagInputValues[filter.key] = current
                            },
                            onAddCustom = { value ->
                                val current = tagInputValues[filter.key]?.toMutableList() ?: mutableListOf()
                                if (value !in current) { current.add(value); tagInputValues[filter.key] = current }
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            // Apply button
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        onApply(buildActiveFilters())
                        textValues.forEach { (key, v) -> if (v.isNotBlank()) onTextHistoryAdd(key, v) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorAccent()),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.filters_apply), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FilterSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = colorAccent(), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

@Composable
private fun SortSection(filter: LuaFilter.Sort, value: String, ascending: Boolean, onValueChange: (String) -> Unit, onAscendingChange: (Boolean) -> Unit) {
    Column {
        FilterSectionHeader(filter.label)
        // Опции по алфавиту: Lua-плагины могут отдавать их в произвольном порядке
        filter.options.sortedBy { it.label.lowercase() }.forEach { opt ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = value == opt.value, onClick = { if (value == opt.value) onAscendingChange(!ascending) else onValueChange(opt.value) }, colors = RadioButtonDefaults.colors(selectedColor = colorAccent()))
                Text(opt.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 4.dp), color = if (value == opt.value) colorAccent() else MaterialTheme.colorScheme.onSurface)
                if (value == opt.value) {
                    IconButton(onClick = { onAscendingChange(!ascending) }) {
                        Icon(if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, contentDescription = null, tint = colorAccent())
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectSection(filter: LuaFilter.Select, value: String, onChange: (String) -> Unit) {
    Column {
        FilterSectionHeader(filter.label)
        if (filter.options.size <= 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                filter.options.sortedBy { it.label.lowercase() }.forEach { opt ->
                    SelectOptionButton(opt.label, value == opt.value, { onChange(opt.value) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            var expanded by remember { mutableStateOf(false) }
            val currentLabel = filter.options.find { it.value == value }?.label ?: value
            Column {
                OutlinedButton(onClick = { expanded = !expanded }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.5.dp, colorAccent().copy(alpha = 0.6f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth()) {
                    Text(currentLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null, modifier = Modifier.size(18.dp), tint = colorAccent())
                }
                if (expanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, colorAccent().copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                            filter.options.sortedBy { it.label.lowercase() }.forEach { opt ->
                                val selected = value == opt.value
                                Surface(
                                    onClick = { onChange(opt.value); expanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(opt.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) colorAccent() else MaterialTheme.colorScheme.onSurface)
                                        if (selected) Icon(Icons.Filled.Check, null, tint = colorAccent(), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckboxSection(filter: LuaFilter.CheckboxGroup, included: Set<String>, onToggle: (String) -> Unit) {
    Column {
        FilterSectionHeader(filter.label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filter.options.sortedBy { it.label.lowercase() }.forEach { opt ->
                val isIncluded = opt.value in included
                FilterChip(
                    selected = isIncluded,
                    onClick = { onToggle(opt.value) },
                    label = { Text(opt.label) },
                    leadingIcon = if (isIncluded) ({ Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorAccent().copy(alpha = 0.15f), selectedLabelColor = colorAccent(), selectedLeadingIconColor = colorAccent()),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isIncluded, selectedBorderColor = colorAccent(), selectedBorderWidth = 1.5.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriStateSection(filter: LuaFilter.TriState, stateMap: Map<String, TriStateValue>, onToggle: (String) -> Unit) {
    Column {
        FilterSectionHeader(filter.label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filter.options.sortedBy { it.label.lowercase() }.forEach { opt ->
                TriStateChip(opt.label, stateMap[opt.value] ?: TriStateValue.NEUTRAL) { onToggle(opt.value) }
            }
        }
    }
}

@Composable
private fun SwitchSection(filter: LuaFilter.Switch, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(filter.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = colorAccent(), checkedTrackColor = colorAccent().copy(alpha = 0.4f)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSection(
    filter: LuaFilter.TextInput,
    value: String,
    onChange: (String) -> Unit,
    history: List<FilterHistoryEntry> = emptyList(),
    onHistoryAdd: (String) -> Unit = {},
    onHistoryRemove: (String) -> Unit = {},
) {
    // ponytail: Feeder-style autocomplete — Column + onFocusChanged + AnimatedVisibility.
    // No Popup, no DropdownMenu, no window focus stealing.
    val filteredHistory by remember(value, history, filter.key) {
        derivedStateOf {
            history
                .mapNotNull { entry -> entry.filters.textValues[filter.key]?.let { v -> v to entry.timestamp } }
                .sortedByDescending { it.second }
                .distinctBy { it.first }
                .filter { (histValue, _) -> histValue != value }
                .take(10)
        }
    }

    var showSuggestions by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.onFocusChanged { showSuggestions = it.hasFocus }
    ) {
        FilterSectionHeader(filter.label)
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent(), focusedLabelColor = colorAccent(), cursorColor = colorAccent()),
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(Icons.Filled.Close, "Clear")
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        AnimatedVisibility(visible = showSuggestions && filteredHistory.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.heightIn(max = 200.dp)) {
                    filteredHistory.forEach { (histValue, _) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onChange(histValue); onHistoryAdd(histValue); showSuggestions = false },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                histValue,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            IconButton(onClick = { onHistoryRemove(histValue) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagInputSection(
    filter: LuaFilter.TagInput,
    selectedValues: List<String>,
    onToggle: (String) -> Unit,
    onAddCustom: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val filteredOptions = filter.options.filter {
        it.label.contains(text, ignoreCase = true) && it.value !in selectedValues
    }
    val showAddCustom = filter.allowCustom && text.isNotBlank() &&
        filter.options.none { it.label.equals(text, ignoreCase = true) } &&
        text !in selectedValues

    Column {
        FilterSectionHeader(filter.label)
        if (selectedValues.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                selectedValues.forEach { value ->
                    val label = filter.options.find { it.value == value }?.label ?: value
                    FilterChip(
                        selected = true,
                        onClick = { onToggle(value) },
                        label = { Text(label) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colorAccent().copy(alpha = 0.15f), selectedLabelColor = colorAccent()),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = true, selectedBorderColor = colorAccent(), selectedBorderWidth = 1.5.dp)
                    )
                }
            }
        }
        // ponytail: Inline suggestions instead of DropdownMenu — avoids popup window stealing
        // IME focus inside ModalBottomSheet (Android WindowManager bug with nested popups).
        val showSuggestions = expanded && (filteredOptions.isNotEmpty() || showAddCustom)
        Column(
            modifier = Modifier.onFocusChanged { if (!it.hasFocus) expanded = false }
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; expanded = true },
                placeholder = { Text("Type to search...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent(), cursorColor = colorAccent()),
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = ""; expanded = false }) {
                            Icon(Icons.Filled.Close, "Clear")
                        }
                    }
                }
            )
            if (showSuggestions) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, colorAccent().copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        filteredOptions.take(10).forEach { opt ->
                            Surface(
                                onClick = { onToggle(opt.value); text = ""; expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(opt.label)
                                }
                            }
                        }
                        if (showAddCustom) {
                            Surface(
                                onClick = { onAddCustom(text); text = ""; expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Add: $text", color = colorAccent())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSection(
    presets: List<FilterPreset>,
    onLoad: (FilterPreset) -> Unit,
    onDelete: (FilterPreset) -> Unit,
    onSave: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (presets.isNotEmpty()) {
            Column {
                OutlinedButton(onClick = { expanded = !expanded }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.5.dp, colorAccent().copy(alpha = 0.6f))) {
                    Text("Presets", style = MaterialTheme.typography.bodyMedium)
                }
                if (expanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp),
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, colorAccent().copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                            presets.forEach { preset ->
                                Surface(
                                    onClick = { onLoad(preset); expanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Filled.Check, null, tint = colorAccent(), modifier = Modifier.size(18.dp))
                                            Text(preset.name)
                                        }
                                        IconButton(onClick = { onDelete(preset) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Filled.Close, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { showSaveDialog = true }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.5.dp, colorAccent().copy(alpha = 0.6f))) {
            Text("Save filters", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save filter preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { if (it.length <= 50) presetName = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { if (presetName.isNotBlank()) { onSave(presetName.trim()); presetName = ""; showSaveDialog = false } }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetName = ""; showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TriStateChip(label: String, state: TriStateValue, onClick: () -> Unit) {
    val containerColor = when (state) {
        TriStateValue.NEUTRAL  -> Color.Transparent
        TriStateValue.INCLUDED -> colorAccent().copy(alpha = 0.15f)
        TriStateValue.EXCLUDED -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    }
    val labelColor = when (state) {
        TriStateValue.NEUTRAL  -> MaterialTheme.colorScheme.onSurface
        TriStateValue.INCLUDED -> colorAccent()
        TriStateValue.EXCLUDED -> MaterialTheme.colorScheme.error
    }
    val borderColor = when (state) {
        TriStateValue.NEUTRAL  -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        TriStateValue.INCLUDED -> colorAccent()
        TriStateValue.EXCLUDED -> MaterialTheme.colorScheme.error
    }
    val icon: @Composable (() -> Unit)? = when (state) {
        TriStateValue.NEUTRAL  -> null
        TriStateValue.INCLUDED -> ({ Icon(Icons.Filled.Check, null, tint = colorAccent(), modifier = Modifier.size(FilterChipDefaults.IconSize)) })
        TriStateValue.EXCLUDED -> ({ Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(FilterChipDefaults.IconSize)) })
    }

    FilterChip(
        selected = state != TriStateValue.NEUTRAL,
        onClick = onClick,
        label = { Text(label, color = labelColor) },
        leadingIcon = icon,
        colors = FilterChipDefaults.filterChipColors(containerColor = containerColor, selectedContainerColor = containerColor, labelColor = labelColor, selectedLabelColor = labelColor),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = state != TriStateValue.NEUTRAL, borderColor = borderColor, selectedBorderColor = borderColor, selectedBorderWidth = 1.5.dp)
    )
}

@Composable
private fun SelectOptionButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colorAccent().copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(width = if (selected) 1.5.dp else 1.dp, color = if (selected) colorAccent() else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) colorAccent() else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}