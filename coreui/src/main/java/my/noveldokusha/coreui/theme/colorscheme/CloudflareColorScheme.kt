package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object CloudflareColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFF38020),
        onPrimary = Color(0xFF2A1800),
        primaryContainer = Color(0xFF6A3500),
        onPrimaryContainer = Color(0xFFFFE0C0),
        inversePrimary = Color(0xFFD6BAFF),
        secondary = Color(0xFFF5A623),
        onSecondary = Color(0xFF2A1800),
        secondaryContainer = Color(0xFF332A20),
        onSecondaryContainer = Color(0xFFF0D8C0),
        tertiary = Color(0xFFA0A8B0),
        onTertiary = Color(0xFF1B1B22),
        tertiaryContainer = Color(0xFF2C3038),
        onTertiaryContainer = Color(0xFFD0D8E0),
        background = Color(0xFF1B1B22),
        onBackground = Color(0xFFEFF2F5),
        surface = Color(0xFF1B1B22),
        onSurface = Color(0xFFEFF2F5),
        surfaceVariant = Color(0xFF3F3F46),
        onSurfaceVariant = Color(0xFFD0CDD4),
        surfaceTint = Color(0xFFF38020),
        inverseSurface = Color(0xFFF3EFF4),
        inverseOnSurface = Color(0xFF313033),
        outline = Color(0xFF8A7060),
        outlineVariant = Color(0xFF3A3028),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF303038),
        surfaceDim = Color(0xFF141418),
        surfaceContainerLowest = Color(0xFF141418),
        surfaceContainerLow = Color(0xFF1B1B22),
        surfaceContainer = Color(0xFF222230),
        surfaceContainerHigh = Color(0xFF28283A),
        surfaceContainerHighest = Color(0xFF303045),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFF38020),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFCCA0),
        onPrimaryContainer = Color(0xFF2A0E00),
        inversePrimary = Color(0xFFD6BAFF),
        secondary = Color(0xFFD4691A),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF0E4D8),
        onSecondaryContainer = Color(0xFF3A1800),
        tertiary = Color(0xFF6B7280),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0E4EA),
        onTertiaryContainer = Color(0xFF1B2028),
        background = Color(0xFFEFF2F5),
        onBackground = Color(0xFF1B1B22),
        surface = Color(0xFFEFF2F5),
        onSurface = Color(0xFF1B1B22),
        surfaceVariant = Color(0xFFB9B0CC),
        onSurfaceVariant = Color(0xFF49454E),
        surfaceTint = Color(0xFFF38020),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF3EFF4),
        outline = Color(0xFF8A7060),
        outlineVariant = Color(0xFFC8C0B8),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF5F8FB),
        surfaceDim = Color(0xFFD8DCE0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F8FB),
        surfaceContainer = Color(0xFFEFF2F5),
        surfaceContainerHigh = Color(0xFFE5E8EC),
        surfaceContainerHighest = Color(0xFFD8DCE0),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF1B1B22)
    override val readerBackgroundColorLight: Color = Color(0xFFEFF2F5)
    override val readerSelectionColorDark: Color = Color(0x40F38020)
    override val readerSelectionColorLight: Color = Color(0x40F38020)
}
