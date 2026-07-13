package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object SapphireColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF1E88E5),
        onPrimary = Color(0xFF002244),
        primaryContainer = Color(0xFF003A7A),
        onPrimaryContainer = Color(0xFFC0E0FF),
        inversePrimary = Color(0xFF2979FF),
        secondary = Color(0xFF42A5F5),
        onSecondary = Color(0xFF002244),
        secondaryContainer = Color(0xFF1A2A3A),
        onSecondaryContainer = Color(0xFFB0D0F0),
        tertiary = Color(0xFF78909C),
        onTertiary = Color(0xFFFAFAFA),
        tertiaryContainer = Color(0xFF263238),
        onTertiaryContainer = Color(0xFFB0C8D8),
        background = Color(0xFF212121),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF212121),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF424242),
        onSurfaceVariant = Color(0xFFD0D0D0),
        surfaceTint = Color(0xFF1E88E5),
        inverseSurface = Color(0xFFFAFAFA),
        inverseOnSurface = Color(0xFF313131),
        outline = Color(0xFF607080),
        outlineVariant = Color(0xFF2A3848),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF323A42),
        surfaceDim = Color(0xFF141C24),
        surfaceContainerLowest = Color(0xFF141C24),
        surfaceContainerLow = Color(0xFF1C2430),
        surfaceContainer = Color(0xFF222C38),
        surfaceContainerHigh = Color(0xFF2A3440),
        surfaceContainerHighest = Color(0xFF323C48),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF1E88E5),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF90C0F0),
        onPrimaryContainer = Color(0xFF002A60),
        inversePrimary = Color(0xFF2979FF),
        secondary = Color(0xFF0D6EBF),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0ECF5),
        onSecondaryContainer = Color(0xFF003A7A),
        tertiary = Color(0xFF546E7A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE1F5FE),
        onTertiaryContainer = Color(0xFF1A3A4A),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF212121),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF212121),
        surfaceVariant = Color(0xFFB3E5FC),
        onSurfaceVariant = Color(0xFF49454E),
        surfaceTint = Color(0xFF1E88E5),
        inverseSurface = Color(0xFF424242),
        inverseOnSurface = Color(0xFFFAFAFA),
        outline = Color(0xFF607080),
        outlineVariant = Color(0xFFC0D0E0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD8E4F0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F6FF),
        surfaceContainer = Color(0xFFE8F0FA),
        surfaceContainerHigh = Color(0xFFE0E8F5),
        surfaceContainerHighest = Color(0xFFD8E0F0),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF212121)
    override val readerBackgroundColorLight: Color = Color(0xFFFFFFFF)
    override val readerSelectionColorDark: Color = Color(0x401E88E5)
    override val readerSelectionColorLight: Color = Color(0x401E88E5)
}
