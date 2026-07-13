package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object LavenderColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFA177FF),
        onPrimary = Color(0xFF3D0090),
        primaryContainer = Color(0xFF3E2080),
        onPrimaryContainer = Color(0xFFE8D0FF),
        secondary = Color(0xFFA177FF),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF2E2048),
        onSecondaryContainer = Color(0xFFD0C0F0),
        tertiary = Color(0xFFCDBDFF),
        onTertiary = Color(0xFF360096),
        tertiaryContainer = Color(0xFF5512D8),
        onTertiaryContainer = Color(0xFFEFE6FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF111129),
        onBackground = Color(0xFFE7E0EC),
        surface = Color(0xFF111129),
        onSurface = Color(0xFFE7E0EC),
        surfaceVariant = Color(0xFF3D2F6B),
        onSurfaceVariant = Color(0xFFCBC3D6),
        outline = Color(0xFF958E9F),
        outlineVariant = Color(0xFF4A4453),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFE7E0EC),
        inverseOnSurface = Color(0xFF322F38),
        inversePrimary = Color(0xFF6D41C8),
        surfaceDim = Color(0xFF111129),
        surfaceBright = Color(0xFF3B3841),
        surfaceContainerLowest = Color(0xFF0D0B22),
        surfaceContainerLow = Color(0xFF171531),
        surfaceContainer = Color(0xFF1D193B),
        surfaceContainerHigh = Color(0xFF241f41),
        surfaceContainerHighest = Color(0xFF282446),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF6D41C8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC0A0F0),
        onPrimaryContainer = Color(0xFF180030),
        secondary = Color(0xFF7B46AF),
        onSecondary = Color(0xFFEDE2FF),
        secondaryContainer = Color(0xFFE8E0F0),
        onSecondaryContainer = Color(0xFF2A0060),
        tertiary = Color(0xFFEDE2FF),
        onTertiary = Color(0xFF7B46AF),
        tertiaryContainer = Color(0xFF6D3BF0),
        onTertiaryContainer = Color(0xFFFFFFFF),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFEDE2FF),
        onBackground = Color(0xFF1D1A22),
        surface = Color(0xFFEDE2FF),
        onSurface = Color(0xFF1D1A22),
        surfaceVariant = Color(0xFFE4D5F8),
        onSurfaceVariant = Color(0xFF4A4453),
        outline = Color(0xFF7B7485),
        outlineVariant = Color(0xFFCBC3D6),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFF322F38),
        inverseOnSurface = Color(0xFFF5EEFA),
        inversePrimary = Color(0xFFA177FF),
        surfaceDim = Color(0xFFDED7E3),
        surfaceBright = Color(0xFFEDE2FF),
        surfaceContainerLowest = Color(0xFFF5EEFF),
        surfaceContainerLow = Color(0xFFDED0F1),
        surfaceContainer = Color(0xFFE4D5F8),
        surfaceContainerHigh = Color(0xFFEADCFD),
        surfaceContainerHighest = Color(0xFFEEE2FF),
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
    override val readerBackgroundColorDark: Color = Color(0xFF111129)
    override val readerBackgroundColorLight: Color = Color(0xFFEDE2FF)

    // Выделение текста — единственное место в ридере где допустим акцентный цвет
    override val readerSelectionColorDark: Color = Color(0x40A177FF)
    override val readerSelectionColorLight: Color = Color(0x406D41C8)
}