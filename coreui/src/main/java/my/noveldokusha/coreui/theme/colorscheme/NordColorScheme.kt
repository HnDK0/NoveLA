package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object NordColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF88C0D0),
        onPrimary = Color(0xFF2E3440),
        primaryContainer = Color(0xFF2A5060),
        onPrimaryContainer = Color(0xFFB8E0EA),
        inversePrimary = Color(0xFF397E91),
        secondary = Color(0xFF81A1C1),
        onSecondary = Color(0xFF2E3440),
        secondaryContainer = Color(0xFF3B4858),
        onSecondaryContainer = Color(0xFF88C0D0),
        tertiary = Color(0xFF5E81AC),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF5E81AC),
        onTertiaryContainer = Color(0xFF000000),
        error = Color(0xFFBF616A),
        onError = Color(0xFF2E3440),
        errorContainer = Color(0xFF7A0010),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF2E3440),
        onBackground = Color(0xFFECEFF4),
        surface = Color(0xFF2E3440),
        onSurface = Color(0xFFECEFF4),
        surfaceVariant = Color(0xFF414C5C),
        onSurfaceVariant = Color(0xFFECEFF4),
        surfaceTint = Color(0xFF88C0D0),
        inverseSurface = Color(0xFFD8DEE9),
        inverseOnSurface = Color(0xFF2E3440),
        outline = Color(0xFF6d717b),
        outlineVariant = Color(0xFF90939a),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF5E6880),
        surfaceDim = Color(0xFF242B38),
        surfaceContainerLowest = Color(0xFF373F4D),
        surfaceContainerLow = Color(0xFF3E4756),
        surfaceContainer = Color(0xFF414C5C),
        surfaceContainerHigh = Color(0xFF4E5766),
        surfaceContainerHighest = Color(0xFF505968),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF5E81AC),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFA0C0E0),
        onPrimaryContainer = Color(0xFF0A2A44),
        inversePrimary = Color(0xFF8CA8CD),
        secondary = Color(0xFF81A1C1),
        onSecondary = Color(0xFF2E3440),
        secondaryContainer = Color(0xFFE0E8F0),
        onSecondaryContainer = Color(0xFF2E3440),
        tertiary = Color(0xFF88C0D0),
        onTertiary = Color(0xFF2E3440),
        tertiaryContainer = Color(0xFF88C0D0),
        onTertiaryContainer = Color(0xFF2E3440),
        error = Color(0xFFBF616A),
        onError = Color(0xFFECEFF4),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFECEFF4),
        onBackground = Color(0xFF2E3440),
        surface = Color(0xFFE5E9F0),
        onSurface = Color(0xFF2E3440),
        surfaceVariant = Color(0xFFDAE0EA),
        onSurfaceVariant = Color(0xFF2E3440),
        surfaceTint = Color(0xFF5E81AC),
        inverseSurface = Color(0xFF3B4252),
        inverseOnSurface = Color(0xFFECEFF4),
        outline = Color(0xFF2E3440),
        outlineVariant = Color(0xFFC0C8D4),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFEFF2F8),
        surfaceDim = Color(0xFFC8CED8),
        surfaceContainerLowest = Color(0xFFF0F4F8),
        surfaceContainerLow = Color(0xFFE5EAF0),
        surfaceContainer = Color(0xFFDAE0EA),
        surfaceContainerHigh = Color(0xFFE9EDF3),
        surfaceContainerHighest = Color(0xFFF2F4F8),
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = Color(0xFF2E3440)
    override val readerBackgroundColorLight: Color = Color(0xFFECEFF4)
    override val readerSelectionColorDark: Color = Color(0x4088C0D0)
    override val readerSelectionColorLight: Color = Color(0x405E81AC)
}
