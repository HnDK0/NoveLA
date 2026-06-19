package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R

@Composable
internal fun SettingsData(
    databaseSize: String,
    imagesFolderSize: String,
    chapterCacheSize: String,
    isCleaningDatabase: Boolean,
    isCleaningImages: Boolean,
    isCleaningChapterCache: Boolean,
    onCleanDatabase: () -> Unit,
    onCleanImageFolder: () -> Unit,
    onCleanChapterCache: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(id = R.string.data),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = colorAccent()
        )
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.clean_database))
            },
            supportingContent = {
                Column {
                    Text(text = stringResource(id = R.string.size) + " " + databaseSize)
                }
            },
            leadingContent = {
                if (isCleaningDatabase) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.DataArray, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            modifier = Modifier.clickable(enabled = !isCleaningDatabase) { onCleanDatabase() }
        )
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.clean_images_folder))
            },
            supportingContent = {
                Column {
                    Text(text = stringResource(id = R.string.size) + " " + imagesFolderSize)
                }
            },
            leadingContent = {
                if (isCleaningImages) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            modifier = Modifier.clickable(enabled = !isCleaningImages) { onCleanImageFolder() }
        )
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.clean_chapter_cache))
            },
            supportingContent = {
                Column {
                    Text(text = stringResource(id = R.string.size) + " " + chapterCacheSize)
                }
            },
            leadingContent = {
                if (isCleaningChapterCache) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            modifier = Modifier.clickable(enabled = !isCleaningChapterCache) { onCleanChapterCache() }
        )
    }
}