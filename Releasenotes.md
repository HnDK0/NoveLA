# NoveParas v1.3.4

**Performance optimization, memory leak fixes, and translator bug fixes.**

---

## 🚀 What's New

### Translator Fixes
- **Gemini translator fixed** — API key now passed via `X-Goog-API-Key` header (was broken with `?key=` URL param, returned 403)
- **Key rotation crash fixed** (Gemini + OpenAI) — `Math.floorMod()` replaces `%` operator that could return negative indices

### Performance
- **APK size reduced 71%** — 24 MB → 7.0 MB via R8 minification + resource shrinking
- **Parallel source search** in novel migration — 50 sources now searched 8-wide instead of sequential (~16 min → ~80 sec)
- **Batch DB operations** in migration — O(N) round-trips → 5 round-trips
- **30+ regex patterns hoisted** out of hot loops — eliminates ~8000+ unnecessary compilations per bulk translation
- **OkHttp sharing** — Gemini + OpenAI now share the scraper's connection pool (4 clients → 2)

### Memory Leaks Fixed
- Reader session scopes (`ReaderLiveTranslation`, `ChaptersIsReadRoutine`) now properly cancelled on close
- `FloatingTtsService` stops when reader closes — no more zombie floating bubble
- `NarratorMediaControlsService` notification removed on app swipe-away
- `Toasty` and `AppPreferences` no longer create orphan coroutine scopes per call
- Backup/Restore/EPUB import services use proper `SupervisorJob` scopes
- `WakeLock` now has 10-min safety timeout (was indefinite on crash)
- Cover bitmap recycled on notification close

### TTS & UI
- Floating TTS is opt-in (Settings → Voice Reader → Floating TTS) — defaults to static in-reader controls
- TTS lifecycle fixed — closing the novel stops TTS, removes notification, removes floating bubble
- Swiping app from recents stops all TTS services
- Cramped floating TTS settings row redesigned to fit narrow screens

### Compose & Reader
- `collectAsStateWithLifecycle` replaces `collectAsState` at 5 sites — saves battery when backgrounded
- `MutableInteractionSource` now `remember`-ed at 5 sites — eliminates GC churn during scrolling
- `ChaptersScreen` "are all selected chapters read?" check: O(N×M) → O(N+M) via `derivedStateOf` + `HashSet`
- `MainActivity` only composes the active screen (was keeping 3 screens alive via alpha=0)
- `ExpandableText` caches `Paragraph` layout — was re-running on every recomposition
- ReaderChaptersLoader deduplication bug fixed — duplicate paragraphs no longer lose their translations

### Network & Image
- Coil memory cache capped at 15% of heap (was 25% — caused OOM on low-RAM devices)
- EPUB/FB2 cover dimensions read via `inJustDecodeBounds` (was full-decoding 4MB images just for width/height)
- Cloudflare WebView reused (was creating a new ~10MB WebView per challenge)
- `NetworkClient` tuned: 50 idle connections (was 15), 4 max/host (was 16 — IP ban risk), 15s connect timeout (was 30s)

---

## 📥 Download

**Requires Android 8.0+**

`NoveParas_v1.3.4-release.apk` — 7.0 MB, v2 signed

Installs side-by-side with the original NoveLA (different package: `my.novelparas`).

---

## 🔑 Keystore

```
Alias:    novelparas
Password: novelparas123
Format:   JKS (RSA 2048, SHA256withRSA)
```

---

## 🐴 Methodology

Optimizations driven by the [Ponytail](https://github.com/DietrichGebert/ponytail) minimalist-coding audit — 121 findings across 4 parallel scans. Every fix carries a `// ponytail:` comment.

Full details: see `PR_DESCRIPTION.md`.
