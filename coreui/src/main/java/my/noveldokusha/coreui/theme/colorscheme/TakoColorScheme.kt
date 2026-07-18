package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Tako — эталонная цветовая схема (фиолетово-оранжевая).
 *
 * Используется как образец при создании новых схем:
 * все поля должны быть заданы явно, ни одно не пропущено.
 *
 * Палитра:
 *   Акцент dark  : оранжевый      #F3B375
 *   Акцент light : фиолетовый     #66577E
 *   Фон dark     : тёмно-синий    #21212E
 *   Фон light    : светло-лавандовый #F7F5FF
 */
internal object TakoColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        // ----- Primary -----
        // Использовать для: основные кнопки (Button), FAB, активные иконки в навбаре,
        //                   индикаторы прогресса, активный таб
        primary = Color(0xFFF3B375),

        // Текст/иконки ПОВЕРХ primary (на кнопках, FAB)
        onPrimary = Color(0xFF38294E),

        // Фон чипов (selected FilterChip), выделенных карточек, FAB extended background
        // ❌ Было: = primary — нет разницы между кнопкой и контейнером
        primaryContainer = Color(0xFF5C3D1E),

        // Текст/иконки внутри primaryContainer
        onPrimaryContainer = Color(0xFFFFD9B0),

        // Используется в inverseSurface-контексте (напр. SnackBar action button)
        inversePrimary = Color(0xFF84531E),

        // ----- Secondary -----
        // Использовать для: вторичные кнопки, менее акцентные иконки, альтернативные теги
        // ❌ Было: = primary — нельзя отличить основное действие от вторичного
        secondary = Color(0xFFC8A0D8),

        // Текст/иконки ПОВЕРХ secondary
        onSecondary = Color(0xFF2D1040),

        // Фон вторичных чипов, карточек второго уровня
        secondaryContainer = Color(0xFF3D2550),

        // Текст/иконки внутри secondaryContainer
        // ❌ Было: = primary — путался с основным акцентом
        onSecondaryContainer = Color(0xFFE8CCFF),

        // ----- Tertiary -----
        // Использовать для: третичные акценты, иконки дополнительных функций,
        //                   бейджи, специальные выделения
        tertiary = Color(0xFF9C8AB4),

        // Текст/иконки ПОВЕРХ tertiary
        onTertiary = Color(0xFF1A0A2E),

        // Фон третичных контейнеров
        tertiaryContainer = Color(0xFF4E4065),

        // Текст/иконки внутри tertiaryContainer
        onTertiaryContainer = Color(0xFFEDDCFF),

        // ----- Error -----
        // Использовать для: поля с ошибкой валидации, иконки ошибок,
        //                   диалоги удаления, снекбары с ошибками
        // ❌ Было: отсутствовало — Material3 подставлял дефолтный красный
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),

        // ----- Background -----
        // Использовать для: основной фон экранов (Scaffold background)
        // ❌ НЕ использовать в ридере — брать readerBackgroundColor
        background = Color(0xFF21212E),

        // Основной текст на background (заголовки экранов, основной контент списков)
        // ❌ НЕ использовать в ридере — тянет фиолетовый оттенок
        onBackground = Color(0xFFE3E0F2),

        // ----- Surface -----
        // Использовать для: фон карточек (Card), BottomSheet, Dialog, NavigationBar
        surface = Color(0xFF21212E),

        // Текст/иконки на surface
        onSurface = Color(0xFFE3E0F2),

        // Фон для элементов с чуть большим визуальным весом: TextField fill, Chip background
        surfaceVariant = Color(0xFF2A2A3C),

        // Текст/иконки на surfaceVariant: placeholder, неактивные иконки навбара,
        //                                 вторичные подписи
        onSurfaceVariant = Color(0xFFCBC4CE),

        // Оттенок elevation (тень поверхностей) — обычно = primary
        surfaceTint = Color(0xFF66577E),

        // Используется в инвертированных поверхностях (SnackBar background)
        inverseSurface = Color(0xFFE5E1E6),
        inverseOnSurface = Color(0xFF1B1B1E),

        // ----- Surface containers -----
        // Иерархия от самого тёмного к самому светлому (в dark теме — от тёмного к менее тёмному).
        // Использовать для разделения уровней вложенности UI.
        // Lowest: фон под всем, скрытые области
        surfaceContainerLowest = Color(0xFF1A1A26),
        // Low: фон экранов второго уровня
        surfaceContainerLow = Color(0xFF21212E),
        // Container: карточки, диалоги
        surfaceContainer = Color(0xFF2A2A3C),
        // High: выдвинутые панели, активные карточки
        surfaceContainerHigh = Color(0xFF303044),
        // Highest: попап-меню, тултипы
        surfaceContainerHighest = Color(0xFF36364D),

        // Bright/Dim — используются для адаптивной яркости поверхностей
        surfaceBright = Color(0xFF3D3D52),
        surfaceDim = Color(0xFF181824),

        // ----- Outline -----
        // outline: бордеры OutlinedTextField, OutlinedButton, OutlinedCard — основные границы
        outline = Color(0xFF958F99),

        // outlineVariant: Divider, разделители списков, тонкие вторичные бордеры
        // ❌ Было: отсутствовало — Divider брал outline, было слишком жирно
        outlineVariant = Color(0xFF3D3848),

        // ----- Scrim -----
        // Полупрозрачный оверлей под BottomSheet, NavDrawer, Dialog
        // ❌ Было: отсутствовало
        scrim = Color(0xFF000000),
    )

    override val lightScheme = lightColorScheme(
        // ----- Primary -----
        primary = Color(0xFF66577E),
        onPrimary = Color(0xFFFFFFFF),

        // Пастельный фиолетовый контейнер — фон чипов, FAB extended
        // ❌ Было: = primary — нет разницы между кнопкой и контейнером
        primaryContainer = Color(0xFFD0B0E8),

        // Тёмный текст на светлом контейнере — WCAG AA
        // ❌ Было: = onPrimary (#F3B375) на светлом фоне — плохой контраст
        onPrimaryContainer = Color(0xFF250052),

        inversePrimary = Color(0xFFD6BAFF),

        // ----- Secondary -----
        // Чуть другой фиолетовый, визуально отличим от primary
        // ❌ Было: = primary
        secondary = Color(0xFF7B5C9E),
        onSecondary = Color(0xFFFFFFFF),

        // Светло-лавандовый, отличим от primaryContainer
        secondaryContainer = Color(0xFFE8E0EB),
        onSecondaryContainer = Color(0xFF2D0060),

        // ----- Tertiary -----
        // Оранжевый как контрастный акцент в светлой теме
        tertiary = Color(0xFFB05A00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFDD6B0),
        onTertiaryContainer = Color(0xFF3A1A00),

        // ----- Error -----
        // ❌ Было: отсутствовало
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        // ----- Background -----
        // ❌ НЕ использовать в ридере
        background = Color(0xFFF7F5FF),
        onBackground = Color(0xFF1B1B22),

        // ----- Surface -----
        surface = Color(0xFFF7F5FF),
        onSurface = Color(0xFF1B1B22),
        surfaceVariant = Color(0xFFE8E0EB),
        onSurfaceVariant = Color(0xFF49454E),
        surfaceTint = Color(0xFF66577E),
        inverseSurface = Color(0xFF313033),
        inverseOnSurface = Color(0xFFF3EFF4),

        // ----- Surface containers -----
        // В light теме: Lowest — самый светлый, Highest — самый тёмный
        // ❌ Было: Lowest (#D7D0DA) темнее Container (#E8E0EB) — иерархия нарушена
        surfaceContainerLowest = Color(0xFFF7F0FA),
        surfaceContainerLow = Color(0xFFEFE8F2),
        surfaceContainer = Color(0xFFE8E0EB),
        surfaceContainerHigh = Color(0xFFE0D8E4),
        surfaceContainerHighest = Color(0xFFD8D0DC),

        surfaceBright = Color(0xFFF7F5FF),
        surfaceDim = Color(0xFFD8D0E0),

        // ----- Outline -----
        outline = Color(0xFF7A757E),

        // ❌ Было: отсутствовало
        outlineVariant = Color(0xFFCAC4CE),

        // ❌ Было: отсутствовало
        scrim = Color(0xFF000000),
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
    override val readerBackgroundColorDark: Color = Color(0xFF21212E)
    override val readerBackgroundColorLight: Color = Color(0xFFF7F5FF)

    // Выделение текста — единственное место в ридере где допустим акцентный цвет
    override val readerSelectionColorDark: Color = Color(0x40F3B375)  // оранжевый primary 25%
    override val readerSelectionColorLight: Color = Color(0x4066577E) // фиолетовый primary 25%
}