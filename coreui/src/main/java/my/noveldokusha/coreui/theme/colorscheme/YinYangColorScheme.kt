package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object YinYangColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFFFFF),
        onPrimary = Color(0xFF5A5A5A),
        primaryContainer = Color(0xFFFFFFFF),
        onPrimaryContainer = Color(0xFF000000),
        inversePrimary = Color(0xFFCECECE),
        secondary = Color(0xFFFFFFFF),
        onSecondary = Color(0xFF5A5A5A),
        secondaryContainer = Color(0xFF717171),
        onSecondaryContainer = Color(0xFFE4E4E4),
        tertiary = Color(0xFF000000),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF00419E),
        onTertiaryContainer = Color(0xFFD8E2FF),
        background = Color(0xFF1E1E1E),
        onBackground = Color(0xFFE6E6E6),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFE6E6E6),
        surfaceVariant = Color(0xFF313131),
        onSurfaceVariant = Color(0xFFD1D1D1),
        surfaceTint = Color(0xFFFFFFFF),
        inverseSurface = Color(0xFFE6E6E6),
        inverseOnSurface = Color(0xFF1E1E1E),
        outline = Color(0xFF999999),
        outlineVariant = Color(0xFF444444),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF484848),
        surfaceDim = Color(0xFF141414),
        surfaceContainerLowest = Color(0xFF141414),
        surfaceContainerLow = Color(0xFF2D2D2D),
        surfaceContainer = Color(0xFF313131),
        surfaceContainerHigh = Color(0xFF383838),
        surfaceContainerHighest = Color(0xFF3F3F3F),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF000000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF000000),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFFA6A6A6),
        secondary = Color(0xFF000000),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDDDDDD),
        onSecondaryContainer = Color(0xFF0C0C0C),
        tertiary = Color(0xFFFFFFFF),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFFD8E2FF),
        onTertiaryContainer = Color(0xFF001947),
        background = Color(0xFFFDFDFD),
        onBackground = Color(0xFF222222),
        surface = Color(0xFFFDFDFD),
        onSurface = Color(0xFF222222),
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF515151),
        surfaceTint = Color(0xFF000000),
        inverseSurface = Color(0xFF333333),
        inverseOnSurface = Color(0xFFF4F4F4),
        outline = Color(0xFF838383),
        outlineVariant = Color(0xFFC8C8C8),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFDFDFD),
        surfaceDim = Color(0xFFD8D8D8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F4F4),
        surfaceContainer = Color(0xFFE8E8E8),
        surfaceContainerHigh = Color(0xFFECECEC),
        surfaceContainerHighest = Color(0xFFEFEFEF),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF1E1E1E)
    override val readerBackgroundColorLight: Color = Color(0xFFFDFDFD)
    override val readerSelectionColorDark: Color = Color(0x40FFFFFF)
    override val readerSelectionColorLight: Color = Color(0x40000000)
}
