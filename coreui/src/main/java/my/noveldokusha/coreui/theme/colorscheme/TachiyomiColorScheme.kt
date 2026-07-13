package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import my.noveldokusha.coreui.theme.*

internal object TachiyomiColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = HighlightDark,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF003A7A),
        onPrimaryContainer = Color(0xFFB0D0F0),
        secondary = HighlightDark,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF2A2A3A),
        onSecondaryContainer = Color(0xFFE6A817),
        tertiary = Color(0xFF4CAF50),
        onTertiary = Color(0xFF003910),
        tertiaryContainer = Success800,
        onTertiaryContainer = Success50,
        background = BgDark,
        onBackground = TextDark,
        surface = BgDark,
        onSurface = TextDark,
        surfaceVariant = BgDark,
        onSurfaceVariant = SubTextDark,
        surfaceTint = HighlightDark,
        inverseSurface = TextDark,
        inverseOnSurface = BgDark,
        error = Color(0xFFCF6679),
        onError = Color(0xFF690005),
        errorContainer = Error800,
        onErrorContainer = Error50,
        outline = StrokeDark,
        outlineVariant = StrokeDark,
        scrim = Color.Black.copy(alpha = 0.5f),
        surfaceBright = Grey700,
        surfaceDim = BgDark,
        surfaceContainerLowest = Grey1000,
        surfaceContainerLow = BgDark,
        surfaceContainer = SubBgDark,
        surfaceContainerHigh = PopupDark,
        surfaceContainerHighest = Grey600,
    )

    override val lightScheme = lightColorScheme(
        primary = HighlightLight,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF84A1D9),
        onPrimaryContainer = Color(0xFF003060),
        secondary = HighlightLight,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8E0EC),
        onSecondaryContainer = Color(0xFF4A3800),
        tertiary = Success600,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Success50,
        onTertiaryContainer = Success900,
        background = BgLight,
        onBackground = TextLight,
        surface = BgLight,
        onSurface = TextLight,
        surfaceVariant = BgLight,
        onSurfaceVariant = SubTextLight,
        surfaceTint = HighlightLight,
        inverseSurface = TextLight,
        inverseOnSurface = BgLight,
        error = Color(0xFFE53935),
        onError = Color(0xFFFFFFFF),
        errorContainer = Error200,
        onErrorContainer = Error900,
        outline = StrokeLight,
        outlineVariant = StrokeLight,
        scrim = Color.Black.copy(alpha = 0.5f),
        surfaceBright = SubBgLight,
        surfaceDim = Grey100,
        surfaceContainerLowest = Grey0,
        surfaceContainerLow = BgLight,
        surfaceContainer = SubBgLight,
        surfaceContainerHigh = PopupLight,
        surfaceContainerHighest = Grey100,
    )
    // -----------------------------------------------------------------------------------------
    // Reader colors — используются ТОЛЬКО в ReaderScreen
    // -----------------------------------------------------------------------------------------

    // Основной текст — нейтральный, без фиолетового оттенка темы
    override val readerTextColorDark: Color = Color(0xFFE8E8E8)  // тёплый почти-белый
    override val readerTextColorLight: Color = Color(0xFF1A1A1A) // мягкий почти-чёрный

    // Вторичный текст: номера глав, сноски, метаданные
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)

    // Фон страницы чтения — совпадает с background, задан явно для будущего переопределения
    override val readerBackgroundColorDark: Color = BgDark
    override val readerBackgroundColorLight: Color = BgLight

    // Выделение текста — единственное место в ридере где допустим акцентный цвет
    override val readerSelectionColorDark: Color = HighlightDark.copy(alpha = 0.25f)
    override val readerSelectionColorLight: Color = HighlightLight.copy(alpha = 0.25f)
}