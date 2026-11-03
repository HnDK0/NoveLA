import React, { useState, useMemo } from 'react';
import {
  Compass,
  Search,
  Filter,
  Star,
  Bookmark,
  BookmarkCheck,
  Flame,
  Clock,
  CheckCircle2,
  Tag,
} from 'lucide-react';
import { Book, SourceExtension } from '../types';
import { storage } from '../services/storage';
import { BUILT_IN_EXTENSIONS } from '../services/sources';

interface CatalogScreenProps {
  onSelectBook: (book: Book) => void;
}

export const CatalogScreen: React.FC<CatalogScreenProps> = ({ onSelectBook }) => {
  const extensions = storage.getExtensions().filter((e) => e.isInstalled);
  const [selectedSourceId, setSelectedSourceId] = useState<string>(extensions[0]?.id || 'royalroad');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedGenre, setSelectedGenre] = useState<string>('All');
  const [sortFilter, setSortFilter] = useState<'popular' | 'latest' | 'completed'>('popular');

  const [allBooks, setAllBooks] = useState<Book[]>(() => storage.getBooks());

  const currentSource = extensions.find((e) => e.id === selectedSourceId) || extensions[0];

  const handleToggleLibrary = (book: Book, e: React.MouseEvent) => {
    e.stopPropagation();
    storage.toggleInLibrary(book.url);
    setAllBooks(storage.getBooks());
  };

  // Extract all unique genres for the selected source
  const sourceBooks = allBooks.filter((b) => b.sourceId === selectedSourceId);
  const genres = useMemo(() => {
    const set = new Set<string>();
    sourceBooks.forEach((b) => {
      b.genres.split(',').forEach((g) => {
        if (g.trim()) set.add(g.trim());
      });
    });
    return ['All', ...Array.from(set)];
  }, [sourceBooks]);

  // Filtered list
  const filteredBooks = useMemo(() => {
    return sourceBooks.filter((b) => {
      if (selectedGenre !== 'All' && !b.genres.toLowerCase().includes(selectedGenre.toLowerCase())) {
        return false;
      }
      if (searchQuery) {
        const q = searchQuery.toLowerCase();
        const matchesTitle = b.title.toLowerCase().includes(q);
        const matchesAuthor = b.author?.toLowerCase().includes(q);
        const matchesDesc = b.description.toLowerCase().includes(q);
        if (!matchesTitle && !matchesAuthor && !matchesDesc) return false;
      }
      if (sortFilter === 'completed' && !b.completed && b.status !== 'Completed') {
        return false;
      }
      return true;
    });
  }, [sourceBooks, selectedGenre, searchQuery, sortFilter]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pb-28 pt-4 md:pt-20 px-4 md:px-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-zinc-100 flex items-center space-x-2">
              <Compass className="w-7 h-7 text-amber-400" />
              <span>Catalog Explorer</span>
            </h1>
            <p className="text-xs text-zinc-400">
              Browse top web novels, serials, and light novel repositories
            </p>
          </div>
        </div>

        {/* Source tabs bar */}
        <div className="flex items-center space-x-2 overflow-x-auto pb-1 scrollbar-none">
          {extensions.map((ext) => {
            const isSelected = selectedSourceId === ext.id;
            return (
              <button
                key={ext.id}
                onClick={() => {
                  setSelectedSourceId(ext.id);
                  setSelectedGenre('All');
                }}
                className={`flex items-center space-x-2 px-4 py-2 rounded-2xl text-xs font-bold whitespace-nowrap transition border ${
                  isSelected
                    ? 'bg-amber-500 text-zinc-950 border-amber-500 shadow-md shadow-amber-500/20'
                    : 'bg-zinc-900 text-zinc-300 border-zinc-800 hover:border-zinc-700'
                }`}
              >
                <span className="text-base">{ext.icon}</span>
                <span>{ext.name}</span>
                <span className="text-[10px] opacity-75 font-normal">({ext.language})</span>
              </button>
            );
          })}
        </div>

        {/* Search & Filter Controls */}
        <div className="flex flex-col md:flex-row items-center gap-3">
          <div className="relative flex-1 w-full">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={`Search novels on ${currentSource?.name || 'source'}...`}
              className="w-full pl-10 pr-4 py-2.5 bg-zinc-900 border border-zinc-800 rounded-2xl text-xs sm:text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-amber-500/60 transition"
            />
          </div>

          <div className="flex items-center space-x-2 w-full md:w-auto">
            <div className="flex items-center bg-zinc-900 border border-zinc-800 rounded-2xl p-0.5 w-full md:w-auto">
              <button
                onClick={() => setSortFilter('popular')}
                className={`flex-1 md:flex-initial flex items-center justify-center space-x-1 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  sortFilter === 'popular' ? 'bg-amber-500 text-zinc-950' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <Flame className="w-3.5 h-3.5" />
                <span>Popular</span>
              </button>
              <button
                onClick={() => setSortFilter('latest')}
                className={`flex-1 md:flex-initial flex items-center justify-center space-x-1 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  sortFilter === 'latest' ? 'bg-amber-500 text-zinc-950' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <Clock className="w-3.5 h-3.5" />
                <span>Latest</span>
              </button>
              <button
                onClick={() => setSortFilter('completed')}
                className={`flex-1 md:flex-initial flex items-center justify-center space-x-1 px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                  sortFilter === 'completed' ? 'bg-amber-500 text-zinc-950' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>Completed</span>
              </button>
            </div>
          </div>
        </div>

        {/* Genre Tags Filter Scrollbar */}
        {genres.length > 1 && (
          <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 scrollbar-none">
            {genres.map((g) => (
              <button
                key={g}
                onClick={() => setSelectedGenre(g)}
                className={`px-3 py-1 rounded-xl text-xs font-medium whitespace-nowrap transition border ${
                  selectedGenre === g
                    ? 'bg-amber-500/20 text-amber-400 border-amber-500/40'
                    : 'bg-zinc-900/60 text-zinc-400 border-zinc-800 hover:text-zinc-200'
                }`}
              >
                {g}
              </button>
            ))}
          </div>
        )}

        {/* Books Grid */}
        {filteredBooks.length === 0 ? (
          <div className="p-12 text-center bg-zinc-900/40 border border-dashed border-zinc-800 rounded-3xl space-y-3">
            <Tag className="w-8 h-8 text-zinc-600 mx-auto" />
            <h3 className="font-bold text-sm text-zinc-300">No novels match the current filter</h3>
            <p className="text-xs text-zinc-500">Try selecting &apos;All&apos; genres or changing your search terms.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {filteredBooks.map((book) => {
              return (
                <div
                  key={book.url}
                  onClick={() => onSelectBook(book)}
                  className="group relative flex flex-col bg-zinc-900 border border-zinc-800/80 hover:border-amber-500/50 rounded-2xl overflow-hidden cursor-pointer transition-all duration-200 hover:-translate-y-1 shadow-lg hover:shadow-amber-500/10"
                >
                  {/* Cover Aspect Ratio Container */}
                  <div className="relative aspect-[2/3] w-full overflow-hidden bg-zinc-950">
                    <img
                      src={book.coverImageUrl}
                      alt={book.title}
                      loading="lazy"
                      className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                    />

                    {/* Gradient Overlay */}
                    <div className="absolute inset-0 bg-gradient-to-t from-zinc-950 via-transparent to-black/30 opacity-70 group-hover:opacity-50 transition" />

                    {/* Bookmark Toggle in Card */}
                    <button
                      onClick={(e) => handleToggleLibrary(book, e)}
                      title={book.inLibrary ? 'In Library' : 'Add to Library'}
                      className={`absolute top-2 right-2 p-1.5 rounded-lg backdrop-blur-xs transition ${
                        book.inLibrary
                          ? 'bg-amber-500 text-zinc-950 shadow-md'
                          : 'bg-black/60 text-zinc-300 hover:text-amber-400'
                      }`}
                    >
                      {book.inLibrary ? <BookmarkCheck className="w-4 h-4 fill-current" /> : <Bookmark className="w-4 h-4" />}
                    </button>

                    {/* Source badge */}
                    <span className="absolute top-2 left-2 px-1.5 py-0.5 bg-black/70 backdrop-blur-xs text-amber-400 text-[10px] font-bold rounded-md uppercase tracking-wider border border-white/10">
                      {book.sourceName}
                    </span>
                  </div>

                  {/* Card Info */}
                  <div className="p-3 flex flex-col flex-1 justify-between bg-zinc-900">
                    <div>
                      <h3 className="font-bold text-xs text-zinc-100 line-clamp-2 group-hover:text-amber-400 transition">
                        {book.title}
                      </h3>
                      {book.author && (
                        <p className="text-[11px] text-zinc-400 truncate mt-0.5">{book.author}</p>
                      )}
                    </div>

                    <div className="flex items-center justify-between pt-2 mt-2 border-t border-zinc-800/80 text-[10px] text-zinc-500">
                      <span>{book.status || 'Ongoing'}</span>
                      {book.rating && (
                        <span className="flex items-center text-amber-400 font-bold">
                          <Star className="w-3 h-3 fill-current mr-0.5" />
                          {book.rating}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
