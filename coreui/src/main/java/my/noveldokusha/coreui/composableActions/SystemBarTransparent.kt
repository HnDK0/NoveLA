package my.noveldokusha.coreui.composableActions

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import my.noveldokusha.coreui.theme.isLightTheme

@Composable
fun SetSystemBarTransparent(alpha: Float = 0f) {
    val context = LocalContext.current
    val useDarkIcons = MaterialTheme.colorScheme.isLightTheme()
    val baseColor = MaterialTheme.colorScheme.primary
    val color = remember(alpha, baseColor) { baseColor.copy(alpha = alpha) }
    SideEffect {
        val activity = context as? ComponentActivity ?: return@SideEffect
        val colorInt = color.toArgb()
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(colorInt, colorInt) { !useDarkIcons },
            navigationBarStyle = SystemBarStyle.auto(colorInt, colorInt) { !useDarkIcons }
        )
    }
}