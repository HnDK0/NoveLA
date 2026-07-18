package my.noveldokusha.coreui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun ColorScheme.isLightTheme() = background.luminance() > 0.5

// =============================================================================================
// Light scheme
// =============================================================================================
val light_colorScheme = ColorScheme(
    // ----- Primary -----
    // Основные кнопки (Button), FAB, активные иконки в навбаре,
    // индикаторы прогресса, активный таб, FilterChip selected border
    primary = HighlightLight,                           // #0470E6
    // Текст/иконки ПОВЕРХ primary (на кнопках, FAB)
    onPrimary = Color(0xFFFFFFFF),
    // Фон чипов (selected FilterChip), выделенных карточек, FAB extended
    primaryContainer = Color(0xFFD6E4FF),
    // Текст/иконки внутри primaryContainer
    onPrimaryContainer = Color(0xFF001B3D),
    // Используется в inverseSurface-контексте (напр. SnackBar action button)
    inversePrimary = Color(0xFF9ECAFF),

    // ----- Secondary -----
    // Вторичные кнопки, менее акцентные иконки, альтернативные теги
    secondary = Color(0xFF5C6370),
    // Текст/иконки ПОВЕРХ secondary
    onSecondary = Color(0xFFFFFFFF),
    // Фон вторичных чипов, карточек второго уровня
    secondaryContainer = Color(0xFFE0E3EA),
    // Текст/иконки внутри secondaryContainer
    onSecondaryContainer = Color(0xFF1A1C20),

    // ----- Tertiary -----
    // Третичные акценты, бейджи, специальные выделения
    tertiary = Success600,                              // #108219
    // Текст/иконки ПОВЕРХ tertiary
    onTertiary = Color(0xFFFFFFFF),
    // Фон третичных контейнеров
    tertiaryContainer = Success50,                      // #90D388
    // Текст/иконки внутри tertiaryContainer
    onTertiaryContainer = Success900,                   // #094813

    // ----- Error -----
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    // ----- Background -----
    // Основной фон экранов (Scaffold background)
    // ❌ НЕ использовать в ридере — брать readerBackgroundColor
    background = BgLight,                              // #F7F9FA
    // Основной текст на background
    onBackground = TextLight,                           // #1E1715

    // ----- Surface -----
    // Фон карточек (Card), BottomSheet, Dialog, NavigationBar
    surface = BgLight,                                 // #F7F9FA
    // Текст/иконки на surface
    onSurface = TextLight,                              // #1E1715
    // Фон для элементов с чуть большим весом: TextField fill, Chip background
    surfaceVariant = Color(0xFFE4E7EC),
    // Текст/иконки на surfaceVariant: placeholder, неактивные иконки, вторичные подписи
    onSurfaceVariant = SubTextLight,                    // #8C8C8C
    // Оттенок elevation
    surfaceTint = HighlightLight,                       // #0470E6
    // Инвертированные поверхности (SnackBar background)
    inverseSurface = TextLight,                         // #1E1715
    inverseOnSurface = BgLight,                         // #F7F9FA

    // ----- Surface containers -----
    // Lowest: фон под всем, скрытые области
    surfaceContainerLowest = Color(0xFFFFFFFF),
    // Low: фон экранов второго уровня
    surfaceContainerLow = Color(0xFFF1F4F8),
    // Container: карточки, диалоги
    surfaceContainer = SubBgLight,                      // #FFFFFF
    // High: выдвинутые панели, активные карточки
    surfaceContainerHigh = PopupLight,                  // #FFFFFF
    // Highest: попап-меню, тултипы
    surfaceContainerHighest = Color(0xFFD9DDE3),
    // Bright/Dim
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Grey100,                               // #C3C3C3

    // ----- Outline -----
    // Бордеры OutlinedTextField, OutlinedButton, OutlinedCard — основные границы
    outline = Color(0xFF73777F),
    // Divider, разделители списков, тонкие вторичные бордеры
    outlineVariant = Color(0xFFC3C7CF),

    // ----- Scrim -----
    scrim = Color.Black.copy(alpha = 0.5f),
)

// =============================================================================================
// Dark scheme
// =============================================================================================
val dark_colorScheme = ColorScheme(
    // ----- Primary -----
    primary = HighlightDark,                            // #0088FF
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004A80),
    onPrimaryContainer = Color(0xFFD1E4FF),
    inversePrimary = Color(0xFF4DA6FF),

    // ----- Secondary -----
    secondary = Color(0xFFC0C6D0),
    onSecondary = Color(0xFF2C313A),
    secondaryContainer = Color(0xFF43484F),
    onSecondaryContainer = Color(0xFFDCE2EC),

    // ----- Tertiary -----
    tertiary = Color(0xFF78D480),
    onTertiary = Color(0xFF003910),
    tertiaryContainer = Success800,                     // #0B5B16
    onTertiaryContainer = Success50,                    // #90D388

    // ----- Error -----
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // ----- Background -----
    // ❌ НЕ использовать в ридере — брать readerBackgroundColor
    background = BgDark,                               // #0C0C0C
    onBackground = TextDark,                            // #EBEEF1

    // ----- Surface -----
    surface = BgDark,                                  // #0C0C0C
    onSurface = TextDark,                               // #EBEEF1
    // Фон для элементов с чуть большим весом
    surfaceVariant = Color(0xFF42474E),
    // Вторичные подписи, placeholder, неактивные иконки
    onSurfaceVariant = SubTextDark,                     // #BFE1E6EB
    surfaceTint = HighlightDark,                        // #0088FF
    inverseSurface = TextDark,                          // #EBEEF1
    inverseOnSurface = BgDark,                          // #0C0C0C

    // ----- Surface containers -----
    surfaceContainerLowest = Color(0xFF06080B),
    surfaceContainerLow = Color(0xFF0E1114),
    surfaceContainer = Color(0xFF161A1E),
    surfaceContainerHigh = Color(0xFF212529),
    surfaceContainerHighest = Color(0xFF2C3034),
    surfaceBright = Color(0xFF383C40),
    surfaceDim = BgDark,                               // #0C0C0C

    // ----- Outline -----
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),

    // ----- Scrim -----
    scrim = Color.Black.copy(alpha = 0.5f),
)

// =============================================================================================
// Black (AMOLED) scheme
// =============================================================================================
val black_colorScheme = ColorScheme(
    // ----- Primary -----
    primary = HighlightDark,                            // #0088FF
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004A80),
    onPrimaryContainer = Color(0xFFD1E4FF),
    inversePrimary = Color(0xFF4DA6FF),

    // ----- Secondary -----
    secondary = Color(0xFFC0C6D0),
    onSecondary = Color(0xFF2C313A),
    secondaryContainer = Color(0xFF43484F),
    onSecondaryContainer = Color(0xFFDCE2EC),

    // ----- Tertiary -----
    tertiary = Color(0xFF78D480),
    onTertiary = Color(0xFF003910),
    tertiaryContainer = Success800,                     // #0B5B16
    onTertiaryContainer = Success50,                    // #90D388

    // ----- Error -----
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // ----- Background -----
    // ❌ НЕ использовать в ридере — брать readerBackgroundColor
    background = BgBlack,                              // #000000
    onBackground = TextDark,                            // #EBEEF1

    // ----- Surface -----
    surface = BgBlack,                                 // #000000
    onSurface = TextDark,                               // #EBEEF1
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = SubTextDark,                     // #BFE1E6EB
    surfaceTint = HighlightDark,                        // #0088FF
    inverseSurface = TextDark,                          // #EBEEF1
    inverseOnSurface = BgBlack,                         // #000000

    // ----- Surface containers -----
    // AMOLED: не чисто чёрные — контент скроллится позади навбара
    surfaceContainerLowest = Color(0xFF060606),
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceBright = Color(0xFF303030),
    surfaceDim = BgBlack,                              // #000000

    // ----- Outline -----
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),

    // ----- Scrim -----
    scrim = Color.Black.copy(alpha = 0.5f),
)