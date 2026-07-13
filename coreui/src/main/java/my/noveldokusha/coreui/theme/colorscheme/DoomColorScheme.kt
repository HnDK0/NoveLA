package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object DoomColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFF0000),
        onPrimary = Color(0xFFFAFAFA),
        primaryContainer = Color(0xFF5C0000),
        onPrimaryContainer = Color(0xFFFFB3B3),
        secondary = Color(0xFFCC2200),
        onSecondary = Color(0xFFFAFAFA),
        secondaryContainer = Color(0xFF4D2222),
        onSecondaryContainer = Color(0xFFFFD0D0),
        tertiary = Color(0xFFBFBFBF),
        onTertiary = Color(0xFF1B1B1B),
        tertiaryContainer = Color(0xFF3D3D3D),
        onTertiaryContainer = Color(0xFFFF8080),
        background = Color(0xFF1B1B1B),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF1B1B1B),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF303030),
        onSurfaceVariant = Color(0xFFD0CCCC),
        surfaceTint = Color(0xFFFF0000),
        inverseSurface = Color(0xFFFAFAFA),
        inverseOnSurface = Color(0xFF313131),
        outline = Color(0xFF8A4040),
        outlineVariant = Color(0xFF3D2020),
        scrim = Color(0xFF000000),
        inversePrimary = Color(0xFF6D0D0B),
        surfaceBright = Color(0xFF333333),
        surfaceDim = Color(0xFF0D0D0D),
        surfaceContainerLowest = Color(0xFF0D0D0D),
        surfaceContainerLow = Color(0xFF181818),
        surfaceContainer = Color(0xFF222222),
        surfaceContainerHigh = Color(0xFF2A2A2A),
        surfaceContainerHighest = Color(0xFF333333),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFCC0000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFB3B3),
        onPrimaryContainer = Color(0xFF5C0000),
        inversePrimary = Color(0xFFFF6D6D),
        secondary = Color(0xFF9A0000),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF0E0E0),
        onSecondaryContainer = Color(0xFF410000),
        tertiary = Color(0xFF5D5D5D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0E0E0),
        onTertiaryContainer = Color(0xFF1B1B1B),
        background = Color(0xFFFFF8F8),
        onBackground = Color(0xFF211111),
        surface = Color(0xFFFFF8F8),
        onSurface = Color(0xFF211111),
        surfaceVariant = Color(0xFFF5DEDE),
        onSurfaceVariant = Color(0xFF534343),
        surfaceTint = Color(0xFFCC0000),
        inverseSurface = Color(0xFF362F2F),
        inverseOnSurface = Color(0xFFFFEDED),
        outline = Color(0xFF857373),
        outlineVariant = Color(0xFFE0C8C8),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFF8F8),
        surfaceDim = Color(0xFFE8D0D0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF0F0),
        surfaceContainer = Color(0xFFF5E0E0),
        surfaceContainerHigh = Color(0xFFF0D8D8),
        surfaceContainerHighest = Color(0xFFE8CCCC),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF211111)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF6A4444)
    override val readerBackgroundColorDark: Color = Color(0xFF1B1B1B)
    override val readerBackgroundColorLight: Color = Color(0xFFFFF8F8)
    override val readerSelectionColorDark: Color = Color(0x40FF0000)
    override val readerSelectionColorLight: Color = Color(0x40FF0000)
}
