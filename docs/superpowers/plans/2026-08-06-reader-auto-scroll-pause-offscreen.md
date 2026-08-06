# Reader auto-scroll: pause only when the current paragraph is off-screen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the reader's TTS auto-scroll pause only when a manual scroll moves the current paragraph off-screen (and resume when it is visible again or on Focus/paragraph navigation), without changing default behavior or fighting manual scrolling, and fix the auto-scroll jitter.

**Architecture:** A tiny pure follow-state machine (`ReaderAutoScroll.kt`) decides ACTIVE/PAUSED from gesture + visibility inputs; `ReaderActivity.kt` feeds it from the scroll listeners and gates the follow-scroll path on it, plus dedupes identical programmatic smooth scrolls. Only the `features/reader` module changes.

**Tech Stack:** Kotlin, Android `AbsListView`, JUnit 4, GitHub Actions (`buildRelease.yml` for APK).

## Global Constraints

- Branch: `fix/reader-auto-scroll-offscreen-pause` (fresh, off `origin/default` = `7fe3fb94`). PR base: `default`, head: this branch.
- **No local Android build possible** (Termux has no Android SDK/JDK). All compile/test verification happens in GitHub Actions. Do not claim a local build passed.
- Only these files may change in the PR: `ReaderAutoScroll.kt` (new), `ReaderActivity.kt`, `ReaderAutoScrollTest.kt` (new), and the docs (`docs/superpowers/specs/2026-08-06-reader-auto-scroll-pause-offscreen-design.md`). The temp workflow is removed before the PR.
- **NEVER modify** `.github/workflows/buildRelease.yml`.
- The temporary workflow (Task 3) MUST be removed (`git rm`) and committed away before the PR; it must not appear in the PR diff.
- Release-build dispatch (Task 4) uses `ref: fix/reader-auto-scroll-offscreen-pause` (NOT `default`) so the APK actually builds this branch's code.
- The GitHub PAT is a sensitive secret: use it only in `curl` Authorization headers / the push URL. **NEVER write it into any repo file** and never include it in reports or commit messages. In this plan it is written as `<PAT>`; the implementer receives the actual value from the dispatch prompt.
- Existing `ReaderActivity.kt` comments are in Russian — match that style in new comments.
- Do not touch TTS playback, highlighting, translation, the adapter, chapter loading, settings, resources, or any other module.

---

### Task 1: Pure follow-state logic + unit tests

**Files:**
- Create: `features/reader/src/main/java/my/noveldokusha/features/reader/ReaderAutoScroll.kt`
- Test: `features/reader/src/test/java/my/noveldokusha/features/reader/ReaderAutoScrollTest.kt`

**Interfaces:**
- Produces (used by Task 2, same package — no import needed):
  - `enum class ReaderAutoScrollFollow { ACTIVE, PAUSED }`
  - `fun nextFollowState(current: ReaderAutoScrollFollow, manualGestureActive: Boolean, currentParagraphVisible: Boolean): ReaderAutoScrollFollow`
  - `fun shouldIssueSmoothScroll(lastTarget: Pair<Int, Int>?, lastStartedAt: Long, newTarget: Pair<Int, Int>, now: Long, windowMs: Long = 500L): Boolean`

Note on TDD order: this environment cannot run Gradle locally (no JDK/SDK). Tests are written before the implementation; the "red" state is verified by inspection at review (tests reference not-yet-created functions), and the "green" state is verified in CI (Task 3, step `:features:reader:testDebugUnitTest`).

- [ ] **Step 1: Write the failing tests**

Create `features/reader/src/test/java/my/noveldokusha/features/reader/ReaderAutoScrollTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Write the minimal implementation**

Create `features/reader/src/main/java/my/noveldokusha/features/reader/ReaderAutoScroll.kt`:

```kotlin
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
```

- [ ] **Step 3: Commit**

```bash
git add features/reader/src/main/java/my/noveldokusha/features/reader/ReaderAutoScroll.kt \
        features/reader/src/test/java/my/noveldokusha/features/reader/ReaderAutoScrollTest.kt
git commit -m "feat(reader): pure auto-scroll follow-state logic with tests"
```

---

### Task 2: Wire the state machine into ReaderActivity.kt

**Files:**
- Modify: `features/reader/src/main/java/my/noveldokusha/features/reader/ReaderActivity.kt`

**Interfaces:**
- Consumes (from Task 1): `ReaderAutoScrollFollow`, `nextFollowState`, `shouldIssueSmoothScroll` (same package).
- Produces: the `followScrollEnabled`, `manualGestureActive`, `lastSmoothScrollTarget`, `lastSmoothScrollStart` fields, the `isCurrentParagraphVisible()` / `followSmoothScrollTo(...)` helpers — all private to `ReaderActivity`.

All edits below are `git apply`-able context blocks. Apply them in order.

- [ ] **Step 1: Replace the follow flag and add new fields**

Find (currently around lines 136–138):

```kotlin
    // Ручной скролл приостанавливает follow-скролл за TTS-абзацем до нажатия
    // кнопки «Фокус» (или навигации по абзацам), чтобы экран не перехватывался.
    private var followScrollEnabled = true
```

Replace with:

```kotlin
    // Следование за TTS-абзацем приостанавливается только когда ручной скролл
    // увёл текущий абзац с экрана; возобновляется, когда абзац снова виден, либо
    // по кнопке «Фокус» / навигации по абзацам / главам.
    private var followScrollEnabled = ReaderAutoScrollFollow.ACTIVE
    // Палец пользователя лежит на экране (TOUCH_SCROLL) — до IDLE это один жест,
    // включая fling. Programmatic smoothScrollToPosition* TOUCH_SCROLL не порождает,
    // поэтому этот флаг надёжно отличает жест пользователя от нашего скролла.
    private var manualGestureActive = false
    // Последний целевой (позиция, отступ) programmatic-скролла и время его старта:
    // дедупликация повторных smoothScrollToPositionFromTop в один и тот же таргет
    // (LOADING+PLAYING одного абзаца рестартят одну и ту же анимацию — вибрация).
    private var lastSmoothScrollTarget: Pair<Int, Int>? = null
    private var lastSmoothScrollStart = 0L
```

- [ ] **Step 2: Resume follow on Focus / paragraph navigation (type update)**

Find (around lines 316–325):

```kotlin
        viewModel.readerSpeaker.scrollToReaderItem.asLiveData().observe(this) {
            if (it !is ReaderItem.Position) return@observe
            // «Фокус» и переходы по абзацам — явное действие пользователя:
            // возобновляем follow-скролл.
            followScrollEnabled = true
            scrollToReadingPositionForced(
                chapterIndex = it.chapterIndex,
                chapterItemPosition = it.chapterItemPosition,
            )
        }
```

Replace `followScrollEnabled = true` with `followScrollEnabled = ReaderAutoScrollFollow.ACTIVE`.

- [ ] **Step 3: Resume follow on chapter navigation**

Find (around lines 327–333):

```kotlin
        viewModel.readerSpeaker.scrollToChapterTop.asLiveData()
            .observe(this) { chapterIndex ->
                scrollToReadingPositionForced(
                    chapterIndex = chapterIndex,
                    chapterItemPosition = 0,
                )
            }
```

Replace with:

```kotlin
        viewModel.readerSpeaker.scrollToChapterTop.asLiveData()
            .observe(this) { chapterIndex ->
                // Переход на главу — как и навигация по абзацам: возобновляем follow.
                followScrollEnabled = ReaderAutoScrollFollow.ACTIVE
                scrollToReadingPositionForced(
                    chapterIndex = chapterIndex,
                    chapterItemPosition = 0,
                )
            }
```

- [ ] **Step 4: Recompute follow state in onScroll**

Find (around lines 488–500):

```kotlin
                    lastScrollEventTime = SystemClock.elapsedRealtime()
                    updateCurrentReadingPosSavingState(
                        firstVisibleItemIndex = viewAdapter.listView.fromPositionToIndex(
                            viewBind.listView.firstVisiblePosition
                        )
                    )
                    updateInfoView()
                    // Only trigger chapter loading when the user is actually scrolling,
                    // not during programmatic layout changes (e.g. after notifyDataSetChanged).
                    if (listIsScrolling) {
                        updateReadingState()
                    }
                }
```

Replace with:

```kotlin
                    lastScrollEventTime = SystemClock.elapsedRealtime()
                    updateCurrentReadingPosSavingState(
                        firstVisibleItemIndex = viewAdapter.listView.fromPositionToIndex(
                            viewBind.listView.firstVisiblePosition
                        )
                    )
                    updateInfoView()
                    // Only trigger chapter loading when the user is actually scrolling,
                    // not during programmatic layout changes (e.g. after notifyDataSetChanged).
                    if (listIsScrolling) {
                        updateReadingState()
                    }
                    followScrollEnabled = nextFollowState(
                        current = followScrollEnabled,
                        manualGestureActive = manualGestureActive,
                        currentParagraphVisible = isCurrentParagraphVisible(),
                    )
                }
```

- [ ] **Step 5: Replace the touch-pause with the gesture latch + IDLE recompute**

Find (around lines 511–520):

```kotlin
                    // Programmatic smoothScrollToPosition* тоже отдаёт FLING (а не только
                    // жест пользователя), поэтому follow отключаем ТОЛЬКО по TOUCH_SCROLL:
                    // это состояние возникает исключительно при касании пальца, когда
                    // палец ещё на экране, и не бывает при programmatic-скролле.
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                        followScrollEnabled = false
                    }
                    // When the user lifts their finger, check if we need to load more chapters
                    if (!listIsScrolling) {
                        updateReadingState()
```

Replace with:

```kotlin
                    // Programmatic smoothScrollToPosition* тоже отдаёт FLING (а не только
                    // жест пользователя), поэтому жест детектируем ТОЛЬКО по TOUCH_SCROLL:
                    // это состояние возникает исключительно при касании пальца, когда
                    // палец ещё на экране, и не бывает при programmatic-скролле. Follow
                    // приостанавливается не самим касанием, а уходом абзаца с экрана
                    // (см. nextFollowState и onScroll).
                    if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                        manualGestureActive = true
                    }
                    // When the user lifts their finger, check if we need to load more chapters
                    if (!listIsScrolling) {
                        manualGestureActive = false
                        followScrollEnabled = nextFollowState(
                            current = followScrollEnabled,
                            manualGestureActive = manualGestureActive,
                            currentParagraphVisible = isCurrentParagraphVisible(),
                        )
                        updateReadingState()
```

- [ ] **Step 6: Gate the follow path on PAUSED and on the gesture latch**

Find (around lines 646–652):

```kotlin
        // Ручной скролл приостанавливает follow-скролл до кнопки «Фокус».
        if (!followScrollEnabled) {
            return
        }

        // If user is scrolling, don't auto-scroll
        if (listIsScrolling) {
```

Replace with:

```kotlin
        // Ручной скролл, уведший текущий абзац с экрана, приостанавливает follow-скролл.
        if (followScrollEnabled == ReaderAutoScrollFollow.PAUSED) {
            return
        }

        // Пока палец на экране или идёт fling от жеста — не начинаем свой скролл,
        // чтобы не бороться с ручным скроллом пользователя.
        if (manualGestureActive) {
            return
        }

        // If user is scrolling, don't auto-scroll
        if (listIsScrolling) {
```

- [ ] **Step 7: Route the follow smooth-scrolls through the dedupe coordinator**

Find (around lines 682–685):

```kotlin
                // Scroll only if item is below the desired visible position (fast scroll)
                if (currentOffsetPx > newOffsetPx) {
                    viewBind.listView.smoothScrollToPositionFromTop(index, newOffsetPx, 400)
                }
```

Replace the `smoothScrollToPositionFromTop(...)` call with `followSmoothScrollTo(index, newOffsetPx)`.

Find (around lines 706–714):

```kotlin
        when {
            distanceBelow in 1..threshold -> {
                // Close below visible area - smooth scroll
                viewBind.listView.smoothScrollToPositionFromTop(itemPosition, newOffsetPx, 400)
            }
            distanceAbove in 1..threshold -> {
                // Close above visible area - smooth scroll
                viewBind.listView.smoothScrollToPositionFromTop(itemPosition, newOffsetPx, 400)
            }
```

Replace both `smoothScrollToPositionFromTop(itemPosition, newOffsetPx, 400)` calls with `followSmoothScrollTo(itemPosition, newOffsetPx)`. Leave the `setSelectionFromTop(itemPosition, newOffsetPx)` instant-jump branch untouched.

- [ ] **Step 8: Add the two private helpers**

Insert immediately BEFORE `private fun scrollToReadingPositionForced(` (currently line ~722):

```kotlin
    private fun followSmoothScrollTo(itemPosition: Int, offsetPx: Int) {
        val target = itemPosition to offsetPx
        val now = SystemClock.elapsedRealtime()
        if (!shouldIssueSmoothScroll(lastSmoothScrollTarget, lastSmoothScrollStart, target, now)) {
            return
        }
        lastSmoothScrollTarget = target
        lastSmoothScrollStart = now
        viewBind.listView.smoothScrollToPositionFromTop(itemPosition, offsetPx, 400)
    }

    private fun isCurrentParagraphVisible(): Boolean {
        val playing = viewModel.readerSpeaker.currentTextPlaying.value
        val chapterIndex = playing.itemPos.chapterIndex
        val chapterItemPosition = playing.itemPos.chapterItemPosition
        val firstIndex = viewBind.listView.firstVisiblePosition
        val lastIndex = viewBind.listView.lastVisiblePosition
        for (index in firstIndex..lastIndex) {
            val item = viewAdapter.listView.getItem(index)
            if (
                item.chapterIndex == chapterIndex &&
                item is ReaderItem.Position &&
                item.chapterItemPosition == chapterItemPosition
            ) {
                return true
            }
        }
        return false
    }
```

- [ ] **Step 9: Jitter trace — verify no remaining opposing/duplicate programmatic scrolls**

Apply systematic debugging (read-only) before committing:

1. Enumerate every programmatic scroll call site in `features/reader` (grep `smoothScrollToPosition|setSelectionFromTop`). Expected list, all in `ReaderActivity.kt`: initial/session restore (255, 278, 794), chapter-edge handlers (287, 299), follow path (now `followSmoothScrollTo`), forced Focus/nav (733), onResume (833), intro immediate (747).
2. Confirm chapter-edge handlers `ttsScrolledToTheTop` / `ttsScrolledToTheBottom` only fire on explicit next/prev-chapter actions at book boundaries — grep `scrolledToTheBottom.emit|scrolledToTheTop.emit` in `ReaderTextToSpeech.kt`; expected: lines ~797 and ~836 only, inside `playNextChapter`/`playPreviousChapter`. Conclusion: they never fire during normal playback, so the follow path and the edge handlers cannot oppose each other during reading → **no change to the edge handlers** (avoid unnecessary changes).
3. Confirm the dedupe window (500ms) > the follow animation duration (400ms), so a repeated emission for the same paragraph cannot restart its own animation (jitter/vibration fix).
4. Confirm `manualGestureActive` blocks the follow path for the whole gesture (touch → fling → IDLE), so the 500ms FLING watchdog can no longer start a follow scroll into a settling fling.

Record the trace conclusions in your task report. If the trace surfaces any actual co-occurring opposing scroll sequence during normal reading that steps 1–7 do not cover, report it and STOP for a controller decision — do not add speculative fixes.

- [ ] **Step 10: Commit**

```bash
git add features/reader/src/main/java/my/noveldokusha/features/reader/ReaderActivity.kt
git commit -m "fix(reader): pause TTS follow only when the current paragraph leaves the screen"
```

---

### Task 3: Temporary CI verification (tests + compile)

**Files:**
- Create (TEMPORARY): `.github/workflows/run-tests.yml`
- Delete later in this task: `.github/workflows/run-tests.yml`

- [ ] **Step 1: Write the temporary workflow**

Create `.github/workflows/run-tests.yml`:

```yaml
name: Temp Reader Auto-Scroll Tests

on:
  push:
    branches:
      - fix/reader-auto-scroll-offscreen-pause

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - name: Clone repo
        uses: actions/checkout@v7

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: 21

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6.2.0

      - name: Unit tests (features:reader)
        run: ./gradlew :features:reader:testDebugUnitTest

      - name: Compile (features:reader)
        run: ./gradlew :features:reader:compileReleaseKotlin
```

- [ ] **Step 2: Commit and push the branch (with the workflow)**

```bash
git add .github/workflows/run-tests.yml
git commit -m "ci: add temporary reader auto-scroll tests workflow"
git push https://x-access-token:<PAT>@github.com/Vaizer0/NoveLA fix/reader-auto-scroll-offscreen-pause
```

- [ ] **Step 3: Wait for the run and verify**

Find the newest workflow run for the branch:

```bash
curl -sS -H "Authorization: Bearer <PAT>" \
  "https://api.github.com/repos/Vaizer0/NoveLA/actions/runs?branch=fix/reader-auto-scroll-offscreen-pause&event=push"
```

Poll the newest run (GET the run URL, `status` → `completed`) every 60s (the build can take 10–25 min). Confirm `"conclusion": "success"`. Expected verification: `:features:reader:testDebugUnitTest` passes (all `ReaderAutoScrollTest` tests) and `:features:reader:compileReleaseKotlin` succeeds. If it FAILS, read the failing job/step logs, fix the code, commit, push again, and re-verify. Do NOT report success without a green run.

- [ ] **Step 4: Remove the temporary workflow and push**

```bash
git rm .github/workflows/run-tests.yml
git commit -m "chore: remove temporary test workflow"
git push https://x-access-token:<PAT>@github.com/Vaizer0/NoveLA fix/reader-auto-scroll-offscreen-pause
```

Verify via API that the file is gone from the branch (`GET .../contents/.github/workflows/run-tests.yml?ref=<branch>` → 404).

- [ ] **Step 5: Write the task report**

Report: run id/URL + conclusion, the exact test results line from CI, the Step 9 trace conclusions, and the removal commit SHA. Do NOT include the PAT anywhere.

---

### Task 4: Final review, PR, and release build

**Files:** none (remote operations). The controller runs the final whole-branch review before the PR.

- [ ] **Step 1: Final whole-branch review**

Controller dispatches a code-reviewer subagent over the full branch diff (base `7fe3fb94`, head = branch HEAD). Fix any Critical/Important findings before proceeding (fixes go through a new task/amend on this branch, re-verified in CI).

- [ ] **Step 2: Push the final branch**

```bash
git push https://x-access-token:<PAT>@github.com/Vaizer0/NoveLA fix/reader-auto-scroll-offscreen-pause
```

- [ ] **Step 3: Open the pull request**

```bash
curl -sS -X POST \
  -H "Authorization: Bearer <PAT>" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Vaizer0/NoveLA/pulls" \
  -d '{"title": "reader: pause auto-scroll only when the current paragraph is off-screen", "head": "fix/reader-auto-scroll-offscreen-pause", "base": "default", "body": "TTS auto-scroll now pauses only when a manual scroll moves the current paragraph off-screen, and resumes automatically when the paragraph is visible again, on the Focus button, or on next/prev paragraph / chapter navigation.\n\nChanges (features/reader only):\n- New pure follow-state machine (ReaderAutoScroll.kt): ACTIVE/PAUSED decided from gesture + paragraph visibility; unit-tested.\n- ReaderActivity now latches a real finger gesture (TOUCH_SCROLL→IDLE) and never issues a programmatic scroll during it, so auto-scroll never fights manual scrolling.\n- Programmatic smooth scrolls to the same target are deduped (500ms window), eliminating the restart-vibration jitter; highlight/rebind behavior is unchanged.\n- Default behavior when not manually scrolling is unchanged.\n\nVerified: unit tests (:features:reader:testDebugUnitTest) and compile (:features:reader:compileReleaseKotlin) via Actions; APK build via buildRelease.yml (build_type: test)."}'
```

Confirm `"state": "open"`, head commit == branch HEAD.

- [ ] **Step 4: Dispatch the release build on THIS branch and confirm the APK**

```bash
curl -sS -X POST \
  -H "Authorization: Bearer <PAT>" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Vaizer0/NoveLA/actions/workflows/buildRelease.yml/dispatches" \
  -d '{"ref": "fix/reader-auto-scroll-offscreen-pause", "inputs": {"build_type": "test"}}'
```

Poll the newest `workflow_dispatch` run until `completed`; confirm `"conclusion": "success"` and that an artifact named `NoveLA-*-test` for this run exists. This builds the branch's code (the workflow checks out the dispatched ref), unlike dispatching on `default`. If it fails, report and fix before claiming success.

- [ ] **Step 5: Report**

Report the PR number + URL, the release-build run id/URL + conclusion, and the artifact name(s). Do NOT include the PAT.

---

## Self-Review

**1. Spec coverage:**
- §3.1 follow-state machine → Task 1 + Task 2 Steps 1, 4, 5. ✓
- §3.2 real-gesture latch → Task 2 Step 5. ✓
- §3.3 gate order (PAUSED → manualGestureActive → listIsScrolling+watchdog) → Task 2 Step 6. ✓
- §3.4 scroll dedupe → Task 1 (`shouldIssueSmoothScroll`) + Task 2 Steps 7, 8. ✓
- §3.5 resume on chapter nav → Task 2 Step 3. ✓
- §4 glitch verification (H1/H2 fixed, H3 traced+dismissed) → Task 2 Step 9. ✓
- §5 files → Tasks 1–2 (only `features/reader` + docs). ✓
- §7 tests + CI + branch-ref release build → Task 3, Task 4 Step 4. ✓

**2. Placeholder scan:** No TBD/TODO. The only placeholder is `<PAT>`, deliberately NOT inlined (secret); the implementer receives it in the dispatch prompt. ✓

**3. Type consistency:**
- `nextFollowState(current, manualGestureActive, currentParagraphVisible)` defined Task 1, called Task 2 Steps 4/5 with identical arg names/types. ✓
- `shouldIssueSmoothScroll(lastTarget, lastStartedAt, newTarget, now, windowMs)` defined Task 1, called in Task 2 Step 8 (`followSmoothScrollTo`) with `(lastSmoothScrollTarget, lastSmoothScrollStart, target, now)` — `windowMs` defaults to 500L. ✓
- `ReaderAutoScrollFollow.ACTIVE/PAUSED` used consistently in Task 2 Steps 1–6. ✓
- Fields `manualGestureActive`, `lastSmoothScrollTarget`, `lastSmoothScrollStart` declared Step 1, used Steps 4/5/8. ✓
- Helpers `isCurrentParagraphVisible()`, `followSmoothScrollTo(itemPosition, offsetPx)` defined Step 8, called Steps 4/5/7. ✓
- Chapter-edge handlers intentionally left unchanged (Task 2 Step 9). ✓
