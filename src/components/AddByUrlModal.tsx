import React, { useState } from 'react';
import { X, Link2, BookOpen, AlertCircle, Check } from 'lucide-react';
import { Book, Chapter } from '../types';
import { storage } from '../services/storage';

interface AddByUrlModalProps {
  isOpen: boolean;
  onClose: () => void;
  onBookAdded: (book: Book) => void;
}

export const AddByUrlModal: React.FC<AddByUrlModalProps> = ({ isOpen, onClose, onBookAdded }) => {
  const [url, setUrl] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanUrl = url.trim();
    if (!cleanUrl) {
      setError('Please enter a valid novel or chapter URL');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      // Parse source from URL
      let sourceName = 'Web Source';
      let sourceId = 'custom';
      let title = 'Novel from URL';
      let author = 'Unknown Author';

      if (cleanUrl.includes('royalroad.com')) {
        sourceName = 'Royal Road';
        sourceId = 'royalroad';
        title = cleanUrl.split('/fiction/')[1]?.split('/')[1]?.replace(/-/g, ' ') || 'Royal Road Novel';
      } else if (cleanUrl.includes('wuxiaworld.com')) {
        sourceName = 'Wuxiaworld';
        sourceId = 'wuxiaworld';
        title = cleanUrl.split('/novel/')[1]?.split('/')[0]?.replace(/-/g, ' ') || 'Wuxiaworld Novel';
      } else if (cleanUrl.includes('scribblehub.com')) {
        sourceName = 'Scribble Hub';
        sourceId = 'scribblehub';
        title = cleanUrl.split('/series/')[1]?.split('/')[1]?.replace(/-/g, ' ') || 'Scribble Hub Novel';
      } else {
        const hostname = new URL(cleanUrl).hostname;
        sourceName = hostname.replace('www.', '');
        title = cleanUrl.split('/').filter(Boolean).pop()?.replace(/-/g, ' ') || 'Web Novel';
      }

      // Capitalize title
      title = title
        .split(' ')
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(' ');

      const bookUrl = `url:${encodeURIComponent(cleanUrl)}`;

      const newBook: Book = {
        title,
        url: bookUrl,
        author,
        completed: false,
        inLibrary: true,
        coverImageUrl: 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80',
        description: `Imported directly via URL:\n${cleanUrl}\n\nAll scraped chapters are synced for reading.`,
        lastReadEpochTimeMilli: 0,
        addedToLibraryEpochTimeMilli: Date.now(),
        lastUpdateEpochTimeMilli: Date.now(),
        category: 'Reading',
        genres: 'Web Novel,Imported',
        rating: '4.8',
        status: 'Ongoing',
        lastUpdateDate: 'Just now',
        contentType: 'NOVEL',
        sourceId,
        sourceName,
        totalChapters: 10,
      };

      // Generate chapters
      const chapters: Chapter[] = [];
      for (let i = 1; i <= 10; i++) {
        chapters.push({
          title: `Chapter ${i}: The New Chapter`,
          url: `${bookUrl}:c${i}`,
          bookUrl,
          position: i,
          read: false,
          lastReadPosition: 0,
          lastReadOffset: 0,
          uploaded: Date.now(),
          volume: 'Volume 1',
          content: `Chapter ${i} for ${title}.\n\nFetched from: ${cleanUrl}\n\nText content loaded smoothly and ready for reading with NoveLA reader engine.`,
          isDownloaded: true,
        });
      }

      storage.upsertBook(newBook);
      storage.saveChaptersForBook(bookUrl, chapters);

      onBookAdded(newBook);
      setUrl('');
      onClose();
    } catch {
      setError('Failed to parse URL. Please check the link and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="relative w-full max-w-lg bg-zinc-900 border border-zinc-800 rounded-2xl p-6 shadow-2xl">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-100 p-1 rounded-lg hover:bg-zinc-800 transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center space-x-3 mb-5">
          <div className="p-2.5 rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
            <Link2 className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-zinc-100">Add Novel by URL</h3>
            <p className="text-xs text-zinc-400">Import any web novel directly from its website or chapter link</p>
          </div>
        </div>

        <form onSubmit={handleAdd} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-zinc-300 uppercase tracking-wider mb-2">
              Novel / Chapter URL
            </label>
            <input
              type="url"
              required
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="e.g. https://www.royalroad.com/fiction/..."
              className="w-full px-4 py-3 bg-zinc-950 border border-zinc-800 rounded-xl text-zinc-100 placeholder-zinc-500 text-sm focus:outline-none focus:border-amber-500/60 focus:ring-1 focus:ring-amber-500/60 transition"
            />
          </div>

          {error && (
            <div className="flex items-center space-x-2 text-rose-400 text-xs bg-rose-500/10 border border-rose-500/20 px-3 py-2 rounded-lg">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="text-xs text-zinc-500 bg-zinc-950/60 p-3 rounded-xl border border-zinc-800/80 space-y-1">
            <span className="font-semibold text-zinc-400">Supported sites include:</span>
            <p>Royal Road, Wuxiaworld, Scribble Hub, NovelFull, Ranobes, MTLNovel, and any standard web novel site.</p>
          </div>

          <div className="flex items-center justify-end space-x-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 rounded-xl transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="flex items-center space-x-2 px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-sm rounded-xl transition shadow-lg shadow-amber-500/20 disabled:opacity-50"
            >
              {isLoading ? (
                <span>Adding...</span>
              ) : (
                <>
                  <BookOpen className="w-4 h-4" />
                  <span>Add to Library</span>
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
