package my.noveldokusha.features.reader.manga.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaReaderOrientation
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.features.reader.manga.setting.MangaTappingInvertMode
import my.noveldokusha.features.reader.manga.setting.MangaZoomStart
import my.noveldokusha.reader.R

/** Палитра для цветового фильтра (ARGB-значения, 0 = прозрачный/выкл). */
private val colorFilterPalette: List<Int> = listOf(
    0x00000000,
    0xFFFFFFFF.toInt(),
    0xFFFFF176.toInt(),
    0xFFFFB74D.toInt(),
    0xFFE57373.toInt(),
    0xFFF06292.toInt(),
    0xFFBA68C8.toInt(),
    0xFF9575CD.toInt(),
    0xFF7986CB.toInt(),
    0xFF64B5F6.toInt(),
    0xFF4FC3F7.toInt(),
    0xFF4DB6AC.toInt(),
    0xFF81C784.toInt(),
    0xFFAED581.toInt(),
    0xFFFF8A65.toInt(),
    0xFFA1887F.toInt(),
)

/** Скорость анимации зума двойным тапом: значение -> label. */
private val doubleTapSpeeds = listOf(
    300 to R.string.manga_double_tap_speed_fast,
    500 to R.string.manga_double_tap_speed_medium,
    750 to R.string.manga_double_tap_speed_slow,
)

/** Режимы цветового фильтра: значение -> label. */
private val colorFilterModes = listOf(
    0 to R.string.manga_color_filter_mode_normal,
    1 to R.string.manga_color_filter_mode_multiply,
    2 to R.string.manga_color_filter_mode_screen,
    3 to R.string.manga_color_filter_mode_overlay,
    4 to R.string.manga_color_filter_mode_lighten,
    5 to R.string.manga_color_filter_mode_darken,
)

/** Фоны читалки: значение -> label (0=WHITE, 1=BLACK, 2=GRAY, 3=AUTO). */
private val readerThemes = listOf(
    0 to R.string.manga_theme_white,
    1 to R.string.manga_theme_black,
    2 to R.string.manga_theme_gray,
    3 to R.string.manga_theme_auto,
)

/**
 * Настройки манга/манхва-читалки (страничные главы).
 * Зеркалит паттерн MoreSettingDialog: BasicAlertDialog + ElevatedCard,
 * секции с заголовками, Column с verticalScroll.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MangaReaderSettingsSheet(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
    onClose: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onClose) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp, top = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.manga_reader_settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    GeneralSection(settings, actions)
                    ControlsSection(settings, actions)
                    WebtoonSection(settings, actions)
                    ColorFilterSection(settings, actions)
                    AutoScrollSection(settings, actions)
                }
            }
        }
    }
}

@Composable
private fun GeneralSection(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingSectionTitle(R.string.manga_reader_general)

    ChipSettingItem(
        titleRes = R.string.manga_reader_reading_mode,
        chips = MangaReadingMode.entries.map { mode ->
            chip(
                labelRes = mode.labelRes,
                selected = mode == settings.readingMode,
                onClick = { actions.setReadingMode(mode) },
            )
        },
    )
    ChipSettingItem(
        titleRes = R.string.manga_reader_orientation,
        chips = MangaReaderOrientation.entries.map { orientation ->
            chip(
                labelRes = orientation.labelRes,
                selected = orientation == settings.orientation,
                onClick = { actions.setOrientation(orientation) },
            )
        },
    )
    SwitchSettingItem(
        titleRes = R.string.manga_reader_transitions_pager,
        checked = settings.transitionsPager,
        onCheckedChange = actions::setTransitionsPager,
    )
    SwitchSettingItem(
        titleRes = R.string.manga_reader_transitions_webtoon,
        checked = settings.transitionsWebtoon,
        onCheckedChange = actions::setTransitionsWebtoon,
    )
    SwitchSettingItem(
        titleRes = R.string.manga_reader_show_page_number,
        checked = settings.showPageNumber,
        onCheckedChange = actions::setShowPageNumber,
    )
    SwitchSettingItem(
        titleRes = R.string.manga_reader_keep_screen_on,
        checked = settings.keepScreenOn,
        onCheckedChange = actions::setKeepScreenOn,
    )
    SwitchSettingItem(
        titleRes = R.string.manga_reader_fullscreen,
        checked = settings.fullscreen,
        onCheckedChange = actions::setFullscreen,
    )
    ChipSettingItem(
        titleRes = R.string.manga_reader_theme,
        chips = readerThemes.map { (value, labelRes) ->
            chip(
                labelRes = labelRes,
                selected = value == settings.readerTheme,
                onClick = { actions.setReaderTheme(value) },
            )
        },
    )
}

@Composable
private fun ControlsSection(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingSectionTitle(R.string.manga_reader_controls)

    // Тап-зоны: единый ряд, применяется сразу к пейджеру и webtoon.
    SettingChipRow(
        chips = MangaNavigationMode.entries.map { mode ->
            chip(
                labelRes = mode.labelRes,
                selected = mode == settings.navModePager,
                onClick = {
                    actions.setNavModePager(mode)
                    actions.setNavModeWebtoon(mode)
                },
            )
        },
    )
    ChipSettingItem(
        titleRes = R.string.manga_zoom_start,
        chips = MangaZoomStart.entries.map { zoomStart ->
            chip(
                labelRes = zoomStart.labelRes,
                selected = zoomStart == settings.zoomStart,
                onClick = { actions.setZoomStart(zoomStart) },
            )
        },
    )
    ChipSettingItem(
        titleRes = R.string.manga_double_tap_speed,
        chips = doubleTapSpeeds.map { (value, labelRes) ->
            chip(
                labelRes = labelRes,
                selected = value == settings.doubleTapAnimSpeed,
                onClick = { actions.setDoubleTapAnimSpeed(value) },
            )
        },
    )
    ChipSettingItem(
        titleRes = R.string.manga_tapping_inverted,
        chips = listOf(
            MangaTappingInvertMode.NONE to R.string.manga_tapping_inverted_none,
            MangaTappingInvertMode.HORIZONTAL to R.string.manga_tapping_inverted_horizontal,
            MangaTappingInvertMode.VERTICAL to R.string.manga_tapping_inverted_vertical,
            MangaTappingInvertMode.BOTH to R.string.manga_tapping_inverted_both,
        ).map { (mode, labelRes) ->
            chip(
                labelRes = labelRes,
                selected = mode == settings.tappingInvertedPager,
                onClick = {
                    actions.setTappingInvertedPager(mode)
                    actions.setTappingInvertedWebtoon(mode)
                },
            )
        },
    )
    SwitchSettingItem(
        titleRes = R.string.manga_volume_keys,
        checked = settings.volumeKeys,
        onCheckedChange = actions::setVolumeKeys,
    )
    SwitchSettingItem(
        titleRes = R.string.manga_volume_keys_inverted,
        checked = settings.volumeKeysInverted,
        onCheckedChange = actions::setVolumeKeysInverted,
    )
    SwitchSettingItem(
        titleRes = R.string.manga_long_tap,
        checked = settings.longTap,
        onCheckedChange = actions::setLongTap,
    )
}

@Composable
private fun WebtoonSection(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingSectionTitle(R.string.manga_reader_webtoon)

    StepperSettingItem(
        titleRes = R.string.manga_webtoon_side_padding,
        valueText = settings.webtoonSidePadding.toString(),
        onDecrease = {
            actions.setWebtoonSidePadding((settings.webtoonSidePadding - 1).coerceAtLeast(0))
        },
        onIncrease = {
            actions.setWebtoonSidePadding((settings.webtoonSidePadding + 1).coerceAtMost(25))
        },
    )
}

@Composable
private fun ColorFilterSection(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingSectionTitle(R.string.manga_reader_color_filter)

    SwitchSettingItem(
        titleRes = R.string.manga_color_filter_enable,
        checked = settings.colorFilterEnabled,
        onCheckedChange = actions::setColorFilterEnabled,
    )
    if (settings.colorFilterEnabled) {
        SlimListItem(
            headlineContent = { Text(stringResource(R.string.manga_color_filter_value)) },
        )
        ColorSwatchRow(
            colors = colorFilterPalette,
            selected = settings.colorFilterValue,
            onSelect = actions::setColorFilterValue,
        )
        ChipSettingItem(
            titleRes = R.string.manga_color_filter_mode,
            chips = colorFilterModes.map { (value, labelRes) ->
                chip(
                    labelRes = labelRes,
                    selected = value == settings.colorFilterMode,
                    onClick = { actions.setColorFilterMode(value) },
                )
            },
        )
    }
    SwitchSettingItem(
        titleRes = R.string.manga_grayscale,
        checked = settings.grayscale,
        onCheckedChange = actions::setGrayscale,
    )
    SwitchSettingItem(
        titleRes = R.string.manga_inverted_colors,
        checked = settings.invertedColors,
        onCheckedChange = actions::setInvertedColors,
    )
}

@Composable
private fun AutoScrollSection(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingSectionTitle(R.string.manga_reader_auto_scroll)

    SwitchSettingItem(
        titleRes = R.string.auto_scroll,
        checked = settings.autoscrollEnabled,
        onCheckedChange = actions::setAutoscrollEnabled,
    )
    if (settings.autoscrollEnabled) {
        val intervalText = if (settings.autoscrollInterval % 1f == 0f) {
            settings.autoscrollInterval.toInt().toString()
        } else {
            "%.1f".format(settings.autoscrollInterval)
        }
        StepperSettingItem(
            titleRes = R.string.auto_scroll_interval,
            valueText = stringResource(R.string.auto_scroll_interval_value, intervalText),
            onDecrease = {
                actions.setAutoscrollInterval((settings.autoscrollInterval - 0.5f).coerceAtLeast(1f))
            },
            onIncrease = {
                actions.setAutoscrollInterval((settings.autoscrollInterval + 0.5f).coerceAtMost(60f))
            },
        )
        SwitchSettingItem(
            titleRes = R.string.auto_scroll_smooth,
            checked = settings.autoscrollSmooth,
            onCheckedChange = actions::setAutoscrollSmooth,
        )
    }
    ChipSettingItem(
        titleRes = R.string.page_prefetch_count,
        chips = listOf(4, 8, 12, 24).map { count ->
            SettingChip(
                selected = count == settings.pagePrefetchCount,
                onClick = { actions.setPagePrefetchCount(count) },
            ) { Text(count.toString()) }
        },
    )
}

// ── Вспомогательные компоненты ──────────────────────────────────────────────

@Composable
private fun SettingSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
    )
}

/** Один chip выбора в ряду. */
private class SettingChip(
    val selected: Boolean,
    val onClick: () -> Unit,
    val label: @Composable () -> Unit,
)

/** Фабрика chip'а с label из строкового ресурса. */
private fun chip(
    @StringRes labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
): SettingChip = SettingChip(selected, onClick) { Text(stringResource(labelRes)) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingChipRow(
    chips: List<SettingChip>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { option ->
            FilterChip(
                selected = option.selected,
                onClick = option.onClick,
                label = option.label,
            )
        }
    }
}

/** Заголовок + ряд chips. */
@Composable
private fun ChipSettingItem(
    titleRes: Int,
    chips: List<SettingChip>,
) {
    SlimListItem(
        headlineContent = { Text(stringResource(titleRes)) },
    )
    SettingChipRow(chips = chips)
}

@Composable
private fun SwitchSettingItem(
    @StringRes titleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SlimListItem(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        headlineContent = { Text(stringResource(titleRes)) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorAccent(),
                    checkedTrackColor = colorAccent().copy(alpha = 0.4f),
                ),
            )
        },
    )
}

@Composable
private fun StepperSettingItem(
    @StringRes titleRes: Int,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    SlimListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease) {
                    Icon(Icons.Filled.Remove, null)
                }
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onIncrease) {
                    Icon(Icons.Filled.Add, null)
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(
    colors: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (argb == 0) MaterialTheme.colorScheme.surfaceVariant
                        else Color(argb)
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(argb) },
            )
        }
    }
}
