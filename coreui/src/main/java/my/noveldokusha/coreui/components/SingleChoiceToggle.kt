package my.noveldokusha.coreui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.InternalTheme

@Composable
fun SingleChoiceToggle(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    icon: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null // null to prevent double-clicking
        )
        icon?.let {
            it()
        }
        Text(
            text = text,
            style = textStyle,
            modifier = Modifier.padding(start = if (icon != null) 8.dp else 16.dp)
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewView() {
    InternalTheme {
        androidx.compose.foundation.layout.Column {
            SingleChoiceToggle(
                text = "Selected option",
                selected = true,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
            SingleChoiceToggle(
                text = "Unselected option",
                selected = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
