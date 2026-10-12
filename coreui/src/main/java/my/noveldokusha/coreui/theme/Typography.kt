package my.noveldokusha.coreui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import my.noveldokusha.coreui.R

/**
 * Redesign typography ("Reading, refined"):
 *  - DM Sans for all UI text (labels, rows, buttons, chips)
 *  - Literata (serif) reserved for page titles / section headings / editorial accents,
 *    mirroring the prototype where .page-title / .bottom-sheet h2 use var(--serif).
 */
private val Serif = FontFamily(
    Font(
        resId = R.font.literata_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.literata_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.literata_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.literata_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private val Sans = FontFamily(
    Font(
        resId = R.font.dmsans_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = R.font.dmsans_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = R.font.dmsans_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = R.font.dmsans_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

val typography = Typography(
    // Editorial page titles (serif) — prototype .page-title 24-27px Literata 500
    displayLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, letterSpacing = (-0.03).sp),
    displayMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 30.sp, letterSpacing = (-0.03).sp),
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = (-0.035).sp),
    headlineLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, letterSpacing = (-0.03).sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 24.sp, letterSpacing = (-0.035).sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 21.sp, letterSpacing = (-0.04).sp),
    // Serif sheet/section headings — prototype .bottom-sheet h2 20px, serif section h2 16px
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 19.sp, letterSpacing = (-0.025).sp),
    // Sans section heads — prototype .section-head h2 14px/700
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = (-0.025).sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = (-0.01).sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    // Buttons/chips — prototype 11px/700
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
)
