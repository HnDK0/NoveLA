<!--
  android novel reader, web novel app, light novel reader android, epub reader android, ranobe reader, wuxiaworld, royal road, scribble hub, free novel reader, open source novel app
  андроид читалка ранобэ, читалка веб новелл андроид, ранобэ приложение, epub читалка андроид, бесплатная читалка новелл, jaomix, ranobelib
  安卓小说阅读器, 网络小说APP, 轻小说阅读器, 免费小说阅读, epub阅读器安卓, 开源小说应用
-->

<div align="center">

<img src="https://github.com/HnDK0/NoveLA/raw/default/screenshots/NoveLA.png" width="88" height="88" alt="NoveLA"/>

# NoveLA

Free and open source web novel reader for Android.

🇬🇧 English · [🇷🇺 Русский](README_RU.md)

[![Release](https://img.shields.io/github/v/release/HnDK0/NoveLA?style=flat-square&labelColor=27303D&color=0D1117)](https://github.com/HnDK0/NoveLA/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/HnDK0/NoveLA/total?style=flat-square&labelColor=27303D&color=0D1117)](https://github.com/HnDK0/NoveLA/releases)
[![License: GPL-3.0](https://img.shields.io/github/license/HnDK0/NoveLA?style=flat-square&labelColor=27303D&color=0D1117)](LICENSE)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-brightgreen?style=flat-square&labelColor=27303D&color=3DDC84&logo=android&logoColor=white)](https://github.com/HnDK0/NoveLA/releases/latest)

> ⚠️ **Status:** New features are temporarily paused. Current focus is on fixing bugs, cleaning up the codebase, and performance/optimization work. Bug reports and PRs targeting existing issues are very welcome — new feature requests will be revisited later.

<br/>

<img src="preview.png" alt="NoveLA preview" width="100%"/>

</div>

---

## Download

**[Get the latest APK](https://github.com/HnDK0/NoveLA/releases/latest)** — requires Android 8.0+

Or build from source:

```bash
git clone https://github.com/HnDK0/NoveLA
# Open in Android Studio and run on a device or emulator
```

---

## Features

- 35+ sources (built-in + Lua plugins)
- Global multi-source search; add any novel by URL
- In-reader translation with parallel mode and novel-specific prompts — no copy-paste, no app switching
- Infinite chapter scrolling with offline caching
- Custom fonts, text size, light/dark themes (Material 3)
- Text-to-speech with floating mini-player, background playback, speed/pitch control, Bluetooth support, and multiple engine support
- Local EPUB and FB2 library with bulk import
- Backup & restore with granular selection and auto backup
- Regex text cleanup (strip ads and injected text)
- Automatic Cloudflare Turnstile bypass
- Novel migration between sources
- Library filters by genre, source, and category
- Download all chapters
- TTS reading timer
- 20 interface languages

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

---

### Plugins

NoveLA supports external Lua-based source plugins installable directly from the app.

Official plugin repo: [`HnDK0/external-sources`](https://github.com/HnDK0/external-sources)

To add: **Finder → Extensions → ⚙️ → paste repo URL**

---

## Contributing

Pull requests are welcome. For major changes, open an issue first.

- Fix or improve existing source parsers
- Add new sources via the [plugin repo](https://github.com/HnDK0/external-sources)
- Report bugs via [Issues](https://github.com/HnDK0/NoveLA/issues)

---

## Tech stack

Kotlin · Coroutines · Jetpack Compose · Material 3 · Room · Jsoup · OkHttp · Coil 3 · LuaJ · Hilt · WorkManager · Android TTS & Media APIs

---

## License

[GPL-3.0](LICENSE)
