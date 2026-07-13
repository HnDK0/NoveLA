package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import my.noveldokusha.coreui.components.MySlider
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.features.reader.tools.FontsLoader
import my.noveldokusha.features.reader.ui.ReaderScreenState
import my.noveldokusha.reader.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StyleSettingDialog(
    state: ReaderScreenState.Settings.StyleSettingsData,
    onTextSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onTextFontChange: (String) -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
    onAppThemeChange: (AppTheme) -> Unit,
) {
    val context = LocalContext.current
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
    ) {
        // Text size
        var currentTextSize by remember { mutableFloatStateOf(state.textSize.value) }
        MySlider(
            value = currentTextSize,
            valueRange = 8f..32f,
            onValueChange = {
                currentTextSize = it
                onTextSizeChange(currentTextSize)
            },
            text = stringResource(R.string.text_size) + ": %.2f".format(currentTextSize),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Line height
        var currentLineHeight by remember { mutableFloatStateOf(state.lineHeight.value) }
        MySlider(
            value = currentLineHeight,
            valueRange = 1.0f..2.5f,
            onValueChange = {
                currentLineHeight = it
                onLineHeightChange(currentLineHeight)
            },
            text = stringResource(R.string.line_height) + ": %.2f".format(currentLineHeight),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Paragraph spacing
        var currentParagraphSpacing by remember { mutableFloatStateOf(state.paragraphSpacing.value) }
        MySlider(
            value = currentParagraphSpacing,
            valueRange = 0f..40f,
            onValueChange = {
                currentParagraphSpacing = it
                onParagraphSpacingChange(currentParagraphSpacing)
            },
            text = stringResource(R.string.paragraph_spacing) + ": %.0f dp".format(currentParagraphSpacing),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Text font — clickable card-style selector
        Box {
            var showFontsDropdown by rememberSaveable { mutableStateOf(false) }
            val fontLoader = remember(context) { FontsLoader(context) }
            var rowSize by remember { mutableStateOf(Size.Zero) }

            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clickable { showFontsDropdown = !showFontsDropdown }
                    .onGloballyPositioned { rowSize = it.size.toSize() },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.TextFields,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.textFont.value,
                        fontFamily = fontLoader.getFontFamily(state.textFont.value),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = showFontsDropdown,
                onDismissRequest = { showFontsDropdown = false },
                offset = DpOffset(0.dp, 10.dp),
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .width(with(LocalDensity.current) { rowSize.width.toDp() })
            ) {
                FontsLoader.availableFonts.forEach { item ->
                    DropdownMenuItem(
                        onClick = { onTextFontChange(item) },
                        text = {
                            Text(
                                text = item,
                                fontFamily = fontLoader.getFontFamily(item),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }
        }

        // Dark mode chips (compact)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Icon(
                Icons.Outlined.ColorLens,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        FlowRow(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DarkMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == state.currentDarkMode.value,
                    onClick = { onDarkModeChange(mode) },
                    label = { Text(text = stringResource(id = mode.titleRes)) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (mode) {
                                DarkMode.SYSTEM -> Icons.Outlined.BrightnessMedium
                                DarkMode.LIGHT -> Icons.Outlined.LightMode
                                DarkMode.DARK -> Icons.Outlined.DarkMode
                                DarkMode.BLACK -> Icons.Outlined.Nightlight
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 30.dp),
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }

        // Color scheme chips (compact LazyRow)
        Spacer(Modifier.padding(top = 4.dp))
        Text(
            text = "Цветовая схема",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(AppTheme.entries) { theme ->
                CompactThemePreviewChip(
                    theme = theme,
                    isSelected = theme == state.currentAppTheme.value,
                    onClick = { onAppThemeChange(theme) },
                )
            }
        }
        Spacer(Modifier.padding(bottom = 4.dp))
    }
}

@Composable
private fun CompactThemePreviewChip(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = if (theme.isMonet) MaterialTheme.colorScheme.primary
                      else Color(getThemeAccentColor(theme))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .width(44.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(id = theme.titleRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun getThemeAccentColor(theme: AppTheme): Long = when (theme) {
    AppTheme.DEFAULT -> 0L // unused, handled via isMonet
    AppTheme.TACHIYOMI -> 0xFF0088FF
    AppTheme.GREEN_APPLE -> 0xFF188140
    AppTheme.LAVENDER -> 0xFFA177FF
    AppTheme.MIDNIGHT_DUSK -> 0xFFF02475
    AppTheme.STRAWBERRY_DAIQUIRI -> 0xFFED4A65
    AppTheme.TAKO -> 0xFFF3B375
    AppTheme.TEALTURQUOISE -> 0xFF40E0D0
    AppTheme.TIDAL_WAVE -> 0xFF5ed4fc
    AppTheme.YOTSUBA -> 0xFFAE3200
    AppTheme.MONOCHROME -> 0xFF888888
    AppTheme.CATPPUCCIN -> 0xFFCBA6F7
    AppTheme.NORD -> 0xFF88C0D0
    AppTheme.YINYANG -> 0xFF000000
    AppTheme.CLOUDFLARE -> 0xFFF38020
    AppTheme.COTTONCANDY -> 0xFFFFCBCB
    AppTheme.DOOM -> 0xFFFF0000
    AppTheme.MATRIX -> 0xFF00FF00
    AppTheme.MOCHA -> 0xFFBF9270
    AppTheme.SAPPHIRE -> 0xFF1E88E5
}