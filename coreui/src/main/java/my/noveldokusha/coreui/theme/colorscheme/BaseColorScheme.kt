package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Base class for all color schemes in the app.
 *
 * ## Структура
 * - [darkScheme] / [lightScheme] — стандартные Material3 роли, используются везде в UI
 * - [readerTextColor] / [readerBackgroundColor] и др. — поля ТОЛЬКО для ридера,
 *   всегда нейтральные (почти белый/чёрный), не тянут акцент темы
 *
 * ## Правило ридера
 * В экране чтения глав НИКОГДА не брать цвета из [getColorScheme].
 * Использовать только reader-поля этого класса.
 * Это гарантирует комфорт глаз при долгом чтении независимо от выбранной темы.
 */
internal abstract class BaseColorScheme {

    // -----------------------------------------------------------------------------------------
    // Material3 ColorScheme
    // Используется во всём UI кроме ридера: навбар, карточки, диалоги, кнопки, чипы и т.д.
    // -----------------------------------------------------------------------------------------

    abstract val darkScheme: ColorScheme
    abstract val lightScheme: ColorScheme

    // -----------------------------------------------------------------------------------------
    // Reader colors
    // Используются ТОЛЬКО в экране чтения глав (ReaderScreen).
    // Текст всегда нейтральный (без акцентного оттенка) — комфорт при долгом чтении.
    // -----------------------------------------------------------------------------------------

    /**
     * Основной текст в ридере — тёмная тема.
     * Почти белый без акцентного оттенка (~#E8E8E8).
     * Чуть теплее чистого #FFFFFF — меньше напрягает глаза при долгом чтении.
     * ❌ Не использовать: colorScheme.onBackground / onSurface — они тянут акцент темы
     */
    abstract val readerTextColorDark: Color

    /**
     * Основной текст в ридере — светлая тема.
     * Почти чёрный без акцентного оттенка (~#1A1A1A).
     * Чуть мягче чистого #000000.
     * ❌ Не использовать: colorScheme.onBackground / onSurface — они тянут акцент темы
     */
    abstract val readerTextColorLight: Color

    /**
     * Вторичный текст в ридере — тёмная тема: номера глав, сноски, метаданные.
     * Приглушённый нейтральный серый (~#A0A0A0).
     */
    abstract val readerTextSecondaryColorDark: Color

    /**
     * Вторичный текст в ридере — светлая тема: номера глав, сноски, метаданные.
     * Средний нейтральный серый (~#5A5A5A).
     */
    abstract val readerTextSecondaryColorLight: Color

    /**
     * Фон страницы чтения — тёмная тема.
     * По умолчанию совпадает с darkScheme.background, но задан явно:
     * в будущем можно переопределить (сепия, кастомный фон) не трогая ColorScheme.
     */
    abstract val readerBackgroundColorDark: Color

    /**
     * Фон страницы чтения — светлая тема.
     * По умолчанию совпадает с lightScheme.background, но задан явно.
     */
    abstract val readerBackgroundColorLight: Color

    /**
     * Выделение текста в ридере — тёмная тема.
     * Единственное место в ридере где допустим акцентный цвет.
     * Обычно = primary dark с alpha 0x40 (25%).
     */
    abstract val readerSelectionColorDark: Color

    /**
     * Выделение текста в ридере — светлая тема.
     * Обычно = primary light с alpha 0x40 (25%).
     */
    abstract val readerSelectionColorLight: Color

    // -----------------------------------------------------------------------------------------
    // AMOLED surface containers
    // Используются в getColorScheme() при isAmoled = true.
    // Не чисто чёрные — контент скроллится позади навбара:
    // https://m3.material.io/components/navigation-bar/guidelines#90615a71-607e-485e-9e09-778bfc080563
    // -----------------------------------------------------------------------------------------

    private val amoledSurfaceContainer = Color(0xFF0C0C0C)
    private val amoledSurfaceContainerHigh = Color(0xFF131313)
    private val amoledSurfaceContainerHighest = Color(0xFF1B1B1B)

    /**
     * Возвращает финальную ColorScheme с учётом тёмного режима и AMOLED.
     * Для ридера НЕ использовать — брать reader-поля напрямую.
     */
    fun getColorScheme(
        isDark: Boolean,
        isAmoled: Boolean,
        overrideDarkSurfaceContainers: Boolean = true,
    ): ColorScheme {
        if (!isDark) return lightScheme

        if (!isAmoled) return darkScheme

        val amoledScheme = darkScheme.copy(
            background = Color.Black,
            onBackground = darkScheme.onBackground, // сохраняем акцент темы, не форсируем White
            surface = Color.Black,
            onSurface = darkScheme.onSurface,
        )

        if (!overrideDarkSurfaceContainers) return amoledScheme

        return amoledScheme.copy(
            surfaceVariant = amoledSurfaceContainer,
            surfaceContainerLowest = amoledSurfaceContainer,
            surfaceContainerLow = amoledSurfaceContainer,
            surfaceContainer = amoledSurfaceContainer,
            surfaceContainerHigh = amoledSurfaceContainerHigh,
            surfaceContainerHighest = amoledSurfaceContainerHighest,
        )
    }
}