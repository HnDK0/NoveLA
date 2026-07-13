package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object YotsubaColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFB59D),
        onPrimary = Color(0xFF5F1600),
        primaryContainer = Color(0xFF862200),
        onPrimaryContainer = Color(0xFFFFDBCF),
        inversePrimary = Color(0xFFAE3200),
        secondary = Color(0xFFFFB59D),
        onSecondary = Color(0xFF5F1600),
        secondaryContainer = Color(0xFF382220),
        onSecondaryContainer = Color(0xFFF0D8CC),
        tertiary = Color(0xFFD7C68D),
        onTertiary = Color(0xFF3A2F05),
        tertiaryContainer = Color(0xFF524619),
        onTertiaryContainer = Color(0xFFF5E2A7),
        background = Color(0xFF211A18),
        onBackground = Color(0xFFEDE0DD),
        surface = Color(0xFF211A18),
        onSurface = Color(0xFFEDE0DD),
        surfaceVariant = Color(0xFF332723),
        onSurfaceVariant = Color(0xFFD8C2BC),
        surfaceTint = Color(0xFFFFB59D),
        inverseSurface = Color(0xFFEDE0DD),
        inverseOnSurface = Color(0xFF211A18),
        outline = Color(0xFFA08C87),
        outlineVariant = Color(0xFF4A3028),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3D2E28),
        surfaceDim = Color(0xFF150E0C),
        surfaceContainerLowest = Color(0xFF150E0C),
        surfaceContainerLow = Color(0xFF312521),
        surfaceContainer = Color(0xFF332723),
        surfaceContainerHigh = Color(0xFF413531),
        surfaceContainerHighest = Color(0xFF4C403D),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFAE3200),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8B8A0),
        onPrimaryContainer = Color(0xFF3B0A00),
        inversePrimary = Color(0xFFFFB59D),
        secondary = Color(0xFFAE3200),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF0E8E4),
        onSecondaryContainer = Color(0xFF4A1800),
        tertiary = Color(0xFF6B5E2F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF5E2A7),
        onTertiaryContainer = Color(0xFF231B00),
        background = Color(0xFFFCFCFC),
        onBackground = Color(0xFF211A18),
        surface = Color(0xFFFCFCFC),
        onSurface = Color(0xFF211A18),
        surfaceVariant = Color(0xFFF6EBE7),
        onSurfaceVariant = Color(0xFF53433F),
        surfaceTint = Color(0xFFAE3200),
        inverseSurface = Color(0xFF362F2D),
        inverseOnSurface = Color(0xFFFBEEEB),
        outline = Color(0xFF85736E),
        outlineVariant = Color(0xFFD8C8C0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFCFCFC),
        surfaceDim = Color(0xFFE8DCD8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F0EC),
        surfaceContainer = Color(0xFFF6EBE7),
        surfaceContainerHigh = Color(0xFFFAF4F2),
        surfaceContainerHighest = Color(0xFFFBF6F4),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF211A18)
    override val readerBackgroundColorLight: Color = Color(0xFFFCFCFC)
    override val readerSelectionColorDark: Color = Color(0x40FFB59D)
    override val readerSelectionColorLight: Color = Color(0x40AE3200)
}
