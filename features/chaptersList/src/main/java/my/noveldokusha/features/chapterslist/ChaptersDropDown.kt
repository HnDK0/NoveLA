package my.noveldokusha.features.chapterslist

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import my.noveldokusha.chapterslist.R
import my.noveldokusha.strings.R as StringsR

@Composable
internal fun ChaptersDropDown(
    isLocalSource: Boolean,
    openInBrowser: () -> Unit,
    onSearchBookInDatabase: () -> Unit,
    onResumeReading: () -> Unit,
    onChangeCover: () -> Unit,
    onDownloadAllChapters: () -> Unit,
    onMigrateBook: () -> Unit = {},
    onDeleteTranslations: () -> Unit = {},
    onFixBook: () -> Unit = {},
) {
    if (!isLocalSource) DropdownMenuItem(
        onClick = openInBrowser,
        text = {
            Text(text = stringResource(id = R.string.open_in_browser))
        },
        leadingIcon = {
            Icon(Icons.Outlined.Public, stringResource(R.string.open_in_browser))
        }
    )
    DropdownMenuItem(
        onClick = onSearchBookInDatabase,
        text = {
            Text(text = stringResource(R.string.find_in_database))
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                stringResource(R.string.find_in_database)
            )
        }
    )
    DropdownMenuItem(
        onClick = onResumeReading,
        text = {
            Text(text = stringResource(id = R.string.resume_reading))
        },
        leadingIcon = {
            Icon(
                Icons.Filled.PlayArrow,
                stringResource(R.string.resume_reading),
            )
        }
    )
    DropdownMenuItem(
        onClick = onChangeCover,
        text = {
            Text(text = stringResource(R.string.change_cover))
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Image,
                stringResource(R.string.change_cover),
            )
        }
    )
    if (!isLocalSource) DropdownMenuItem(
        onClick = onDownloadAllChapters,
        text = {
            Text(text = stringResource(R.string.download_all_chapters))
        },
        leadingIcon = {
            Icon(
                Icons.Filled.CloudDownload,
                stringResource(R.string.download_all_chapters),
            )
        }
    )
    DropdownMenuItem(
        onClick = onMigrateBook,
        text = {
            Text(text = stringResource(StringsR.string.migration_tab))
        },
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.AltRoute,
                contentDescription = stringResource(StringsR.string.migration_tab),
            )
        }
    )
    DropdownMenuItem(
        onClick = onDeleteTranslations,
        text = {
            Text(text = stringResource(R.string.delete_book_translations))
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.delete_book_translations),
            )
        }
    )
    if (!isLocalSource) DropdownMenuItem(
        onClick = onFixBook,
        text = {
            Text(text = stringResource(R.string.book_fix))
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Build,
                contentDescription = stringResource(R.string.book_fix),
            )
        }
    )
}