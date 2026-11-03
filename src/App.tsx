import React, { useState } from 'react';
import { Navbar, NavTab } from './components/Navbar';
import { LibraryScreen } from './components/LibraryScreen';
import { CatalogScreen } from './components/CatalogScreen';
import { GlobalSearchScreen } from './components/GlobalSearchScreen';
import { ExtensionsScreen } from './components/ExtensionsScreen';
import { HistoryScreen } from './components/HistoryScreen';
import { SettingsScreen } from './components/SettingsScreen';
import { BookDetailsScreen } from './components/BookDetailsScreen';
import { ReaderScreen } from './components/ReaderScreen';
import { TtsMiniPlayer } from './components/TtsMiniPlayer';
import { Book } from './types';
import { storage } from './services/storage';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<NavTab>('library');
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [activeChapterUrl, setActiveChapterUrl] = useState<string | null>(null);

  const libraryBooksCount = storage.getBooks().filter((b) => b.inLibrary).length;

  // Navigate to book details
  const handleSelectBook = (book: Book) => {
    setSelectedBook(book);
    setActiveChapterUrl(null);
  };

  // Open chapter in reader
  const handleOpenChapter = (chapterUrl: string) => {
    setActiveChapterUrl(chapterUrl);
  };

  // Back from reader to book details
  const handleBackFromReader = () => {
    setActiveChapterUrl(null);
  };

  // Back from book details to current tab
  const handleBackFromBookDetails = () => {
    setSelectedBook(null);
    setActiveChapterUrl(null);
  };

  // Open novel directly from history
  const handleOpenFromHistory = (bookUrl: string, chapterUrl: string) => {
    const book = storage.getBook(bookUrl);
    if (book) {
      setSelectedBook(book);
      setActiveChapterUrl(chapterUrl);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col font-sans">
      {/* Active View */}
      {activeChapterUrl && selectedBook ? (
        <ReaderScreen
          book={selectedBook}
          chapterUrl={activeChapterUrl}
          onBack={handleBackFromReader}
          onSelectChapter={(chUrl) => setActiveChapterUrl(chUrl)}
        />
      ) : selectedBook ? (
        <BookDetailsScreen
          book={selectedBook}
          onBack={handleBackFromBookDetails}
          onOpenChapter={handleOpenChapter}
          onBookUpdated={(updated) => setSelectedBook(updated)}
        />
      ) : (
        <main className="flex-1">
          {activeTab === 'library' && (
            <LibraryScreen
              onSelectBook={handleSelectBook}
              onExploreCatalog={() => setActiveTab('catalog')}
            />
          )}
          {activeTab === 'catalog' && <CatalogScreen onSelectBook={handleSelectBook} />}
          {activeTab === 'search' && <GlobalSearchScreen onSelectBook={handleSelectBook} />}
          {activeTab === 'extensions' && <ExtensionsScreen />}
          {activeTab === 'history' && <HistoryScreen onOpenNovel={handleOpenFromHistory} />}
          {activeTab === 'settings' && <SettingsScreen />}
        </main>
      )}

      {/* Persistent Bottom/Top Navigation Bar (hidden in active reader view) */}
      {!activeChapterUrl && (
        <Navbar
          activeTab={activeTab}
          onTabChange={(tab) => {
            setSelectedBook(null);
            setActiveChapterUrl(null);
            setActiveTab(tab);
          }}
          libraryCount={libraryBooksCount}
        />
      )}

      {/* Global Floating TTS Narrator Mini-Player */}
      <TtsMiniPlayer onClose={() => {}} />
    </div>
  );
};

export default App;
