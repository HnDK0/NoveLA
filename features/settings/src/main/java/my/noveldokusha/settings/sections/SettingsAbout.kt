package my.noveldokusha.settings.sections

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R

private const val REPO_URL = "https://github.com/HnDK0/NoveLA"
private const val ISSUES_URL = "https://github.com/HnDK0/NoveLA/issues"

@Composable
internal fun SettingsAbout(
    appVersion: String,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied_to_clipboard)

    Text(
        text = stringResource(R.string.about),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.textPadding(),
        color = colorAccent()
    )

    SlimListItem(
        headlineContent = {
            Text(text = stringResource(R.string.version_format, appVersion))
        },
        leadingContent = {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                scope.launch {
                    val clipData = ClipData.newPlainText("version", appVersion)
                    clipboard.setClipEntry(clipData.toClipEntry())
                }
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            }
        )
    )

    SlimListItem(
        headlineContent = {
            Text(text = stringResource(R.string.github_repository))
        },
        leadingContent = {
            Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
        }
    )

    SlimListItem(
        headlineContent = {
            Text(text = stringResource(R.string.report_issue))
        },
        supportingContent = {
            Text(text = stringResource(R.string.report_issue_description))
        },
        leadingContent = {
            Icon(Icons.Outlined.NewReleases, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)))
        }
    )
}
