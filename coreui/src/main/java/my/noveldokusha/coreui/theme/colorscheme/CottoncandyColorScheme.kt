package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object CottoncandyColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFB3B4),
        onPrimary = Color(0xFF561D21),
        primaryContainer = Color(0xFF733336),
        onPrimaryContainer = Color(0xFFFFDAD9),
        secondary = Color(0xFF80D4D8),
        onSecondary = Color(0xFF003739),
        secondaryContainer = Color(0xFF2A3A3C),
        onSecondaryContainer = Color(0xFFB0E8E8),
        tertiary = Color(0xFFEBB5ED),
        onTertiary = Color(0xFF48204E),
        tertiaryContainer = Color(0xFF613766),
        onTertiaryContainer = Color(0xFFFFD6FE),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF1A1111),
        onBackground = Color(0xFFF0DEDE),
        surface = Color(0xFF1A1112),
        onSurface = Color(0xFFF0DEDF),
        surfaceVariant = Color(0xFF524343),
        onSurfaceVariant = Color(0xFFD7C1C1),
        outline = Color(0xFFA08C8C),
        outlineVariant = Color(0xFF524343),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFF0DEDF),
        inverseOnSurface = Color(0xFF382E2F),
        inversePrimary = Color(0xFF8F4A4C),
        surfaceDim = Color(0xFF1A1112),
        surfaceBright = Color(0xFF413738),
        surfaceContainerLowest = Color(0xFF140C0D),
        surfaceContainerLow = Color(0xFF22191A),
        surfaceContainer = Color(0xFF261D1E),
        surfaceContainerHigh = Color(0xFF312828),
        surfaceContainerHighest = Color(0xFF3D3233),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF8F4A4C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8B0B0),
        onPrimaryContainer = Color(0xFF3B080E),
        secondary = Color(0xFF00696D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8F0F0),
        onSecondaryContainer = Color(0xFF003838),
        tertiary = Color(0xFF7B4E7F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD6FE),
        onTertiaryContainer = Color(0xFF310938),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF8F7),
        onBackground = Color(0xFF221919),
        surface = Color(0xFFFFF8F7),
        onSurface = Color(0xFF22191A),
        surfaceVariant = Color(0xFFF4DDDD),
        onSurfaceVariant = Color(0xFF524343),
        outline = Color(0xFF857373),
        outlineVariant = Color(0xFFD7C1C1),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF382E2F),
        inverseOnSurface = Color(0xFFFEEDED),
        inversePrimary = Color(0xFFFFB3B4),
        surfaceDim = Color(0xFFE7D6D7),
        surfaceBright = Color(0xFFFFF8F7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF0F1),
        surfaceContainer = Color(0xFFFBEAEB),
        surfaceContainerHigh = Color(0xFFF6E4E5),
        surfaceContainerHighest = Color(0xFFF0DEDF),
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
    override val readerBackgroundColorDark: Color = Color(0xFF1A1111)
    override val readerBackgroundColorLight: Color = Color(0xFFFFF8F7)

    // Выделение текста — единственное место в ридере где допустим акцентный цвет
    override val readerSelectionColorDark: Color = Color(0x40FFB3B4)
    override val readerSelectionColorLight: Color = Color(0x408F4A4C)
}