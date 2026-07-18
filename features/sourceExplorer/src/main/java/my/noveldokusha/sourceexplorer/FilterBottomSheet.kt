package my.noveldokusha.sourceexplorer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.scraper.ActiveFilters
import my.noveldokusha.scraper.LuaFilter

enum class TriStateValue { NEUTRAL, INCLUDED, EXCLUDED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterBottomSheet(
    filterList: List<LuaFilter>,
    activeFilters: ActiveFilters,
    onApply: (ActiveFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    val sortValues    = remember { mutableStateMapOf<String, String>() }
    val sortAscending = remember { mutableStateMapOf<String, Boolean>() }
    val selectValues  = remember { mutableStateMapOf<String, String>() }
    val checkboxState = remember { mutableStateMapOf<String, Set<String>>() }
    val tristateState = remember { mutableStateMapOf<String, Map<String, TriStateValue>>() }
    val switchValues  = remember { mutableStateMapOf<String, Boolean>() }
    val textValues    = remember { mutableStateMapOf<String, String>() }

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

            HorizontalDivider()

            // Filter sections
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                        is LuaFilter.TextInput -> TextSection(filter, textValues[filter.key] ?: filter.defaultValue) { textValues[filter.key] = it }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            // Apply button
            HorizontalDivider()
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = { onApply(buildActiveFilters()) },
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
        filter.options.forEach { opt ->
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
                filter.options.forEach { opt ->
                    SelectOptionButton(opt.label, value == opt.value, { onChange(opt.value) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            var expanded by remember { mutableStateOf(false) }
            val currentLabel = filter.options.find { it.value == value }?.label ?: value
            Box {
                OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.5.dp, colorAccent().copy(alpha = 0.6f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth()) {
                    Text(currentLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    filter.options.forEach { opt ->
                        val selected = value == opt.value
                        DropdownMenuItem(
                            text = { Text(opt.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) colorAccent() else MaterialTheme.colorScheme.onSurface) },
                            trailingIcon = if (selected) ({ Icon(Icons.Filled.Check, null, tint = colorAccent()) }) else null,
                            onClick = { onChange(opt.value); expanded = false }
                        )
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
            filter.options.forEach { opt ->
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
            filter.options.forEach { opt ->
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

@Composable
private fun TextSection(filter: LuaFilter.TextInput, value: String, onChange: (String) -> Unit) {
    Column {
        FilterSectionHeader(filter.label)
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorAccent(), focusedLabelColor = colorAccent(), cursorColor = colorAccent()))
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