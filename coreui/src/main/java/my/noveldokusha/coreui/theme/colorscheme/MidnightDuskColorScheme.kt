package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object MidnightDuskColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFF02475),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF8A0035),
        onPrimaryContainer = Color(0xFFFFD9E1),
        inversePrimary = Color(0xFFF02475),
        secondary = Color(0xFFF02475),
        onSecondary = Color(0xFF16151D),
        secondaryContainer = Color(0xFF3A1A28),
        onSecondaryContainer = Color(0xFFF8C0D8),
        tertiary = Color(0xFF55971C),
        onTertiary = Color(0xFF16151D),
        tertiaryContainer = Color(0xFF386412),
        onTertiaryContainer = Color(0xFFE5E1E5),
        background = Color(0xFF16151D),
        onBackground = Color(0xFFE5E1E5),
        surface = Color(0xFF16151D),
        onSurface = Color(0xFFE5E1E5),
        surfaceVariant = Color(0xFF281624),
        onSurfaceVariant = Color(0xFFD6C1C4),
        surfaceTint = Color(0xFFF02475),
        inverseSurface = Color(0xFF333043),
        inverseOnSurface = Color(0xFFFFFFFF),
        outline = Color(0xFF9F8C8F),
        outlineVariant = Color(0xFF3D1A28),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3A2535),
        surfaceDim = Color(0xFF100C15),
        surfaceContainerLowest = Color(0xFF221320),
        surfaceContainerLow = Color(0xFF251522),
        surfaceContainer = Color(0xFF281624),
        surfaceContainerHigh = Color(0xFF2D1C2A),
        surfaceContainerHighest = Color(0xFF2F1F2C),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFBB0054),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8B0C0),
        onPrimaryContainer = Color(0xFF3F0017),
        inversePrimary = Color(0xFFFFB1C4),
        secondary = Color(0xFFBB0054),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF0E0E8),
        onSecondaryContainer = Color(0xFF4A0022),
        tertiary = Color(0xFF006638),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF00894b),
        onTertiaryContainer = Color(0xFF2D1600),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFF9E6F1),
        onSurfaceVariant = Color(0xFF524346),
        surfaceTint = Color(0xFFBB0054),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF4F0F4),
        outline = Color(0xFF847376),
        outlineVariant = Color(0xFFD4B0C0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFBFF),
        surfaceDim = Color(0xFFE8D0DC),
        surfaceContainerLowest = Color(0xFFFFF5F9),
        surfaceContainerLow = Color(0xFFFAEAF3),
        surfaceContainer = Color(0xFFF9E6F1),
        surfaceContainerHigh = Color(0xFFFCF3F8),
        surfaceContainerHighest = Color(0xFFFEF9FC),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF16151D)
    override val readerBackgroundColorLight: Color = Color(0xFFFFFBFF)
    override val readerSelectionColorDark: Color = Color(0x40F02475)
    override val readerSelectionColorLight: Color = Color(0x40BB0054)
}
