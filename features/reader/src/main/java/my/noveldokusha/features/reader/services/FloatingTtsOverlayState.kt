package my.noveldokusha.features.reader.services

internal enum class FloatingTtsOverlayPanelSize(val widthDp: Float) {
    Small(280f),
    Medium(336f),
    Large(420f);

    fun next(): FloatingTtsOverlayPanelSize = when (this) {
        Small -> Medium
        Medium -> Large
        Large -> Small
    }
}

internal data class FloatingTtsOverlayState(
    val isPlaying: Boolean = false,
    val isActive: Boolean = false,
    val speed: Float = 1f,
    val remainingSeconds: Int = 0,
    val currentParagraph: String = "",
    val liveParagraphEnabled: Boolean = false,
    val showRemainingTime: Boolean = true,
    val showPlaybackSpeed: Boolean = true,
    val opacity: Float = 0.92f,
    val bubbleSizeDp: Float = 56f,
    val panelSize: FloatingTtsOverlayPanelSize = FloatingTtsOverlayPanelSize.Medium,
    val collapsed: Boolean = true,
)
