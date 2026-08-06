import my.noveldokusha.features.reader.ui.manualHighlightDragDelta
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Регрессионные тесты геометрии драга панели ручной подсветки (#117).
 *
 * Константы совпадают с реальными размерами в ManualHighlightPill:
 * квадрат 36dp, панель ~150dp, ширина экрана 1080px.
 * Старый код пересчитывал offsetX из абсолютной клампированной позиции панели,
 * из-за чего у края экрана маркер «телепортировался» внутрь на (P-S)/2 ≈ 57px
 * при первом же событии драга, и после закрытия квадрат возвращался левее.
 */
class ManualHighlightDragDeltaTest {

    private val squareSize = 36f
    private val panelWidth = 150f
    private val maxPanelX = 1080f - panelWidth // 930

    @Test
    fun noClampMidScreenMovesExactlyByDrag() {
        val delta = manualHighlightDragDelta(
            startOffset = 300f,
            squareSize = squareSize,
            panelSize = panelWidth,
            maxPanel = maxPanelX,
            dragAmount = -20f,
        )
        assertEquals(-20f, delta, 0.001f)
    }

    @Test
    fun rightEdgeClampedPanelDoesNotTeleportMarker() {
        // Маркер у правого края: offsetX = W - S = 1044, панель клампится к W - P = 930.
        // Драг на 20px влево: панель реально сдвинулась на 20px — маркер тоже ровно на 20.
        // Старый код давал -77px (телепорт на (P-S)/2 + драг).
        val delta = manualHighlightDragDelta(
            startOffset = 1044f,
            squareSize = squareSize,
            panelSize = panelWidth,
            maxPanel = maxPanelX,
            dragAmount = -20f,
        )
        assertEquals(-20f, delta, 0.001f)
    }

    @Test
    fun pinnedAtEdgeDragIntoEdgeMovesNothing() {
        // Панель уже прижата к правому краю, драг вправо — движения нет.
        val delta = manualHighlightDragDelta(
            startOffset = 1044f,
            squareSize = squareSize,
            panelSize = panelWidth,
            maxPanel = maxPanelX,
            dragAmount = 50f,
        )
        assertEquals(0f, delta, 0.001f)
    }

    @Test
    fun leftEdgeClampedPanelMovesByDrag() {
        // Маркер у левого края: offsetX = 0, панель клампится к 0. Драг вправо на 30.
        val delta = manualHighlightDragDelta(
            startOffset = 0f,
            squareSize = squareSize,
            panelSize = panelWidth,
            maxPanel = maxPanelX,
            dragAmount = 30f,
        )
        assertEquals(30f, delta, 0.001f)
    }

    @Test
    fun dragFarRightMovesMarkerUntilPanelPins() {
        // Маркер на 900, центр панели на 843. Драг вправо: панель двигается,
        // пока не упрётся в край (930), маркер — на ту же дельту (87), дальше стоп.
        val delta = manualHighlightDragDelta(
            startOffset = 900f,
            squareSize = squareSize,
            panelSize = panelWidth,
            maxPanel = maxPanelX,
            dragAmount = 500f,
        )
        assertEquals(87f, delta, 0.001f)
    }

    @Test
    fun zeroPanelSizeCollapsedStateBehavesLikeNoClamp() {
        // Свёрнутое состояние: panelSize == squareSize, дельта ровно равна драгу.
        val delta = manualHighlightDragDelta(
            startOffset = 700f,
            squareSize = squareSize,
            panelSize = squareSize,
            maxPanel = maxPanelX,
            dragAmount = -15f,
        )
        assertEquals(-15f, delta, 0.001f)
    }
}
