package my.noveldokusha.coreui.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Reading, refined" redesign schemes — token-for-token ports of the prototype
 * palettes (paper / night / sepia):
 *
 *  paper:  canvas #F5F3ED  surface #FBFAF6  surface-2 #EFEDE6  ink #242622  ink-2 #424640
 *          muted #777A72   line #DEDED5    line-strong #C9CBC1
 *          accent #53604B  accent-soft #E6E9DF  warm #A17C4D  danger #A65A4C
 *
 *  night:  canvas #171916  surface #20231F  surface-2 #292C27  ink #E9E9E1  ink-2 #D0D2C9
 *          muted #A4A79E   line #383C35    line-strong #4A4F46
 *          accent #B2BCA8  accent-soft #30372E  warm #D2AD7C  danger #DB8C7E
 *
 *  sepia:  canvas #EDE5D6  surface #F6F0E4  surface-2 #E9DFCC  ink #302D27  ink-2 #4A4439
 *          muted #786F60   line #D8CEBB    line-strong #C3B69F
 *          accent #6D6952  accent-soft #E2DAC5  warm #9A7446  danger #A65A4C
 *
 * In the prototype, cards ("surface" #FBFAF6) sit slightly LIGHTER than the page
 * canvas (#F5F3ED) in light mode, and slightly lighter than canvas in dark too —
 * mirrored here via the surfaceContainer* roles.
 */
internal object PaperColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFB2BCA8),
        onPrimary = Color(0xFF171916),
        primaryContainer = Color(0xFF30372E),
        onPrimaryContainer = Color(0xFFC9D2BC),
        secondary = Color(0xFFD0D2C9),
        onSecondary = Color(0xFF20231F),
        secondaryContainer = Color(0xFF292C27),
        onSecondaryContainer = Color(0xFFD0D2C9),
        tertiary = Color(0xFFD2AD7C),
        onTertiary = Color(0xFF20231F),
        tertiaryContainer = Color(0xFF4A3A26),
        onTertiaryContainer = Color(0xFFE8C9A2),
        background = Color(0xFF171916),
        onBackground = Color(0xFFE9E9E1),
        surface = Color(0xFF171916),
        onSurface = Color(0xFFE9E9E1),
        surfaceVariant = Color(0xFF20231F),
        onSurfaceVariant = Color(0xFFA4A79E),
        surfaceTint = Color(0xFFB2BCA8),
        inverseSurface = Color(0xFFE9E9E1),
        inverseOnSurface = Color(0xFF171916),
        error = Color(0xFFDB8C7E),
        onError = Color(0xFF20231F),
        errorContainer = Color(0xFF5C3026),
        onErrorContainer = Color(0xFFF2C4B9),
        outline = Color(0xFF4A4F46),
        outlineVariant = Color(0xFF383C35),
        scrim = Color.Black.copy(alpha = 0.5f),
        surfaceBright = Color(0xFF2A2D28),
        surfaceDim = Color(0xFF121411),
        surfaceContainerLowest = Color(0xFF121411),
        surfaceContainerLow = Color(0xFF1B1E1A),
        surfaceContainer = Color(0xFF20231F),
        surfaceContainerHigh = Color(0xFF292C27),
        surfaceContainerHighest = Color(0xFF333731),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF53604B),
        onPrimary = Color(0xFFF5F3ED),
        primaryContainer = Color(0xFFE6E9DF),
        onPrimaryContainer = Color(0xFF3E4838),
        secondary = Color(0xFF424640),
        onSecondary = Color(0xFFFBFAF6),
        secondaryContainer = Color(0xFFEFEEE6),
        onSecondaryContainer = Color(0xFF424640),
        tertiary = Color(0xFFA17C4D),
        onTertiary = Color(0xFFFBFAF6),
        tertiaryContainer = Color(0xFFF0E4D3),
        onTertiaryContainer = Color(0xFF6B4F2B),
        background = Color(0xFFF5F3ED),
        onBackground = Color(0xFF242622),
        surface = Color(0xFFF5F3ED),
        onSurface = Color(0xFF242622),
        surfaceVariant = Color(0xFFEFEBE1),
        onSurfaceVariant = Color(0xFF777A72),
        surfaceTint = Color(0xFF53604B),
        inverseSurface = Color(0xFF242622),
        inverseOnSurface = Color(0xFFF5F3ED),
        error = Color(0xFFA65A4C),
        onError = Color(0xFFFBFAF6),
        errorContainer = Color(0xFFF2DDD7),
        onErrorContainer = Color(0xFF743528),
        outline = Color(0xFFC9CBC1),
        outlineVariant = Color(0xFFDEDED5),
        scrim = Color.Black.copy(alpha = 0.5f),
        surfaceBright = Color(0xFFFBFAF6),
        surfaceDim = Color(0xFFEDEBE2),
        surfaceContainerLowest = Color(0xFFFBFAF6),
        surfaceContainerLow = Color(0xFFF7F5EE),
        surfaceContainer = Color(0xFFFBFAF6),
        surfaceContainerHigh = Color(0xFFEFEBE1),
        surfaceContainerHighest = Color(0xFFE9E6DA),
    )

    override val readerTextColorDark: Color = Color(0xFFD0D2C9)
    override val readerTextColorLight: Color = Color(0xFF424640)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA4A79E)
    override val readerTextSecondaryColorLight: Color = Color(0xFF777A72)
    override val readerBackgroundColorDark: Color = Color(0xFF171916)
    override val readerBackgroundColorLight: Color = Color(0xFFF5F3ED)
    override val readerSelectionColorDark: Color = Color(0xFFB2BCA8).copy(alpha = 0.25f)
    override val readerSelectionColorLight: Color = Color(0xFF53604B).copy(alpha = 0.25f)
}

internal object SepiaColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFB2BCA8),
        onPrimary = Color(0xFF171916),
        primaryContainer = Color(0xFF30372E),
        onPrimaryContainer = Color(0xFFC9D2BC),
        secondary = Color(0xFFD0D2C9),
        onSecondary = Color(0xFF20231F),
        secondaryContainer = Color(0xFF292C27),
        onSecondaryContainer = Color(0xFFD0D2C9),
        tertiary = Color(0xFFD2AD7C),
        onTertiary = Color(0xFF20231F),
        tertiaryContainer = Color(0xFF4A3A26),
        onTertiaryContainer = Color(0xFFE8C9A2),
        background = Color(0xFF171916),
        onBackground = Color(0xFFE9E9E1),
        surface = Color(0xFF171916),
        onSurface = Color(0xFFE9E9E1),
        surfaceVariant = Color(0xFF20231F),
        onSurfaceVariant = Color(0xFFA4A79E),
        surfaceTint = Color(0xFFB2BCA8),
        inverseSurface = Color(0xFFE9E9E1),
        inverseOnSurface = Color(0xFF171916),
        error = Color(0xFFDB8C7E),
        onError = Color(0xFF20231F),
        errorContainer = Color(0xFF5C3026),
        onErrorContainer = Color(0xFFF2C4B9),
        outline = Color(0xFF4A4F46),
        outlineVariant = Color(0xFF383C35),
        scrim = Color.Black.copy(alpha = 0.5f),
        surfaceBright = Color(0xFF2A2D28),
        surfaceDim = Color(0xFF121411),
        surfaceContainerLowest = Color(0xFF121411),
        surfaceContainerLow = Color(0xFF1B1E1A),
        surfaceContainer = Color(0xFF20231F),
        surfaceContainerHigh = Color(0xFF292C27),
        surfaceContainerHighest = Color(0xFF333731),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFF6D6952),
        onPrimary = Color(0xFFF6F0E4),
        primaryContainer = Color(0xFFE2DAC5),
        onPrimaryContainer = Color(0xFF55523E),
        secondary = Color(0xFF4A4439),
        onSecondary = Color(0xFFF6F0E4),
        secondaryContainer = Color(0xFFE5DCC8),
        onSecondaryContainer = Color(0xFF4A4439),
        tertiary = Color(0xFF9A7446),
        onTertiary = Color(0xFFF6F0E4),
        tertiaryContainer = Color(0xFFEBD9BC),
        onTertiaryContainer = Color(0xFF6E4F2A),
        background = Color(0xFFEDE5D6),
        onBackground = Color(0xFF302D27),
        surface = Color(0xFFEDE5D6),
        onSurface = Color(0xFF302D27),
        surfaceVariant = Color(0xFFE9DFCC),
        onSurfaceVariant = Color(0xFF786F60),
        surfaceTint = Color(0xFF6D6952),
        inverseSurface = Color(0xFF302D27),
        inverseOnSurface = Color(0xFFEDE5D6),
        error = Color(0xFFA65A4C),
        onError = Color(0xFFF6F0E4),
        errorContainer = Color(0xFFEFDBD2),
        onErrorContainer = Color(0xFF6E3428),
        outline = Color(0xFFC3B69F),
        outlineVariant = Color(0xFFD8CEBB),
        scrim = Color.Black.copy(alpha = 0.5f),
        surfaceBright = Color(0xFFF6F0E4),
        surfaceDim = Color(0xFFE3DAC9),
        surfaceContainerLowest = Color(0xFFF6F0E4),
        surfaceContainerLow = Color(0xFFF1EADC),
        surfaceContainer = Color(0xFFF6F0E4),
        surfaceContainerHigh = Color(0xFFE9DFCC),
        surfaceContainerHighest = Color(0xFFE2D7BF),
    )

    override val readerTextColorDark: Color = Color(0xFFD0D2C9)
    override val readerTextColorLight: Color = Color(0xFF4A4439)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA4A79E)
    override val readerTextSecondaryColorLight: Color = Color(0xFF786F60)
    override val readerBackgroundColorDark: Color = Color(0xFF171916)
    override val readerBackgroundColorLight: Color = Color(0xFFEDE5D6)
    override val readerSelectionColorDark: Color = Color(0xFFB2BCA8).copy(alpha = 0.25f)
    override val readerSelectionColorLight: Color = Color(0xFF6D6952).copy(alpha = 0.25f)
}

internal object NightColorScheme : BaseColorScheme() {

    // Night is a dark-first scheme: both DarkMode.DARK and DARK+AMOLED render this.
    override val darkScheme = PaperColorScheme.darkScheme

    // Light-mode fallback for the NIGHT entry (rarely used; keeps the sage accent).
    override val lightScheme = PaperColorScheme.lightScheme

    override val readerTextColorDark: Color = Color(0xFFD0D2C9)
    override val readerTextColorLight: Color = Color(0xFF424640)
    override val readerTextSecondaryColorDark: Color = Color(0xFFA4A79E)
    override val readerTextSecondaryColorLight: Color = Color(0xFF777A72)
    override val readerBackgroundColorDark: Color = Color(0xFF171916)
    override val readerBackgroundColorLight: Color = Color(0xFFF5F3ED)
    override val readerSelectionColorDark: Color = Color(0xFFB2BCA8).copy(alpha = 0.25f)
    override val readerSelectionColorLight: Color = Color(0xFF53604B).copy(alpha = 0.25f)
}
