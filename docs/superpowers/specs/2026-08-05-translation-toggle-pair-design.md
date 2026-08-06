# Design: Independent per-novel translation toggle and language pair

Date: 2026-08-05
Status: Approved (design presented to user, user approved)

## Problem

In per-novel translation mode the on/off state and the language pair are conflated:

- `resolveTranslationEnabled()` derives "enabled" from the pair map:
  `map[bookUrl]?.isComplete == true` (AppPreferences.kt).
- Selecting a language pair completes it → translation auto-turns ON.
- Turning translation OFF (`onEnable(false)` → `onUnpinBook()` →
  `clearTranslationPairForBook()`) deletes the saved pair.

This breaks the requirement that the toggle and the pair are independent per novel.

## Goal

1. Selecting a language pair must NOT turn translation on.
2. Turning translation off must NOT clear the saved pair.
3. Turning translation on again restores that novel's last selected pair.
4. Each novel remembers its own toggle and pair independently.
5. Global-mode behavior stays unchanged.
6. Everything else stays unchanged; minimal, clean, bug-free.

## Design

### Storage (core module)

Add a new per-novel enabled map preference:

```
TRANSLATION_BOOK_ENABLED_MAP : Map<String, Boolean>  (new, JSON via SharedPreference_Serializable)
```

Existing `TRANSLATION_BOOK_LANG_PAIR` keeps its meaning: the per-novel language pair.

Name must NOT be `TRANSLATION_BOOK_ENABLED` — that key still holds legacy data in
SharedPreferences and is referenced by the legacy migration.

New/changed core functions:

- `translationEnabledForBook(bookUrl)`:
  - global mode → `GLOBAL_TRANSLATION_ENABLED` (unchanged)
  - per-novel → `TRANSLATION_BOOK_ENABLED_MAP.value[bookUrl] == true`
- `setTranslationEnabledForBook(bookUrl, enabled)`:
  - global mode → `GLOBAL_TRANSLATION_ENABLED`
  - per-novel → write/remove key in the enabled map
- `resolveTranslationEnabled(globalMode, globalEnabled, enabledMap, bookUrl)`: per-novel
  mode reads the enabled map instead of pair completeness.
- `updateTranslationPairMap`: persist partial pairs (remove entry only when BOTH source
  and target are blank), so a half-configured pair is not wiped.
- One-time migration (after the existing legacy migration in `init`): when
  `TRANSLATION_BOOK_ENABLED_MAP` key is absent, build it from the pair map — every novel
  with a complete pair becomes `enabled = true`. Preserves current users' state.

### Reader (ReaderLiveTranslation.kt)

- `onEnable(it)` per-novel branch: write `setTranslationEnabledForBook(bookUrl, it)`; do NOT
  touch the pair. If a pair is saved, turning on creates the translator again (restores it).
- `onSourceChange` / `onTargetChange`: save the pair only; remove the
  `state.enable.value = appPreferences.translationEnabledForBook(bookUrl)` re-read (enable
  no longer follows the pair).
- Translation is active ⇔ toggle ON AND pair complete AND `source != target`
  (existing `updateTranslatorState()` already encodes this).
- Remove `onUnpinBook()` and the now-unused `onUnpinBook` field from
  `LiveTranslationSettingData`.

### Reader UI (TranslatorSettingDialog.kt)

- The toggle switch becomes freely toggleable in per-novel mode
  (`enabled = true` instead of `translationGlobalMode || enable`).
- Hint text:
  - toggle OFF (per-novel): "Translation is off for this novel." (new string)
  - toggle ON but missing pair: "Select source and target languages." (update existing
    `translation_select_pair_to_enable` string; en + ru)

### Reader preview (ReaderScreen.kt)

- Remove `onUnpinBook = {}` from the preview `LiveTranslationSettingData`.

### Chapters list (ChaptersViewModel.kt)

- Add `appPreferences.TRANSLATION_BOOK_ENABLED_MAP.flow()` to the translated-titles
  `combine(...)` and pass the enabled map to `resolveTranslationEnabled(...)`.
- Target-language lookup stays pair-based.

### Consumers already safe

`DownloadManager` and `ChaptersViewModel.translateSelected()` check both
`translationEnabledForBook()` AND pair blankness → a novel toggled ON without a pair is
treated as not configured (no translation). `ReaderChaptersLoader` activates translation
only when `translatorState != null`, which requires the toggle + a valid pair.

## Testing

- Update `TranslationLangPairTest` for the new `resolveTranslationEnabled` semantics and
  partial-pair persistence; add tests for the enabled-map migration.
- Run `./gradlew :core:testDebugUnitTest` (or the project's unit-test task).
- Build APK via GitHub Actions `buildRelease.yml` (`build_type: test`).

## Out of scope

- Global translation mode behavior.
- Translation providers, prompts, display options, download pipeline internals.
- Any unrelated reader/translation features.
