package my.noveldokusha.coreui.theme

import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope

interface ThemeProvider {

    fun currentAppTheme(stateCoroutineScope: CoroutineScope): State<AppTheme>

    fun currentDarkMode(stateCoroutineScope: CoroutineScope): State<DarkMode>
}
