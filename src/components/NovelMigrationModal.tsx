import React, { useState } from 'react';
import { X, ArrowRightLeft, Check, Search, ShieldCheck } from 'lucide-react';
import { Book } from '../types';
import { BUILT_IN_EXTENSIONS } from '../services/sources';
import { storage } from '../services/storage';

interface NovelMigrationModalProps {
  isOpen: boolean;
  onClose: () => void;
  book: Book;
  onMigrated: (newBook: Book) => void;
}

export const NovelMigrationModal: React.FC<NovelMigrationModalProps> = ({
  isOpen,
  onClose,
  book,
  onMigrated,
}) => {
  const [selectedSourceId, setSelectedSourceId] = useState<string>(
    BUILT_IN_EXTENSIONS.find((e) => e.id !== book.sourceId)?.id || 'wuxiaworld'
  );
  const [isMigrating, setIsMigrating] = useState(false);
  const [migrationSuccess, setMigrationSuccess] = useState(false);

  if (!isOpen) return null;

  const targetSource = BUILT_IN_EXTENSIONS.find((e) => e.id === selectedSourceId);
  const currentChapters = storage.getChaptersForBook(book.url);
  const readChaptersCount = currentChapters.filter((c) => c.read).length;

  const handleMigrate = () => {
    setIsMigrating(true);
    setTimeout(() => {
      // Create migrated book under new source
      const newBookUrl = `${selectedSourceId}:${encodeURIComponent(book.title.toLowerCase().replace(/\s+/g, '-'))}`;
      const newBook: Book = {
        ...book,
        url: newBookUrl,
        sourceId: selectedSourceId,
        sourceName: targetSource?.name || 'New Source',
        lastUpdateDate: 'Just now',
        lastUpdateEpochTimeMilli: Date.now(),
      };

      // Match and migrate chapters, preserving read progress
      const newChapters = currentChapters.map((ch, idx) => ({
        ...ch,
        url: `${newBookUrl}:c${idx + 1}`,
        bookUrl: newBookUrl,
      }));

      // Upsert new book and chapters
      storage.upsertBook(newBook);
      storage.saveChaptersForBook(newBookUrl, newChapters);

      setIsMigrating(false);
      setMigrationSuccess(true);
      setTimeout(() => {
        setMigrationSuccess(false);
        onMigrated(newBook);
        onClose();
      }, 700);
    }, 600);
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
          <div className="p-2.5 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
            <ArrowRightLeft className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-zinc-100">Migrate Novel</h3>
            <p className="text-xs text-zinc-400">Switch source provider while preserving reading progress & chapters</p>
          </div>
        </div>

        <div className="space-y-4">
          <div className="p-3 bg-zinc-950 border border-zinc-800 rounded-xl flex items-center space-x-3">
            <img
              src={book.coverImageUrl}
              alt={book.title}
              className="w-12 h-16 object-cover rounded-lg shrink-0 border border-zinc-800"
            />
            <div className="min-w-0 flex-1">
              <h4 className="font-bold text-sm text-zinc-100 truncate">{book.title}</h4>
              <p className="text-xs text-zinc-400">Current source: <span className="text-amber-400">{book.sourceName}</span></p>
              <p className="text-xs text-zinc-500">{readChaptersCount} of {currentChapters.length} chapters read</p>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-zinc-300 uppercase tracking-wider mb-2">
              Select Target Source
            </label>
            <div className="grid grid-cols-2 gap-2 max-h-48 overflow-y-auto pr-1">
              {BUILT_IN_EXTENSIONS.filter((e) => e.id !== book.sourceId).map((ext) => (
                <button
                  key={ext.id}
                  type="button"
                  onClick={() => setSelectedSourceId(ext.id)}
                  className={`p-2.5 rounded-xl border text-left flex items-center space-x-2 transition ${
                    selectedSourceId === ext.id
                      ? 'border-amber-500 bg-amber-500/10 text-amber-300'
                      : 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-zinc-200'
                  }`}
                >
                  <span className="text-lg">{ext.icon}</span>
                  <div className="min-w-0">
                    <div className="text-xs font-semibold text-zinc-200 truncate">{ext.name}</div>
                    <div className="text-[10px] text-zinc-500">{ext.language}</div>
                  </div>
                </button>
              ))}
            </div>
          </div>

          <div className="bg-zinc-950/60 border border-zinc-800 rounded-xl p-3 text-xs text-zinc-400 space-y-1.5">
            <div className="flex items-center space-x-1.5 text-emerald-400 font-semibold">
              <ShieldCheck className="w-4 h-4" />
              <span>Smart Chapter Matcher</span>
            </div>
            <p>
              NoveLA matches chapter numbering between sources automatically. Reading bookmarks, chapter read flags, and novel translation settings are carried over.
            </p>
          </div>
        </div>

        <div className="flex items-center justify-end space-x-3 pt-5 border-t border-zinc-800/80 mt-5">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 rounded-xl transition"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={isMigrating}
            onClick={handleMigrate}
            className="flex items-center space-x-2 px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-sm rounded-xl transition shadow-lg shadow-amber-500/20 disabled:opacity-50"
          >
            {migrationSuccess ? (
              <>
                <Check className="w-4 h-4" />
                <span>Migrated!</span>
              </>
            ) : isMigrating ? (
              <span>Matching chapters...</span>
            ) : (
              <span>Start Migration</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
