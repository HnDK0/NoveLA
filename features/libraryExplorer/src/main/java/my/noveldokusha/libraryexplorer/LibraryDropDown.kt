package my.noveldokusha.libraryexplorer

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import my.noveldokusha.tooling.epub_importer.onDoImportEPUB

@Composable
internal fun LibraryDropDown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelectionModeToggle: () -> Unit = {},
    onSortClick: (() -> Unit)? = null
) {
    if (expanded) {
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    // Позиционируем dropdown в правом верхнем углу экрана
                    return IntOffset(
                        x = windowSize.width - popupContentSize.width - 16,
                        y = 80 // Под TopAppBar
                    )
                }
            },
            onDismissRequest = onDismiss
        ) {
            Column {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.filter))
                    },
                    text = { Text(stringResource(R.string.filter)) },
                    onClick = {
                        onDismiss()
                        onSortClick?.invoke()
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.Checklist, stringResource(R.string.select_books))
                    },
                    text = { Text(stringResource(R.string.select_books)) },
                    onClick = {
                        onDismiss()
                        onSelectionModeToggle()
                    }
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(Icons.Filled.FileOpen, stringResource(id = R.string.import_epub))
                    },
                    text = { Text(stringResource(id = R.string.import_epub)) },
                    onClick = onDoImportEPUB()
                )
            }
        }
    }
}
