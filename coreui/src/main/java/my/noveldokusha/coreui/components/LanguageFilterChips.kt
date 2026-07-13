package my.noveldokusha.coreui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChipOption(
    val id: String,
    val label: String,
    val count: Int = 0,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageFilterChips(
    selected: Set<String>,
    all: List<ChipOption>,
    onToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        FlowRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val allSelected = selected.isEmpty()

            FilterChip(
                selected = allSelected,
                onClick = onClearAll,
                label = { Text(text = "All") },
            )

            all.forEach { option ->
                FilterChip(
                    selected = option.id in selected,
                    onClick = { onToggle(option.id) },
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}