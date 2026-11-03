import React, { useState, useMemo } from 'react';
import { Search, Globe, Star, Bookmark, BookmarkCheck, ExternalLink, ArrowRight } from 'lucide-react';
import { Book } from '../types';
import { storage } from '../services/storage';

interface GlobalSearchScreenProps {
  onSelectBook: (book: Book) => void;
}

export const GlobalSearchScreen: React.FC<GlobalSearchScreenProps> = ({ onSelectBook }) => {
  const [query, setQuery] = useState('');
  const [allBooks, setAllBooks] = useState<Book[]>(() => storage.getBooks());

  const handleToggleLibrary = (book: Book, e: React.MouseEvent) => {
    e.stopPropagation();
    storage.toggleInLibrary(book.url);
    setAllBooks(storage.getBooks());
  };

  const results = useMemo(() => {
    if (!query.trim()) return [];
    const q = query.toLowerCase();
    return allBooks.filter((b) => {
      const matchTitle = b.title.toLowerCase().includes(q);
      const matchAuthor = b.author?.toLowerCase().includes(q);
      const matchGenres = b.genres.toLowerCase().includes(q);
      const matchDesc = b.description.toLowerCase().includes(q);
      return matchTitle || matchAuthor || matchGenres || matchDesc;
    });
  }, [allBooks, query]);

  // Group by source
  const groupedBySource = useMemo(() => {
    const map: Record<string, Book[]> = {};
    results.forEach((b) => {
      if (!map[b.sourceName]) map[b.sourceName] = [];
      map[b.sourceName].push(b);
    });
    return map;
  }, [results]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pb-28 pt-4 md:pt-20 px-4 md:px-8">
      <div className="max-w-5xl mx-auto space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-zinc-100 flex items-center space-x-2">
            <Globe className="w-7 h-7 text-amber-400" />
            <span>Global Multi-Source Search</span>
          </h1>
          <p className="text-xs text-zinc-400">
            Simultaneously query across all enabled source plugins and repositories
          </p>
        </div>

        {/* Search input */}
        <div className="relative">
          <Search className="w-5 h-5 absolute left-4 top-1/2 -translate-y-1/2 text-zinc-400" />
          <input
            type="text"
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by novel title, author, cultivation tag, LitRPG..."
            className="w-full pl-12 pr-4 py-3.5 bg-zinc-900 border border-zinc-800 rounded-2xl text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-amber-500/60 focus:ring-1 focus:ring-amber-500/60 shadow-xl transition"
          />
        </div>

        {/* Quick Suggestion Chips */}
        {!query && (
          <div className="space-y-3 pt-2">
            <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider block">
              Popular Searches
            </span>
            <div className="flex flex-wrap gap-2">
              {['Defiance', 'Dragon', 'Lord', 'Primal', 'Shadow', 'Solo', 'Cultivation', 'LitRPG'].map(
                (term) => (
                  <button
                    key={term}
                    onClick={() => setQuery(term)}
                    className="px-3.5 py-1.5 rounded-xl bg-zinc-900 border border-zinc-800 text-xs font-medium text-zinc-300 hover:text-amber-400 hover:border-amber-500/40 transition"
                  >
                    {term}
                  </button>
                )
              )}
            </div>
          </div>
        )}

        {/* Results */}
        {query && results.length === 0 ? (
          <div className="p-12 text-center bg-zinc-900/40 border border-dashed border-zinc-800 rounded-3xl space-y-2">
            <Search className="w-8 h-8 text-zinc-600 mx-auto" />
            <h3 className="font-bold text-sm text-zinc-300">No results found for &quot;{query}&quot;</h3>
            <p className="text-xs text-zinc-500">Try broader keywords or paste a direct novel link via &apos;Add by URL&apos;.</p>
          </div>
        ) : (
          <div className="space-y-6">
            {Object.entries(groupedBySource).map(([sourceName, books]) => (
              <div key={sourceName} className="space-y-3">
                <div className="flex items-center space-x-2 border-b border-zinc-800 pb-2">
                  <span className="text-xs font-bold text-amber-400 uppercase tracking-wider">
                    {sourceName}
                  </span>
                  <span className="text-[11px] text-zinc-500">({books.length} matches)</span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {books.map((book) => (
                    <div
                      key={book.url}
                      onClick={() => onSelectBook(book)}
                      className="flex items-center justify-between p-3.5 bg-zinc-900 border border-zinc-800 hover:border-amber-500/50 rounded-2xl cursor-pointer transition group"
                    >
                      <div className="flex items-center space-x-3.5 min-w-0">
                        <img
                          src={book.coverImageUrl}
                          alt={book.title}
                          className="w-14 h-20 object-cover rounded-xl border border-zinc-800 shrink-0"
                        />
                        <div className="min-w-0">
                          <h4 className="font-bold text-sm text-zinc-100 truncate group-hover:text-amber-400 transition">
                            {book.title}
                          </h4>
                          <p className="text-xs text-zinc-400 truncate mt-0.5">{book.author || 'Unknown'}</p>
                          <div className="flex items-center space-x-2 mt-1 text-[11px] text-zinc-500">
                            <span>{book.contentType}</span>
                            <span>·</span>
                            <span>{book.status || 'Ongoing'}</span>
                            {book.rating && (
                              <>
                                <span>·</span>
                                <span className="flex items-center text-amber-400 font-semibold">
                                  <Star className="w-3 h-3 fill-current mr-0.5" />
                                  {book.rating}
                                </span>
                              </>
                            )}
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center space-x-2 shrink-0">
                        <button
                          onClick={(e) => handleToggleLibrary(book, e)}
                          title={book.inLibrary ? 'In Library' : 'Add to Library'}
                          className={`p-2 rounded-xl border transition ${
                            book.inLibrary
                              ? 'bg-amber-500/20 text-amber-400 border-amber-500/40'
                              : 'bg-zinc-800 text-zinc-400 border-zinc-700 hover:text-zinc-200'
                          }`}
                        >
                          {book.inLibrary ? (
                            <BookmarkCheck className="w-4 h-4 fill-current" />
                          ) : (
                            <Bookmark className="w-4 h-4" />
                          )}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
