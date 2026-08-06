package my.noveldokusha.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAutoScrollTest {

    @Test
    fun visibleParagraphActivatesFollow() {
        assertEquals(
            ReaderAutoScrollFollow.ACTIVE,
            nextFollowState(ReaderAutoScrollFollow.PAUSED, manualGestureActive = true, currentParagraphVisible = true)
        )
        assertEquals(
            ReaderAutoScrollFollow.ACTIVE,
            nextFollowState(ReaderAutoScrollFollow.PAUSED, manualGestureActive = false, currentParagraphVisible = true)
        )
        assertEquals(
            ReaderAutoScrollFollow.ACTIVE,
            nextFollowState(ReaderAutoScrollFollow.ACTIVE, manualGestureActive = false, currentParagraphVisible = true)
        )
    }

    @Test
    fun manualGestureWithParagraphOffScreenPausesFollow() {
        assertEquals(
            ReaderAutoScrollFollow.PAUSED,
            nextFollowState(ReaderAutoScrollFollow.ACTIVE, manualGestureActive = true, currentParagraphVisible = false)
        )
        assertEquals(
            ReaderAutoScrollFollow.PAUSED,
            nextFollowState(ReaderAutoScrollFollow.PAUSED, manualGestureActive = true, currentParagraphVisible = false)
        )
    }

    @Test
    fun offScreenWithoutGestureKeepsCurrentState() {
        assertEquals(
            ReaderAutoScrollFollow.ACTIVE,
            nextFollowState(ReaderAutoScrollFollow.ACTIVE, manualGestureActive = false, currentParagraphVisible = false)
        )
        assertEquals(
            ReaderAutoScrollFollow.PAUSED,
            nextFollowState(ReaderAutoScrollFollow.PAUSED, manualGestureActive = false, currentParagraphVisible = false)
        )
    }

    @Test
    fun sameTargetWithinWindowIsNotReissued() {
        val target = 10 to 200
        assertFalse(shouldIssueSmoothScroll(lastTarget = target, lastStartedAt = 1_000L, newTarget = target, now = 1_200L))
    }

    @Test
    fun sameTargetAfterWindowIsReissued() {
        val target = 10 to 200
        assertTrue(shouldIssueSmoothScroll(lastTarget = target, lastStartedAt = 1_000L, newTarget = target, now = 1_600L))
    }

    @Test
    fun differentTargetIsAlwaysReissued() {
        assertTrue(shouldIssueSmoothScroll(lastTarget = 10 to 200, lastStartedAt = 1_000L, newTarget = 11 to 200, now = 1_100L))
    }

    @Test
    fun noPreviousTargetIsReissued() {
        assertTrue(shouldIssueSmoothScroll(lastTarget = null, lastStartedAt = 0L, newTarget = 10 to 200, now = 1_100L))
    }
}
