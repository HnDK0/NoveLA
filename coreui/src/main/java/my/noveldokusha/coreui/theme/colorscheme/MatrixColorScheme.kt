package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object MatrixColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF00FF00),
        onPrimary = Color(0xFF001A00),
        primaryContainer = Color(0xFF003300),
        onPrimaryContainer = Color(0xFF80FF80),
        secondary = Color(0xFF00CC00),
        onSecondary = Color(0xFF001A00),
        secondaryContainer = Color(0xFF1A2A1A),
        onSecondaryContainer = Color(0xFFB0FFB0),
        tertiary = Color(0xFFFFFFFF),
        onTertiary = Color(0xFF00FF00),
        tertiaryContainer = Color(0xFF1A1A1A),
        onTertiaryContainer = Color(0xFF00FF00),
        background = Color(0xFF111111),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF111111),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF212121),
        onSurfaceVariant = Color(0xFFD0D0D0),
        surfaceTint = Color(0xFF00FF00),
        inverseSurface = Color(0xFFFAFAFA),
        inverseOnSurface = Color(0xFF313131),
        outline = Color(0xFF006600),
        outlineVariant = Color(0xFF002200),
        scrim = Color(0xFF000000),
        inversePrimary = Color(0xFF007700),
        surfaceBright = Color(0xFF1A1A1A),
        surfaceDim = Color(0xFF080808),
        surfaceContainerLowest = Color(0xFF080808),
        surfaceContainerLow = Color(0xFF0E0E0E),
        surfaceContainer = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF1A1A1A),
        surfaceContainerHighest = Color(0xFF212121),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF007700),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA8F0A8),
        onPrimaryContainer = Color(0xFF003300),
        inversePrimary = Color(0xFF00CC00),
        secondary = Color(0xFF006600),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8F5E8),
        onSecondaryContainer = Color(0xFF003300),
        tertiary = Color(0xFF4A4A4A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD0E8D0),
        onTertiaryContainer = Color(0xFF1A1A1A),
        background = Color(0xFFF5FFF5),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFF5FFF5),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFE0F0E0),
        onSurfaceVariant = Color(0xFF3A4A3A),
        surfaceTint = Color(0xFF007700),
        inverseSurface = Color(0xFF363636),
        inverseOnSurface = Color(0xFFF0F0F0),
        outline = Color(0xFF6A7A6A),
        outlineVariant = Color(0xFFC0D0C0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF5FFF5),
        surfaceDim = Color(0xFFD0E8D0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0FAF0),
        surfaceContainer = Color(0xFFE8F0E8),
        surfaceContainerHigh = Color(0xFFDEE8DE),
        surfaceContainerHighest = Color(0xFFD0E0D0),
    )

    override val readerTextColorDark: Color = Color(0xFF00FF00)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFF009900)
    override val readerTextSecondaryColorLight: Color = Color(0xFF4A6A4A)
    override val readerBackgroundColorDark: Color = Color(0xFF111111)
    override val readerBackgroundColorLight: Color = Color(0xFFF5FFF5)
    override val readerSelectionColorDark: Color = Color(0x4000FF00)
    override val readerSelectionColorLight: Color = Color(0x4000FF00)
}
