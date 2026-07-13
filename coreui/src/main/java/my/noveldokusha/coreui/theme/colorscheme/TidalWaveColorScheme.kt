package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object TidalWaveColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFF5ed4fc),
        onPrimary = Color(0xFF003544),
        primaryContainer = Color(0xFF004d61),
        onPrimaryContainer = Color(0xFFb8eaff),
        inversePrimary = Color(0xFFa12b03),
        secondary = Color(0xFF5ed4fc),
        onSecondary = Color(0xFF003544),
        secondaryContainer = Color(0xFF1A2A30),
        onSecondaryContainer = Color(0xFFA0D0E0),
        tertiary = Color(0xFF92f7bc),
        onTertiary = Color(0xFF001c3b),
        tertiaryContainer = Color(0xFFc3fada),
        onTertiaryContainer = Color(0xFF78ffd6),
        background = Color(0xFF001c3b),
        onBackground = Color(0xFFd5e3ff),
        surface = Color(0xFF001c3b),
        onSurface = Color(0xFFd5e3ff),
        surfaceVariant = Color(0xFF082b4b),
        onSurfaceVariant = Color(0xFFbfc8cc),
        surfaceTint = Color(0xFF5ed4fc),
        inverseSurface = Color(0xFFffe3c4),
        inverseOnSurface = Color(0xFF001c3b),
        outline = Color(0xFF8a9296),
        surfaceContainerLowest = Color(0xFF072642),
        surfaceContainerLow = Color(0xFF072947),
        surfaceContainer = Color(0xFF082b4b),
        surfaceContainerHigh = Color(0xFF093257),
        surfaceContainerHighest = Color(0xFF0a3861),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF006780),
        onPrimary = Color(0xFFffffff),
        primaryContainer = Color(0xFF80C0D8),
        onPrimaryContainer = Color(0xFF001f28),
        inversePrimary = Color(0xFFff987f),
        secondary = Color(0xFF006780),
        onSecondary = Color(0xFFffffff),
        secondaryContainer = Color(0xFFE0EFF5),
        onSecondaryContainer = Color(0xFF002030),
        tertiary = Color(0xFF92f7bc),
        onTertiary = Color(0xFF001c3b),
        tertiaryContainer = Color(0xFFc3fada),
        onTertiaryContainer = Color(0xFF78ffd6),
        background = Color(0xFFfdfbff),
        onBackground = Color(0xFF001c3b),
        surface = Color(0xFFfdfbff),
        onSurface = Color(0xFF001c3b),
        surfaceVariant = Color(0xFFe8eff5),
        onSurfaceVariant = Color(0xFF40484c),
        surfaceTint = Color(0xFF006780),
        inverseSurface = Color(0xFF020400),
        inverseOnSurface = Color(0xFFffe3c4),
        outline = Color(0xFF70787c),
        surfaceContainerLowest = Color(0xFFe2e8ec),
        surfaceContainerLow = Color(0xFFe5ecf1),
        surfaceContainer = Color(0xFFe8eff5),
        surfaceContainerHigh = Color(0xFFedf4fA),
        surfaceContainerHighest = Color(0xFFf5faff),
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
    override val readerBackgroundColorDark: Color = Color(0xFF001C3B)
    override val readerBackgroundColorLight: Color = Color(0xFFFDFBFF)

    // Выделение текста — единственное место в ридере где допустим акцентный цвет
    override val readerSelectionColorDark: Color = Color(0x405ED4FC)
    override val readerSelectionColorLight: Color = Color(0x40006780)
}