package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object CatppuccinColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFCBA6F7),
        onPrimary = Color(0xFF11111B),
        primaryContainer = Color(0xFF4A2080),
        onPrimaryContainer = Color(0xFFE8D0FF),
        secondary = Color(0xFF89B4FA),
        onSecondary = Color(0xFF001E4A),
        secondaryContainer = Color(0xFF2A2A3E),
        onSecondaryContainer = Color(0xFFD0D8F0),
        tertiary = Color(0xFFA6E3A1),
        onTertiary = Color(0xFF0A2E0A),
        tertiaryContainer = Color(0xFF1E3A1E),
        onTertiaryContainer = Color(0xFFC2F0BC),
        error = Color(0xFFF38BA8),
        onError = Color(0xFF11111B),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF181825),
        onBackground = Color(0xFFCDD6F4),
        surface = Color(0xFF181825),
        onSurface = Color(0xFFCDD6F4),
        surfaceVariant = Color(0xFF1E1E2E),
        onSurfaceVariant = Color(0xFFCDD6F4),
        outline = Color(0xFFCBA6F7),
        outlineVariant = Color(0xFF585B70),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFEFF1F5),
        inverseOnSurface = Color(0xFF4C4F69),
        inversePrimary = Color(0xFF8839EF),
        surfaceDim = Color(0xFF181825),
        surfaceBright = Color(0xFF313244),
        surfaceContainerLowest = Color(0xFF11111B),
        surfaceContainerLow = Color(0xFF161622),
        surfaceContainer = Color(0xFF1E1E2E),
        surfaceContainerHigh = Color(0xFF252535),
        surfaceContainerHighest = Color(0xFF313244),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF8839EF),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD0B0F0),
        onPrimaryContainer = Color(0xFF1A0038),
        secondary = Color(0xFF1E66F5),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E0EC),
        onSecondaryContainer = Color(0xFF1A1A3A),
        tertiary = Color(0xFF40A02B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC2F0BC),
        onTertiaryContainer = Color(0xFF0A2A00),
        error = Color(0xFFD20F39),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFE6E9EF),
        onBackground = Color(0xFF4C4F69),
        surface = Color(0xFFE6E9EF),
        onSurface = Color(0xFF4C4F69),
        surfaceVariant = Color(0xFFEFF1F5),
        onSurfaceVariant = Color(0xFF4C4F69),
        outline = Color(0xFF8839EF),
        outlineVariant = Color(0xFFACB0BE),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF1E1E2E),
        inverseOnSurface = Color(0xFFCDD6F4),
        inversePrimary = Color(0xFFCBA6F7),
        surfaceDim = Color(0xFFD8DBE5),
        surfaceBright = Color(0xFFEFF1F5),
        surfaceContainerLowest = Color(0xFFF5F7FF),
        surfaceContainerLow = Color(0xFFEFF1F5),
        surfaceContainer = Color(0xFFE8EAF0),
        surfaceContainerHigh = Color(0xFFE0E3EA),
        surfaceContainerHighest = Color(0xFFCDD0DA),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF181825)
    override val readerBackgroundColorLight: Color = Color(0xFFE6E9EF)
    override val readerSelectionColorDark: Color = Color(0x40CBA6F7)
    override val readerSelectionColorLight: Color = Color(0x408839EF)
}
