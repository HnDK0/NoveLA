package my.noveldokusha.features.reader.ui.settingDialogs

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.components.PillSlider
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.features.reader.tools.FontsLoader
import my.noveldokusha.features.reader.ui.ReaderScreenState
import my.noveldokusha.reader.R

@Composable
internal fun StyleSettingDialog(
    state: ReaderScreenState.Settings.StyleSettingsData,
    onTextSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onTextFontChange: (String) -> Unit,
    onTextColorChanged: (String) -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
    onAppThemeChange: (AppTheme) -> Unit,
) {
    val context = LocalContext.current
    val fontLoader = remember(context) { FontsLoader(context) }
    val systemFontsSet = remember { FontsLoader.systemFonts.toSet() }
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intent = result.data ?: return@rememberLauncherForActivityResult
        val uris = buildList {
            intent.clipData?.let { clip ->
                repeat(clip.itemCount) { i -> add(clip.getItemAt(i).uri) }
            }
            intent.data?.let { add(it) }
        }
        uris.forEach { uri ->
            scope.launch {
                fontLoader.importFont(uri).onFailure { e ->
                    Toast.makeText(
                        context,
                        e.message ?: "Font import failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
    ) {
        // Text size
        var currentTextSize by remember { mutableFloatStateOf(state.textSize.value) }
        PillSlider(
            label = stringResource(R.string.text_size),
            value = currentTextSize,
            valueRange = 8f..32f,
            onValueChange = {
                currentTextSize = it
                onTextSizeChange(currentTextSize)
            },
            valueText = "%.2f".format(currentTextSize),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Line height
        var currentLineHeight by remember { mutableFloatStateOf(state.lineHeight.value) }
        PillSlider(
            label = stringResource(R.string.line_height),
            value = currentLineHeight,
            valueRange = 1.0f..2.5f,
            onValueChange = {
                currentLineHeight = it
                onLineHeightChange(currentLineHeight)
            },
            valueText = "%.2f".format(currentLineHeight),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Paragraph spacing
        var currentParagraphSpacing by remember { mutableFloatStateOf(state.paragraphSpacing.value) }
        PillSlider(
            label = stringResource(R.string.paragraph_spacing),
            value = currentParagraphSpacing,
            valueRange = 0f..40f,
            onValueChange = {
                currentParagraphSpacing = it
                onParagraphSpacingChange(currentParagraphSpacing)
            },
            valueText = "%.0f dp".format(currentParagraphSpacing),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Letter spacing
        var currentLetterSpacing by remember { mutableFloatStateOf(state.letterSpacing.value) }
        PillSlider(
            label = stringResource(R.string.letter_spacing),
            value = currentLetterSpacing,
            valueRange = 0f..0.3f,
            onValueChange = {
                currentLetterSpacing = it
                onLetterSpacingChange(currentLetterSpacing)
            },
            valueText = "%.2f em".format(currentLetterSpacing),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // Шрифт — кастомный Popup вместо DropdownMenu: позиция вычисляется
        // один раз по якорным координатам из calculatePosition, без пересчёта
        // above/below на каждом кадре (корневая причина фрика в M3 DropdownMenu).
        Box {
            var showFontsDropdown by rememberSaveable { mutableStateOf(false) }

            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .clickable { showFontsDropdown = !showFontsDropdown },
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
                        text = FontsLoader.displayName(state.textFont.value, systemFontsSet),
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

            if (showFontsDropdown) {
                Popup(
                    onDismissRequest = { showFontsDropdown = false },
                    properties = PopupProperties(focusable = true),
                    popupPositionProvider = object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize
                        ): IntOffset {
                            // Центрируем меню по горизонтали относительно якоря,
                            // clamp не даёт меню выйти за пределы экрана.
                            val desiredX = anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2
                            val x = desiredX.coerceIn(
                                0,
                                (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                            )
                            val y = (anchorBounds.top - popupContentSize.height)
                                .coerceAtLeast(0)
                            return IntOffset(x, y)
                        }
                    }
                ) {
                    Surface(
                        shape = MenuDefaults.shape,
                        color = MenuDefaults.containerColor,
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 48.dp — стандартный Material IconButton tap-target.
                            // Системные пункты резервируют пустой Spacer той же ширины,
                            // чтобы текстовый слот был одинаковым у всех пунктов.
                            val trailingIconSlot: @Composable (() -> Unit) = {
                                Spacer(Modifier.size(48.dp))
                            }

                            val allFonts = FontsLoader.availableFonts.value
                            val importedFonts = allFonts.filter { FontsLoader.isImported(it) }
                            // Кастомный Row вместо DropdownMenuItem: Box(weight=1f,
                            // contentAlignment=Center) центрирует текст по всей ширине
                            // пункта, а не по ширине текстового слота внутри Row M3
                            // (где trailingIcon сужает доступную область).
                            allFonts.filterNot { FontsLoader.isImported(it) }.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable { onTextFontChange(item) }
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = FontsLoader.displayName(item, systemFontsSet),
                                            fontFamily = fontLoader.getFontFamily(item),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    trailingIconSlot()
                                }
                            }
                            if (importedFonts.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.reader_fonts_imported),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 4.dp
                                    )
                                )
                                importedFonts.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .clickable { onTextFontChange(item) }
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = FontsLoader.displayName(
                                                    item,
                                                    systemFontsSet
                                                ),
                                                fontFamily = fontLoader.getFontFamily(item),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(onClick = {
                                            val deleted = fontLoader.deleteFont(item)
                                            if (deleted && state.textFont.value == item) {
                                                onTextFontChange("serif")
                                            }
                                        }) {
                                            Icon(
                                                Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.reader_delete_font),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Import font — system document picker, multi-select
        TextButton(
            onClick = {
                val intent = ActivityResultContracts.OpenDocument()
                    .createIntent(context, FONT_MIME_TYPES)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                importLauncher.launch(intent)
            },
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                Icons.Outlined.FileUpload,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.reader_import_font))
        }

        // Text color
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Icon(
                Icons.Outlined.FormatColorFill,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.reader_text_color),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("") + TextColorPalette) { hex ->
                if (hex.isEmpty()) {
                    val autoSelected = state.textColor.value.isEmpty()
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (autoSelected) 3.dp else 0.dp,
                                color = if (autoSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onTextColorChanged("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (autoSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val selected = state.textColor.value.uppercase() == hex
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(safeParseColor(hex)))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onTextColorChanged(hex) }
                    )
                }
            }
        }
        val parsedColor = safeParseColor(state.textColor.value)
        val red = ((parsedColor shr 16) and 0xFF).toFloat()
        val green = ((parsedColor shr 8) and 0xFF).toFloat()
        val blue = (parsedColor and 0xFF).toFloat()
        PillSlider(
            label = stringResource(R.string.manga_color_filter_red),
            value = red,
            valueRange = 0f..255f,
            onValueChange = { r ->
                val argb = 0xFF000000.toInt() or (r.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
                onTextColorChanged("%08X".format(argb))
            },
            valueText = "%.0f".format(red),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        PillSlider(
            label = stringResource(R.string.manga_color_filter_green),
            value = green,
            valueRange = 0f..255f,
            onValueChange = { g ->
                val argb = 0xFF000000.toInt() or (red.toInt() shl 16) or (g.toInt() shl 8) or blue.toInt()
                onTextColorChanged("%08X".format(argb))
            },
            valueText = "%.0f".format(green),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
        PillSlider(
            label = stringResource(R.string.manga_color_filter_blue),
            value = blue,
            valueRange = 0f..255f,
            onValueChange = { b ->
                val argb = 0xFF000000.toInt() or (red.toInt() shl 16) or (green.toInt() shl 8) or b.toInt()
                onTextColorChanged("%08X".format(argb))
            },
            valueText = "%.0f".format(blue),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

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
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(DarkMode.entries) { mode ->
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Icon(
                Icons.Outlined.Palette,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.color_scheme),
                style = MaterialTheme.typography.labelMedium,
            )
        }
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

private val TextColorPalette = listOf(
    "FF000000", "FF212121", "FF616161", "FFFFFFFF",
    "FF3B2F1E", "FF1A237E", "FF0D47A1", "FF1B5E20",
    "FFB71C1C", "FF4A148C", "FF004D40", "FFBF360C",
)

// MIME-типы, под которыми Android отдаёт TTF/OTF-шрифты в системном пикере.
private val FONT_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/vnd.ms-opentype",
    "application/font-sfnt",
)

private fun safeParseColor(hex: String): Int =
    runCatching { android.graphics.Color.parseColor("#$hex") }.getOrElse { 0xFF333333.toInt() }