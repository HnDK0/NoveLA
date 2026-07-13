package my.noveldokusha.coreui.theme

import androidx.annotation.StringRes
import my.noveldokusha.coreui.R

enum class AppTheme(
    @StringRes val titleRes: Int,
    val isMonet: Boolean = false,
) {
    DEFAULT(R.string.theme_name_monet, isMonet = true),
    TACHIYOMI(R.string.theme_name_tachiyomi),
    GREEN_APPLE(R.string.theme_name_green_apple),
    LAVENDER(R.string.theme_name_lavender),
    MIDNIGHT_DUSK(R.string.theme_name_midnight_dusk),
    STRAWBERRY_DAIQUIRI(R.string.theme_name_strawberry_daiquiri),
    TAKO(R.string.theme_name_tako),
    TEALTURQUOISE(R.string.theme_name_teal_turquoise),
    TIDAL_WAVE(R.string.theme_name_tidal_wave),
    YOTSUBA(R.string.theme_name_yotsuba),
    MONOCHROME(R.string.theme_name_monochrome),
    CATPPUCCIN(R.string.theme_name_catppuccin),
    NORD(R.string.theme_name_nord),
    YINYANG(R.string.theme_name_yinyang),
    CLOUDFLARE(R.string.theme_name_cloudflare),
    COTTONCANDY(R.string.theme_name_cottoncandy),
    DOOM(R.string.theme_name_doom),
    MATRIX(R.string.theme_name_matrix),
    MOCHA(R.string.theme_name_mocha),
    SAPPHIRE(R.string.theme_name_sapphire),
}