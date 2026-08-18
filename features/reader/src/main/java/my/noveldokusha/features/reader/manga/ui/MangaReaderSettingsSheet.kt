package my.noveldokusha.features.reader.manga.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.features.reader.manga.setting.MangaNavigationMode
import my.noveldokusha.features.reader.manga.setting.MangaReadingMode
import my.noveldokusha.reader.R
import kotlinx.coroutines.launch

/** Режимы цветового фильтра: значение -> label (tachiyomisy ColorFilterMode). */
private val colorFilterModes = listOf(
    0 to R.string.manga_color_filter_mode_normal,
    1 to R.string.manga_color_filter_mode_multiply,
    2 to R.string.manga_color_filter_mode_screen,
    3 to R.string.manga_color_filter_mode_overlay,
    4 to R.string.manga_color_filter_mode_lighten,
    5 to R.string.manga_color_filter_mode_darken,
)

/**
 * Настройки манга/манхва-читалки — порт tachiyomisy ReaderSettingsDialog:
 * три вкладки (Reading mode / General / Custom filter) в TabRow +
 * HorizontalPager со свайпом между ними.
 *
 * Вкладки зеркалят SY ReadingModePage / GeneralSettingsPage / ColorFilterPage.
 * Фона читалки нет (тема NoveLA); авто-скролл вынесен на страницу читалки
 * (панель снизу, только лента), в настройках его больше нет.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaReaderSettingsSheet(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
    onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    fun selectPage(page: Int) {
        scope.launch { pagerState.scrollToPage(page) }
    }

    BasicAlertDialog(onDismissRequest = onClose) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 900.dp)
                .fillMaxHeight(0.75f)
                // Буг2: контент не перекрывается нав-баром в edge-to-edge режиме.
                .navigationBarsPadding(),
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
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { selectPage(0) },
                        text = { Text(stringResource(R.string.manga_reader_reading_mode)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { selectPage(1) },
                        text = { Text(stringResource(R.string.manga_reader_general)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { selectPage(2) },
                        text = { Text(stringResource(R.string.manga_color_filter_custom)) },
                    )
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    when (page) {
                        0 -> ReadingModePage(settings, actions)
                        1 -> GeneralPage(settings, actions)
                        else -> ColorFilterPage(settings, actions)
                    }
                }
            }
        }
    }
}

/** Вкладка "Reading mode" — порт SY ReadingModePage. */
@Composable
private fun ReadingModePage(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingsPage {
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

        val isWebtoon = settings.readingMode == MangaReadingMode.WEBTOON
        if (isWebtoon) {
            // Лента: навигация/инверсия/отступ/переходы — те же опции
            // управления, что и у пейджера (заголовок не меняется).
            ChipSettingItem(
                titleRes = R.string.manga_reader_controls,
                chips = MangaNavigationMode.entries.map { mode ->
                    chip(
                        labelRes = mode.labelRes,
                        selected = mode == settings.navModeWebtoon,
                        onClick = { actions.setNavModeWebtoon(mode) },
                    )
                },
                singleLine = true,
            )
            SwitchSettingItem(
                titleRes = R.string.manga_reader_tapping_inverted,
                checked = settings.tappingInverted,
                onCheckedChange = actions::setTappingInverted,
            )
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
            SwitchSettingItem(
                titleRes = R.string.manga_reader_transitions_webtoon,
                checked = settings.transitionsWebtoon,
                onCheckedChange = actions::setTransitionsWebtoon,
            )
        } else {
            // Пейджер: навигация/инверсия/переходы.
            ChipSettingItem(
                titleRes = R.string.manga_reader_controls,
                chips = MangaNavigationMode.entries.map { mode ->
                    chip(
                        labelRes = mode.labelRes,
                        selected = mode == settings.navModePager,
                        onClick = { actions.setNavModePager(mode) },
                    )
                },
                singleLine = true,
            )
            SwitchSettingItem(
                titleRes = R.string.manga_reader_tapping_inverted,
                checked = settings.tappingInverted,
                onCheckedChange = actions::setTappingInverted,
            )
            SwitchSettingItem(
                titleRes = R.string.manga_reader_transitions_pager,
                checked = settings.transitionsPager,
                onCheckedChange = actions::setTransitionsPager,
            )
        }
    }
}

/** Вкладка "General" — порт SY GeneralSettingsPage (без темы читалки). */
@Composable
private fun GeneralPage(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingsPage {
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
        SwitchSettingItem(
            titleRes = R.string.manga_long_tap,
            checked = settings.longTap,
            onCheckedChange = actions::setLongTap,
        )
        SwitchSettingItem(
            titleRes = R.string.manga_reader_download_on_open,
            checked = settings.downloadOnOpen,
            onCheckedChange = actions::setDownloadOnOpen,
        )

        // Автопрокрутка убрана из настроек: управляется на странице читалки
        // (панель внизу, связана с Play/Pause тулбара, только для ленты).
    }
}

/** Вкладка "Custom filter" — точный порт SY ColorFilterPage:
 * custom brightness → color filter (R,G,B,A слайдеры + режимы) → grayscale →
 * inverted colors. */
@Composable
private fun ColorFilterPage(
    settings: MangaReaderSettingsState,
    actions: MangaReaderSettingsActions,
) {
    SettingsPage {
        SwitchSettingItem(
            titleRes = R.string.manga_custom_brightness,
            checked = settings.customBrightness,
            onCheckedChange = actions::setCustomBrightness,
        )
        if (settings.customBrightness) {
            RgbaSliderItem(
                titleRes = R.string.manga_custom_brightness,
                value = settings.customBrightnessValue.toFloat(),
                valueRange = -75f..100f,
                onValueChange = { v -> actions.setCustomBrightnessValue(v.toInt()) },
            )
        }
        SwitchSettingItem(
            titleRes = R.string.manga_custom_color_filter,
            checked = settings.colorFilterEnabled,
            onCheckedChange = actions::setColorFilterEnabled,
        )
        if (settings.colorFilterEnabled) {
            RgbaSliderItem(
                titleRes = R.string.manga_color_filter_red,
                value = ((settings.colorFilterValue ushr 16) and 0xFF).toFloat(),
                onValueChange = { v -> actions.setColorFilterValue(withChannel(settings.colorFilterValue, 16, v)) },
            )
            RgbaSliderItem(
                titleRes = R.string.manga_color_filter_green,
                value = ((settings.colorFilterValue ushr 8) and 0xFF).toFloat(),
                onValueChange = { v -> actions.setColorFilterValue(withChannel(settings.colorFilterValue, 8, v)) },
            )
            RgbaSliderItem(
                titleRes = R.string.manga_color_filter_blue,
                value = (settings.colorFilterValue and 0xFF).toFloat(),
                onValueChange = { v -> actions.setColorFilterValue(withChannel(settings.colorFilterValue, 0, v)) },
            )
            RgbaSliderItem(
                titleRes = R.string.manga_color_filter_alpha,
                value = ((settings.colorFilterValue ushr 24) and 0xFF).toFloat(),
                onValueChange = { v -> actions.setColorFilterValue(withChannel(settings.colorFilterValue, 24, v)) },
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
}

/** Подменяет один байт ARGB-значения (новое значение — 0..255). */
private fun withChannel(argb: Int, shift: Int, newValue: Float): Int {
    val channel = newValue.toInt().coerceIn(0, 255)
    return (argb and (0xFF shl shift).inv()) or (channel shl shift)
}

/** Скроллируемая колонка вкладки с отступами внизу. */
@Composable
private fun SettingsPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        content()
    }
}

// ── Вспомогательные компоненты ──────────────────────────────────────────────

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
): SettingChip = SettingChip(selected, onClick) {
    // Одна строка всегда: длинные подписи («Свайпы и тапы») не переносятся,
    // а обрезаются многоточием (в singleLine-ряду чипы скроллятся).
    Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingChipRow(
    chips: List<SettingChip>,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    if (singleLine) {
        // Одна строка с пролистыванием: чипы естественной ширины (без
        // weight/fillMaxWidth — иначе длинные подписи переносятся на 2 строки),
        // ряд скроллится по горизонтали, если не влезает.
        Row(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { option ->
                FilterChip(
                    selected = option.selected,
                    onClick = option.onClick,
                    label = option.label,
                )
            }
        }
    } else {
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
}

/** Заголовок + ряд chips. */
@Composable
private fun ChipSettingItem(
    titleRes: Int,
    chips: List<SettingChip>,
    singleLine: Boolean = false,
) {
    SlimListItem(
        headlineContent = { Text(stringResource(titleRes)) },
    )
    SettingChipRow(chips = chips, singleLine = singleLine)
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

@Composable
private fun RgbaSliderItem(
    @StringRes titleRes: Int,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..255f,
    onValueChange: (Float) -> Unit,
) {
    SlimListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.padding(end = 16.dp),
            )
        },
    )
}
