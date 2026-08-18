package my.noveldokusha.features.reader.manga.viewer.webtoon

import org.junit.Assert.assertEquals
import org.junit.Test

class AccumulatedPixelsTest {

    /** Скорость (px/с), дающая ровно [pxPerTick] за тик 16 мс. */
    private fun speedForPxPerTick(pxPerTick: Float) = pxPerTick * 1000f / 16f

    /** Суммарные пиксели за [ticks] тиков (симулирует цикл runAutoScroll). */
    private fun totalPixels(speedPxPerSec: Float, ticks: Int): Int {
        var totalPixels = 0f
        var total = 0
        repeat(ticks) {
            val (pixels, newTotal) = accumulatedPixels(speedPxPerSec, 16L, totalPixels)
            totalPixels = newTotal
            total += pixels
        }
        return total
    }

    @Test
    fun fractionalSpeedAccumulatesToExactTotal() {
        // 8.7 px/тик × 10 тиков = 87 px: дробные остатки не теряются.
        assertEquals(87, totalPixels(speedForPxPerTick(8.7f), ticks = 10))
    }

    @Test
    fun singleTickKeepsFractionInAccumulator() {
        // Дробная часть остаётся в аккумуляторе для следующего тика.
        val (pixels, newTotal) = accumulatedPixels(speedForPxPerTick(8.7f), 16L, totalPixels = 0f)
        assertEquals(8, pixels)
        assertEquals(8.7f, newTotal, 1e-6f)
    }

    @Test
    fun exactIntegerSpeedHasNoDrift() {
        // 5 px/тик × 4 тика = 20 px, остаток всегда 0.
        assertEquals(20, totalPixels(speedForPxPerTick(5f), ticks = 4))
    }

    @Test
    fun zeroSpeedEmitsNothingAndKeepsAccumulator() {
        val (pixels, newTotal) = accumulatedPixels(speedPxPerSec = 0f, frameDeltaMs = 16L, totalPixels = 0.5f)
        assertEquals(0, pixels)
        assertEquals(0.5f, newTotal, 1e-6f)
        assertEquals(0, totalPixels(speedPxPerSec = 0f, ticks = 10))
    }

    @Test
    fun negativeSpeedAccumulatesSymmetrically() {
        // Скролл вверх: -8.7 px/тик × 10 тиков = -87 px.
        assertEquals(-87, totalPixels(speedForPxPerTick(-8.7f), ticks = 10))
    }
}