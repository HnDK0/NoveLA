package my.noveldokusha.scraper

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Возвращает отображаемое имя источника.
 * Lua источники имеют name != null и nameStrId == 0.
 * Статические источники используют nameStrId.
 *
 * Использование в Compose:
 *   Text(text = source.displayName())
 *
 * Использование вне Compose (ViewModel, etc.):
 *   val name = source.name ?: context.getString(source.nameStrId)
 */
@Composable
fun SourceInterface.displayName(): String {
    return name ?: if (nameStrId != 0) stringResource(nameStrId) else id
}