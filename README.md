<!--
  android novel reader, web novel app, light novel reader android, epub reader android, ranobe reader, wuxiaworld, royal road, scribble hub, free novel reader, open source novel app
  андроид читалка ранобэ, читалка веб новелл андроид, ранобэ приложение, epub читалка андроид, бесплатная читалка новелл, jaomix, ranobelib
  安卓小说阅读器, 网络小说APP, 轻小说阅读器, 免费小说阅读, epub阅读器安卓, 开源小说应用
-->

<div align="center">

<img src="assets/screenshots/NoveLA.png" width="88" height="88" alt="NoveLA"/>

# NoveLA

Free and open source web novel reader for Android.

🇬🇧 English · [🇷🇺 Русский](README_RU.md)

[![Release](https://img.shields.io/github/v/release/Parasgaming122/NoveLA?style=flat-square&labelColor=27303D&color=0D1117)](https://github.com/Parasgaming122/NoveLA/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Parasgaming122/NoveLA/total?style=flat-square&labelColor=27303D&color=0D1117)](https://github.com/Parasgaming122/NoveLA/releases)
[![License: GPL-3.0](https://img.shields.io/github/license/Parasgaming122/NoveLA?style=flat-square&labelColor=27303D&color=0D1117)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-brightgreen?style=flat-square&labelColor=27303D&color=3DDC84&logo=android&logoColor=white)](https://github.com/Parasgaming122/NoveLA/releases/latest)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&labelColor=27303D&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&labelColor=27303D&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

> ⚠️ **Status:** New features are temporarily paused. Current focus is on fixing bugs, cleaning up the codebase, and performance/optimization work. Bug reports and PRs targeting existing issues are very welcome — new feature requests will be revisited later.

<br/>

<img src="assets/screenshots/preview.png" alt="NoveLA preview" width="100%"/>

</div>

---

## 📑 Project Deck (PPT)

A 14-slide presentation covering everything in this README — features, architecture, module breakdown, tech stack, build system, screenshots, and roadmap — packaged as a single `.pptx` you can reuse for talks, demos, or onboarding.

<div align="center">

<a href="assets/NoveLA_Presentation.pptx">
  <img src="assets/preview/contact_sheet.png" alt="NoveLA — 14-slide project deck (contact sheet)" width="92%"/>
</a>

**[⬇️ Download the full deck (PPTX, 14 slides)](assets/NoveLA_Presentation.pptx)** · [📄 PDF preview](assets/preview/NoveLA_Presentation.pdf)

</div>

<details>
<summary><b>🎬 Browse all 14 slides inline</b></summary>
<br/>

| # | Slide | Preview |
|---|-------|---------|
| 01 | Cover — NoveLA | <img src="assets/ppt_slides/slide-01.png" width="380" alt="Slide 01"/> |
| 02 | What is NoveLA? | <img src="assets/ppt_slides/slide-02.png" width="380" alt="Slide 02"/> |
| 03 | Feature Highlights | <img src="assets/ppt_slides/slide-03.png" width="380" alt="Slide 03"/> |
| 04 | Translation Backends | <img src="assets/ppt_slides/slide-04.png" width="380" alt="Slide 04"/> |
| 05 | Plugin System | <img src="assets/ppt_slides/slide-05.png" width="380" alt="Slide 05"/> |
| 06 | Architecture Overview | <img src="assets/ppt_slides/slide-06.png" width="380" alt="Slide 06"/> |
| 07 | Module Breakdown | <img src="assets/ppt_slides/slide-07.png" width="380" alt="Slide 07"/> |
| 08 | Tech Stack | <img src="assets/ppt_slides/slide-08.png" width="380" alt="Slide 08"/> |
| 09 | Build System & Convention Plugins | <img src="assets/ppt_slides/slide-09.png" width="380" alt="Slide 09"/> |
| 10 | Reader & TTS Experience | <img src="assets/ppt_slides/slide-10.png" width="380" alt="Slide 10"/> |
| 11 | Codebase Stats | <img src="assets/ppt_slides/slide-11.png" width="380" alt="Slide 11"/> |
| 12 | App Screenshots Gallery | <img src="assets/ppt_slides/slide-12.png" width="380" alt="Slide 12"/> |
| 13 | Roadmap & Status | <img src="assets/ppt_slides/slide-13.png" width="380" alt="Slide 13"/> |
| 14 | Get Started & Contribute | <img src="assets/ppt_slides/slide-14.png" width="380" alt="Slide 14"/> |

</details>

---

## Download

**[Get the latest APK](https://github.com/Parasgaming122/NoveLA/releases/latest)** — requires Android 8.0+

Or build from source:

```bash
git clone https://github.com/Parasgaming122/NoveLA
# Open in Android Studio and run on a device or emulator
```

---

## Features

- **35+ sources** (built-in + Lua plugins)
- **Global multi-source search**; add any novel by URL
- **In-reader translation** with parallel mode and novel-specific prompts — no copy-paste, no app switching
- **Infinite chapter scrolling** with offline caching
- **Custom fonts**, text size, light/dark themes (Material 3)
- **Text-to-speech** with floating mini-player, background playback, speed/pitch control, Bluetooth support, and multiple engine support
- **Local EPUB and FB2 library** with bulk import
- **Backup & restore** with granular selection and auto backup
- **Regex text cleanup** (strip ads and injected text)
- **Automatic Cloudflare Turnstile bypass**
- **Novel migration** between sources
- **Library filters** by genre, source, and category
- **Download all chapters**
- **TTS reading timer**
- **20 interface languages**

---

## 📸 Screenshots

<div align="center">

| Library (Light) | Library (Dark) | Catalog | Reader |
|:---:|:---:|:---:|:---:|
| <img src="assets/screenshots/library_white.jpg" width="220" alt="Library light"/> | <img src="assets/screenshots/library_black.jpg" width="220" alt="Library dark"/> | <img src="assets/screenshots/catalog.jpg" width="220" alt="Catalog"/> | <img src="assets/screenshots/chapter.jpg" width="220" alt="Reader"/> |

| Book Info | Live Translation | Catalog Filters | Library Filters |
|:---:|:---:|:---:|:---:|
| <img src="assets/screenshots/book_info.jpg" width="220" alt="Book info"/> | <img src="assets/screenshots/translate_chapter.jpg" width="220" alt="Translation"/> | <img src="assets/screenshots/catalog_filters.jpg" width="220" alt="Catalog filters"/> | <img src="assets/screenshots/library_filters.jpg" width="220" alt="Library filters"/> |

| Add by URLs | Installed Plugins | Plugin Repository | Banner |
|:---:|:---:|:---:|:---:|
| <img src="assets/screenshots/add_novel_by_URLs.jpg" width="220" alt="Add by URLs"/> | <img src="assets/screenshots/installed_plugins.jpg" width="220" alt="Installed plugins"/> | <img src="assets/screenshots/plugins_repo.jpg" width="220" alt="Plugin repo"/> | <img src="assets/screenshots/banner.jpg" width="220" alt="Banner"/> |

</div>

---

## Translation

Four backends supported. Multiple API keys are rotated round-robin on rate limits.

| Backend | Cost | API key |
|---|---|---|
| Google Translate (Enhanced) | Free | Not required |
| Google Translate (Simple) | Free | Not required |
| Google Gemini | Free tier | Required |
| OpenAI-compatible | Varies | Required |

OpenAI-compatible accepts OpenAI, OpenRouter, DeepSeek, Ollama, Mistral, and any compatible endpoint.

Parallel mode displays original and translated text side by side. Novel-specific prompts let you customize translation behavior per book.

<div align="center">
<img src="assets/screenshots/translate_settings.jpg" width="280" alt="Translation settings"/>
<br/><em>Live in-reader translation with parallel mode — original and translated text shown side by side.</em>
</div>

---

## Plugins

NoveLA supports external Lua-based source plugins installable directly from the app.

Official plugin repo: [`HnDK0/external-sources`](https://github.com/HnDK0/external-sources)

To add: **Finder → Extensions → ⚙️ → paste repo URL**

<div align="center">

| Plugin Repository Browser | Installed Plugins |
|:---:|:---:|
| <img src="assets/screenshots/plugins_repo.jpg" width="280" alt="Plugin repo browser"/> | <img src="assets/screenshots/installed_plugins.jpg" width="280" alt="Installed plugins"/> |

</div>

---

## 🏗️ Architecture

NoveLA is structured as **32 Gradle modules** organized into **4 strict architectural layers**. Dependencies flow downward only — upper layers may depend on lower, never the reverse.

<div align="center">

| Layer | Modules | Role |
|:---|:---:|:---|
| **App** | 1 | The thin application shell — wires the Hilt graph, hosts `MainActivity`, glues feature modules together via navigation. |
| **Features** | 10 | One module per user-facing screen. Each owns its `Activity`, `ViewModel`, Composables, and state — independently testable. |
| **Tooling** | 12 | Persistence (Room), TTS, translation, backup, EPUB parsing, novel migration, background workers. Pure logic, no UI. |
| **Core / Foundation** | 6 | Shared foundation: app context, preferences, models, navigation, networking client, the scraper engine itself. |

</div>

<div align="center">
<a href="assets/ppt_slides/slide-06.png">
  <img src="assets/ppt_slides/slide-06.png" width="80%" alt="NoveLA layered architecture diagram"/>
</a>
<br/><em>Layered module architecture — 32 modules across 4 layers (full slide from the project deck).</em>
</div>

### Module Breakdown by Layer

<div align="center">
<a href="assets/ppt_slides/slide-07.png">
  <img src="assets/ppt_slides/slide-07.png" width="70%" alt="Module breakdown chart"/>
</a>
</div>

---

## 🧰 Tech Stack

A modern, fully-native Android stack — Kotlin-first, Compose-driven, with carefully chosen libraries for networking, persistence, scraping, and extensibility.

<details>
<summary><b>📊 View as visual grid (from the project deck)</b></summary>
<br/>
<div align="center">
<img src="assets/ppt_slides/slide-08.png" width="85%" alt="Tech stack grid"/>
</div>
</details>

| Category | Technologies |
|---|---|
| **Language & Runtime** | Kotlin `2.0.21` · Coroutines `1.9.0` · KSP `2.0.21-1.0.28` · kotlinx-serialization `1.7.3` · Java `21` |
| **UI Layer** | Jetpack Compose · Material 3 `1.3.1` · Coil `2.7.0` · Landscapist Glide `2.4.4` · Material Icons Extended |
| **DI & Async** | Hilt `2.52` · Hilt-Work `1.2.0` · WorkManager `2.11.2` · Lifecycle `2.8.7` |
| **Data & Persistence** | Room `2.8.4` · OkHttp `5.0.0-alpha.14` · Retrofit `2.12.0` · Jsoup `1.18.1` · Moshi `1.15.2` · Gson `2.14.0` |
| **Platform & SDK** | Android 8.0+ (API 26) · Compile SDK 35 · Target SDK 35 · Splash Screen `1.2.0` · Navigation `2.8.4` |
| **Specialty Libraries** | LuaJ `3.0.1` · SnakeYAML `2.6` · Readability4j `1.0.8` · Crux `5.1.0` · MLKit Translate `17.0.3` · Timber `5.0.1` |

All dependencies are pinned via a Gradle version catalog (`gradle/libs.versions.toml`). Convention plugins (`noveldokusha.android.application`, `noveldokusha.android.library`, `noveldokusha.android.compose`) enforce shared config across every module, and `com.autonomousapps.dependency-analysis` catches unused deps at build time.

---

## 📊 Codebase Stats

<div align="center">

| | | | |
|:---:|:---:|:---:|:---:|
| **390** Kotlin files | **54K** lines of Kotlin | **32** Gradle modules | **120** XML resources |
| **20** UI languages | **14** screenshots | **35+** catalog sources | **GPL-3.0** license |

</div>

<div align="center">
<a href="assets/ppt_slides/slide-11.png">
  <img src="assets/ppt_slides/slide-11.png" width="80%" alt="Codebase stats"/>
</a>
</div>

---

## 🛣️ Roadmap & Status

> **Status:** New features are temporarily paused. Current focus is on fixing bugs, cleaning up the codebase, and performance/optimization work.

| Phase | Status | Focus |
|:---|:---:|:---|
| **Bug Fixes & Cleanup** | 🟢 In progress | Stabilizing the codebase, removing dead code, improving tests. The optimization report (`NoveLA_Optimization_Report.md`) guides this work. |
| **Performance Tuning** | 🔵 Active | Database query optimization, scraper caching, Compose recomposition audits, memory footprint reduction for large libraries. |
| **Source Parser PRs** | 🟡 Welcome | Fixes and improvements to existing source parsers are actively merged. New sources can be added via the plugin repo. |
| **New Feature Requests** | ⚪ Deferred | New feature requests will be revisited later. Stability and codebase health take priority for now. |

<div align="center">
<a href="assets/ppt_slides/slide-13.png">
  <img src="assets/ppt_slides/slide-13.png" width="80%" alt="Roadmap & status"/>
</a>
</div>

---

## Contributing

Pull requests are welcome. For major changes, open an issue first.

- Fix or improve existing source parsers
- Add new sources via the [plugin repo](https://github.com/HnDK0/external-sources)
- Report bugs via [Issues](https://github.com/Parasgaming122/NoveLA/issues)

---

## License

[GPL-3.0](LICENSE)
