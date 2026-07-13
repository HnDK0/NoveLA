package my.noveldokusha.coreui.theme.colorscheme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Dynamic color scheme using Material You (Android 12+).
 * Extracts colors from the user's wallpaper.
 * Falls back to Tachiyomi colors on older devices.
 *
 * primaryContainer/onPrimaryContainer переопределены для гарантированного
 * контраста — dynamicColorScheme иногда даёт слишком близкие цвета.
 */
internal class MonetColorScheme(context: Context) : BaseColorScheme() {

    private val originalDark = runCatching {
        dynamicDarkColorScheme(context)
    }.getOrDefault(TachiyomiColorScheme.darkScheme)

    private val originalLight = runCatching {
        dynamicLightColorScheme(context)
    }.getOrDefault(TachiyomiColorScheme.lightScheme)

    override val darkScheme: ColorScheme = originalDark.copy(
        // primaryContainer = заметно отличающийся от фона цвет с альфа
        primaryContainer = originalDark.primary.copy(alpha = 0.25f),
        onPrimaryContainer = originalDark.primary,
    )

    override val lightScheme: ColorScheme = originalLight.copy(
        primaryContainer = originalLight.primary.copy(alpha = 0.15f),
        onPrimaryContainer = originalLight.primary,
    )

    override val readerTextColorDark: Color = Color(0xFFE8E8E8)
    override val readerTextColorLight: Color = Color(0xFF1A1A1A)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA0A0A0)
    override val readerTextSecondaryColorLight: Color = Color(0xFF5A5A5A)
    override val readerBackgroundColorDark: Color = darkScheme.background
    override val readerBackgroundColorLight: Color = lightScheme.background
    override val readerSelectionColorDark: Color = darkScheme.primary.copy(alpha = 0.25f)
    override val readerSelectionColorLight: Color = lightScheme.primary.copy(alpha = 0.25f)
}