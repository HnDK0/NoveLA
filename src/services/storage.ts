import {
  Book,
  Chapter,
  LibraryCategory,
  ReadingHistoryItem,
  RegexRule,
  ReaderSettings,
  TranslationSettings,
  SourceExtension,
  ManualHighlight,
} from '../types';
import { INITIAL_BOOKS, INITIAL_CHAPTERS, BUILT_IN_EXTENSIONS } from './sources';

const STORAGE_KEYS = {
  BOOKS: 'novela_books_v1',
  CHAPTERS: 'novela_chapters_v1',
  CATEGORIES: 'novela_categories_v1',
  HISTORY: 'novela_history_v1',
  REGEX_RULES: 'novela_regex_rules_v1',
  READER_SETTINGS: 'novela_reader_settings_v1',
  GLOBAL_TRANSLATION: 'novela_translation_settings_v1',
  PER_NOVEL_TRANSLATION: 'novela_per_novel_translation_v1',
  EXTENSIONS: 'novela_extensions_v1',
  HIGHLIGHTS: 'novela_highlights_v1',
};

export const DEFAULT_READER_SETTINGS: ReaderSettings = {
  fontSize: 18,
  lineHeight: 1.7,
  letterSpacing: 0.2,
  fontFamily: 'inter',
  theme: 'dark',
  textAlign: 'left',
  readingMode: 'continuous',
  maxWidth: 820,
  autoScrollSpeed: 0,
  sourceStripPosition: 'on-cover',
  mangaReadingMode: 'webtoon',
};

export const DEFAULT_TRANSLATION_SETTINGS: TranslationSettings = {
  enabled: false,
  sourceLang: 'auto',
  targetLang: 'es',
  mode: 'parallel',
  provider: 'google-free',
  apiKey: '',
  customEndpoint: 'https://api.openai.com/v1',
  customModel: 'gpt-4o-mini',
  customPrompt: 'Translate this novel chapter faithfully, preserving cultivation/fantasy terms and character speech tone.',
};

export const DEFAULT_CATEGORIES: LibraryCategory[] = [
  { id: 'all', name: 'All', order: 0 },
  { id: 'Reading', name: 'Reading', order: 1 },
  { id: 'Plan to Read', name: 'Plan to Read', order: 2 },
  { id: 'Completed', name: 'Completed', order: 3 },
  { id: 'On Hold', name: 'On Hold', order: 4 },
];

export const DEFAULT_REGEX_RULES: RegexRule[] = [
  {
    id: 'ad-rule-1',
    name: 'Strip Webnovel/Site watermark',
    pattern: 'Visit (?:webnovel|wuxiaworld|royalroad|novelfull)\\.com for latest chapters\\.',
    replacement: '',
    isEnabled: true,
    flags: 'gi',
  },
  {
    id: 'ad-rule-2',
    name: 'Clean translator discord plug',
    pattern: 'Join our discord at https?:\\/\\/discord\\.gg\\/\\S+',
    replacement: '',
    isEnabled: true,
    flags: 'gi',
  },
  {
    id: 'ad-rule-3',
    name: 'Remove "Find authorized novels in..."',
    pattern: 'Find authorized novels in Webnovel[^\n]+',
    replacement: '',
    isEnabled: true,
    flags: 'gi',
  },
];

class StorageService {
  // Books
  getBooks(): Book[] {
    const raw = localStorage.getItem(STORAGE_KEYS.BOOKS);
    if (!raw) {
      localStorage.setItem(STORAGE_KEYS.BOOKS, JSON.stringify(INITIAL_BOOKS));
      return INITIAL_BOOKS;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return INITIAL_BOOKS;
    }
  }

  saveBooks(books: Book[]): void {
    localStorage.setItem(STORAGE_KEYS.BOOKS, JSON.stringify(books));
  }

  getBook(bookUrl: string): Book | undefined {
    return this.getBooks().find((b) => b.url === bookUrl);
  }

  upsertBook(book: Book): void {
    const books = this.getBooks();
    const idx = books.findIndex((b) => b.url === book.url);
    if (idx >= 0) {
      books[idx] = { ...books[idx], ...book };
    } else {
      books.unshift(book);
    }
    this.saveBooks(books);
  }

  toggleInLibrary(bookUrl: string): Book | undefined {
    const books = this.getBooks();
    const book = books.find((b) => b.url === bookUrl);
    if (book) {
      book.inLibrary = !book.inLibrary;
      if (book.inLibrary && !book.category) {
        book.category = 'Reading';
        book.addedToLibraryEpochTimeMilli = Date.now();
      }
      this.saveBooks(books);
      return book;
    }
    return undefined;
  }

  setBookCategory(bookUrl: string, category: string): void {
    const books = this.getBooks();
    const book = books.find((b) => b.url === bookUrl);
    if (book) {
      book.category = category;
      this.saveBooks(books);
    }
  }

  // Chapters
  getAllChapters(): Record<string, Chapter[]> {
    const raw = localStorage.getItem(STORAGE_KEYS.CHAPTERS);
    if (!raw) {
      localStorage.setItem(STORAGE_KEYS.CHAPTERS, JSON.stringify(INITIAL_CHAPTERS));
      return INITIAL_CHAPTERS;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return INITIAL_CHAPTERS;
    }
  }

  getChaptersForBook(bookUrl: string): Chapter[] {
    const all = this.getAllChapters();
    if (all[bookUrl]) {
      return all[bookUrl];
    }
    // Generate synthetic chapters for mock books without pre-filled chapter bodies
    const book = this.getBook(bookUrl);
    const count = book?.totalChapters || 15;
    const generated: Chapter[] = [];
    for (let i = 1; i <= count; i++) {
      generated.push({
        title: `Chapter ${i}: The Journey Continues`,
        url: `${bookUrl}:c${i}`,
        bookUrl,
        position: i,
        read: false,
        lastReadPosition: 0,
        lastReadOffset: 0,
        uploaded: Date.now() - (count - i) * 86400000,
        volume: `Volume ${Math.ceil(i / 10)}`,
        content: `This is chapter ${i} of "${book?.title || 'the novel'}".

The misty mountains stretched toward the crimson horizon as the ancient bell echoed through the valley.

Every step upon the cultivation path demands sacrifice, willpower, and an unyielding spirit. The ambient Qi hummed like an invisible river, nourishing the soul and hardening the flesh against the trials of heaven and earth.

"Master, will we reach the sacred gate before nightfall?" the disciple asked, looking up at the towering stone peaks.

"Patience," the master murmured softly. "The path of a thousand miles begins with a single resolute step. Clear your mind, steady your breath, and observe the flow of the universe."

[End of Chapter ${i}. Tap Next to proceed to the following chapter.]`,
      });
    }
    all[bookUrl] = generated;
    localStorage.setItem(STORAGE_KEYS.CHAPTERS, JSON.stringify(all));
    return generated;
  }

  saveChaptersForBook(bookUrl: string, chapters: Chapter[]): void {
    const all = this.getAllChapters();
    all[bookUrl] = chapters;
    localStorage.setItem(STORAGE_KEYS.CHAPTERS, JSON.stringify(all));
  }

  getChapter(bookUrl: string, chapterUrl: string): Chapter | undefined {
    const chapters = this.getChaptersForBook(bookUrl);
    return chapters.find((c) => c.url === chapterUrl);
  }

  markChapterRead(bookUrl: string, chapterUrl: string, read: boolean = true): void {
    const chapters = this.getChaptersForBook(bookUrl);
    const ch = chapters.find((c) => c.url === chapterUrl);
    if (ch) {
      ch.read = read;
      this.saveChaptersForBook(bookUrl, chapters);
    }
  }

  markAllChaptersRead(bookUrl: string, read: boolean): void {
    const chapters = this.getChaptersForBook(bookUrl);
    chapters.forEach((c) => (c.read = read));
    this.saveChaptersForBook(bookUrl, chapters);
  }

  updateReadingPosition(bookUrl: string, chapterUrl: string, position: number): void {
    const chapters = this.getChaptersForBook(bookUrl);
    const ch = chapters.find((c) => c.url === chapterUrl);
    if (ch) {
      ch.lastReadPosition = position;
      ch.read = true;
      this.saveChaptersForBook(bookUrl, chapters);
    }

    const books = this.getBooks();
    const b = books.find((item) => item.url === bookUrl);
    if (b) {
      b.lastReadChapter = chapterUrl;
      b.lastReadEpochTimeMilli = Date.now();
      this.saveBooks(books);
    }
  }

  // Categories
  getCategories(): LibraryCategory[] {
    const raw = localStorage.getItem(STORAGE_KEYS.CATEGORIES);
    if (!raw) {
      localStorage.setItem(STORAGE_KEYS.CATEGORIES, JSON.stringify(DEFAULT_CATEGORIES));
      return DEFAULT_CATEGORIES;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return DEFAULT_CATEGORIES;
    }
  }

  saveCategories(categories: LibraryCategory[]): void {
    localStorage.setItem(STORAGE_KEYS.CATEGORIES, JSON.stringify(categories));
  }

  addCategory(name: string): LibraryCategory {
    const categories = this.getCategories();
    const newCat: LibraryCategory = {
      id: name.trim(),
      name: name.trim(),
      order: categories.length,
    };
    categories.push(newCat);
    this.saveCategories(categories);
    return newCat;
  }

  deleteCategory(id: string): void {
    let categories = this.getCategories();
    categories = categories.filter((c) => c.id !== id);
    this.saveCategories(categories);

    // Unset books with this category
    const books = this.getBooks();
    books.forEach((b) => {
      if (b.category === id) b.category = '';
    });
    this.saveBooks(books);
  }

  // History
  getHistory(): ReadingHistoryItem[] {
    const raw = localStorage.getItem(STORAGE_KEYS.HISTORY);
    if (!raw) return [];
    try {
      return JSON.parse(raw);
    } catch {
      return [];
    }
  }

  addHistoryItem(item: Omit<ReadingHistoryItem, 'id' | 'timestamp'>): void {
    let history = this.getHistory();
    // Remove previous entry for this book if exists to keep list clean
    history = history.filter((h) => h.bookUrl !== item.bookUrl);
    const newEntry: ReadingHistoryItem = {
      ...item,
      id: `hist_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      timestamp: Date.now(),
    };
    history.unshift(newEntry);
    if (history.length > 50) history = history.slice(0, 50);
    localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify(history));
  }

  clearHistory(): void {
    localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify([]));
  }

  deleteHistoryItem(id: string): void {
    const history = this.getHistory().filter((h) => h.id !== id);
    localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify(history));
  }

  // Regex Rules
  getRegexRules(): RegexRule[] {
    const raw = localStorage.getItem(STORAGE_KEYS.REGEX_RULES);
    if (!raw) {
      localStorage.setItem(STORAGE_KEYS.REGEX_RULES, JSON.stringify(DEFAULT_REGEX_RULES));
      return DEFAULT_REGEX_RULES;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return DEFAULT_REGEX_RULES;
    }
  }

  saveRegexRules(rules: RegexRule[]): void {
    localStorage.setItem(STORAGE_KEYS.REGEX_RULES, JSON.stringify(rules));
  }

  addRegexRule(rule: Omit<RegexRule, 'id'>): RegexRule {
    const rules = this.getRegexRules();
    const newRule: RegexRule = {
      ...rule,
      id: `rule_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
    };
    rules.push(newRule);
    this.saveRegexRules(rules);
    return newRule;
  }

  deleteRegexRule(id: string): void {
    const rules = this.getRegexRules().filter((r) => r.id !== id);
    this.saveRegexRules(rules);
  }

  // Reader Settings
  getReaderSettings(): ReaderSettings {
    const raw = localStorage.getItem(STORAGE_KEYS.READER_SETTINGS);
    if (!raw) return DEFAULT_READER_SETTINGS;
    try {
      return { ...DEFAULT_READER_SETTINGS, ...JSON.parse(raw) };
    } catch {
      return DEFAULT_READER_SETTINGS;
    }
  }

  saveReaderSettings(settings: Partial<ReaderSettings>): ReaderSettings {
    const current = this.getReaderSettings();
    const updated = { ...current, ...settings };
    localStorage.setItem(STORAGE_KEYS.READER_SETTINGS, JSON.stringify(updated));
    return updated;
  }

  // Translation Settings
  getGlobalTranslationSettings(): TranslationSettings {
    const raw = localStorage.getItem(STORAGE_KEYS.GLOBAL_TRANSLATION);
    if (!raw) return DEFAULT_TRANSLATION_SETTINGS;
    try {
      return { ...DEFAULT_TRANSLATION_SETTINGS, ...JSON.parse(raw) };
    } catch {
      return DEFAULT_TRANSLATION_SETTINGS;
    }
  }

  saveGlobalTranslationSettings(settings: Partial<TranslationSettings>): TranslationSettings {
    const current = this.getGlobalTranslationSettings();
    const updated = { ...current, ...settings };
    localStorage.setItem(STORAGE_KEYS.GLOBAL_TRANSLATION, JSON.stringify(updated));
    return updated;
  }

  getNovelTranslationSettings(bookUrl: string): TranslationSettings {
    const raw = localStorage.getItem(STORAGE_KEYS.PER_NOVEL_TRANSLATION);
    if (!raw) return this.getGlobalTranslationSettings();
    try {
      const mapping = JSON.parse(raw);
      if (mapping[bookUrl]) {
        return { ...this.getGlobalTranslationSettings(), ...mapping[bookUrl] };
      }
    } catch {
      // fallback
    }
    return this.getGlobalTranslationSettings();
  }

  saveNovelTranslationSettings(bookUrl: string, settings: Partial<TranslationSettings>): void {
    const raw = localStorage.getItem(STORAGE_KEYS.PER_NOVEL_TRANSLATION) || '{}';
    let mapping: Record<string, Partial<TranslationSettings>> = {};
    try {
      mapping = JSON.parse(raw);
    } catch {
      mapping = {};
    }
    mapping[bookUrl] = { ...(mapping[bookUrl] || {}), ...settings };
    localStorage.setItem(STORAGE_KEYS.PER_NOVEL_TRANSLATION, JSON.stringify(mapping));
  }

  // Extensions
  getExtensions(): SourceExtension[] {
    const raw = localStorage.getItem(STORAGE_KEYS.EXTENSIONS);
    if (!raw) {
      localStorage.setItem(STORAGE_KEYS.EXTENSIONS, JSON.stringify(BUILT_IN_EXTENSIONS));
      return BUILT_IN_EXTENSIONS;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return BUILT_IN_EXTENSIONS;
    }
  }

  saveExtensions(extensions: SourceExtension[]): void {
    localStorage.setItem(STORAGE_KEYS.EXTENSIONS, JSON.stringify(extensions));
  }

  toggleExtension(id: string): void {
    const extensions = this.getExtensions();
    const ext = extensions.find((e) => e.id === id);
    if (ext) {
      ext.isInstalled = !ext.isInstalled;
      this.saveExtensions(extensions);
    }
  }

  addCustomExtension(ext: SourceExtension): void {
    const extensions = this.getExtensions();
    extensions.push(ext);
    this.saveExtensions(extensions);
  }

  // Highlights
  getHighlights(bookUrl?: string): ManualHighlight[] {
    const raw = localStorage.getItem(STORAGE_KEYS.HIGHLIGHTS);
    if (!raw) return [];
    try {
      const all: ManualHighlight[] = JSON.parse(raw);
      return bookUrl ? all.filter((h) => h.bookUrl === bookUrl) : all;
    } catch {
      return [];
    }
  }

  addHighlight(highlight: Omit<ManualHighlight, 'id' | 'timestamp'>): ManualHighlight {
    const all = this.getHighlights();
    const item: ManualHighlight = {
      ...highlight,
      id: `hl_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      timestamp: Date.now(),
    };
    all.push(item);
    localStorage.setItem(STORAGE_KEYS.HIGHLIGHTS, JSON.stringify(all));
    return item;
  }

  deleteHighlight(id: string): void {
    const all = this.getHighlights().filter((h) => h.id !== id);
    localStorage.setItem(STORAGE_KEYS.HIGHLIGHTS, JSON.stringify(all));
  }

  // Full Backup Export and Restore
  createBackupData(): string {
    const backup = {
      version: '1.6.1',
      date: new Date().toISOString(),
      books: this.getBooks(),
      chapters: this.getAllChapters(),
      categories: this.getCategories(),
      history: this.getHistory(),
      regexRules: this.getRegexRules(),
      readerSettings: this.getReaderSettings(),
      globalTranslation: this.getGlobalTranslationSettings(),
      highlights: this.getHighlights(),
    };
    return JSON.stringify(backup, null, 2);
  }

  restoreBackupData(jsonString: string): { success: boolean; message: string } {
    try {
      const data = JSON.parse(jsonString);
      if (data.books && Array.isArray(data.books)) {
        this.saveBooks(data.books);
      }
      if (data.chapters) {
        localStorage.setItem(STORAGE_KEYS.CHAPTERS, JSON.stringify(data.chapters));
      }
      if (data.categories && Array.isArray(data.categories)) {
        this.saveCategories(data.categories);
      }
      if (data.history && Array.isArray(data.history)) {
        localStorage.setItem(STORAGE_KEYS.HISTORY, JSON.stringify(data.history));
      }
      if (data.regexRules && Array.isArray(data.regexRules)) {
        this.saveRegexRules(data.regexRules);
      }
      if (data.readerSettings) {
        this.saveReaderSettings(data.readerSettings);
      }
      if (data.highlights) {
        localStorage.setItem(STORAGE_KEYS.HIGHLIGHTS, JSON.stringify(data.highlights));
      }
      return { success: true, message: 'Backup restored successfully!' };
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : String(err);
      return { success: false, message: `Failed to restore: ${message}` };
    }
  }
}

export const storage = new StorageService();
