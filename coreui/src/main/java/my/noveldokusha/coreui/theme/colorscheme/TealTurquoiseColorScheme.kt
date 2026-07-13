package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object TealTurquoiseColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF40E0D0),
        onPrimary = Color(0xFF004D48),
        primaryContainer = Color(0xFF004D48),
        onPrimaryContainer = Color(0xFF80F0E8),
        inversePrimary = Color(0xFF008080),
        secondary = Color(0xFF40E0D0),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF1A3A38),
        onSecondaryContainer = Color(0xFFA0E8E0),
        tertiary = Color(0xFFBF1F2F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF200508),
        onTertiaryContainer = Color(0xFFBF1F2F),
        background = Color(0xFF202125),
        onBackground = Color(0xFFDFDEDA),
        surface = Color(0xFF202125),
        onSurface = Color(0xFFDFDEDA),
        surfaceVariant = Color(0xFF233133),
        onSurfaceVariant = Color(0xFFDFDEDA),
        surfaceTint = Color(0xFF40E0D0),
        inverseSurface = Color(0xFFDFDEDA),
        inverseOnSurface = Color(0xFF202125),
        outline = Color(0xFF899391),
        outlineVariant = Color(0xFF2A4040),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF2A3840),
        surfaceDim = Color(0xFF141C20),
        surfaceContainerLowest = Color(0xFF141C20),
        surfaceContainerLow = Color(0xFF222F31),
        surfaceContainer = Color(0xFF233133),
        surfaceContainerHigh = Color(0xFF28383A),
        surfaceContainerHighest = Color(0xFF2F4244),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF008080),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF80D8D8),
        onPrimaryContainer = Color(0xFF002828),
        inversePrimary = Color(0xFF40E0D0),
        secondary = Color(0xFF008080),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE4F0F0),
        onSecondaryContainer = Color(0xFF003838),
        tertiary = Color(0xFFFF7F7F),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF2A1616),
        onTertiaryContainer = Color(0xFFFF7F7F),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF050505),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF050505),
        surfaceVariant = Color(0xFFEBF3F1),
        onSurfaceVariant = Color(0xFF050505),
        surfaceTint = Color(0xFFBFDFDF),
        inverseSurface = Color(0xFF050505),
        inverseOnSurface = Color(0xFFFAFAFA),
        outline = Color(0xFF6F7977),
        outlineVariant = Color(0xFFC0D8D8),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFAFAFA),
        surfaceDim = Color(0xFFD8E8E8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFE6EEEC),
        surfaceContainer = Color(0xFFEBF3F1),
        surfaceContainerHigh = Color(0xFFF0F8F6),
        surfaceContainerHighest = Color(0xFFF7FFFD),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF202125)
    override val readerBackgroundColorLight: Color = Color(0xFFFAFAFA)
    override val readerSelectionColorDark: Color = Color(0x4040E0D0)
    override val readerSelectionColorLight: Color = Color(0x40008080)
}
