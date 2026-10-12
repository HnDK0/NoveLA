package my.noveldokusha.features.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import androidx.compose.material3.rememberModalBottomSheetState

/**
 * Redesign home sheets (prototype sheet system): homepage feed settings
 * (feed choice + contributing sources) and the appearance picker.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverFeedSheet(
    state: DiscoverUiState,
    onDismiss: () -> Unit,
    onSetFeedSource: (HomeFeed, String?) -> Unit,
    onSetVisibleFeed: (HomeFeed, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // Which category's source picker is expanded (one at a time).
    var expandedCategory by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<HomeFeed?>(null)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Home feed & sources",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Every category you set up shows on the homepage at once, each filled with one source's catalog. Pick one source per category — all sources stay searchable and browsable in Finder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp),
            )

            // — Discovery sections: which category rows appear on the homepage —
            Text(
                text = "DISCOVERY SECTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            HomeFeed.entries.forEach { feed ->
                val shown = state.sections.any { it.feed == feed }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetVisibleFeed(feed, !shown) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = feed.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (shown) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (shown) "Shown on the homepage" else "Hidden from the homepage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = shown,
                        onCheckedChange = { onSetVisibleFeed(feed, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // — One source per category (inline radio picker) —
            Text(
                text = "CATEGORY SOURCES",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (state.installedSources.isEmpty()) {
                Text(
                    text = "No book sources installed yet. Open Finder to install Lua extensions from external-sources.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                HomeFeed.entries.forEach { feed ->
                    val chosen = state.sections.firstOrNull { it.feed == feed }?.sourceName
                    val isExpanded = expandedCategory == feed
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedCategory = if (isExpanded) null else feed
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = feed.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = chosen ?: "No source chosen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (chosen != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = if (isExpanded) "Close" else "Choose",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                        if (isExpanded) {
                            SourceRadioPicker(
                                selectedBaseUrl = state.sections
                                    .firstOrNull { it.feed == feed }?.sourceBaseUrl,
                                sources = state.installedSources,
                                onSelect = { baseUrl ->
                                    onSetFeedSource(feed, baseUrl)
                                    expandedCategory = null
                                },
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            Text(
                text = "Catalog settings only — one source per category, all rows visible on the homepage at once. Every source stays searchable and browsable in Finder.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

/** Single-select source list for one homepage category ("None" clears it). */
@Composable
private fun SourceRadioPicker(
    selectedBaseUrl: String?,
    sources: List<FeedSource>,
    onSelect: (String?) -> Unit,
) {
    Column(Modifier.padding(bottom = 10.dp)) {
        SourceRadioRow(
            name = "None",
            detail = "Hide this category's catalog",
            selected = selectedBaseUrl == null,
            onClick = { onSelect(null) },
        )
        sources.forEach { source ->
            SourceRadioRow(
                name = source.name,
                detail = source.language.uppercase().takeIf { it.isNotBlank() } ?: source.baseUrl,
                selected = selectedBaseUrl == source.baseUrl,
                onClick = { onSelect(source.baseUrl) },
            )
        }
    }
}

@Composable
private fun SourceRadioRow(
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                )
                .border(
                    width = if (selected) 0.dp else 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverThemeSheet(
    currentTheme: AppTheme,
    currentDarkMode: DarkMode,
    onDismiss: () -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Dark mode and color scheme, live for the whole app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 5.dp, bottom = 14.dp),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DarkMode.entries.forEach { mode ->
                    val isSelected = mode == currentDarkMode
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.inverseSurface
                        else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.inverseSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .clickable { onSetDarkMode(mode) },
                    ) {
                        Text(
                            text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) MaterialTheme.colorScheme.inverseOnSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Color scheme",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(AppTheme.entries) { theme ->
                    val isSelected = theme == currentTheme
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSetTheme(theme) }
                            .padding(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(themeAccent(theme))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                    else Color.Transparent,
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = themeName(theme),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** Accent dot per theme (mirrors SettingsTheme swatches). */
private fun themeAccent(theme: AppTheme): Color = when (theme) {
    AppTheme.PAPER -> Color(0xFF53604B)
    AppTheme.NIGHT -> Color(0xFFB2BCA8)
    AppTheme.SEPIA -> Color(0xFF6D6952)
    AppTheme.DEFAULT -> Color(0xFF3A5BA0)
    AppTheme.TACHIYOMI -> Color(0xFF0088FF)
    AppTheme.GREEN_APPLE -> Color(0xFF188140)
    AppTheme.LAVENDER -> Color(0xFFA177FF)
    AppTheme.MIDNIGHT_DUSK -> Color(0xFFF02475)
    AppTheme.STRAWBERRY_DAIQUIRI -> Color(0xFFED4A65)
    AppTheme.TAKO -> Color(0xFFF3B375)
    AppTheme.TEALTURQUOISE -> Color(0xFF40E0D0)
    AppTheme.TIDAL_WAVE -> Color(0xFF5ED4FC)
    AppTheme.YOTSUBA -> Color(0xFFAE3200)
    AppTheme.MONOCHROME -> Color(0xFF000000)
    AppTheme.CATPPUCCIN -> Color(0xFFCBA6F7)
    AppTheme.NORD -> Color(0xFF88C0D0)
    AppTheme.YINYANG -> Color(0xFF000000)
    AppTheme.CLOUDFLARE -> Color(0xFFF38020)
    AppTheme.COTTONCANDY -> Color(0xFFFFCBCB)
    AppTheme.DOOM -> Color(0xFFFF0000)
    AppTheme.MATRIX -> Color(0xFF00FF00)
    AppTheme.MOCHA -> Color(0xFFBF9270)
    AppTheme.SAPPHIRE -> Color(0xFF1E88E5)
}

/** Plain theme names (app module has no access to settings-module strings). */
private fun themeName(theme: AppTheme): String = when (theme) {
    AppTheme.PAPER -> "Paper"
    AppTheme.NIGHT -> "Night"
    AppTheme.SEPIA -> "Sepia"
    AppTheme.DEFAULT -> "Monet"
    AppTheme.TACHIYOMI -> "Tachiyomi"
    AppTheme.GREEN_APPLE -> "Green apple"
    AppTheme.LAVENDER -> "Lavender"
    AppTheme.MIDNIGHT_DUSK -> "Midnight dusk"
    AppTheme.STRAWBERRY_DAIQUIRI -> "Strawberry"
    AppTheme.TAKO -> "Tako"
    AppTheme.TEALTURQUOISE -> "Teal"
    AppTheme.TIDAL_WAVE -> "Tidal wave"
    AppTheme.YOTSUBA -> "Yotsuba"
    AppTheme.MONOCHROME -> "Monochrome"
    AppTheme.CATPPUCCIN -> "Catppuccin"
    AppTheme.NORD -> "Nord"
    AppTheme.YINYANG -> "Yin & yang"
    AppTheme.CLOUDFLARE -> "Cloudflare"
    AppTheme.COTTONCANDY -> "Cotton candy"
    AppTheme.DOOM -> "Doom"
    AppTheme.MATRIX -> "Matrix"
    AppTheme.MOCHA -> "Mocha"
    AppTheme.SAPPHIRE -> "Sapphire"
}
