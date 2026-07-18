package my.noveldokusha.coreui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Reader-specific color configuration.
 *
 * All fields are nullable — when null, the corresponding [MaterialTheme.colorScheme]
 * field is used as-is. When non-null, the reader theme overrides the material scheme.
 */
@Immutable
data class ReaderColorConfig(
    val background: Color? = null,
    val onBackground: Color? = null,
    val surface: Color? = null,
    val onSurface: Color? = null,
    val primary: Color? = null,
    val onPrimary: Color? = null,
    val surfaceVariant: Color? = null,
    val onSurfaceVariant: Color? = null,
    val outline: Color? = null,
    val error: Color? = null,
    val surfaceContainer: Color? = null,
    val surfaceContainerHigh: Color? = null,
)

/** Default (empty) reader color config — no overrides. */
val DefaultReaderColorConfig = ReaderColorConfig()

/** CompositionLocal for passing reader-specific color overrides down the tree. */
val LocalReaderColorConfig = staticCompositionLocalOf { DefaultReaderColorConfig }

/**
 * A composable function that wraps content with reader-specific color overrides.
 *
 * Usage:
 * ```
 * readerTheme(readerColors = myReaderColors) {
 *     // All MaterialTheme.colorScheme usages here will be overridden
 *     // with reader-specific colors.
 *     ReaderScreen(...)
 * }
 * ```
 *
 * Any non-null field in [readerColors] overrides the corresponding
 * [MaterialTheme.colorScheme] field while preserving all other fields.
 */
@Composable
fun readerTheme(
    readerColors: ReaderColorConfig = DefaultReaderColorConfig,
    content: @Composable () -> Unit,
) {
    val baseScheme = MaterialTheme.colorScheme

    val overriddenScheme = if (readerColors.hasOverrides) {
        baseScheme.copy(
            background = readerColors.background ?: baseScheme.background,
            onBackground = readerColors.onBackground ?: baseScheme.onBackground,
            surface = readerColors.surface ?: baseScheme.surface,
            onSurface = readerColors.onSurface ?: baseScheme.onSurface,
            primary = readerColors.primary ?: baseScheme.primary,
            onPrimary = readerColors.onPrimary ?: baseScheme.onPrimary,
            surfaceVariant = readerColors.surfaceVariant ?: baseScheme.surfaceVariant,
            onSurfaceVariant = readerColors.onSurfaceVariant ?: baseScheme.onSurfaceVariant,
            outline = readerColors.outline ?: baseScheme.outline,
            error = readerColors.error ?: baseScheme.error,
            surfaceContainer = readerColors.surfaceContainer ?: baseScheme.surfaceContainer,
            surfaceContainerHigh = readerColors.surfaceContainerHigh ?: baseScheme.surfaceContainerHigh,
        )
    } else {
        baseScheme
    }

    CompositionLocalProvider(LocalReaderColorConfig provides readerColors) {
        MaterialTheme(
            colorScheme = overriddenScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/** Convenience extension to check if the config has any overrides. */
val ReaderColorConfig.hasOverrides: Boolean
    get() = background != null || onBackground != null || surface != null ||
            onSurface != null || primary != null || onPrimary != null ||
            surfaceVariant != null || onSurfaceVariant != null ||
            outline != null || error != null ||
            surfaceContainer != null || surfaceContainerHigh != null

/**
 * Light reader color presets.
 */
object ReaderPresets {

    /** Default reader — uses app theme colors, no overrides. */
    val DEFAULT = ReaderColorConfig()

    /** Sepia / warm paper background. */
    val SEPIA = ReaderColorConfig(
        background = Color(0xFFF5F0E8),
        onBackground = Color(0xFF3B2F1E),
        surface = Color(0xFFF5F0E8),
        onSurface = Color(0xFF3B2F1E),
        surfaceVariant = Color(0xFFEDE5D8),
        onSurfaceVariant = Color(0xFF5C4E3A),
        outline = Color(0xFFC4B89A),
    )

    /** Dark reader — dark surface with light text. */
    val DARK = ReaderColorConfig(
        background = Color(0xFF1A1A1A),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF1A1A1A),
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFB0B0B0),
        outline = Color(0xFF505050),
    )

    /** Pure black for AMOLED screens. */
    val AMOLED = ReaderColorConfig(
        background = Color.Black,
        onBackground = Color(0xFFE0E0E0),
        surface = Color.Black,
        onSurface = Color(0xFFE0E0E0),
        surfaceVariant = Color(0xFF0D0D0D),
        onSurfaceVariant = Color(0xFFB0B0B0),
        outline = Color(0xFF404040),
    )

    /** Cool blue-tinted dark reader. */
    val BLUE_DARK = ReaderColorConfig(
        background = Color(0xFF121620),
        onBackground = Color(0xFFD8E0F0),
        surface = Color(0xFF121620),
        onSurface = Color(0xFFD8E0F0),
        surfaceVariant = Color(0xFF1C2230),
        onSurfaceVariant = Color(0xFFA0A8C0),
        outline = Color(0xFF3A4060),
    )
}