import React, { useState, useMemo, useRef } from 'react';
import {
  BookOpen,
  Plus,
  Search,
  Grid,
  List as ListIcon,
  Link2,
  Upload,
  MoreVertical,
  Star,
  CheckCircle2,
  Trash2,
  FolderPlus,
  SlidersHorizontal,
} from 'lucide-react';
import { Book } from '../types';
import { storage } from '../services/storage';
import { parseEpubFile } from '../services/epubImporter';
import { AddByUrlModal } from './AddByUrlModal';

interface LibraryScreenProps {
  onSelectBook: (book: Book) => void;
  onExploreCatalog: () => void;
}

export const LibraryScreen: React.FC<LibraryScreenProps> = ({ onSelectBook, onExploreCatalog }) => {
  const [books, setBooks] = useState<Book[]>(() => storage.getBooks().filter((b) => b.inLibrary));
  const [categories, setCategories] = useState(() => storage.getCategories());
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [showAddUrlModal, setShowAddUrlModal] = useState(false);
  const [showNewCatInput, setShowNewCatInput] = useState(false);
  const [newCatName, setNewCatName] = useState('');

  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleRefresh = () => {
    setBooks(storage.getBooks().filter((b) => b.inLibrary));
    setCategories(storage.getCategories());
  };

  const handleAddCategory = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCatName.trim()) return;
    storage.addCategory(newCatName.trim());
    setCategories(storage.getCategories());
    setActiveCategory(newCatName.trim());
    setNewCatName('');
    setShowNewCatInput(false);
  };

  const handleImportFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const { book, chapters } = await parseEpubFile(file);
      storage.upsertBook(book);
      storage.saveChaptersForBook(book.url, chapters);
      handleRefresh();
      onSelectBook(book);
    } catch (err) {
      console.error('File import failed:', err);
      alert('Could not parse the selected book file. Please try another EPUB file.');
    }
  };

  const removeFromLibrary = (bookUrl: string, e: React.MouseEvent) => {
    e.stopPropagation();
    storage.toggleInLibrary(bookUrl);
    handleRefresh();
  };

  // Filter books by category and search
  const filteredBooks = useMemo(() => {
    return books.filter((b) => {
      if (activeCategory !== 'all') {
        if (b.category !== activeCategory) return false;
      }
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        const matchesTitle = b.title.toLowerCase().includes(query);
        const matchesAuthor = b.author?.toLowerCase().includes(query);
        const matchesSource = b.sourceName.toLowerCase().includes(query);
        if (!matchesTitle && !matchesAuthor && !matchesSource) return false;
      }
      return true;
    });
  }, [books, activeCategory, searchQuery]);

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pb-28 pt-4 md:pt-20 px-4 md:px-8">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Top Header bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-zinc-100 flex items-center space-x-2">
              <span>Library</span>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-500/15 text-amber-400 font-bold border border-amber-500/20">
                {books.length}
              </span>
            </h1>
            <p className="text-xs text-zinc-400">Your saved web novels, light novels, and offline books</p>
          </div>

          {/* Quick Actions */}
          <div className="flex items-center space-x-2">
            <button
              onClick={() => setShowAddUrlModal(true)}
              className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-zinc-900 border border-zinc-800 hover:border-amber-500/50 text-xs font-semibold text-zinc-200 hover:text-amber-400 transition"
            >
              <Link2 className="w-4 h-4 text-amber-400" />
              <span>Add URL</span>
            </button>

            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-zinc-900 border border-zinc-800 hover:border-emerald-500/50 text-xs font-semibold text-zinc-200 hover:text-emerald-400 transition"
            >
              <Upload className="w-4 h-4 text-emerald-400" />
              <span>Import EPUB</span>
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".epub,.txt,.html"
              onChange={handleImportFile}
              className="hidden"
            />

            <div className="flex items-center bg-zinc-900 border border-zinc-800 rounded-xl p-0.5">
              <button
                onClick={() => setViewMode('grid')}
                className={`p-1.5 rounded-lg transition ${
                  viewMode === 'grid' ? 'bg-amber-500 text-zinc-950' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <Grid className="w-4 h-4" />
              </button>
              <button
                onClick={() => setViewMode('list')}
                className={`p-1.5 rounded-lg transition ${
                  viewMode === 'list' ? 'bg-amber-500 text-zinc-950' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <ListIcon className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>

        {/* Categories Bar */}
        <div className="flex items-center space-x-2 overflow-x-auto pb-1 scrollbar-none">
          {categories.map((cat) => {
            const count =
              cat.id === 'all' ? books.length : books.filter((b) => b.category === cat.name).length;
            const isActive = activeCategory === cat.id || (cat.id !== 'all' && activeCategory === cat.name);
            return (
              <button
                key={cat.id}
                onClick={() => setActiveCategory(cat.id === 'all' ? 'all' : cat.name)}
                className={`flex items-center space-x-1.5 px-3.5 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition border ${
                  isActive
                    ? 'bg-amber-500 text-zinc-950 border-amber-500 shadow-md shadow-amber-500/20'
                    : 'bg-zinc-900 text-zinc-400 border-zinc-800 hover:border-zinc-700 hover:text-zinc-200'
                }`}
              >
                <span>{cat.name}</span>
                <span
                  className={`text-[10px] px-1.5 rounded-full ${
                    isActive ? 'bg-zinc-950/30 text-zinc-900' : 'bg-zinc-800 text-zinc-400'
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}

          {showNewCatInput ? (
            <form onSubmit={handleAddCategory} className="flex items-center space-x-1">
              <input
                type="text"
                autoFocus
                value={newCatName}
                onChange={(e) => setNewCatName(e.target.value)}
                placeholder="Category name..."
                className="px-3 py-1 bg-zinc-900 border border-zinc-700 rounded-full text-xs text-zinc-100 focus:outline-none focus:border-amber-500 w-32"
              />
              <button
                type="submit"
                className="px-2 py-1 bg-amber-500 text-zinc-950 font-bold rounded-full text-xs"
              >
                Add
              </button>
            </form>
          ) : (
            <button
              onClick={() => setShowNewCatInput(true)}
              className="flex items-center space-x-1 px-3 py-1.5 rounded-full bg-zinc-900/60 border border-dashed border-zinc-700 text-xs font-semibold text-zinc-400 hover:text-zinc-200 transition shrink-0"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Category</span>
            </button>
          )}
        </div>

        {/* Search in library */}
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-500" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search your library novels by title, author, source..."
            className="w-full pl-10 pr-4 py-2.5 bg-zinc-900 border border-zinc-800 rounded-2xl text-xs sm:text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-amber-500/60 focus:ring-1 focus:ring-amber-500/60 transition"
          />
        </div>

        {/* Books View: Grid or List */}
        {filteredBooks.length === 0 ? (
          <div className="p-12 text-center bg-zinc-900/50 border border-dashed border-zinc-800 rounded-3xl space-y-4">
            <div className="w-14 h-14 rounded-2xl bg-amber-500/10 text-amber-400 flex items-center justify-center mx-auto border border-amber-500/20">
              <BookOpen className="w-7 h-7" />
            </div>
            <div>
              <h3 className="font-bold text-base text-zinc-200">No books found in this category</h3>
              <p className="text-xs text-zinc-500 max-w-sm mx-auto mt-1">
                Explore source catalogs like Royal Road or Wuxiaworld to discover thousands of novels, or import your own EPUBs.
              </p>
            </div>
            <button
              onClick={onExploreCatalog}
              className="px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-xs rounded-xl transition shadow-lg shadow-amber-500/20"
            >
              Browse Catalogs
            </button>
          </div>
        ) : viewMode === 'grid' ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {filteredBooks.map((book) => {
              const chapters = storage.getChaptersForBook(book.url);
              const unreadCount = chapters.filter((c) => !c.read).length;

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
                    <div className="absolute inset-0 bg-gradient-to-t from-zinc-950 via-transparent to-black/40 opacity-70 group-hover:opacity-50 transition" />

                    {/* Unread badge */}
                    {unreadCount > 0 && (
                      <span className="absolute top-2 right-2 px-1.5 py-0.5 bg-amber-500 text-zinc-950 text-[10px] font-black rounded-md shadow-md">
                        {unreadCount}
                      </span>
                    )}

                    {/* Source strip badge ON COVER */}
                    <span className="absolute top-2 left-2 px-1.5 py-0.5 bg-black/70 backdrop-blur-xs text-amber-400 text-[10px] font-bold rounded-md uppercase tracking-wider border border-white/10">
                      {book.sourceName}
                    </span>

                    {/* Remove from library action */}
                    <button
                      onClick={(e) => removeFromLibrary(book.url, e)}
                      title="Remove from library"
                      className="absolute bottom-2 right-2 p-1.5 rounded-lg bg-black/70 text-zinc-400 hover:text-rose-400 opacity-0 group-hover:opacity-100 transition"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
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
                      <span>{book.category || 'Reading'}</span>
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
        ) : (
          /* Detailed List View */
          <div className="space-y-2">
            {filteredBooks.map((book) => {
              const chapters = storage.getChaptersForBook(book.url);
              const unreadCount = chapters.filter((c) => !c.read).length;

              return (
                <div
                  key={book.url}
                  onClick={() => onSelectBook(book)}
                  className="flex items-center justify-between p-3 bg-zinc-900 border border-zinc-800 hover:border-amber-500/50 rounded-2xl cursor-pointer transition group"
                >
                  <div className="flex items-center space-x-3.5 min-w-0">
                    <img
                      src={book.coverImageUrl}
                      alt={book.title}
                      className="w-12 h-16 object-cover rounded-xl border border-zinc-800 shrink-0"
                    />
                    <div className="min-w-0">
                      <h3 className="font-bold text-sm text-zinc-100 truncate group-hover:text-amber-400 transition">
                        {book.title}
                      </h3>
                      <p className="text-xs text-zinc-400 truncate">
                        {book.author || 'Unknown'} ·{' '}
                        <span className="text-amber-400 font-semibold">{book.sourceName}</span>
                      </p>
                      <div className="flex items-center space-x-2 mt-1 text-[11px] text-zinc-500">
                        <span className="px-1.5 py-0.2 rounded bg-zinc-800 text-zinc-400">
                          {book.category || 'Reading'}
                        </span>
                        <span>{chapters.length} chapters</span>
                        {unreadCount > 0 && (
                          <span className="text-amber-400 font-semibold">{unreadCount} unread</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center space-x-2 shrink-0">
                    <button
                      onClick={(e) => removeFromLibrary(book.url, e)}
                      title="Remove from library"
                      className="p-2 text-zinc-500 hover:text-rose-400 hover:bg-zinc-800 rounded-xl transition"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <AddByUrlModal
        isOpen={showAddUrlModal}
        onClose={() => setShowAddUrlModal(false)}
        onBookAdded={(newBook) => {
          handleRefresh();
          onSelectBook(newBook);
        }}
      />
    </div>
  );
};
