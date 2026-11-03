import React, { useState } from 'react';
import { Puzzle, Plus, Check, Download, Trash2, Globe, Code, ExternalLink, RefreshCw } from 'lucide-react';
import { SourceExtension } from '../types';
import { storage } from '../services/storage';

export const ExtensionsScreen: React.FC = () => {
  const [extensions, setExtensions] = useState<SourceExtension[]>(() => storage.getExtensions());
  const [repoUrl, setRepoUrl] = useState('https://github.com/HnDK0/external-sources');
  const [showAddRepoModal, setShowAddRepoModal] = useState(false);
  const [selectedExtensionForCode, setSelectedExtensionForCode] = useState<SourceExtension | null>(null);

  const toggleInstall = (id: string) => {
    storage.toggleExtension(id);
    setExtensions(storage.getExtensions());
  };

  const handleAddCustomSource = (e: React.FormEvent) => {
    e.preventDefault();
    const newExt: SourceExtension = {
      id: `custom_source_${Date.now()}`,
      name: 'Custom Community Source',
      version: '1.0.0',
      language: 'EN',
      baseUrl: repoUrl,
      icon: '🔌',
      contentType: 'NOVEL',
      isInstalled: true,
      isBuiltIn: false,
      description: `Installed from custom repository: ${repoUrl}`,
    };
    storage.addCustomExtension(newExt);
    setExtensions(storage.getExtensions());
    setShowAddRepoModal(false);
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100 pb-28 pt-4 md:pt-20 px-4 md:px-8">
      <div className="max-w-5xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-zinc-100 flex items-center space-x-2">
              <Puzzle className="w-7 h-7 text-amber-400" />
              <span>Extensions & Source Plugins</span>
            </h1>
            <p className="text-xs text-zinc-400">
              Manage external Lua scrapers and built-in source providers
            </p>
          </div>

          <button
            onClick={() => setShowAddRepoModal(true)}
            className="flex items-center space-x-1.5 px-4 py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-xs transition shadow-lg shadow-amber-500/20"
          >
            <Plus className="w-4 h-4" />
            <span>Add Plugin Repo</span>
          </button>
        </div>

        {/* Official Repo Banner */}
        <div className="p-4 bg-zinc-900 border border-zinc-800 rounded-2xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs">
          <div className="space-y-1">
            <span className="font-bold text-zinc-200">Official NoveLA Plugin Repository</span>
            <p className="text-zinc-400 font-mono text-[11px] truncate">https://github.com/HnDK0/external-sources</p>
          </div>
          <span className="px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-400 font-semibold border border-emerald-500/20 shrink-0">
            Synced & Verified
          </span>
        </div>

        {/* Extensions List */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {extensions.map((ext) => (
            <div
              key={ext.id}
              className="p-4 bg-zinc-900 border border-zinc-800 rounded-2xl flex items-start justify-between gap-4 transition hover:border-zinc-700"
            >
              <div className="flex items-start space-x-3.5 min-w-0">
                <span className="text-3xl p-2 bg-zinc-950 rounded-xl border border-zinc-800/80 shrink-0">
                  {ext.icon}
                </span>
                <div className="min-w-0">
                  <div className="flex items-center space-x-2">
                    <h3 className="font-bold text-sm text-zinc-100 truncate">{ext.name}</h3>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-zinc-800 text-zinc-400 font-mono">
                      v{ext.version}
                    </span>
                  </div>
                  <div className="flex items-center space-x-2 mt-0.5 text-xs text-zinc-400">
                    <span className="text-amber-400 font-semibold">{ext.language}</span>
                    <span>·</span>
                    <span>{ext.contentType}</span>
                  </div>
                  <p className="text-xs text-zinc-500 line-clamp-2 mt-1">{ext.description}</p>
                </div>
              </div>

              <div className="flex flex-col items-end space-y-2 shrink-0">
                <button
                  onClick={() => toggleInstall(ext.id)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition ${
                    ext.isInstalled
                      ? 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700 border border-zinc-700'
                      : 'bg-amber-500 text-zinc-950 hover:bg-amber-400 shadow-md shadow-amber-500/20'
                  }`}
                >
                  {ext.isInstalled ? 'Enabled' : 'Install'}
                </button>

                <button
                  onClick={() => setSelectedExtensionForCode(ext)}
                  title="Inspect Source Definition"
                  className="p-1 text-zinc-500 hover:text-zinc-300 rounded transition"
                >
                  <Code className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Add Repo Modal */}
      {showAddRepoModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
          <div className="relative w-full max-w-md bg-zinc-900 border border-zinc-800 rounded-2xl p-6 shadow-2xl space-y-4">
            <h3 className="text-lg font-bold text-zinc-100">Add External Plugin Repository</h3>
            <p className="text-xs text-zinc-400">
              Enter GitHub or raw repository URL containing Lua scraper manifests (.lua / .yaml)
            </p>
            <input
              type="url"
              required
              value={repoUrl}
              onChange={(e) => setRepoUrl(e.target.value)}
              placeholder="https://github.com/..."
              className="w-full px-3.5 py-2.5 bg-zinc-950 border border-zinc-800 rounded-xl text-xs text-zinc-100 focus:outline-none focus:border-amber-500 font-mono"
            />
            <div className="flex justify-end space-x-2 pt-2">
              <button
                onClick={() => setShowAddRepoModal(false)}
                className="px-4 py-2 text-xs font-semibold text-zinc-400 hover:text-zinc-200"
              >
                Cancel
              </button>
              <button
                onClick={handleAddCustomSource}
                className="px-4 py-2 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-xs rounded-xl shadow-md"
              >
                Add Repository
              </button>
            </div>
          </div>
        </div>
      )}

      {/* View Code Modal */}
      {selectedExtensionForCode && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
          <div className="relative w-full max-w-xl bg-zinc-900 border border-zinc-800 rounded-2xl p-6 shadow-2xl space-y-3">
            <h3 className="text-base font-bold text-zinc-100 flex items-center space-x-2">
              <Code className="w-5 h-5 text-amber-400" />
              <span>{selectedExtensionForCode.name} Scraper Definition</span>
            </h3>
            <div className="p-3 bg-zinc-950 border border-zinc-800 rounded-xl font-mono text-xs text-emerald-400 max-h-72 overflow-y-auto whitespace-pre">
{`-- NoveLA Lua Source Adapter for ${selectedExtensionForCode.name}
local http = require("http")
local html = require("html")

local source = {
  id = "${selectedExtensionForCode.id}",
  name = "${selectedExtensionForCode.name}",
  baseUrl = "${selectedExtensionForCode.baseUrl}",
  contentType = "${selectedExtensionForCode.contentType}",
  language = "${selectedExtensionForCode.language}"
}

function source.getCatalog(page)
  local url = source.baseUrl .. "/explore?page=" .. page
  local resp = http.get(url)
  return html.parseBooks(resp.body)
end

function source.getChapter(chapterUrl)
  local resp = http.get(chapterUrl)
  return html.extractParagraphs(resp.body)
end

return source`}
            </div>
            <div className="flex justify-end pt-2">
              <button
                onClick={() => setSelectedExtensionForCode(null)}
                className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 font-semibold text-xs rounded-xl"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
