# Reader auto-scroll: pause only when the current paragraph is off-screen

**Date:** 2026-08-06
**Status:** Approved design (Approach A)
**Scope:** `features/reader` module only.

## 1. Problem

The reader's "auto-scroll" is the TTS follow: while TTS plays, the app scrolls the list
so the current paragraph stays at the ~200dp anchor near the top. A previous change
(PR #7) added a `followScrollEnabled` gate in `ReaderActivity.kt` that:

- pauses follow on **any** real touch (`SCROLL_STATE_TOUCH_SCROLL`) — even a tiny
  drag that leaves the current paragraph fully visible, and even a drag that the
  user would consider "reading normally";
- resumes only on the Focus button or next/prev paragraph navigation;
- never resumes automatically when the user scrolls back so the current paragraph
  is visible again.

That is more aggressive than requested. Requirements:

1. Default behavior works normally, exactly as today.
2. Manual scroll stops auto-scroll **only when** the current paragraph is no longer
   on screen.
3. While manually scrolling, the app must not force the screen back to the current
   paragraph.
4. Auto-scroll resumes when: the Focus button is pressed, next/prev paragraph is
   used, or the current paragraph is back on screen.
5. Manual scrolling must always be respected.
6–9. No redesign, no extra features, no unrelated changes, no unnecessary changes.
10. TTS auto-scroll must not fight manual scrolling.

Additionally there is a reported glitch: auto-scroll sometimes jitters/vibrates
("going up and down at the same time"), including during normal reading with no touch.

## 2. Current behavior analysis (`ReaderActivity.kt`)

- `followScrollEnabled` (bool, default `true`) — line 138.
- `onScrollStateChanged` (lines 502–534):
  - sets `listIsScrolling` (false only on `SCROLL_STATE_IDLE`);
  - sets `userHasScrolled = true` on `TOUCH_SCROLL` or `FLING`;
  - sets `followScrollEnabled = false` on `TOUCH_SCROLL` (finger down) — the
    too-aggressive pause;
  - on `IDLE` runs a TTS catch-up via `scrollToReadingPositionOptional`.
- `scrollToReadingPositionOptional` (lines 624–720), called from the
  `currentReaderItem` LiveData observer on every `PLAYING`/`LOADING` emission:
  1. rebinds the adapter for highlight (always, before any gate) — must stay;
  2. returns early if `!followScrollEnabled`;
  3. returns early if `listIsScrolling` (with a 500ms FLING watchdog that resets
     `listIsScrolling` and lets a follow scroll start mid-settling-fling);
  4. if the current paragraph is visible: smooth-scrolls it up to the 200dp anchor
     only when it is below the anchor;
  5. if not visible: smooth-scrolls (≤5 items away) or instantly jumps (far).
- Focus / next / prev paragraph (`scrollToReaderItem` observer, lines 316–325):
  `followScrollEnabled = true` + forced smooth scroll.
- Next/prev chapter (`scrollToChapterTop` observer, lines 327–333): forced scroll,
  but does **not** reset `followScrollEnabled` (inconsistent with Focus/paragraph nav).
- Chapter-edge handlers `ttsScrolledToTheTop/Bottom` (lines 282–304): their own
  smooth scrolls, independent of the follow path.

Programmatic `smoothScrollToPositionFromTop` also reports `SCROLL_STATE_FLING`, so
only `TOUCH_SCROLL` reliably means "finger is down" — the current code already relies
on this.

## 3. Design (Approach A)

### 3.1 Follow state becomes visibility-aware

Add a tiny pure state machine (new file
`features/reader/src/main/java/my/noveldokusha/features/reader/ReaderAutoScroll.kt`):

```kotlin
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
```

Semantics:

- **Paragraph visible → ACTIVE** (resumes; covers requirement 4's "current paragraph
  is back on screen").
- **Not visible + real manual gesture → PAUSED** (the only way follow pauses; covers
  requirement 2 — the pause happens exactly when the paragraph leaves the screen).
- **Not visible + no gesture → keep current** (so normal reading keeps the default
  ACTIVE state even while the paragraph is momentarily off-screen and TTS is catching
  up; and a previously PAUSED state stays PAUSED, so the app does not yank back).

`currentParagraphVisible` is computed by scanning `firstVisiblePosition..lastVisiblePosition`
for the current TTS paragraph (`chapterIndex`, `chapterItemPosition` of
`readerSpeaker.currentTextPlaying.value.itemPos`). If there is no active TTS item,
treat it as visible (harmless default; follow only matters while TTS emits).

### 3.2 Real-gesture latch

Add `manualGestureActive` (default `false`):
- `SCROLL_STATE_TOUCH_SCROLL` → `true` (finger down; never produced by programmatic
  scrolls).
- `SCROLL_STATE_IDLE` → `false`.

`onScroll` recomputes `followScrollEnabled = nextFollowState(...)` on every scroll
event, so a drag that slides the paragraph off-screen pauses follow mid-gesture, and a
scroll that brings it back resumes it. `onScrollStateChanged` recomputes once more on
IDLE before the TTS catch-up runs.

### 3.3 Never programmatic-scroll during a real gesture

In `scrollToReadingPositionOptional`, gate order becomes:

1. rebind for highlight (unchanged, always first);
2. `if (followScrollEnabled == PAUSED) return`;
3. `if (manualGestureActive) return` — a follow scroll can never start mid-drag or
   mid-fling, which also prevents the 500ms FLING watchdog from firing a follow
   scroll into a still-settling fling (a jitter source);
4. keep the existing `listIsScrolling` + watchdog gate for the programmatic-FLING
   "stuck" cleanup (rebind storms / chapter loads).

### 3.4 Glitch / jitter fix — programmatic-scroll dedupe

Add a pure helper in the same file:

```kotlin
fun shouldIssueSmoothScroll(
    lastTarget: Pair<Int, Int>?,
    lastStartedAt: Long,
    newTarget: Pair<Int, Int>,
    now: Long,
    windowMs: Long = 500L,
): Boolean = newTarget != lastTarget || now - lastStartedAt >= windowMs
```

In `ReaderActivity`, track `lastSmoothScrollTarget: Pair<Int, Int>?` and
`lastSmoothScrollStart: Long`. Route the follow path's smooth-scroll calls through a
small coordinator:

- same target within the window → skip (kills the vibrate at the 200dp anchor caused
  by repeated `LOADING`/`PLAYING` emissions and resume-time `forceUpdateCurrentItemState`
  re-fires restarting the identical smooth scroll);
- different target → issue (each new AbsListView smooth scroll supersedes the prior
  one, so the follow never lags behind).

The instant-jump branch (`setSelectionFromTop`) is left as-is (hard, rare, not a jitter
source). Forced scrolls (Focus/nav) and the chapter-edge handlers are left as-is.

### 3.5 Resume follow on next/prev chapter too

In the `scrollToChapterTop` observer set `followScrollEnabled = ACTIVE` (one line),
making chapter navigation resume follow exactly like Focus/paragraph navigation. This
also prevents a paused follow from blocking auto-scroll after a next/prev chapter jump.

## 4. Glitch verification (systematic debugging during implementation)

Hypotheses for the reported "up and down / jitter" during normal no-touch reading:

- **H1 (primary):** repeated restarts of the identical smooth scroll (LOADING then
  PLAYING for the same paragraph, plus `onResume` force-update) → vibration at the
  200dp anchor. Addressed by the dedupe (3.4).
- **H2:** follow scroll starting into a settling user fling via the FLING watchdog →
  jitter. Addressed by the real-gesture gate (3.3).
- **H3:** alternating programmatic scrolls between the follow path and the chapter-edge
  handlers (`ttsScrolledToTheTop/Bottom`), which scroll in opposite directions near
  chapter edges → "up and down at the same time". AbsListView smooth scrolls supersede
  one another, so rapid alternation oscillates. During implementation, trace the
  emission timing and AbsListView smooth-scroll semantics; if the conflict is
  confirmed, the coordinator (3.4) is extended to cover the edge handlers or the follow
  path is suppressed while an edge scroll is in flight — whichever the trace shows is
  the minimal correct fix.

No emulator is available (Termux has no Android SDK/JDK), so glitch verification is by
code-trace and static reasoning, gated by CI compile + APK build.

## 5. Files

- `features/reader/src/main/java/my/noveldokusha/features/reader/ReaderAutoScroll.kt` — new, pure logic (enum + `nextFollowState` + `shouldIssueSmoothScroll`).
- `features/reader/src/main/java/my/noveldokusha/features/reader/ReaderActivity.kt` — wire the above: type of `followScrollEnabled` → `ReaderAutoScrollFollow`, add `manualGestureActive`, `lastSmoothScrollTarget`, `lastSmoothScrollStart`; update `onScroll`, `onScrollStateChanged`, `scrollToReadingPositionOptional`, `scrollToChapterTop` observer.
- `features/reader/src/test/java/my/noveldokusha/features/reader/ReaderAutoScrollTest.kt` — new unit tests for the pure functions.

No other modules, no resources, no string changes, no workflow changes.

## 6. Out of scope

- TTS playback, pause/resume, highlighting, translation, parallel mode.
- Reader list adapter, chapter loading, settings, and everything outside `features/reader`.
- No new user-facing features or settings.
- `.github/workflows/buildRelease.yml` is not modified.

## 7. Testing and verification

1. New unit tests in `features/reader` for `nextFollowState` (all state transitions,
   incl. "keep current" branches) and `shouldIssueSmoothScroll` (same-target within
   window, different target, window expiry).
2. CI: dispatch `buildRelease.yml` with `ref: fix/reader-auto-scroll-offscreen-pause`
   and `build_type: test`; confirm the run succeeds and the `NoveLA-*-test` APK
   artifact is produced. (This builds the branch itself, unlike dispatching on
   `ref: default`.)
3. Final whole-branch code review before opening the PR.

## 8. Success criteria mapping

| User requirement | Mechanism |
|---|---|
| 1. Default behavior unchanged | Follow state defaults to ACTIVE; no-gesture off-screen case keeps ACTIVE (3.1) |
| 2. Stop only when paragraph off-screen | PAUSED only when not visible + real gesture (3.1) |
| 3. No forced return while manually scrolling | PAUSED gates all follow scrolls; manualGestureActive blocks mid-gesture scrolls (3.2, 3.3) |
| 4. Resume on Focus / next-prev / paragraph visible | Focus & paragraph nav already resume; paragraph-visible resumes via state machine; chapter nav now also resumes (3.1, 3.5) |
| 5. Manual scrolling respected | Real gesture never triggers programmatic scroll (3.3) |
| 10. No fight | Gesture gate + dedupe + H3 handling (3.3, 3.4, §4) |
| Glitch fixed | Dedupe (3.4), gesture gate (3.3), edge-handler conflict resolved if confirmed (§4) |
| APK builds in Actions | §7.2 |
