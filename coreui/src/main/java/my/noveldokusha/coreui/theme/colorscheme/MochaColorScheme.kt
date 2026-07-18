package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object MochaColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFFB77F),
        onPrimary = Color(0xFF4E2600),
        primaryContainer = Color(0xFF6C3A08),
        onPrimaryContainer = Color(0xFFFFDCC3),
        secondary = Color(0xFFF6BC70),
        onSecondary = Color(0xFF462B00),
        secondaryContainer = Color(0xFF3A2810),
        onSecondaryContainer = Color(0xFFF0D8B0),
        tertiary = Color(0xFFAED18D),
        onTertiary = Color(0xFF1C3704),
        tertiaryContainer = Color(0xFF314E19),
        onTertiaryContainer = Color(0xFFC9EEA7),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF19120C),
        onBackground = Color(0xFFEFDFD6),
        surface = Color(0xFF19120D),
        onSurface = Color(0xFFF0DFD6),
        surfaceVariant = Color(0xFF52443C),
        onSurfaceVariant = Color(0xFFD7C3B8),
        outline = Color(0xFF9F8D83),
        outlineVariant = Color(0xFF52443C),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFF0DFD6),
        inverseOnSurface = Color(0xFF382F28),
        inversePrimary = Color(0xFF89511F),
        surfaceDim = Color(0xFF19120D),
        surfaceBright = Color(0xFF413731),
        surfaceContainerLowest = Color(0xFF140D08),
        surfaceContainerLow = Color(0xFF221A14),
        surfaceContainer = Color(0xFF261E18),
        surfaceContainerHigh = Color(0xFF312822),
        surfaceContainerHighest = Color(0xFF3C332D),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF89511F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE8B080),
        onPrimaryContainer = Color(0xFF2F1500),
        secondary = Color(0xFF815511),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF0E4D0),
        onSecondaryContainer = Color(0xFF2A1800),
        tertiary = Color(0xFF48672E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC9EEA7),
        onTertiaryContainer = Color(0xFF0C2000),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF8F5),
        onBackground = Color(0xFF221A14),
        surface = Color(0xFFFFF8F5),
        onSurface = Color(0xFF221A14),
        surfaceVariant = Color(0xFFF4DED3),
        onSurfaceVariant = Color(0xFF52443C),
        outline = Color(0xFF84746A),
        outlineVariant = Color(0xFFD7C3B8),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF382F28),
        inverseOnSurface = Color(0xFFFEEEE4),
        inversePrimary = Color(0xFFFFB77F),
        surfaceDim = Color(0xFFE7D7CE),
        surfaceBright = Color(0xFFFFF8F5),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF1E9),
        surfaceContainer = Color(0xFFFBEBE1),
        surfaceContainerHigh = Color(0xFFF5E5DC),
        surfaceContainerHighest = Color(0xFFF0DFD6),
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
    override val readerBackgroundColorDark: Color = Color(0xFF19120C)
    override val readerBackgroundColorLight: Color = Color(0xFFFFF8F5)

    // Выделение текста — единственное место в ридере где допустим акцентный цвет
    override val readerSelectionColorDark: Color = Color(0x40FFB77F)
    override val readerSelectionColorLight: Color = Color(0x4089511F)
}