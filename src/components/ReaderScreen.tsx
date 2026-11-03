import React, { useState, useEffect, useRef } from 'react';
import {
  ArrowLeft,
  Settings,
  Languages,
  Volume2,
  Bookmark,
  ChevronLeft,
  ChevronRight,
  List,
  Sliders,
  Type,
  Sun,
  Moon,
  Play,
  RotateCcw,
  Sparkles,
  Palette,
  X,
  Check,
  Highlighter,
} from 'lucide-react';
import { Book, Chapter, ReaderSettings, TranslationSettings, ManualHighlight } from '../types';
import { storage } from '../services/storage';
import { applyRegexRules } from '../services/regexCleaner';
import { translateText, SUPPORTED_LANGUAGES } from '../services/translation';
import { ttsService, TtsState } from '../services/tts';
import { MangaViewer } from './MangaViewer';
import { TranslationSettingsModal } from './TranslationSettingsModal';

interface ReaderScreenProps {
  book: Book;
  chapterUrl: string;
  onBack: () => void;
  onSelectChapter: (chapterUrl: string) => void;
}

export const ReaderScreen: React.FC<ReaderScreenProps> = ({
  book,
  chapterUrl,
  onBack,
  onSelectChapter,
}) => {
  const chapters = storage.getChaptersForBook(book.url);
  const chapter = chapters.find((c) => c.url === chapterUrl) || chapters[0];
  const currentIndex = chapters.findIndex((c) => c.url === chapter.url);

  // Settings
  const [readerSettings, setReaderSettings] = useState<ReaderSettings>(() => storage.getReaderSettings());
  const [translationSettings, setTranslationSettings] = useState<TranslationSettings>(() =>
    storage.getNovelTranslationSettings(book.url)
  );

  // UI state
  const [showControls, setShowControls] = useState(true);
  const [showSettingsDrawer, setShowSettingsDrawer] = useState(false);
  const [showChapterDrawer, setShowChapterDrawer] = useState(false);
  const [showTranslationModal, setShowTranslationModal] = useState(false);

  // Auto-scroll
  const [autoScrollSpeed, setAutoScrollSpeed] = useState(0);

  // Translation cache for current chapter: paragraph index -> translated string
  const [translatedParagraphs, setTranslatedParagraphs] = useState<Record<number, string>>({});
  const [isTranslatingChapter, setIsTranslatingChapter] = useState(false);

  // TTS state
  const [ttsState, setTtsState] = useState<TtsState>(ttsService.getState());

  // Highlights
  const [highlights, setHighlights] = useState<ManualHighlight[]>(() =>
    storage.getHighlights(book.url).filter((h) => h.chapterUrl === chapter.url)
  );
  const [selectedParagraphForHighlight, setSelectedParagraphForHighlight] = useState<number | null>(null);

  // Scroll progress
  const contentRef = useRef<HTMLDivElement>(null);
  const [scrollPercent, setScrollPercent] = useState(0);

  // Check if manga/comic
  if (book.contentType === 'MANGA' || (chapter.images && chapter.images.length > 0)) {
    return (
      <MangaViewer
        chapter={chapter}
        onBack={onBack}
        hasNext={currentIndex < chapters.length - 1}
        hasPrev={currentIndex > 0}
        onNextChapter={() => currentIndex < chapters.length - 1 && onSelectChapter(chapters[currentIndex + 1].url)}
        onPrevChapter={() => currentIndex > 0 && onSelectChapter(chapters[currentIndex - 1].url)}
      />
    );
  }

  // Raw and cleaned text
  const regexRules = storage.getRegexRules();
  const cleanedText = applyRegexRules(chapter.content || 'No text found in chapter.', regexRules);
  const rawParagraphs = cleanedText.split('\n\n').filter((p) => p.trim().length > 0);

  // Subscribe to TTS changes
  useEffect(() => {
    const unsub = ttsService.subscribe(setTtsState);
    return () => unsub();
  }, []);

  // Update read progress on mount / change
  useEffect(() => {
    storage.markChapterRead(book.url, chapter.url, true);
    storage.addHistoryItem({
      bookUrl: book.url,
      chapterUrl: chapter.url,
      chapterTitle: chapter.title,
      bookTitle: book.title,
      coverImageUrl: book.coverImageUrl,
      sourceName: book.sourceName,
      progressPercent: 0,
    });
    // Reset translated paragraphs cache for new chapter
    setTranslatedParagraphs({});
  }, [chapter.url]);

  // Handle translation if enabled
  useEffect(() => {
    if (!translationSettings.enabled) return;

    let isMounted = true;
    setIsTranslatingChapter(true);

    const translateAll = async () => {
      const results: Record<number, string> = {};
      for (let i = 0; i < rawParagraphs.length; i++) {
        if (!isMounted) break;
        const translated = await translateText(rawParagraphs[i], translationSettings);
        results[i] = translated;
        setTranslatedParagraphs({ ...results });
      }
      if (isMounted) setIsTranslatingChapter(false);
    };

    translateAll();

    return () => {
      isMounted = false;
    };
  }, [chapter.url, translationSettings.enabled, translationSettings.targetLang, translationSettings.mode]);

  // Auto-scroll loop
  useEffect(() => {
    if (autoScrollSpeed <= 0) return;
    const interval = setInterval(() => {
      window.scrollBy({ top: autoScrollSpeed * 1.5, behavior: 'smooth' });
    }, 50);
    return () => clearInterval(interval);
  }, [autoScrollSpeed]);

  // Track scroll position
  useEffect(() => {
    const handleScroll = () => {
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;
      if (docHeight > 0) {
        const pct = Math.min(100, Math.max(0, Math.round((window.scrollY / docHeight) * 100)));
        setScrollPercent(pct);
        storage.updateReadingPosition(book.url, chapter.url, pct);
      }
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, [book.url, chapter.url]);

  const handleNext = () => {
    if (currentIndex < chapters.length - 1) {
      ttsService.stop();
      onSelectChapter(chapters[currentIndex + 1].url);
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  };

  const handlePrev = () => {
    if (currentIndex > 0) {
      ttsService.stop();
      onSelectChapter(chapters[currentIndex - 1].url);
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  };

  const startTtsFrom = (index: number) => {
    ttsService.start(rawParagraphs, index, () => {
      // Auto-advance to next chapter if available
      if (currentIndex < chapters.length - 1) {
        handleNext();
      }
    });
  };

  const addHighlight = (color: string) => {
    if (selectedParagraphForHighlight === null) return;
    const text = rawParagraphs[selectedParagraphForHighlight];
    const newHl = storage.addHighlight({
      bookUrl: book.url,
      chapterUrl: chapter.url,
      paragraphIndex: selectedParagraphForHighlight,
      text,
      color,
    });
    setHighlights([...highlights, newHl]);
    setSelectedParagraphForHighlight(null);
  };

  const themeClass = `theme-${readerSettings.theme}`;
  const fontClass = `font-${readerSettings.fontFamily}`;

  return (
    <div className={`min-h-screen ${themeClass} ${fontClass} relative transition-colors duration-200 select-text`}>
      {/* Top Header Overlay */}
      <div
        className={`fixed top-0 left-0 right-0 z-40 transition-transform duration-300 ${
          showControls ? 'translate-y-0' : '-translate-y-full'
        } bg-zinc-900/95 backdrop-blur-md border-b border-zinc-800/80 px-4 py-3 text-zinc-100 shadow-lg`}
      >
        <div className="max-w-4xl mx-auto flex items-center justify-between">
          <div className="flex items-center space-x-3 min-w-0">
            <button
              onClick={onBack}
              className="p-2 rounded-xl bg-zinc-800/60 hover:bg-zinc-700 text-zinc-300 transition"
              title="Back to Book"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div className="min-w-0">
              <h3 className="font-bold text-sm text-zinc-100 truncate">{chapter.title}</h3>
              <p className="text-xs text-zinc-400 truncate">{book.title}</p>
            </div>
          </div>

          <div className="flex items-center space-x-1 sm:space-x-2 shrink-0">
            {/* TTS narration trigger */}
            <button
              onClick={() => {
                if (ttsState.isPlaying) {
                  ttsService.stop();
                } else {
                  startTtsFrom(0);
                }
              }}
              className={`p-2 rounded-xl transition ${
                ttsState.isPlaying
                  ? 'bg-amber-500 text-zinc-950 font-bold'
                  : 'text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800'
              }`}
              title="Narrate with TTS"
            >
              <Volume2 className="w-5 h-5" />
            </button>

            {/* Translation Modal Trigger */}
            <button
              onClick={() => setShowTranslationModal(true)}
              className={`p-2 rounded-xl transition ${
                translationSettings.enabled
                  ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30'
                  : 'text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800'
              }`}
              title="Translation settings & parallel view"
            >
              <Languages className="w-5 h-5" />
            </button>

            {/* Table of contents */}
            <button
              onClick={() => setShowChapterDrawer(true)}
              className="p-2 rounded-xl text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800 transition"
              title="Chapters List"
            >
              <List className="w-5 h-5" />
            </button>

            {/* Styling / Reader settings */}
            <button
              onClick={() => setShowSettingsDrawer(true)}
              className="p-2 rounded-xl text-zinc-300 hover:text-zinc-100 hover:bg-zinc-800 transition"
              title="Reader Customization"
            >
              <Sliders className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>

      {/* Main Reading Canvas */}
      <div
        ref={contentRef}
        onClick={() => setShowControls(!showControls)}
        className="max-w-3xl mx-auto px-5 sm:px-8 pt-24 pb-36 min-h-screen cursor-pointer"
        style={{
          fontSize: `${readerSettings.fontSize}px`,
          lineHeight: readerSettings.lineHeight,
          letterSpacing: `${readerSettings.letterSpacing}px`,
          textAlign: readerSettings.textAlign,
        }}
      >
        {/* Chapter Header */}
        <div className="mb-10 text-center border-b pb-6 border-current/15 opacity-80">
          <span className="text-xs font-semibold uppercase tracking-widest block opacity-70 mb-1">
            {chapter.volume || book.title}
          </span>
          <h1 className="text-2xl sm:text-3xl font-bold">{chapter.title}</h1>
          {translationSettings.enabled && (
            <div className="inline-flex items-center space-x-1 text-xs px-2.5 py-1 rounded-full bg-blue-500/10 text-blue-400 border border-blue-500/20 mt-3">
              <Sparkles className="w-3.5 h-3.5" />
              <span>
                Translated to {translationSettings.targetLang.toUpperCase()} (
                {translationSettings.mode === 'parallel' ? 'Parallel Mode' : 'Replaced'})
              </span>
            </div>
          )}
        </div>

        {/* Paragraphs */}
        <div className="space-y-6">
          {rawParagraphs.map((para, idx) => {
            const isSpeaking = ttsState.isPlaying && ttsState.currentParagraphIndex === idx;
            const translated = translatedParagraphs[idx];
            const highlight = highlights.find((h) => h.paragraphIndex === idx);

            return (
              <div
                key={idx}
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedParagraphForHighlight(idx);
                }}
                className={`relative group rounded-xl p-2 transition-all ${
                  isSpeaking
                    ? 'ring-2 ring-amber-400 bg-amber-500/15'
                    : highlight
                    ? 'border-l-4 pl-3'
                    : 'hover:bg-current/5'
                }`}
                style={{
                  borderColor: highlight ? highlight.color : undefined,
                  backgroundColor: highlight ? `${highlight.color}15` : undefined,
                }}
              >
                {/* Paragraph Action Toolbar on hover/touch */}
                <div className="absolute top-1 right-1 opacity-0 group-hover:opacity-100 transition flex items-center space-x-1 bg-zinc-900/90 text-zinc-100 rounded-lg p-1 text-xs z-10 shadow-md">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      startTtsFrom(idx);
                    }}
                    title="Speak from here"
                    className="p-1 hover:text-amber-400 rounded"
                  >
                    <Play className="w-3.5 h-3.5" />
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setSelectedParagraphForHighlight(idx);
                    }}
                    title="Highlight paragraph"
                    className="p-1 hover:text-yellow-400 rounded"
                  >
                    <Highlighter className="w-3.5 h-3.5" />
                  </button>
                </div>

                {/* Display based on translation settings */}
                {translationSettings.enabled && translationSettings.mode === 'parallel' ? (
                  <div className="space-y-2">
                    <p className="opacity-75 text-[0.95em]">{para}</p>
                    <p className="font-medium text-amber-300/90 border-l-2 border-amber-500/40 pl-3">
                      {translated || (
                        <span className="text-xs opacity-50 italic">Translating paragraph...</span>
                      )}
                    </p>
                  </div>
                ) : translationSettings.enabled && translationSettings.mode === 'replace' ? (
                  <p>{translated || para}</p>
                ) : (
                  <p>{para}</p>
                )}
              </div>
            );
          })}
        </div>

        {/* End of Chapter Navigation Trigger */}
        <div className="mt-16 pt-8 border-t border-current/15 flex items-center justify-between">
          <button
            disabled={currentIndex === 0}
            onClick={(e) => {
              e.stopPropagation();
              handlePrev();
            }}
            className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-current/10 hover:bg-current/20 disabled:opacity-30 text-xs font-semibold transition"
          >
            <ChevronLeft className="w-4 h-4" />
            <span>Previous Chapter</span>
          </button>

          <span className="text-xs opacity-60">
            {currentIndex + 1} / {chapters.length}
          </span>

          <button
            disabled={currentIndex >= chapters.length - 1}
            onClick={(e) => {
              e.stopPropagation();
              handleNext();
            }}
            className="flex items-center space-x-1.5 px-4 py-2 rounded-xl bg-amber-500 text-zinc-950 font-bold disabled:opacity-30 text-xs transition shadow-md"
          >
            <span>Next Chapter</span>
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Bottom Sticky Reading Bar */}
      <div
        className={`fixed bottom-0 left-0 right-0 z-40 transition-transform duration-300 ${
          showControls ? 'translate-y-0' : 'translate-y-full'
        } bg-zinc-900/95 backdrop-blur-md border-t border-zinc-800/80 px-4 py-3 text-zinc-100 shadow-2xl`}
      >
        <div className="max-w-4xl mx-auto flex items-center justify-between gap-4">
          <button
            disabled={currentIndex === 0}
            onClick={handlePrev}
            className="p-2 rounded-xl bg-zinc-800 hover:bg-zinc-700 disabled:opacity-30 transition"
          >
            <ChevronLeft className="w-5 h-5" />
          </button>

          {/* Reading Progress slider */}
          <div className="flex-1 flex items-center space-x-3">
            <span className="text-xs text-zinc-400 font-mono w-10 text-right">{scrollPercent}%</span>
            <div className="flex-1 relative flex items-center">
              <input
                type="range"
                min="0"
                max="100"
                value={scrollPercent}
                onChange={(e) => {
                  const pct = parseInt(e.target.value);
                  setScrollPercent(pct);
                  const targetY = ((document.documentElement.scrollHeight - window.innerHeight) * pct) / 100;
                  window.scrollTo({ top: targetY, behavior: 'instant' });
                }}
                className="w-full accent-amber-500 h-1.5 bg-zinc-800 rounded-lg cursor-pointer"
              />
            </div>
          </div>

          <button
            disabled={currentIndex >= chapters.length - 1}
            onClick={handleNext}
            className="p-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold disabled:opacity-30 transition"
          >
            <ChevronRight className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Reader Styling Drawer */}
      {showSettingsDrawer && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/60 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="w-full max-w-sm bg-zinc-900 border-l border-zinc-800 h-full p-6 text-zinc-100 shadow-2xl overflow-y-auto space-y-6">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <h3 className="font-bold text-base flex items-center space-x-2 text-zinc-100">
                <Sliders className="w-4 h-4 text-amber-400" />
                <span>Reader Customization</span>
              </h3>
              <button
                onClick={() => setShowSettingsDrawer(false)}
                className="p-1 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-zinc-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Theme Presets */}
            <div>
              <label className="block text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
                Color Theme
              </label>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'dark', label: 'Dark', bg: '#18181b', text: '#e4e4e7' },
                  { id: 'amoled', label: 'OLED Black', bg: '#000000', text: '#e4e4e7' },
                  { id: 'sepia', label: 'Warm Sepia', bg: '#f6f1e8', text: '#382c1f' },
                  { id: 'light', label: 'Pure Light', bg: '#fafafa', text: '#18181b' },
                  { id: 'catppuccin', label: 'Catppuccin', bg: '#1e1e2e', text: '#cdd6f4' },
                  { id: 'nord', label: 'Nord Polar', bg: '#2e3440', text: '#eceff4' },
                ].map((th) => (
                  <button
                    key={th.id}
                    onClick={() => {
                      const updated = storage.saveReaderSettings({ theme: th.id as any });
                      setReaderSettings(updated);
                    }}
                    className={`p-2.5 rounded-xl border flex items-center space-x-2 transition ${
                      readerSettings.theme === th.id
                        ? 'border-amber-400 ring-1 ring-amber-400'
                        : 'border-zinc-800 hover:border-zinc-700'
                    }`}
                    style={{ backgroundColor: th.bg, color: th.text }}
                  >
                    <span className="w-3 h-3 rounded-full border border-current shrink-0" />
                    <span className="text-xs font-semibold">{th.label}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Font Family */}
            <div>
              <label className="block text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
                Font Typeface
              </label>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'inter', label: 'Inter Sans', family: 'sans-serif' },
                  { id: 'lora', label: 'Lora Serif', family: 'serif' },
                  { id: 'merriweather', label: 'Merriweather', family: 'serif' },
                  { id: 'source-sans', label: 'Source Sans', family: 'sans-serif' },
                  { id: 'mono-reader', label: 'Monospace', family: 'monospace' },
                ].map((f) => (
                  <button
                    key={f.id}
                    onClick={() => {
                      const updated = storage.saveReaderSettings({ fontFamily: f.id as any });
                      setReaderSettings(updated);
                    }}
                    className={`p-2.5 rounded-xl border text-left transition ${
                      readerSettings.fontFamily === f.id
                        ? 'border-amber-500 bg-amber-500/10 text-amber-300'
                        : 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-zinc-200'
                    }`}
                  >
                    <div className="text-xs font-bold" style={{ fontFamily: f.family }}>
                      {f.label}
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {/* Font Size */}
            <div>
              <div className="flex justify-between text-xs text-zinc-400 mb-1">
                <span>Font Size</span>
                <span className="text-amber-400 font-bold">{readerSettings.fontSize}px</span>
              </div>
              <div className="flex items-center space-x-3">
                <span className="text-xs font-bold">A</span>
                <input
                  type="range"
                  min="13"
                  max="32"
                  value={readerSettings.fontSize}
                  onChange={(e) => {
                    const updated = storage.saveReaderSettings({ fontSize: parseInt(e.target.value) });
                    setReaderSettings(updated);
                  }}
                  className="flex-1 accent-amber-500 h-1.5 bg-zinc-800 rounded-lg cursor-pointer"
                />
                <span className="text-lg font-bold">A</span>
              </div>
            </div>

            {/* Line Height */}
            <div>
              <div className="flex justify-between text-xs text-zinc-400 mb-1">
                <span>Line Height</span>
                <span className="text-amber-400 font-bold">{readerSettings.lineHeight.toFixed(1)}</span>
              </div>
              <input
                type="range"
                min="1.2"
                max="2.5"
                step="0.1"
                value={readerSettings.lineHeight}
                onChange={(e) => {
                  const updated = storage.saveReaderSettings({ lineHeight: parseFloat(e.target.value) });
                  setReaderSettings(updated);
                }}
                className="w-full accent-amber-500 h-1.5 bg-zinc-800 rounded-lg cursor-pointer"
              />
            </div>

            {/* Auto Scroll */}
            <div className="p-3 bg-zinc-950 border border-zinc-800 rounded-xl space-y-2">
              <div className="flex justify-between text-xs text-zinc-300">
                <span>Auto-Scroll Speed</span>
                <span className="text-amber-400 font-bold">
                  {autoScrollSpeed === 0 ? 'Off' : `${autoScrollSpeed}x`}
                </span>
              </div>
              <input
                type="range"
                min="0"
                max="8"
                step="1"
                value={autoScrollSpeed}
                onChange={(e) => setAutoScrollSpeed(parseInt(e.target.value))}
                className="w-full accent-amber-500 h-1.5 bg-zinc-800 rounded-lg cursor-pointer"
              />
            </div>
          </div>
        </div>
      )}

      {/* Chapters Table of Contents Drawer */}
      {showChapterDrawer && (
        <div className="fixed inset-0 z-50 flex justify-start bg-black/60 backdrop-blur-xs animate-in fade-in duration-150">
          <div className="w-full max-w-sm bg-zinc-900 border-r border-zinc-800 h-full p-5 text-zinc-100 shadow-2xl flex flex-col">
            <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
              <h3 className="font-bold text-sm text-zinc-100 flex items-center space-x-2">
                <List className="w-4 h-4 text-amber-400" />
                <span>Chapters ({chapters.length})</span>
              </h3>
              <button
                onClick={() => setShowChapterDrawer(false)}
                className="p-1 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-zinc-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-3 space-y-1 pr-1">
              {chapters.map((ch, idx) => {
                const isCurrent = ch.url === chapter.url;
                return (
                  <button
                    key={ch.url}
                    onClick={() => {
                      onSelectChapter(ch.url);
                      setShowChapterDrawer(false);
                      window.scrollTo({ top: 0, behavior: 'instant' });
                    }}
                    className={`w-full text-left p-3 rounded-xl text-xs flex items-center justify-between transition ${
                      isCurrent
                        ? 'bg-amber-500/15 text-amber-400 font-bold border border-amber-500/30'
                        : 'text-zinc-300 hover:bg-zinc-800 hover:text-zinc-100'
                    }`}
                  >
                    <span className="truncate flex-1">{ch.title}</span>
                    {ch.read && <Check className="w-3.5 h-3.5 text-emerald-400 shrink-0 ml-2" />}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Highlight Color Picker Modal */}
      {selectedParagraphForHighlight !== null && (
        <div className="fixed bottom-24 left-1/2 -translate-x-1/2 z-50 bg-zinc-900 border border-zinc-700 p-2.5 rounded-2xl shadow-2xl flex items-center space-x-2 animate-in zoom-in-95 duration-100">
          <span className="text-xs text-zinc-400 font-semibold px-1">Highlight:</span>
          {['#eab308', '#22c55e', '#3b82f6', '#ec4899', '#a855f7'].map((c) => (
            <button
              key={c}
              onClick={() => addHighlight(c)}
              className="w-6 h-6 rounded-full border border-white/20 transition hover:scale-110"
              style={{ backgroundColor: c }}
            />
          ))}
          <button
            onClick={() => setSelectedParagraphForHighlight(null)}
            className="p-1 text-zinc-400 hover:text-zinc-200 rounded"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Translation Settings Modal */}
      <TranslationSettingsModal
        isOpen={showTranslationModal}
        onClose={() => {
          setShowTranslationModal(false);
          setTranslationSettings(storage.getNovelTranslationSettings(book.url));
        }}
        bookUrl={book.url}
        bookTitle={book.title}
      />
    </div>
  );
};
