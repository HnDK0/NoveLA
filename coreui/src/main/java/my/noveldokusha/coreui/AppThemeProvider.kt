package my.noveldokusha.coreui

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.CoroutineScope
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.coreui.theme.ThemeProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AppThemeProvider @Inject constructor(
    private val appPreferences: AppPreferences
) : ThemeProvider {

    override fun currentAppTheme(stateCoroutineScope: CoroutineScope): State<AppTheme> = derivedStateOf {
        val raw = appPreferences.APP_THEME.state(stateCoroutineScope).value
        runCatching { enumValueOf<AppTheme>(raw) }.getOrDefault(AppTheme.DEFAULT)
    }

    override fun currentDarkMode(stateCoroutineScope: CoroutineScope): State<DarkMode> = derivedStateOf {
        val raw = appPreferences.THEME_DARK_MODE.state(stateCoroutineScope).value
        runCatching { enumValueOf<DarkMode>(raw) }.getOrDefault(DarkMode.SYSTEM)
    }
}