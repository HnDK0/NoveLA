package my.noveldokusha.text_to_speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextToSpeechManagerCalibrationTest {

    private fun assertClose(expected: Float, actual: Float?, eps: Float = 0.05f) {
        assertEquals(expected, actual ?: Float.NaN, eps)
    }

    @Test
    fun `ratio is one when wall and marker clocks run in sync`() {
        // Каждый абзац: 1000 мс маркерного звучат ровно 1000 мс реального времени.
        val slope = calibrationRegressionSlope(
            listOf(
                1_000L to 1_000L,
                2_000L to 2_000L,
                3_000L to 3_000L,
            )
        )
        assertClose(1.0f, slope)
    }

    @Test
    fun `ratio scales slow playback up`() {
        // Сетевой голос играет вдвое медленнее маркерного темпа: те же маркерные секунды
        // звучат вдвое дольше. Регрессия маркерные_мс->wall даёт slope 2.0.
        val slope = calibrationRegressionSlope(
            listOf(
                1_000L to 2_000L,
                2_000L to 4_000L,
                3_000L to 6_000L,
            )
        )
        assertClose(2.0f, slope)
    }

    @Test
    fun `constant paragraph tail is subtracted`() {
        // Хвост абзаца (пауза движка после последнего слова до onFinished) ~300 мс
        // одинаков для всех абзацев и сидит в intercept: короткий абзац из 1000 мс
        // при чистом делении дал бы 1300/1000 = 1.3, но регрессия по разным длинам
        // абзацев вычитает константу и возвращает истинный ratio 1.0.
        val slope = calibrationRegressionSlope(
            listOf(
                1_000L to 1_300L,
                2_000L to 2_300L,
                3_000L to 3_300L,
            )
        )
        assertClose(1.0f, slope)
    }

    @Test
    fun `null when fewer than minimum pairs`() {
        assertNull(calibrationRegressionSlope(emptyList()))
        assertNull(calibrationRegressionSlope(listOf(1_000L to 2_000L)))
    }

    @Test
    fun `null when frame does not advance between pairs`() {
        // Все абзацы одинаковой маркерной длительности — нулевой разброс по X, slope не определён.
        assertNull(
            calibrationRegressionSlope(
                listOf(
                    1_000L to 2_000L,
                    1_000L to 2_500L,
                    1_000L to 3_000L,
                )
            )
        )
    }
}
