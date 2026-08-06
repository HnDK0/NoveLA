package my.noveldokusha.features.reader

enum class ReaderAutoScrollFollow { ACTIVE, PAUSED }

fun nextFollowState(
    current: ReaderAutoScrollFollow,
    manualGestureActive: Boolean,
    currentParagraphVisible: Boolean,
): ReaderAutoScrollFollow = when {
    currentParagraphVisible -> ReaderAutoScrollFollow.ACTIVE
    manualGestureActive -> ReaderAutoScrollFollow.PAUSED
    else -> current
}

fun shouldIssueSmoothScroll(
    lastTarget: Pair<Int, Int>?,
    lastStartedAt: Long,
    newTarget: Pair<Int, Int>,
    now: Long,
    windowMs: Long = 500L,
): Boolean = newTarget != lastTarget || now - lastStartedAt >= windowMs
