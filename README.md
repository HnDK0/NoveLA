<div align="center">

<img src="/ic_logo-playstore.png" width="88" height="88" alt="NoveLA"/>

# NoveLA (Web Edition)

Free and open-source web novel, light novel, and comic reader rewritten as a modern React application.

[![Version: 1.6.1](https://img.shields.io/badge/version-1.6.1-amber.svg)](package.json)
[![React: 19](https://img.shields.io/badge/React-19-blue.svg)](package.json)
[![Tailwind: v4](https://img.shields.io/badge/TailwindCSS-v4-38bdf8.svg)](package.json)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-green.svg)](LICENSE)

</div>

---

## Overview

**NoveLA** is a cross-platform web novel reader rewritten from the original Android application into a high-performance React + TypeScript SPA. It brings all core features of the original app directly to modern browsers.

---

## Features

- **Multi-Source Catalog Browsing**: Explore popular web novel repositories (Royal Road, Wuxiaworld, Scribble Hub, Ranobes, NovelFull, MTLNovel, MangaDex, and Local Books).
- **Global Search**: Search across all enabled sources simultaneously with aggregated results.
- **In-Reader Parallel Live Translation**:
  - Side-by-side (parallel) or in-place replacement mode.
  - Multi-engine support: Free Google Translate endpoint, Google Gemini API, and custom OpenAI-compatible endpoints.
  - Per-novel language pair settings and custom glossary prompts.
- **Text-to-Speech (TTS) Narration**:
  - Integrated with browser Web Speech API.
  - Floating draggable mini-player with speed (0.5x–2.5x) and pitch controls.
  - Live sentence/paragraph highlighting as audio plays.
  - Auto-advance to the next chapter upon finishing narration.
- **Manga & Comic Reader**:
  - Vertical continuous Webtoon scroll mode.
  - Horizontal paged reader mode with zoom and fit controls.
- **Reader Customization**:
  - 6 color theme presets: Dark, OLED Black, Warm Sepia, Light, Catppuccin Mocha, and Nord Polar.
  - Font choices: Inter, Lora Serif, Merriweather, Source Sans, Monospace.
  - Configurable font size, line height, letter spacing, text alignment, and auto-scroll.
- **Local EPUB & File Import**:
  - Direct in-browser EPUB parsing and chapter extraction.
  - Export any book from the library to EPUB or TXT format.
- **Regex Text Cleanup**:
  - Live rule engine to strip site watermarks, ads, Discord invitations, and translator plugs.
  - Interactive test sandbox with real-time preview.
- **Novel Migration**:
  - Switch source provider for any book while matching chapter sequences and preserving reading bookmarks.
- **Backup & Restore**:
  - Complete JSON backup export and restore for library, reading history, custom categories, settings, and regex rules.

---

## Development

```bash
# Install dependencies
npm install

# Start Vite dev server on port 3000
npm run dev

# Build for production
npm run build
```
