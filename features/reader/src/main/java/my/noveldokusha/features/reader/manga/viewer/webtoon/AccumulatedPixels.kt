package my.noveldokusha.features.reader.manga.viewer.webtoon

import kotlin.math.round

/**
 * Накопление пикселей автопрокрутки (чистая функция для JVM-тестов).
 *
 * Субпиксельная скорость (например, 8.7 px за тик 16 мс) не может уйти в
 * scrollBy напрямую: округление каждого тика теряло бы ~0.3 px (≈7 px/с при
 * 60 fps) и давало бы дёрганый скролл. Вместо этого [totalPixels] копит
 * полную дистанцию (включая дробную часть), а на тик отдаётся разность
 * целых частей до и после — сумма целых за N тиков сходится к скорости × N
 * без потерь.
 */
internal fun accumulatedPixels(
    speedPxPerSec: Float,
    frameDeltaMs: Long,
    totalPixels: Float,
): Pair<Int, Float> {
    // Снап к 0.01 px: float32 (8.7f = 8.6999998) при накоплении тянет итог
    // вниз (10 тиков дали бы 86.999998, а не 87); снап гасит этот шум, не
    // трогая реальные дроби. Накопление в Double убирает дрейф на длинных сессиях.
    val newTotal = round((totalPixels + speedPxPerSec * frameDeltaMs / 1000f) * 100.0) / 100.0
    val delta = newTotal.toInt() - totalPixels.toInt()
    return delta to newTotal.toFloat()
}