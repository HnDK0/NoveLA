import React, { useState } from 'react';
import { X, Sparkles, Plus, Trash2, Check, AlertCircle } from 'lucide-react';
import { RegexRule } from '../types';
import { storage } from '../services/storage';
import { applyRegexRules } from '../services/regexCleaner';

interface RegexCleanupModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const RegexCleanupModal: React.FC<RegexCleanupModalProps> = ({ isOpen, onClose }) => {
  const [rules, setRules] = useState<RegexRule[]>(() => storage.getRegexRules());
  const [newName, setNewName] = useState('');
  const [newPattern, setNewPattern] = useState('');
  const [newReplacement, setNewReplacement] = useState('');
  const [testText, setTestText] = useState('Visit webnovel.com for latest chapters.\nJoin our discord at https://discord.gg/novel-readers\nThe hero raised his sword high.');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleToggle = (id: string) => {
    const updated = rules.map((r) => (r.id === id ? { ...r, isEnabled: !r.isEnabled } : r));
    setRules(updated);
    storage.saveRegexRules(updated);
  };

  const handleDelete = (id: string) => {
    const updated = rules.filter((r) => r.id !== id);
    setRules(updated);
    storage.saveRegexRules(updated);
  };

  const handleAddRule = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPattern.trim()) {
      setError('Please provide a valid regex pattern');
      return;
    }
    try {
      new RegExp(newPattern, 'gi'); // Validate pattern syntax
    } catch {
      setError('Invalid regular expression syntax');
      return;
    }

    const created = storage.addRegexRule({
      name: newName.trim() || 'Custom Rule',
      pattern: newPattern.trim(),
      replacement: newReplacement,
      isEnabled: true,
      flags: 'gi',
    });

    setRules([...rules, created]);
    setNewName('');
    setNewPattern('');
    setNewReplacement('');
    setError(null);
  };

  const previewCleaned = applyRegexRules(testText, rules);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="relative w-full max-w-2xl bg-zinc-900 border border-zinc-800 rounded-2xl p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-100 p-1 rounded-lg hover:bg-zinc-800 transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center space-x-3 mb-5">
          <div className="p-2.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <Sparkles className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-zinc-100">Regex Text Cleanup</h3>
            <p className="text-xs text-zinc-400">Remove watermarks, promotional ads, and spam from chapters</p>
          </div>
        </div>

        {/* Existing Rules List */}
        <div className="space-y-3 mb-6">
          <h4 className="text-xs font-semibold text-zinc-300 uppercase tracking-wider">Active Cleanup Rules</h4>
          <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
            {rules.map((rule) => (
              <div
                key={rule.id}
                className="p-3 bg-zinc-950 border border-zinc-800 rounded-xl flex items-center justify-between gap-3"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-semibold text-zinc-200">{rule.name}</span>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-400 font-mono">
                      /{rule.pattern}/{rule.flags || 'g'}
                    </span>
                  </div>
                  {rule.replacement && (
                    <div className="text-[11px] text-zinc-500 truncate mt-0.5">
                      Replace with: &quot;{rule.replacement}&quot;
                    </div>
                  )}
                </div>

                <div className="flex items-center space-x-2 shrink-0">
                  <button
                    type="button"
                    onClick={() => handleToggle(rule.id)}
                    className={`px-2.5 py-1 text-xs font-semibold rounded-lg border transition ${
                      rule.isEnabled
                        ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                        : 'bg-zinc-800 text-zinc-400 border-zinc-700'
                    }`}
                  >
                    {rule.isEnabled ? 'Enabled' : 'Disabled'}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(rule.id)}
                    className="p-1.5 text-zinc-500 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Add New Rule Form */}
        <form onSubmit={handleAddRule} className="p-4 bg-zinc-950/70 border border-zinc-800 rounded-xl space-y-3 mb-6">
          <h4 className="text-xs font-semibold text-zinc-300 uppercase tracking-wider flex items-center space-x-1.5">
            <Plus className="w-3.5 h-3.5 text-amber-400" />
            <span>Add Custom Rule</span>
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="block text-[11px] text-zinc-400 mb-1">Rule Name</label>
              <input
                type="text"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="e.g. Strip Patreon Link"
                className="w-full px-3 py-1.5 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-amber-500"
              />
            </div>
            <div>
              <label className="block text-[11px] text-zinc-400 mb-1">Regex Pattern</label>
              <input
                type="text"
                required
                value={newPattern}
                onChange={(e) => setNewPattern(e.target.value)}
                placeholder="e.g. patreon\.com\/\S+"
                className="w-full px-3 py-1.5 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-100 font-mono focus:outline-none focus:border-amber-500"
              />
            </div>
          </div>
          <div>
            <label className="block text-[11px] text-zinc-400 mb-1">Replacement Text (empty = delete matched)</label>
            <input
              type="text"
              value={newReplacement}
              onChange={(e) => setNewReplacement(e.target.value)}
              placeholder="Leave empty to remove"
              className="w-full px-3 py-1.5 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-amber-500"
            />
          </div>

          {error && (
            <div className="flex items-center space-x-1.5 text-rose-400 text-xs">
              <AlertCircle className="w-3.5 h-3.5" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex justify-end pt-1">
            <button
              type="submit"
              className="px-4 py-1.5 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-xs rounded-lg transition"
            >
              Add Rule
            </button>
          </div>
        </form>

        {/* Live Rule Sandbox Tester */}
        <div className="space-y-2">
          <h4 className="text-xs font-semibold text-zinc-300 uppercase tracking-wider">Live Sandbox Preview</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            <div>
              <span className="text-[11px] text-zinc-400 block mb-1">Sample Raw Text (Editable):</span>
              <textarea
                rows={3}
                value={testText}
                onChange={(e) => setTestText(e.target.value)}
                className="w-full p-2.5 bg-zinc-950 border border-zinc-800 rounded-lg text-zinc-300 font-mono text-[11px] focus:outline-none"
              />
            </div>
            <div>
              <span className="text-[11px] text-zinc-400 block mb-1">Cleaned Output:</span>
              <div className="w-full h-[88px] p-2.5 bg-zinc-950 border border-zinc-800 rounded-lg text-emerald-400 font-mono text-[11px] overflow-y-auto whitespace-pre-wrap">
                {previewCleaned || <span className="text-zinc-600">(all text removed)</span>}
              </div>
            </div>
          </div>
        </div>

        <div className="flex justify-end pt-5 border-t border-zinc-800/80 mt-5">
          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-100 font-semibold text-xs rounded-xl transition"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
