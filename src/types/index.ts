export type ContentType = 'NOVEL' | 'MANGA' | 'COMIC' | 'VIDEO';

export interface Book {
  title: string;
  url: string; // Unique ID (e.g., "royalroad:12345" or web URL)
  author?: string;
  artist?: string;
  completed: boolean;
  lastReadChapter?: string | null;
  inLibrary: boolean;
  coverImageUrl: string;
  description: string;
  lastReadEpochTimeMilli: number;
  addedToLibraryEpochTimeMilli: number;
  lastUpdateEpochTimeMilli: number;
  category: string; // e.g., "Reading", "Plan to Read", "Completed", or ""
  genres: string; // comma separated: "Action,Fantasy,LitRPG"
  rating: string; // e.g. "4.8", "4.5/5", "#12"
  status: string; // e.g. "Ongoing", "Completed", "Hiatus"
  lastUpdateDate: string;
  contentType: ContentType;
  sourceId: string;
  sourceName: string;
  totalChapters?: number;
}

export interface Chapter {
  title: string;
  url: string; // Unique ID
  bookUrl: string;
  position: number; // 0-indexed or 1-indexed sort order
  read: boolean;
  lastReadPosition: number; // paragraph index or scroll pixel
  lastReadOffset: number;
  uploaded?: number | null; // epoch ms
  volume?: string | null;
  content?: string; // HTML or text body
  images?: string[]; // Manga/comic images list
  isDownloaded?: boolean;
}

export interface LibraryCategory {
  id: string;
  name: string;
  order: number;
}

export interface ReadingHistoryItem {
  id: string;
  bookUrl: string;
  chapterUrl: string;
  chapterTitle: string;
  bookTitle: string;
  coverImageUrl: string;
  sourceName: string;
  timestamp: number;
  progressPercent: number;
}

export interface RegexRule {
  id: string;
  name: string;
  pattern: string;
  replacement: string;
  isEnabled: boolean;
  bookUrl?: string; // Optional: specific to novel or global
  flags?: string; // e.g., "gmi"
}

export interface TranslationSettings {
  enabled: boolean;
  sourceLang: string;
  targetLang: string;
  mode: 'parallel' | 'replace';
  provider: 'google-free' | 'gemini' | 'openai';
  apiKey?: string;
  customEndpoint?: string;
  customModel?: string;
  customPrompt?: string;
}

export interface ReaderSettings {
  fontSize: number; // px, 12 to 36
  lineHeight: number; // 1.2 to 2.6
  letterSpacing: number; // -1 to 5 px
  fontFamily: 'inter' | 'lora' | 'merriweather' | 'source-sans' | 'mono-reader';
  theme: 'amoled' | 'dark' | 'sepia' | 'light' | 'catppuccin' | 'nord' | 'lavender';
  textAlign: 'left' | 'justify';
  readingMode: 'continuous' | 'paged';
  maxWidth: number; // max-w-prose, max-w-2xl, max-w-3xl, max-w-4xl, etc.
  autoScrollSpeed: number; // 0 = off, 1-10
  sourceStripPosition: 'on-cover' | 'below-cover' | 'none';
  mangaReadingMode: 'webtoon' | 'pager';
}

export interface SourceExtension {
  id: string;
  name: string;
  version: string;
  language: string;
  baseUrl: string;
  icon: string;
  contentType: ContentType;
  isInstalled: boolean;
  isBuiltIn: boolean;
  code?: string;
  description: string;
}

export interface ManualHighlight {
  id: string;
  bookUrl: string;
  chapterUrl: string;
  paragraphIndex: number;
  text: string;
  color: string;
  note?: string;
  timestamp: number;
}
