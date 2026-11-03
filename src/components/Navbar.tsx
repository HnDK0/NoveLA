import React from 'react';
import { BookOpen, Compass, Search, Puzzle, History, Settings } from 'lucide-react';

export type NavTab = 'library' | 'catalog' | 'search' | 'extensions' | 'history' | 'settings';

interface NavbarProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
  libraryCount?: number;
}

export const Navbar: React.FC<NavbarProps> = ({ activeTab, onTabChange, libraryCount = 0 }) => {
  const tabs: { id: NavTab; label: string; icon: React.ReactNode; badge?: number }[] = [
    { id: 'library', label: 'Library', icon: <BookOpen className="w-5 h-5" />, badge: libraryCount },
    { id: 'catalog', label: 'Catalog', icon: <Compass className="w-5 h-5" /> },
    { id: 'search', label: 'Search', icon: <Search className="w-5 h-5" /> },
    { id: 'extensions', label: 'Plugins', icon: <Puzzle className="w-5 h-5" /> },
    { id: 'history', label: 'History', icon: <History className="w-5 h-5" /> },
    { id: 'settings', label: 'Settings', icon: <Settings className="w-5 h-5" /> },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 bg-zinc-900/95 backdrop-blur-md border-t border-zinc-800 px-2 py-1 md:top-0 md:bottom-auto md:border-b md:border-t-0 md:px-6">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        {/* Brand logo on desktop */}
        <div className="hidden md:flex items-center space-x-3 cursor-pointer" onClick={() => onTabChange('library')}>
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-amber-500 to-orange-600 flex items-center justify-center shadow-lg shadow-amber-500/20">
            <span className="text-zinc-950 font-black text-lg">N</span>
          </div>
          <div>
            <span className="font-bold text-lg tracking-tight text-zinc-100">Nove<span className="text-amber-400">LA</span></span>
            <span className="ml-2 text-[10px] font-semibold uppercase tracking-wider px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-400">v1.6.1</span>
          </div>
        </div>

        {/* Tab buttons */}
        <div className="flex items-center justify-around w-full md:w-auto md:space-x-1">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onTabChange(tab.id)}
                className={`relative flex flex-col md:flex-row items-center justify-center gap-1 md:gap-2 px-3 py-1.5 md:py-2 md:px-4 rounded-xl text-xs md:text-sm font-medium transition-all ${
                  isActive
                    ? 'text-amber-400 bg-amber-500/10'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/50'
                }`}
              >
                <div className="relative">
                  {tab.icon}
                  {tab.badge !== undefined && tab.badge > 0 && (
                    <span className="absolute -top-1.5 -right-2 bg-amber-500 text-zinc-950 text-[10px] font-bold px-1.5 min-w-4 h-4 rounded-full flex items-center justify-center leading-none">
                      {tab.badge}
                    </span>
                  )}
                </div>
                <span>{tab.label}</span>
                {isActive && (
                  <span className="md:hidden absolute -bottom-1 left-1/2 -translate-x-1/2 w-4 h-0.5 bg-amber-400 rounded-full" />
                )}
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
};
