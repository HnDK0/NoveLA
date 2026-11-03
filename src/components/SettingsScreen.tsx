import React, { useState, useRef } from 'react';
import {
  Settings,
  Palette,
  Languages,
  Sparkles,
  Download,
  Upload,
  Info,
  Shield,
  Trash2,
  Check,
  Globe,
  Sliders,
  ExternalLink,
} from 'lucide-react';
import { storage } from '../services/storage';
import { TranslationSettingsModal } from './TranslationSettingsModal';
import { RegexCleanupModal } from './RegexCleanupModal';

export const SettingsScreen: React.FC = () => {
  const [readerSettings, setReaderSettings] = useState(() => storage.getReaderSettings());
  const [showTranslationModal, setShowTranslationModal] = useState(false);
  const [showRegexModal, setShowRegexModal] = useState(false);
  const [backupMessage, setBackupMessage] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleSourceStripChange = (val: 'on-cover' | 'below-cover' | 'none') => {
    const updated = storage.saveReaderSettings({ sourceStripPosition: val });
    setReaderSettings(updated);
  };

  const handleBackupExport = () => {
    const json = storage.createBackupData();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `NoveLA_Backup_${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    setBackupMessage('Backup exported successfully!');
    setTimeout(() => setBackupMessage(null), 3000);
  };

  const handleBackupRestore = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      const res = storage.restoreBackupData(content);
      setBackupMessage(res.message);
      setReaderSettings(storage.getReaderSettings());
      setTimeout(() => setBackupMessage(null), 3000);
    };
    reader.readAsText(file);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pb-28 pt-4 md:pt-20 px-4 md:px-8">
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Header */}
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-zinc-100 flex items-center space-x-2">
            <Settings className="w-7 h-7 text-amber-400" />
            <span>Settings</span>
          </h1>
          <p className="text-xs text-zinc-400">
            Configure reading appearance, translation engines, text cleanup, and data backups
          </p>
        </div>

        {backupMessage && (
          <div className="p-3.5 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 rounded-2xl text-xs font-semibold flex items-center space-x-2 animate-in fade-in">
            <Check className="w-4 h-4 shrink-0" />
            <span>{backupMessage}</span>
          </div>
        )}

        {/* Section: Reading & Appearance */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 space-y-4 shadow-xl">
          <h2 className="text-sm font-bold uppercase tracking-wider text-amber-400 flex items-center space-x-2">
            <Palette className="w-4 h-4" />
            <span>Reading & Appearance</span>
          </h2>

          <div className="space-y-4">
            {/* Theme Presets */}
            <div>
              <label className="block text-xs font-semibold text-zinc-300 mb-2">Default Reader Theme</label>
              <div className="grid grid-cols-3 sm:grid-cols-6 gap-2">
                {[
                  { id: 'dark', label: 'Dark', bg: '#18181b', text: '#e4e4e7' },
                  { id: 'amoled', label: 'AMOLED', bg: '#000000', text: '#e4e4e7' },
                  { id: 'sepia', label: 'Sepia', bg: '#f6f1e8', text: '#382c1f' },
                  { id: 'light', label: 'Light', bg: '#fafafa', text: '#18181b' },
                  { id: 'catppuccin', label: 'Mocha', bg: '#1e1e2e', text: '#cdd6f4' },
                  { id: 'nord', label: 'Nord', bg: '#2e3440', text: '#eceff4' },
                ].map((th) => (
                  <button
                    key={th.id}
                    onClick={() => {
                      const updated = storage.saveReaderSettings({ theme: th.id as any });
                      setReaderSettings(updated);
                    }}
                    className={`p-2.5 rounded-xl border text-center transition ${
                      readerSettings.theme === th.id
                        ? 'border-amber-400 ring-1 ring-amber-400'
                        : 'border-zinc-800 hover:border-zinc-700'
                    }`}
                    style={{ backgroundColor: th.bg, color: th.text }}
                  >
                    <div className="text-xs font-bold">{th.label}</div>
                  </button>
                ))}
              </div>
            </div>

            {/* Source Strip Position */}
            <div>
              <label className="block text-xs font-semibold text-zinc-300 mb-2">
                Source Badge Strip Position (Library Cards)
              </label>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { id: 'on-cover', label: 'On Cover' },
                  { id: 'below-cover', label: 'Below Cover' },
                  { id: 'none', label: 'Hidden' },
                ].map((item) => (
                  <button
                    key={item.id}
                    onClick={() => handleSourceStripChange(item.id as any)}
                    className={`p-2.5 rounded-xl border text-center text-xs font-semibold transition ${
                      readerSettings.sourceStripPosition === item.id
                        ? 'border-amber-500 bg-amber-500/10 text-amber-300'
                        : 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-zinc-200'
                    }`}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Section: Features & Engines */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Translation Config Card */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 space-y-3 shadow-xl">
            <div className="p-2.5 rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20 w-fit">
              <Languages className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-sm text-zinc-100">Translation Engines</h3>
              <p className="text-xs text-zinc-400 mt-1">
                Configure default Google Free, Google Gemini AI, or OpenAI-compatible backend endpoints.
              </p>
            </div>
            <button
              onClick={() => setShowTranslationModal(true)}
              className="w-full py-2.5 px-4 bg-zinc-950 hover:bg-zinc-800 border border-zinc-800 rounded-xl text-xs font-bold text-zinc-200 transition"
            >
              Configure Translation
            </button>
          </div>

          {/* Regex Cleanup Card */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 space-y-3 shadow-xl">
            <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 w-fit">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-sm text-zinc-100">Regex Text Cleanup</h3>
              <p className="text-xs text-zinc-400 mt-1">
                Manage regular expressions to strip site watermarks, discord links, and promotional ads.
              </p>
            </div>
            <button
              onClick={() => setShowRegexModal(true)}
              className="w-full py-2.5 px-4 bg-zinc-950 hover:bg-zinc-800 border border-zinc-800 rounded-xl text-xs font-bold text-zinc-200 transition"
            >
              Manage Regex Rules
            </button>
          </div>
        </div>

        {/* Section: Backup & Restore */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 space-y-4 shadow-xl">
          <h2 className="text-sm font-bold uppercase tracking-wider text-amber-400 flex items-center space-x-2">
            <Shield className="w-4 h-4" />
            <span>Backup & Data Portability</span>
          </h2>

          <p className="text-xs text-zinc-400">
            Export your entire library, reading progress, categories, translation settings, and regex rules to a JSON file.
          </p>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={handleBackupExport}
              className="flex items-center space-x-2 px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-xs rounded-xl transition shadow-lg shadow-amber-500/20"
            >
              <Download className="w-4 h-4" />
              <span>Export Backup JSON</span>
            </button>

            <button
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center space-x-2 px-5 py-2.5 bg-zinc-950 hover:bg-zinc-800 border border-zinc-800 text-zinc-200 font-bold text-xs rounded-xl transition"
            >
              <Upload className="w-4 h-4" />
              <span>Restore Backup</span>
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".json"
              onChange={handleBackupRestore}
              className="hidden"
            />
          </div>
        </div>

        {/* Section: About NoveLA */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-3xl p-6 space-y-3 shadow-xl">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-amber-500 to-orange-600 flex items-center justify-center font-black text-zinc-950 text-xl shadow-md">
              N
            </div>
            <div>
              <h3 className="font-bold text-sm text-zinc-100">NoveLA Web Edition</h3>
              <p className="text-xs text-zinc-400">Version 1.6.1 · Open Source (GPL-3.0)</p>
            </div>
          </div>

          <p className="text-xs text-zinc-400 leading-relaxed">
            Free and open source web novel and light novel reader. Features multi-source catalog scraping, global multi-source search, in-reader live parallel translation, web speech text-to-speech, EPUB import/export, and rich reader themes.
          </p>

          <div className="pt-2 flex items-center space-x-3 text-xs text-zinc-400">
            <a
              href="https://github.com/HnDK0/NoveLA"
              target="_blank"
              rel="noreferrer"
              className="flex items-center space-x-1.5 hover:text-amber-400 transition"
            >
              <ExternalLink className="w-4 h-4" />
              <span>GitHub Repository</span>
            </a>
            <span>·</span>
            <span className="flex items-center space-x-1">
              <Globe className="w-4 h-4" />
              <span>20 Languages Supported</span>
            </span>
          </div>
        </div>
      </div>

      {/* Modals */}
      <TranslationSettingsModal
        isOpen={showTranslationModal}
        onClose={() => setShowTranslationModal(false)}
      />

      <RegexCleanupModal
        isOpen={showRegexModal}
        onClose={() => setShowRegexModal(false)}
      />
    </div>
  );
};
