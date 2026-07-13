package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R
import my.noveldokusha.settings.RegexCleanupSettingsScreen
import my.noveldokusha.settings.RegexCleanupSettingsViewModel

@Composable
fun SettingsRegexCleanup(
    onNavigateToRegexCleanup: () -> Unit
) {
    Column {
        Text(
            text = stringResource(id = R.string.regex_cleanup),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = colorAccent()
        )
        // Navigate to Regex Cleanup Settings
        SlimListItem(
            modifier = Modifier
                .clickable { onNavigateToRegexCleanup() },
            headlineContent = {
                Text(text = stringResource(id = R.string.regex_cleanup_manage_rules))
            },
            supportingContent = {
                Text(
                    text = stringResource(id = R.string.regex_cleanup_manage_description),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.FindReplace,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}