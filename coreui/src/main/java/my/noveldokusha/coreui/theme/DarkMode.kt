package my.noveldokusha.coreui.theme

import androidx.annotation.StringRes
import my.noveldokusha.coreui.R

enum class DarkMode(
    @StringRes val titleRes: Int,
) {
    SYSTEM(R.string.dark_mode_system),
    LIGHT(R.string.dark_mode_light),
    DARK(R.string.dark_mode_dark),
    BLACK(R.string.dark_mode_black);

    val isLight: Boolean get() = this == LIGHT
    val isDark: Boolean get() = this == DARK || this == BLACK
    val isAmoled: Boolean get() = this == BLACK
}