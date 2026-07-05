package my.noveldokusha.settings.sections

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.HighlightDark
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsTheme(
    currentAppTheme: AppTheme,
    currentDarkMode: DarkMode,
    onAppThemeChange: (AppTheme) -> Unit,
    onDarkModeChange: (DarkMode) -> Unit,
) {
    Column {
        Text(
            text = stringResource(id = R.string.theme),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.textPadding(),
            color = colorAccent()
        )

        // Mode chips (System/Light/Dark/Black)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DarkMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == currentDarkMode,
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
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Color schemes section
        Text(
            text = stringResource(id = R.string.color_scheme),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Compact horizontal scrollable theme picker
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(AppTheme.entries) { theme ->
                ThemePreviewChip(
                    theme = theme,
                    isSelected = theme == currentAppTheme,
                    onClick = { onAppThemeChange(theme) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ThemePreviewChip(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = when (theme) {
        AppTheme.DEFAULT -> MaterialTheme.colorScheme.primary
        AppTheme.TACHIYOMI -> HighlightDark
        AppTheme.GREEN_APPLE -> Color(0xFF188140)
        AppTheme.LAVENDER -> Color(0xFFA177FF)
        AppTheme.MIDNIGHT_DUSK -> Color(0xFFF02475)
        AppTheme.STRAWBERRY_DAIQUIRI -> Color(0xFFED4A65)
        AppTheme.TAKO -> Color(0xFFF3B375)
        AppTheme.TEALTURQUOISE -> Color(0xFF40E0D0)
        AppTheme.TIDAL_WAVE -> Color(0xFF5ed4fc)
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .width(52.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor)
                    .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    shape = CircleShape
                )
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(id = theme.titleRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}