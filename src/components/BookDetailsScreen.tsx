import React, { useState, useMemo } from 'react';
import {
  ArrowLeft,
  BookOpen,
  Bookmark,
  BookmarkCheck,
  Download,
  Share2,
  FileDown,
  Languages,
  ArrowRightLeft,
  Star,
  Clock,
  Tag,
  ListFilter,
  CheckCircle2,
  Circle,
  Search,
  ChevronDown,
  ChevronUp,
  FolderPlus,
} from 'lucide-react';
import { Book, Chapter } from '../types';
import { storage } from '../services/storage';
import { exportBookToEpub, exportBookToTxt } from '../services/epubImporter';
import { TranslationSettingsModal } from './TranslationSettingsModal';
import { NovelMigrationModal } from './NovelMigrationModal';

interface BookDetailsScreenProps {
  book: Book;
  onBack: () => void;
  onOpenChapter: (chapterUrl: string) => void;
  onBookUpdated?: (updated: Book) => void;
}

export const BookDetailsScreen: React.FC<BookDetailsScreenProps> = ({
  book,
  onBack,
  onOpenChapter,
  onBookUpdated,
}) => {
  const [currentBook, setCurrentBook] = useState<Book>(book);
  const [chapters, setChapters] = useState<Chapter[]>(() => storage.getChaptersForBook(book.url));
  const categories = storage.getCategories();

  // Modals
  const [showTranslationModal, setShowTranslationModal] = useState(false);
  const [showMigrationModal, setShowMigrationModal] = useState(false);
  const [showCategoryMenu, setShowCategoryMenu] = useState(false);

  // Chapters list filters & sort
  const [searchQuery, setSearchQuery] = useState('');
  const [sortAsc, setSortAsc] = useState(true);
  const [filterUnreadOnly, setFilterUnreadOnly] = useState(false);
  const [isDownloadingBatch, setIsDownloadingBatch] = useState(false);
  const [isExporting, setIsExporting] = useState(false);

  const toggleLibrary = () => {
    const updated = storage.toggleInLibrary(currentBook.url);
    if (updated) {
      setCurrentBook({ ...updated });
      onBookUpdated?.(updated);
    }
  };

  const setCategory = (cat: string) => {
    storage.setBookCategory(currentBook.url, cat);
    const updated = { ...currentBook, category: cat };
    setCurrentBook(updated);
    onBookUpdated?.(updated);
    setShowCategoryMenu(false);
  };

  const toggleChapterRead = (chapterUrl: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const ch = chapters.find((c) => c.url === chapterUrl);
    if (ch) {
      const newRead = !ch.read;
      storage.markChapterRead(currentBook.url, chapterUrl, newRead);
      setChapters(storage.getChaptersForBook(currentBook.url));
    }
  };

  const handleMarkAll = (read: boolean) => {
    storage.markAllChaptersRead(currentBook.url, read);
    setChapters(storage.getChaptersForBook(currentBook.url));
  };

  const handleBatchDownload = () => {
    setIsDownloadingBatch(true);
    setTimeout(() => {
      const updated = chapters.map((c) => ({ ...c, isDownloaded: true }));
      storage.saveChaptersForBook(currentBook.url, updated);
      setChapters(updated);
      setIsDownloadingBatch(false);
    }, 800);
  };

  const handleExportEpub = async () => {
    setIsExporting(true);
    try {
      const blob = await exportBookToEpub(currentBook, chapters);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${currentBook.title.replace(/[^a-z0-9]/gi, '_')}.epub`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('EPUB export error:', err);
    } finally {
      setIsExporting(false);
    }
  };

  const handleExportTxt = () => {
    const blob = exportBookToTxt(currentBook, chapters);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${currentBook.title.replace(/[^a-z0-9]/gi, '_')}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Filter and sort chapters
  const filteredChapters = useMemo(() => {
    return chapters
      .filter((c) => {
        if (filterUnreadOnly && c.read) return false;
        if (searchQuery && !c.title.toLowerCase().includes(searchQuery.toLowerCase())) return false;
        return true;
      })
      .sort((a, b) => (sortAsc ? a.position - b.position : b.position - a.position));
  }, [chapters, filterUnreadOnly, searchQuery, sortAsc]);

  // Determine continue chapter
  const lastReadChapterObj = currentBook.lastReadChapter
    ? chapters.find((c) => c.url === currentBook.lastReadChapter)
    : null;
  const nextUnreadChapter = chapters.find((c) => !c.read) || chapters[0];
  const primaryChapterToOpen = lastReadChapterObj || nextUnreadChapter || chapters[0];

  const genresList = currentBook.genres ? currentBook.genres.split(',').map((g) => g.trim()) : [];

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pb-28 pt-4 md:pt-20 px-4 md:px-8">
      <div className="max-w-5xl mx-auto space-y-6">
        {/* Back navigation */}
        <button
          onClick={onBack}
          className="flex items-center space-x-2 text-zinc-400 hover:text-zinc-100 transition px-2 py-1 rounded-lg"
        >
          <ArrowLeft className="w-5 h-5" />
          <span className="text-sm font-semibold">Back</span>
        </button>

        {/* Book Header Card */}
        <div className="relative bg-zinc-900 border border-zinc-800 rounded-3xl p-6 md:p-8 shadow-2xl overflow-hidden">
          {/* Blurred Cover Backdrop */}
          <div
            className="absolute inset-0 opacity-15 blur-3xl scale-125 pointer-events-none"
            style={{
              backgroundImage: `url(${currentBook.coverImageUrl})`,
              backgroundPosition: 'center',
              backgroundSize: 'cover',
            }}
          />

          <div className="relative z-10 flex flex-col md:flex-row gap-6 md:gap-8 items-start">
            {/* Cover image */}
            <div className="w-36 sm:w-44 shrink-0 mx-auto md:mx-0 shadow-2xl rounded-2xl overflow-hidden border border-zinc-700/60 aspect-[2/3]">
              <img
                src={currentBook.coverImageUrl}
                alt={currentBook.title}
                className="w-full h-full object-cover"
              />
            </div>

            {/* Info details */}
            <div className="flex-1 min-w-0 space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="px-2.5 py-1 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20 text-xs font-bold uppercase tracking-wider">
                  {currentBook.sourceName}
                </span>
                <span className="px-2.5 py-1 rounded-lg bg-zinc-800 text-zinc-300 text-xs font-medium">
                  {currentBook.contentType}
                </span>
                <span
                  className={`px-2.5 py-1 rounded-lg text-xs font-semibold ${
                    currentBook.status === 'Completed'
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                      : 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                  }`}
                >
                  {currentBook.status || 'Ongoing'}
                </span>
                {currentBook.rating && (
                  <span className="flex items-center space-x-1 px-2.5 py-1 rounded-lg bg-zinc-800 text-amber-400 text-xs font-bold">
                    <Star className="w-3.5 h-3.5 fill-current" />
                    <span>{currentBook.rating}</span>
                  </span>
                )}
              </div>

              <h1 className="text-2xl sm:text-3xl font-extrabold text-zinc-100 tracking-tight leading-snug">
                {currentBook.title}
              </h1>

              {currentBook.author && (
                <p className="text-sm text-zinc-400">
                  By <span className="text-zinc-200 font-medium">{currentBook.author}</span>
                </p>
              )}

              {/* Genres tags */}
              {genresList.length > 0 && (
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {genresList.map((g) => (
                    <span
                      key={g}
                      className="text-[11px] px-2 py-0.5 rounded-full bg-zinc-800/80 text-zinc-400 border border-zinc-700/50"
                    >
                      {g}
                    </span>
                  ))}
                </div>
              )}

              {/* Action Buttons Row */}
              <div className="flex flex-wrap items-center gap-2.5 pt-3">
                {/* Primary Start/Continue Reading */}
                {primaryChapterToOpen && (
                  <button
                    onClick={() => onOpenChapter(primaryChapterToOpen.url)}
                    className="flex items-center space-x-2 px-6 py-3 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-extrabold text-sm rounded-xl transition shadow-lg shadow-amber-500/20"
                  >
                    <BookOpen className="w-5 h-5 fill-current" />
                    <span>
                      {lastReadChapterObj
                        ? `Continue: ${lastReadChapterObj.title.split(':')[0]}`
                        : 'Start Reading'}
                    </span>
                  </button>
                )}

                {/* In Library toggle */}
                <button
                  onClick={toggleLibrary}
                  className={`flex items-center space-x-2 px-4 py-3 rounded-xl border text-sm font-semibold transition ${
                    currentBook.inLibrary
                      ? 'bg-amber-500/15 border-amber-500/40 text-amber-400'
                      : 'bg-zinc-800/60 border-zinc-700 text-zinc-300 hover:bg-zinc-800'
                  }`}
                >
                  {currentBook.inLibrary ? (
                    <>
                      <BookmarkCheck className="w-4 h-4 fill-current" />
                      <span>In Library</span>
                    </>
                  ) : (
                    <>
                      <Bookmark className="w-4 h-4" />
                      <span>Add to Library</span>
                    </>
                  )}
                </button>

                {/* Category Selector */}
                {currentBook.inLibrary && (
                  <div className="relative">
                    <button
                      onClick={() => setShowCategoryMenu(!showCategoryMenu)}
                      className="flex items-center space-x-1.5 px-3.5 py-3 rounded-xl bg-zinc-800/60 border border-zinc-700 text-zinc-300 text-sm font-medium hover:bg-zinc-800 transition"
                    >
                      <FolderPlus className="w-4 h-4 text-amber-400" />
                      <span>{currentBook.category || 'Reading'}</span>
                      <ChevronDown className="w-3.5 h-3.5 ml-1" />
                    </button>

                    {showCategoryMenu && (
                      <div className="absolute left-0 top-full mt-2 w-48 bg-zinc-900 border border-zinc-800 rounded-xl p-1 shadow-2xl z-20">
                        {categories
                          .filter((c) => c.id !== 'all')
                          .map((cat) => (
                            <button
                              key={cat.id}
                              onClick={() => setCategory(cat.name)}
                              className="w-full text-left px-3 py-2 text-xs font-semibold text-zinc-300 hover:text-amber-400 hover:bg-zinc-800 rounded-lg transition"
                            >
                              {cat.name}
                            </button>
                          ))}
                      </div>
                    )}
                  </div>
                )}

                {/* Per-Novel Translation Settings */}
                <button
                  onClick={() => setShowTranslationModal(true)}
                  className="p-3 rounded-xl bg-zinc-800/60 border border-zinc-700 text-zinc-300 hover:text-blue-400 hover:bg-zinc-800 transition"
                  title="Translation Settings"
                >
                  <Languages className="w-4 h-4" />
                </button>

                {/* Novel Migration */}
                <button
                  onClick={() => setShowMigrationModal(true)}
                  className="p-3 rounded-xl bg-zinc-800/60 border border-zinc-700 text-zinc-300 hover:text-purple-400 hover:bg-zinc-800 transition"
                  title="Migrate to Another Source"
                >
                  <ArrowRightLeft className="w-4 h-4" />
                </button>

                {/* EPUB Export */}
                <button
                  onClick={handleExportEpub}
                  disabled={isExporting}
                  className="p-3 rounded-xl bg-zinc-800/60 border border-zinc-700 text-zinc-300 hover:text-emerald-400 hover:bg-zinc-800 transition disabled:opacity-50"
                  title="Export to EPUB"
                >
                  <FileDown className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>

          {/* Description */}
          {currentBook.description && (
            <div className="mt-6 pt-6 border-t border-zinc-800/80 text-xs sm:text-sm text-zinc-300 leading-relaxed whitespace-pre-line">
              {currentBook.description}
            </div>
          )}
        </div>

        {/* Chapters Section */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 shadow-2xl space-y-4">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-zinc-800/80 pb-4">
            <div>
              <h2 className="text-lg font-bold text-zinc-100 flex items-center space-x-2">
                <span>Chapters</span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-400">
                  {chapters.length}
                </span>
              </h2>
              <p className="text-xs text-zinc-400">
                {chapters.filter((c) => c.read).length} read · {chapters.length - chapters.filter((c) => c.read).length} unread
              </p>
            </div>

            {/* Quick Actions */}
            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => setSortAsc(!sortAsc)}
                className="px-3 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-xs font-semibold text-zinc-300 transition"
              >
                Sort: {sortAsc ? 'Asc (1→N)' : 'Desc (N→1)'}
              </button>

              <button
                onClick={() => setFilterUnreadOnly(!filterUnreadOnly)}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                  filterUnreadOnly
                    ? 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                    : 'bg-zinc-800 hover:bg-zinc-700 text-zinc-300'
                }`}
              >
                Unread Only
              </button>

              <button
                onClick={handleBatchDownload}
                disabled={isDownloadingBatch}
                className="flex items-center space-x-1 px-3 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-xs font-semibold text-zinc-300 transition disabled:opacity-50"
              >
                <Download className="w-3.5 h-3.5" />
                <span>{isDownloadingBatch ? 'Downloading...' : 'Download All'}</span>
              </button>

              <button
                onClick={() => handleMarkAll(true)}
                className="px-2.5 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-[11px] text-zinc-400"
              >
                Mark All Read
              </button>
            </div>
          </div>

          {/* Search Chapters */}
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search chapters..."
              className="w-full pl-9 pr-4 py-2 bg-zinc-950 border border-zinc-800 rounded-xl text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-amber-500/50"
            />
          </div>

          {/* Chapters List */}
          <div className="divide-y divide-zinc-800/60 max-h-[500px] overflow-y-auto pr-1">
            {filteredChapters.map((ch) => (
              <div
                key={ch.url}
                onClick={() => onOpenChapter(ch.url)}
                className="py-3 px-2 flex items-center justify-between hover:bg-zinc-800/40 rounded-xl cursor-pointer transition group"
              >
                <div className="flex items-center space-x-3 min-w-0 flex-1">
                  <button
                    onClick={(e) => toggleChapterRead(ch.url, e)}
                    className="text-zinc-500 hover:text-amber-400 transition shrink-0"
                    title={ch.read ? 'Mark as Unread' : 'Mark as Read'}
                  >
                    {ch.read ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-400 fill-emerald-500/20" />
                    ) : (
                      <Circle className="w-4 h-4" />
                    )}
                  </button>
                  <div className="min-w-0 flex-1">
                    <span
                      className={`text-xs sm:text-sm font-medium block truncate ${
                        ch.read ? 'text-zinc-500 line-through decoration-zinc-700' : 'text-zinc-200 group-hover:text-amber-400'
                      }`}
                    >
                      {ch.title}
                    </span>
                    <span className="text-[10px] text-zinc-500 block">
                      {ch.volume || 'Volume 1'} · Chapter {ch.position}
                    </span>
                  </div>
                </div>

                <div className="flex items-center space-x-2 shrink-0">
                  {ch.isDownloaded && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400">
                      Offline
                    </span>
                  )}
                  <span className="text-xs text-amber-400 opacity-0 group-hover:opacity-100 font-semibold transition">
                    Read →
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Modals */}
      <TranslationSettingsModal
        isOpen={showTranslationModal}
        onClose={() => setShowTranslationModal(false)}
        bookUrl={currentBook.url}
        bookTitle={currentBook.title}
      />

      <NovelMigrationModal
        isOpen={showMigrationModal}
        onClose={() => setShowMigrationModal(false)}
        book={currentBook}
        onMigrated={(newBook) => {
          setCurrentBook(newBook);
          setChapters(storage.getChaptersForBook(newBook.url));
          onBookUpdated?.(newBook);
        }}
      />
    </div>
  );
};
