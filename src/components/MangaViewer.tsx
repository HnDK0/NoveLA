import React, { useState } from 'react';
import { ChevronLeft, ChevronRight, ZoomIn, ZoomOut, Maximize2, LayoutGrid, ArrowLeft } from 'lucide-react';
import { Chapter } from '../types';

interface MangaViewerProps {
  chapter: Chapter;
  onBack: () => void;
  onNextChapter?: () => void;
  onPrevChapter?: () => void;
  hasNext: boolean;
  hasPrev: boolean;
}

export const MangaViewer: React.FC<MangaViewerProps> = ({
  chapter,
  onBack,
  onNextChapter,
  onPrevChapter,
  hasNext,
  hasPrev,
}) => {
  const images = chapter.images || [
    'https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=900&q=80',
    'https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=900&q=80',
  ];

  const [mode, setMode] = useState<'webtoon' | 'pager'>('webtoon');
  const [currentPage, setCurrentPage] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [showControls, setShowControls] = useState(true);

  return (
    <div className="fixed inset-0 z-50 bg-black text-white flex flex-col select-none overflow-hidden">
      {/* Top Header */}
      {showControls && (
        <div className="absolute top-0 left-0 right-0 z-20 bg-gradient-to-b from-black/90 to-transparent p-4 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <button
              onClick={onBack}
              className="p-2 rounded-xl bg-zinc-900/80 hover:bg-zinc-800 text-zinc-200 transition"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div>
              <h3 className="font-bold text-sm text-zinc-100 truncate max-w-[200px] sm:max-w-md">
                {chapter.title}
              </h3>
              <p className="text-[11px] text-zinc-400">Comic / Manga Reader</p>
            </div>
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={() => setMode(mode === 'webtoon' ? 'pager' : 'webtoon')}
              className="px-2.5 py-1.5 rounded-lg bg-zinc-900/80 hover:bg-zinc-800 text-xs font-semibold flex items-center space-x-1"
            >
              <LayoutGrid className="w-3.5 h-3.5" />
              <span>{mode === 'webtoon' ? 'Webtoon (Vertical)' : 'Paged Mode'}</span>
            </button>
            <button
              onClick={() => setZoom((z) => Math.min(2, z + 0.25))}
              className="p-2 rounded-lg bg-zinc-900/80 hover:bg-zinc-800 text-zinc-300"
            >
              <ZoomIn className="w-4 h-4" />
            </button>
            <button
              onClick={() => setZoom((z) => Math.max(0.75, z - 0.25))}
              className="p-2 rounded-lg bg-zinc-900/80 hover:bg-zinc-800 text-zinc-300"
            >
              <ZoomOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Comic Body */}
      <div
        className="flex-1 overflow-y-auto flex items-center justify-center p-0"
        onClick={() => setShowControls(!showControls)}
      >
        {mode === 'webtoon' ? (
          <div
            className="flex flex-col items-center w-full max-w-3xl mx-auto space-y-1 transition-transform"
            style={{ transform: `scale(${zoom})`, transformOrigin: 'top center' }}
          >
            {images.map((src, idx) => (
              <div key={idx} className="w-full relative">
                <img
                  src={src}
                  alt={`Page ${idx + 1}`}
                  className="w-full object-contain shadow-2xl block"
                  loading="lazy"
                />
                <div className="absolute bottom-2 right-2 px-2 py-0.5 bg-black/60 rounded text-[10px] text-zinc-400">
                  {idx + 1} / {images.length}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="relative w-full h-full flex items-center justify-center">
            <img
              src={images[currentPage]}
              alt={`Page ${currentPage + 1}`}
              className="max-h-[90vh] max-w-full object-contain transition-transform"
              style={{ transform: `scale(${zoom})` }}
            />

            {/* Pager Prev / Next tap areas */}
            {currentPage > 0 && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setCurrentPage((p) => p - 1);
                }}
                className="absolute left-4 top-1/2 -translate-y-1/2 p-3 bg-black/50 hover:bg-black/80 rounded-full"
              >
                <ChevronLeft className="w-6 h-6" />
              </button>
            )}

            {currentPage < images.length - 1 && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setCurrentPage((p) => p + 1);
                }}
                className="absolute right-4 top-1/2 -translate-y-1/2 p-3 bg-black/50 hover:bg-black/80 rounded-full"
              >
                <ChevronRight className="w-6 h-6" />
              </button>
            )}
          </div>
        )}
      </div>

      {/* Bottom Bar */}
      {showControls && (
        <div className="absolute bottom-0 left-0 right-0 z-20 bg-gradient-to-t from-black/90 to-transparent p-4 flex items-center justify-between">
          <button
            disabled={!hasPrev}
            onClick={onPrevChapter}
            className="px-4 py-2 rounded-xl bg-zinc-900/80 hover:bg-zinc-800 disabled:opacity-40 text-xs font-semibold"
          >
            Previous Chapter
          </button>

          <span className="text-xs text-zinc-400">
            {mode === 'webtoon' ? `${images.length} Pages` : `Page ${currentPage + 1} of ${images.length}`}
          </span>

          <button
            disabled={!hasNext}
            onClick={onNextChapter}
            className="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-zinc-950 disabled:opacity-40 text-xs font-bold"
          >
            Next Chapter
          </button>
        </div>
      )}
    </div>
  );
};
