import React, { useState } from 'react';
import { History, BookOpen, Trash2, Clock, ArrowRight } from 'lucide-react';
import { ReadingHistoryItem, Book } from '../types';
import { storage } from '../services/storage';

interface HistoryScreenProps {
  onOpenNovel: (bookUrl: string, chapterUrl: string) => void;
}

export const HistoryScreen: React.FC<HistoryScreenProps> = ({ onOpenNovel }) => {
  const [history, setHistory] = useState<ReadingHistoryItem[]>(() => storage.getHistory());

  const handleClear = () => {
    if (confirm('Clear all reading history?')) {
      storage.clearHistory();
      setHistory([]);
    }
  };

  const handleDelete = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    storage.deleteHistoryItem(id);
    setHistory(storage.getHistory());
  };

  const formatTime = (ts: number) => {
    const diff = Date.now() - ts;
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    const days = Math.floor(hrs / 24);
    return `${days}d ago`;
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pb-28 pt-4 md:pt-20 px-4 md:px-8">
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-zinc-100 flex items-center space-x-2">
              <History className="w-7 h-7 text-amber-400" />
              <span>Reading History</span>
            </h1>
            <p className="text-xs text-zinc-400">
              Resume where you left off across all web novels and light novels
            </p>
          </div>

          {history.length > 0 && (
            <button
              onClick={handleClear}
              className="flex items-center space-x-1.5 px-3 py-2 rounded-xl bg-zinc-900 border border-zinc-800 hover:border-rose-500/50 text-xs font-semibold text-zinc-400 hover:text-rose-400 transition"
            >
              <Trash2 className="w-4 h-4" />
              <span>Clear History</span>
            </button>
          )}
        </div>

        {/* History List */}
        {history.length === 0 ? (
          <div className="p-12 text-center bg-zinc-900/40 border border-dashed border-zinc-800 rounded-3xl space-y-3">
            <Clock className="w-8 h-8 text-zinc-600 mx-auto" />
            <h3 className="font-bold text-sm text-zinc-300">No reading history yet</h3>
            <p className="text-xs text-zinc-500">Chapters you read will appear here automatically.</p>
          </div>
        ) : (
          <div className="space-y-3">
            {history.map((item) => (
              <div
                key={item.id}
                onClick={() => onOpenNovel(item.bookUrl, item.chapterUrl)}
                className="flex items-center justify-between p-4 bg-zinc-900 border border-zinc-800 hover:border-amber-500/50 rounded-2xl cursor-pointer transition group"
              >
                <div className="flex items-center space-x-4 min-w-0">
                  <img
                    src={item.coverImageUrl}
                    alt={item.bookTitle}
                    className="w-14 h-20 object-cover rounded-xl border border-zinc-800 shrink-0"
                  />
                  <div className="min-w-0">
                    <span className="text-[10px] uppercase font-bold text-amber-400 tracking-wider">
                      {item.sourceName}
                    </span>
                    <h3 className="font-bold text-sm text-zinc-100 truncate group-hover:text-amber-400 transition">
                      {item.bookTitle}
                    </h3>
                    <p className="text-xs text-zinc-400 truncate mt-0.5">{item.chapterTitle}</p>
                    <div className="flex items-center space-x-2 mt-2 text-[11px] text-zinc-500">
                      <span>{formatTime(item.timestamp)}</span>
                      {item.progressPercent > 0 && (
                        <>
                          <span>·</span>
                          <span className="text-emerald-400 font-semibold">{item.progressPercent}% finished</span>
                        </>
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex items-center space-x-3 shrink-0">
                  <button
                    onClick={(e) => handleDelete(item.id, e)}
                    title="Remove item"
                    className="p-2 text-zinc-500 hover:text-rose-400 hover:bg-zinc-800 rounded-xl transition"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>

                  <div className="p-2 rounded-xl bg-amber-500 text-zinc-950 font-bold group-hover:scale-105 transition shadow-md">
                    <ArrowRight className="w-4 h-4" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
