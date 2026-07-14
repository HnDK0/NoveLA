package my.noveldokusha.coreui.components.editor

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SyntaxColors(
    val background:     Color,
    val foreground:     Color,
    val gutter:         Color,
    val gutterText:     Color,
    val currentLine:    Color,
    val selection:      Color,
    val keyword:        Color,
    val string:         Color,
    val number:         Color,
    val comment:        Color,
    val function:       Color,
    val type:           Color,
    val operator:       Color,
)

val LocalSyntaxColors = staticCompositionLocalOf {
    // Fallback to default Dark+ colors if none provided
    SyntaxColors(
        background  = Color(0xFF1E1E1E), foreground = Color(0xFFD4D4D4),
        gutter      = Color(0xFF1E1E1E), gutterText = Color(0xFF858585),
        currentLine = Color(0xFF2A2D2E), selection  = Color(0xFF264F78),
        keyword     = Color(0xFF569CD6), string     = Color(0xFFCE9178),
        number      = Color(0xFFB5CEA8), comment    = Color(0xFF6A9955),
        function    = Color(0xFFDCDCAA), type       = Color(0xFF4EC9B0),
        operator    = Color(0xFFD4D4D4),
    )
}
