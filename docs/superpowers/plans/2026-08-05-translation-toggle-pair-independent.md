# Translation Toggle / Language Pair Independence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the per-novel translation on/off toggle and the per-novel language pair fully independent: selecting a pair must NOT auto-enable translation, turning translation off must NOT clear the saved pair, re-enabling restores the last pair, and each novel keeps its own toggle + pair.

**Architecture:** Introduce a new `TRANSLATION_BOOK_ENABLED_MAP` preference (`Map<bookUrl, Boolean>`) that stores the per-novel toggle separately from the existing `TRANSLATION_BOOK_LANG_PAIR` (`Map<bookUrl, TranslationLangPair>`). `resolveTranslationEnabled` is re-pointed at the toggle map instead of pair completeness, `updateTranslationPairMap` keeps partial pairs (removes only when both languages are blank), `onUnpinBook`/`clearTranslationPairForBook` are deleted, the toggle switch becomes freely toggleable, and a one-time migration preserves current users by deriving `enabled=true` for every novel that already has a complete pair.

**Tech Stack:** Kotlin, AndroidX SharedPreferences (`SharedPreference_Serializable` JSON codec via `org.json`), Compose (Material3 `Switch` + `Text`), coroutines `combine`, JUnit 4 unit tests, Gradle 9.4.1 / AGP 9.2.1 / JDK 21 (CI).

## Global Constraints

- Do NOT modify `.github/workflows/buildRelease.yml` — it is the release pipeline and must stay untouched.
- No local Android build is possible (no Android SDK on this machine). Verification is done via GitHub Actions: a **temporary** workflow `run-tests.yml` runs `:core:testDebugUnitTest` + Kotlin compile of `:features:reader` and `:features:chaptersList`; it MUST be removed (committed out) before opening the final PR so it never appears in the PR diff.
- Git push auth: use the GitHub PAT token `REDACTED_PAT` (as credential in the remote URL; never commit it). Remote: fork `https://github.com/Vaizer0/NoveLA`, local branch `fix/translation-toggle-pair-independent` (created from `origin/default` = `7fe3fb94`).
- Never reuse the legacy pref key `TRANSLATION_BOOK_ENABLED` (still holds legacy data, consumed by the existing migration `migrateLegacyEnabledToPairs`). New key is `TRANSLATION_BOOK_ENABLED_MAP`.
- Comments in this codebase are Russian; write new comments in Russian to match.
- All existing behavior for global mode (`TRANSLATION_GLOBAL_MODE == true`) must be unchanged.
- Strings must be added/updated in BOTH `values/strings.xml` (English) and `values-ru/strings.xml` (Russian).
- The switch must remain visible and enabled (can be toggled) in per-novel mode; a hint must explain the missing pair when translation is ON but the pair is incomplete.

---

### Task 1: Core storage — independent enabled map

**Files:**
- Modify: `core/src/main/java/my/noveldokusha/core/appPreferences/AppPreferences.kt`
- Test: `core/src/test/java/my/noveldokusha/core/appPreferences/TranslationLangPairTest.kt`

**Interfaces:**
- Produces:
  - `internal fun encodeEnabledMap(map: Map<String, Boolean>): String`
  - `internal fun decodeEnabledMap(raw: String): Map<String, Boolean>`
  - `fun resolveTranslationEnabled(globalMode: Boolean, globalEnabled: Boolean, enabledMap: Map<String, Boolean>, bookUrl: String): Boolean` (signature changed: 3rd param is now the toggle map, NOT the pair map)
  - `internal fun updateTranslationPairMap(map: Map<String, TranslationLangPair>, bookUrl: String, source: String, target: String): Map<String, TranslationLangPair>` (partial pairs now persist; entry removed only when source AND target are blank)
  - `internal fun deriveEnabledMapFromPairs(pairs: Map<String, TranslationLangPair>): Map<String, Boolean>`
  - On `AppPreferences`: `val TRANSLATION_BOOK_ENABLED_MAP: Preference<Map<String, Boolean>>` (name `"TRANSLATION_BOOK_ENABLED_MAP"`), `fun translationEnabledForBook(bookUrl: String): Boolean`, `fun setTranslationEnabledForBook(bookUrl: String, enabled: Boolean)`, `private fun migrateEnabledStateFromPairs()` (called from `init` after `migrateLegacyTranslationSettings()`). `fun clearTranslationPairForBook(bookUrl: String)` is REMOVED.
- Consumes: existing `SharedPreference_Serializable` from `my.noveldokusha.core` (already imported at AppPreferences.kt:28), existing `Preference<T>.flow()` (AppPreferences.kt:831-833), existing `TranslationLangPair.isComplete` (AppPreferences.kt:49-50).

- [ ] **Step 1: Write the failing unit tests**

Replace the "Per-novel mode semantics" section and the `writing partial pair removes entry` test in `core/src/test/java/my/noveldokusha/core/appPreferences/TranslationLangPairTest.kt`, and append new sections. Full intended final test content for the changed/added parts:

```kotlin
// ─── Per-novel mode semantics ───────────────────────────────────────────

@Test
fun `per-novel mode off by default`() {
    val enabledMap = emptyMap<String, Boolean>()

    assertFalse(resolveTranslationEnabled(false, globalEnabled = true, enabledMap = enabledMap, bookUrl = "a"))
    assertEquals(TranslationLangPair(), resolveTranslationPair(false, "en", "ru", emptyMap(), "a"))
}

@Test
fun `per-novel mode enabled only when flag present`() {
    val enabledMap = mapOf("a" to true)

    assertTrue(resolveTranslationEnabled(false, globalEnabled = false, enabledMap = enabledMap, bookUrl = "a"))
    assertFalse(resolveTranslationEnabled(false, globalEnabled = false, enabledMap = enabledMap, bookUrl = "b"))
}

@Test
fun `full pair does not enable - toggle decides`() {
    val pairs = mapOf("a" to TranslationLangPair(source = "en", target = "ru"))
    val enabledMap = emptyMap<String, Boolean>()

    assertFalse(resolveTranslationEnabled(false, globalEnabled = true, enabledMap = enabledMap, bookUrl = "a"))
    assertEquals(TranslationLangPair("en", "ru"), resolveTranslationPair(false, "en", "ru", pairs, "a"))
}

@Test
fun `global mode ignores per-novel enabled map`() {
    val enabledMap = mapOf("a" to false)

    assertTrue(resolveTranslationEnabled(true, globalEnabled = true, enabledMap = enabledMap, bookUrl = "a"))
    assertFalse(resolveTranslationEnabled(true, globalEnabled = false, enabledMap = enabledMap, bookUrl = "a"))
    assertEquals(
        TranslationLangPair(source = "fr", target = "de"),
        resolveTranslationPair(true, "fr", "de", emptyMap(), "a"),
    )
}

@Test
fun `writing full pair stores it`() {
    val updated = updateTranslationPairMap(
        map = emptyMap(), bookUrl = "a", source = "en", target = "ru"
    )

    assertEquals(mapOf("a" to TranslationLangPair("en", "ru")), updated)
}

@Test
fun `writing partial pair keeps entry`() {
    val initial = mapOf("a" to TranslationLangPair("en", "ru"))

    val updated = updateTranslationPairMap(initial, bookUrl = "a", source = "", target = "ru")
    val updated2 = updateTranslationPairMap(initial, bookUrl = "a", source = "en", target = "")

    assertEquals(TranslationLangPair("", "ru"), updated["a"])
    assertEquals(TranslationLangPair("en", ""), updated2["a"])
}

@Test
fun `writing empty pair removes entry`() {
    val initial = mapOf("a" to TranslationLangPair("en", "ru"))

    val updated = updateTranslationPairMap(initial, bookUrl = "a", source = "", target = "")

    assertTrue(updated.isEmpty())
}

// ─── Enabled map codec ──────────────────────────────────────────────────

@Test
fun `enabled map codec roundtrip preserves map`() {
    val map = mapOf(
        "https://example.com/a" to true,
        "local://Книга" to false,
    )

    val decoded = decodeEnabledMap(encodeEnabledMap(map))

    assertEquals(map, decoded)
}

@Test
fun `enabled map decode of corrupt json returns empty map`() {
    assertTrue(decodeEnabledMap("not json at all {").isEmpty())
    assertTrue(decodeEnabledMap("").isEmpty())
    assertTrue(decodeEnabledMap("[]").isEmpty())
}

@Test
fun `enabled map decode treats missing or non-bool values as false`() {
    val decoded = decodeEnabledMap("""{"a": true, "b": "yes", "c": 1}""")

    assertEquals(true, decoded["a"])
    assertEquals(false, decoded["b"])
    assertEquals(false, decoded["c"])
}

// ─── Enabled state migration (pairs -> toggle map) ──────────────────────

@Test
fun `migration derives enabled from complete pairs only`() {
    val pairs = mapOf(
        "a" to TranslationLangPair("en", "ru"),
        "b" to TranslationLangPair("en"),
        "c" to TranslationLangPair(),
    )

    assertEquals(mapOf("a" to true), deriveEnabledMapFromPairs(pairs))
}

@Test
fun `migration from empty pairs gives empty enabled map`() {
    assertTrue(deriveEnabledMapFromPairs(emptyMap()).isEmpty())
}
```

Keep the existing tests for `isComplete`, the pair-map codec, and the legacy `migrateLegacyEnabledToPairs` migration unchanged. Delete the old tests `per-novel mode off by default without a full pair`, `partial pair is not enabled in per-novel mode`, `full pair enables the novel in per-novel mode`, `global mode ignores per-novel map`, and `writing partial pair removes entry (unpin)` (their behavior changed by design).

- [ ] **Step 2: Verify the tests fail against old code**

The new tests reference `resolveTranslationEnabled(false, ..., enabledMap = ..., ...)` and `decodeEnabledMap`/`encodeEnabledMap`/`deriveEnabledMapFromPairs`, which do not exist yet or have the wrong parameter type (`Map<String, TranslationLangPair>` vs `Map<String, Boolean>`), so the `core` module will not compile. Local run is not possible (no SDK); the compile failure is confirmed via the temporary CI workflow in Task 5. Do not proceed past Step 3 until the workflow exists; ordering: complete Steps 1-4 of all tasks, then use Task 5's CI run as the single red-then-green verification.

- [ ] **Step 3: Implement the core storage changes**

In `core/src/main/java/my/noveldokusha/core/appPreferences/AppPreferences.kt`:

(a) After `decodeTranslationPairMap` (line 80), add the enabled-map codec:

```kotlin
internal fun encodeEnabledMap(map: Map<String, Boolean>): String {
    val obj = org.json.JSONObject()
    map.forEach { (url, enabled) -> obj.put(url, enabled) }
    return obj.toString()
}

internal fun decodeEnabledMap(raw: String): Map<String, Boolean> =
    try {
        val obj = org.json.JSONObject(raw)
        val result = mutableMapOf<String, Boolean>()
        for (key in obj.keys()) {
            result[key] = obj.optBoolean(key, false)
        }
        result
    } catch (_: Exception) { emptyMap() }
```

(b) Replace `resolveTranslationEnabled` (lines 82-89) with:

```kotlin
// Персональный режим: новелла включена собственным переключателем
// (TRANSLATION_BOOK_ENABLED_MAP), независимым от пары языков.
fun resolveTranslationEnabled(
    globalMode: Boolean,
    globalEnabled: Boolean,
    enabledMap: Map<String, Boolean>,
    bookUrl: String,
): Boolean =
    if (globalMode) globalEnabled else enabledMap[bookUrl] == true
```

(c) Replace `updateTranslationPairMap` (lines 101-115) with:

```kotlin
// Персональный режим: пара сохраняется даже частичной — она не равна
// выключению перевода (переключатель хранится отдельно).
// Запись удаляется только когда оба языка пустые.
internal fun updateTranslationPairMap(
    map: Map<String, TranslationLangPair>,
    bookUrl: String,
    source: String,
    target: String,
): Map<String, TranslationLangPair> {
    val current = map.toMutableMap()
    if (source.isBlank() && target.isBlank()) {
        current.remove(bookUrl)
    } else {
        current[bookUrl] = TranslationLangPair(source = source, target = target)
    }
    return current
}
```

(d) After `updateTranslationPairMap`, add the migration helper:

```kotlin
// Миграция: «включено» раньше означало наличие полной пары в персональной карте.
// Переносим это состояние в отдельный переключатель TRANSLATION_BOOK_ENABLED_MAP.
internal fun deriveEnabledMapFromPairs(pairs: Map<String, TranslationLangPair>): Map<String, Boolean> =
    pairs.filterValues { it.isComplete }.mapValues { true }
```

(e) After the `TRANSLATION_BOOK_LANG_PAIR` declaration (line 412), add the new preference:

```kotlin
// Персональный переключатель перевода новеллы: Map<bookUrl, Boolean>.
// Хранится отдельно от TRANSLATION_BOOK_LANG_PAIR: выбор пары не включает перевод,
// выключение перевода не удаляет пару. Отсутствие ключа = перевод выключен.
val TRANSLATION_BOOK_ENABLED_MAP =
    object : Preference<Map<String, Boolean>>("TRANSLATION_BOOK_ENABLED_MAP") {
        override var value by SharedPreference_Serializable<Map<String, Boolean>>(
            name = name,
            sharedPreferences = preferences,
            defaultValue = emptyMap(),
            encode = { encodeEnabledMap(it) },
            decode = { decodeEnabledMap(it) }
        )
    }
```

(f) Update `translationEnabledForBook` (lines 414-420) to read the new map:

```kotlin
fun translationEnabledForBook(bookUrl: String): Boolean =
    resolveTranslationEnabled(
        globalMode = TRANSLATION_GLOBAL_MODE.value,
        globalEnabled = GLOBAL_TRANSLATION_ENABLED.value,
        enabledMap = TRANSLATION_BOOK_ENABLED_MAP.value,
        bookUrl = bookUrl,
    )
```

(g) Replace `clearTranslationPairForBook` (lines 451-457) with `setTranslationEnabledForBook`:

```kotlin
// Включает/выключает перевод конкретной новеллы (персональный режим).
// Пару языков не трогает — она остаётся сохранённой и восстанавливается
// при повторном включении.
fun setTranslationEnabledForBook(bookUrl: String, enabled: Boolean) {
    if (TRANSLATION_GLOBAL_MODE.value) {
        GLOBAL_TRANSLATION_ENABLED.value = enabled
        return
    }
    val current = TRANSLATION_BOOK_ENABLED_MAP.value.toMutableMap()
    if (enabled) current[bookUrl] = true else current.remove(bookUrl)
    TRANSLATION_BOOK_ENABLED_MAP.value = current
}
```

(h) Update `init` and add the one-time migration (around lines 463-483):

```kotlin
init {
    migrateLegacyTranslationSettings()
    migrateEnabledStateFromPairs()
}

// Миграция (один раз): новеллы с полной парой в TRANSLATION_BOOK_LANG_PAIR
// получают enabled=true в новом переключателе TRANSLATION_BOOK_ENABLED_MAP —
// сохраняем прежнее поведение («есть пара = перевод включён»).
private fun migrateEnabledStateFromPairs() {
    if (preferences.contains("TRANSLATION_BOOK_ENABLED_MAP")) return
    TRANSLATION_BOOK_ENABLED_MAP.value = deriveEnabledMapFromPairs(TRANSLATION_BOOK_LANG_PAIR.value)
}
```

- [ ] **Step 4: Commit**

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
git add core/src/main/java/my/noveldokusha/core/appPreferences/AppPreferences.kt \
        core/src/test/java/my/noveldokusha/core/appPreferences/TranslationLangPairTest.kt
git commit -m "feat(core): store per-novel translation toggle independently from language pair"
```

---

### Task 2: Reader live translation — toggle independent of pair

**Files:**
- Modify: `features/reader/src/main/java/my/noveldokusha/features/reader/features/ReaderLiveTranslation.kt`
- Modify: `features/reader/src/main/java/my/noveldokusha/features/reader/ui/ReaderScreen.kt`

**Interfaces:**
- Consumes: `appPreferences.setTranslationEnabledForBook(bookUrl, enabled)`, `appPreferences.translationEnabledForBook(bookUrl)` (Task 1).
- Produces: `LiveTranslationSettingData` WITHOUT the `onUnpinBook` field; `onEnable(it)` toggles per-novel state directly.

- [ ] **Step 1: Update `LiveTranslationSettingData`**

In `ReaderLiveTranslation.kt`:
- Remove line 50 `val onUnpinBook: () -> Unit,` from the data class.
- Remove line 97 `onUnpinBook = ::onUnpinBook,` from the `state` construction.

- [ ] **Step 2: Update `onEnable` to toggle freely without touching the pair**

Replace `onEnable` (lines 187-209):

```kotlin
private fun onEnable(it: Boolean) {
    Timber.d("onEnable: $it")
    try {
        // Глобальный режим: единый переключатель для всех новелл.
        if (appPreferences.TRANSLATION_GLOBAL_MODE.value) {
            state.enable.value = it
            appPreferences.GLOBAL_TRANSLATION_ENABLED.value = it
        } else {
            // Персональный режим: переключатель независим от пары языков.
            // Выключение не удаляет пару — при повторном включении она восстановится.
            state.enable.value = it
            appPreferences.setTranslationEnabledForBook(bookUrl, it)
        }
        val update = updateTranslatorState()
        Timber.d("onEnable: updateRequired=$update")
        if (update) scope.launch {
            _onTranslatorChanged.emit(Unit)
        }
    } catch (e: Exception) {
        Timber.e(e, "onEnable: error")
        throw e
    }
}
```

- [ ] **Step 3: Stop re-deriving enable state from pair writes**

In `onSourceChange`, delete line 220 `state.enable.value = appPreferences.translationEnabledForBook(bookUrl)`. In `onTargetChange`, delete line 241 `state.enable.value = appPreferences.translationEnabledForBook(bookUrl)`. Leave the rest of both methods unchanged.

- [ ] **Step 4: Remove the now-unused `onUnpinBook`**

Delete the whole `onUnpinBook()` function (lines 270-280). Verify with grep that no other references remain:

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
grep -rn "onUnpinBook\|clearTranslationPairForBook" --include="*.kt" . | grep -v "/build/"
```

Expected: only the `ReaderScreen.kt` preview reference remains (fixed in Step 5).

- [ ] **Step 5: Remove the preview stub in `ReaderScreen.kt`**

In `features/reader/src/main/java/my/noveldokusha/features/reader/ui/ReaderScreen.kt`, delete line 477 `onUnpinBook = {},`.

- [ ] **Step 6: Commit**

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
git add features/reader/src/main/java/my/noveldokusha/features/reader/features/ReaderLiveTranslation.kt \
        features/reader/src/main/java/my/noveldokusha/features/reader/ui/ReaderScreen.kt
git commit -m "feat(reader): make translation toggle independent of language pair"
```

---

### Task 3: Translation dialog — freely toggleable switch and state hints

**Files:**
- Modify: `features/reader/src/main/java/my/noveldokusha/features/reader/ui/settingDialogs/TranslatorSettingDialog.kt`
- Modify: `strings/src/main/res/values/strings.xml`
- Modify: `strings/src/main/res/values-ru/strings.xml`

**Interfaces:**
- Consumes: `LiveTranslationSettingData` (no `onUnpinBook`, Task 2); `R.string.translation_toggle_off_hint` (new), `R.string.translation_select_pair_to_enable` (reworded).
- Produces: hint texts shown in the dialog.

- [ ] **Step 1: Make the switch always enabled**

In `TranslatorSettingDialog.kt`, change line 86:

```kotlin
enabled = state.translationGlobalMode.value || state.enable.value,
```
to:
```kotlin
enabled = true,
```

- [ ] **Step 2: Show per-state hint (off vs on-but-no-pair)**

Replace the hint block (lines 120-128):

```kotlin
// ── Подсказка состояния: переключатель и пара независимы. ──
when {
    !state.translationGlobalMode.value && !state.enable.value ->
        Text(
            text = stringResource(R.string.translation_toggle_off_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    !state.translationGlobalMode.value && (state.source.value == null || state.target.value == null) ->
        Text(
            text = stringResource(R.string.translation_select_pair_to_enable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
}
```

- [ ] **Step 3: Update and add strings (English)**

In `strings/src/main/res/values/strings.xml`:
- Change line 138:
```xml
<string name="translation_select_pair_to_enable">Select source and target languages to start translating</string>
```
- Add immediately after line 138:
```xml
<string name="translation_toggle_off_hint">Translation is off for this novel. Turn it on to translate.</string>
```

- [ ] **Step 4: Update and add strings (Russian)**

In `strings/src/main/res/values-ru/strings.xml`:
- Change line 256:
```xml
<string name="translation_select_pair_to_enable">Выберите язык источника и перевода, чтобы начать перевод</string>
```
- Add immediately after line 256:
```xml
<string name="translation_toggle_off_hint">Перевод выключен для этой новеллы. Включите его, чтобы переводить.</string>
```

- [ ] **Step 5: Commit**

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
git add features/reader/src/main/java/my/noveldokusha/features/reader/ui/settingDialogs/TranslatorSettingDialog.kt \
        strings/src/main/res/values/strings.xml \
        strings/src/main/res/values-ru/strings.xml
git commit -m "feat(reader): allow free toggle and add state hints in translation dialog"
```

---

### Task 4: Chapters list — use the independent per-novel toggle

**Files:**
- Modify: `features/chaptersList/src/main/java/my/noveldokusha/features/chapterslist/ChaptersViewModel.kt`

**Interfaces:**
- Consumes: `appPreferences.TRANSLATION_BOOK_ENABLED_MAP.flow()` and `resolveTranslationEnabled(globalMode, globalEnabled, enabledMap, url)` (Task 1).
- Produces: the translated-titles flow keyed off the toggle map, so translated chapter titles disappear immediately when the toggle is turned off (and appear when turned on) without depending on pair completeness.

- [ ] **Step 1: Re-point the combine at the enabled map**

Replace the `combine` block (lines 232-242) with:

```kotlin
            combine(
                bookUrlFlow,
                appPreferences.TRANSLATION_BOOK_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_BOOK_LANG_PAIR.flow(),
                appPreferences.GLOBAL_TRANSLATION_ENABLED.flow(),
                appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow(),
                appPreferences.TRANSLATION_GLOBAL_MODE.flow()
            ) { url, bookEnabled, bookPairs, globalEnabled, globalTarget, globalMode ->
                val enabled = resolveTranslationEnabled(globalMode, globalEnabled, bookEnabled, url)
                val target = if (globalMode) globalTarget else bookPairs[url]?.target ?: ""
                enabled to target
            }
```

Verify the import for `resolveTranslationEnabled` already exists (it does — it is used at line 239 today; no import changes needed).

- [ ] **Step 2: Commit**

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
git add features/chaptersList/src/main/java/my/noveldokusha/features/chapterslist/ChaptersViewModel.kt
git commit -m "feat(chaptersList): use independent per-novel toggle for translated titles"
```

---

### Task 5: Temporary CI test workflow + verification

**Files:**
- Create: `.github/workflows/run-tests.yml` (TEMPORARY — must be removed before opening the PR)

**Interfaces:**
- Consumes: all Task 1-4 changes; the `core` unit tests; Kotlin compile of `:features:reader` and `:features:chaptersList`.

- [ ] **Step 1: Create the temporary workflow**

Create `.github/workflows/run-tests.yml`:

```yaml
name: Temp Core Tests

on:
  workflow_dispatch:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - name: Clone repo
        uses: actions/checkout@v7

      - name: Set up JDK environment
        uses: actions/setup-java@v5
        with:
          distribution: 'temurin'
          java-version: 21

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6.2.0

      - name: Remove local Java home from gradle.properties
        run: sed -i '/org\.gradle\.java\.home/d' gradle.properties

      - name: Run core unit tests and compile reader/chapters modules
        run: ./gradlew :core:testDebugUnitTest :features:reader:compileReleaseKotlin :features:chaptersList:compileReleaseKotlin

      - name: Upload test report
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: core/build/reports/tests/
        if: always()
```

- [ ] **Step 2: Commit and push the feature branch**

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
git add .github/workflows/run-tests.yml
git commit -m "ci: add temporary core test workflow"
git push -u origin fix/translation-toggle-pair-independent
```

- [ ] **Step 3: Dispatch the workflow and confirm green**

```bash
curl -sS -X POST \
  -H "Authorization: Bearer REDACTED_PAT" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Vaizer0/NoveLA/actions/workflows/run-tests.yml/dispatches" \
  -d '{"ref": "fix/translation-toggle-pair-independent"}'
```

Then poll until done:

```bash
curl -sS -H "Authorization: Bearer REDACTED_PAT" \
  "https://api.github.com/repos/Vaizer0/NoveLA/actions/runs?event=workflow_dispatch&branch=fix/translation-toggle-pair-independent"
```

Expected: the latest run (per run number / head_sha matching the pushed commit) has `"conclusion": "success"`. Open the `core/build/reports/tests/.../index.html` artifact if needed to confirm all `TranslationLangPairTest` tests passed. If the run failed, read the failing step logs, fix the code, commit, push, and re-dispatch until green.

- [ ] **Step 4: Commit**

No commit in this step (verification only; workflow already committed in Step 2).

---

### Task 6: Full build, cleanup, and PR

**Files:**
- Delete: `.github/workflows/run-tests.yml` (temporary workflow removed from the PR)
- (Remote) dispatch the real release workflow

- [ ] **Step 1: Remove the temporary workflow**

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
git rm .github/workflows/run-tests.yml
git commit -m "chore: remove temporary test workflow"
```

- [ ] **Step 2: Push the final branch**

```bash
cd /data/data/com.termux/files/usr/tmp/opencode/NoveLA
git push
```

- [ ] **Step 3: Open the pull request**

```bash
curl -sS -X POST \
  -H "Authorization: Bearer REDACTED_PAT" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Vaizer0/NoveLA/pulls" \
  -d '{"title": "Make translation toggle and language pair independent per novel", "head": "fix/translation-toggle-pair-independent", "base": "default", "body": "Per-novel translation toggle and language pair are now stored and handled independently:\n- Selecting a language pair no longer auto-enables translation.\n- Turning translation off no longer clears the saved pair.\n- Re-enabling translation restores the last selected pair.\n- Each novel keeps its own toggle and its own pair.\n\nImplementation:\n- New `TRANSLATION_BOOK_ENABLED_MAP` pref (per-novel toggle), separate from `TRANSLATION_BOOK_LANG_PAIR`.\n- `resolveTranslationEnabled` reads the toggle map instead of pair completeness.\n- `updateTranslationPairMap` keeps partial pairs (entry removed only when both languages are blank).\n- Removed `onUnpinBook` / `clearTranslationPairForBook`.\n- One-time migration: novels with a complete pair get `enabled=true` (preserves current users).\n- Translation dialog toggle is freely switchable; shows hints when off or when on without a pair.\n\nCI: unit tests for the new semantics added in TranslationLangPairTest."}'
```

- [ ] **Step 4: Dispatch the release build (test) and confirm the APK**

```bash
curl -sS -X POST \
  -H "Authorization: Bearer REDACTED_PAT" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/Vaizer0/NoveLA/actions/workflows/buildRelease.yml/dispatches" \
  -d '{"ref": "default", "inputs": {"build_type": "test"}}'
```

Wait for the run to complete; confirm `"conclusion": "success"` and that the artifact `NoveLA-*-test` with the debug-signed APK is produced. Do NOT modify `buildRelease.yml`.

---

## Self-Review

**1. Spec coverage:**
- Selecting a pair does not auto-enable → `resolveTranslationEnabled` now reads the toggle map (Task 1b/f); `onSourceChange`/`onTargetChange` no longer re-derive enable (Task 2 Step 3); switch is free (Task 3 Step 1). ✓
- Turning off does not clear the pair → `setTranslationEnabledForBook` never touches `TRANSLATION_BOOK_LANG_PAIR` (Task 1g); `onEnable` uses it (Task 2 Step 2); `onUnpinBook`/`clearTranslationPairForBook` removed (Task 2 Step 4, Task 1g). ✓
- Re-enabling restores last pair → pair persists (Task 1c `updateTranslationPairMap` keeps partial pairs) and `refreshFromPrefs` restores source/target from the pair (unchanged ReaderLiveTranslation.kt:117-126). ✓
- Per-novel independence → `TRANSLATION_BOOK_ENABLED_MAP` is keyed by bookUrl (Task 1e). ✓
- Global mode unchanged → every branch guards on `TRANSLATION_GLOBAL_MODE.value`; `setTranslationEnabledForBook`/`onEnable` global path writes `GLOBAL_TRANSLATION_ENABLED` exactly as before. ✓
- Hint texts → Task 3 Step 2-4 (en + ru). ✓
- APK build → Task 6 Step 4. ✓
- Docs in PR → spec committed at `docs/superpowers/specs/2026-08-05-translation-toggle-pair-design.md`; plan file lives in `docs/superpowers/plans/` on the same branch (both included in the PR — acceptable, user approved the spec being in the PR). ✓

**2. Placeholder scan:** No TBD/TODO/placeholder steps; every code step contains exact final code or an exact deletion. ✓

**3. Type consistency:**
- `resolveTranslationEnabled(Boolean, Boolean, Map<String, Boolean>, String)` used identically in AppPreferences.kt:414-420 (Task 1f) and ChaptersViewModel.kt (Task 4 Step 1). ✓
- `setTranslationEnabledForBook(String, Boolean)` defined in Task 1g, consumed in Task 2 Step 2. ✓
- `deriveEnabledMapFromPairs(Map<String, TranslationLangPair>)` defined Task 1d, consumed in Task 1h. ✓
- `encodeEnabledMap`/`decodeEnabledMap` defined Task 1a, used in Task 1e and tests Task 1 Step 1. ✓
- `LiveTranslationSettingData` without `onUnpinBook` defined Task 2 Step 1, consumed by dialog (Task 3) and `ReaderScreen.kt` preview stub removed Task 2 Step 5. ✓
- `R.string.translation_toggle_off_hint` added Task 3 Step 3/4, referenced Task 3 Step 2. ✓
- `updateTranslationPairMap` semantics (partial persists, both-blank removes) consistent between Task 1c, `setTranslationPairForBook` (unchanged, AppPreferences.kt:437-449), and tests. ✓
- `TRANSLATION_BOOK_ENABLED_MAP` name used consistently as key string `"TRANSLATION_BOOK_ENABLED_MAP"` (Task 1e, Task 1h `preferences.contains`). ✓
