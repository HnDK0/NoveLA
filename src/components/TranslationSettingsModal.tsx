import React, { useState } from 'react';
import { X, Languages, Sparkles, Check, Key, Globe } from 'lucide-react';
import { TranslationSettings } from '../types';
import { SUPPORTED_LANGUAGES } from '../services/translation';
import { storage } from '../services/storage';

interface TranslationSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  bookUrl?: string; // If provided, configures settings specific to this novel
  bookTitle?: string;
}

export const TranslationSettingsModal: React.FC<TranslationSettingsModalProps> = ({
  isOpen,
  onClose,
  bookUrl,
  bookTitle,
}) => {
  const isNovelSpecific = Boolean(bookUrl);

  const [settings, setSettings] = useState<TranslationSettings>(() => {
    return isNovelSpecific && bookUrl
      ? storage.getNovelTranslationSettings(bookUrl)
      : storage.getGlobalTranslationSettings();
  });

  const [savedSuccess, setSavedSuccess] = useState(false);

  if (!isOpen) return null;

  const handleSave = () => {
    if (isNovelSpecific && bookUrl) {
      storage.saveNovelTranslationSettings(bookUrl, settings);
    } else {
      storage.saveGlobalTranslationSettings(settings);
    }
    setSavedSuccess(true);
    setTimeout(() => {
      setSavedSuccess(false);
      onClose();
    }, 600);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="relative w-full max-w-lg bg-zinc-900 border border-zinc-800 rounded-2xl p-6 shadow-2xl max-h-[90vh] overflow-y-auto">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-zinc-400 hover:text-zinc-100 p-1 rounded-lg hover:bg-zinc-800 transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center space-x-3 mb-5">
          <div className="p-2.5 rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20">
            <Languages className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-zinc-100">
              {isNovelSpecific ? 'Per-Novel Translation' : 'Translation Settings'}
            </h3>
            <p className="text-xs text-zinc-400">
              {isNovelSpecific
                ? `Custom translation configuration for "${bookTitle || 'this book'}"`
                : 'Configure default translation backends & parallel reading'}
            </p>
          </div>
        </div>

        <div className="space-y-4">
          {/* Enable toggle */}
          <div className="flex items-center justify-between p-3.5 bg-zinc-950 border border-zinc-800 rounded-xl">
            <div>
              <span className="text-sm font-semibold text-zinc-100 block">Enable In-Reader Translation</span>
              <span className="text-xs text-zinc-400">Translate chapters automatically while reading</span>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={settings.enabled}
                onChange={(e) => setSettings({ ...settings, enabled: e.target.checked })}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-zinc-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-amber-500"></div>
            </label>
          </div>

          {/* Reading Mode */}
          <div>
            <label className="block text-xs font-semibold text-zinc-300 uppercase tracking-wider mb-2">
              Display Mode
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setSettings({ ...settings, mode: 'parallel' })}
                className={`p-3 rounded-xl border text-left text-xs font-medium transition ${
                  settings.mode === 'parallel'
                    ? 'border-amber-500 bg-amber-500/10 text-amber-300'
                    : 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <div className="font-bold text-sm mb-0.5 text-zinc-100">Parallel (Side-by-Side)</div>
                <div>Shows original and translated paragraph pairs together</div>
              </button>
              <button
                type="button"
                onClick={() => setSettings({ ...settings, mode: 'replace' })}
                className={`p-3 rounded-xl border text-left text-xs font-medium transition ${
                  settings.mode === 'replace'
                    ? 'border-amber-500 bg-amber-500/10 text-amber-300'
                    : 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-zinc-200'
                }`}
              >
                <div className="font-bold text-sm mb-0.5 text-zinc-100">Replace Original</div>
                <div>Displays only translated text in the reader body</div>
              </button>
            </div>
          </div>

          {/* Target Language */}
          <div>
            <label className="block text-xs font-semibold text-zinc-300 uppercase tracking-wider mb-2">
              Target Language
            </label>
            <select
              value={settings.targetLang}
              onChange={(e) => setSettings({ ...settings, targetLang: e.target.value })}
              className="w-full px-4 py-2.5 bg-zinc-950 border border-zinc-800 rounded-xl text-zinc-100 text-sm focus:outline-none focus:border-amber-500 transition"
            >
              {SUPPORTED_LANGUAGES.map((lang) => (
                <option key={lang.code} value={lang.code}>
                  {lang.name}
                </option>
              ))}
            </select>
          </div>

          {/* Provider */}
          <div>
            <label className="block text-xs font-semibold text-zinc-300 uppercase tracking-wider mb-2">
              Translation Engine / Backend
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[
                { id: 'google-free', label: 'Google (Free)', tag: 'No key needed' },
                { id: 'gemini', label: 'Gemini AI', tag: 'Fast & Smart' },
                { id: 'openai', label: 'OpenAI / Custom', tag: 'Compatible' },
              ].map((prov) => (
                <button
                  key={prov.id}
                  type="button"
                  onClick={() => setSettings({ ...settings, provider: prov.id as any })}
                  className={`p-2.5 rounded-xl border text-center transition ${
                    settings.provider === prov.id
                      ? 'border-amber-500 bg-amber-500/10 text-amber-300'
                      : 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-zinc-200'
                  }`}
                >
                  <div className="font-semibold text-xs text-zinc-100">{prov.label}</div>
                  <div className="text-[10px] text-zinc-500 mt-0.5">{prov.tag}</div>
                </button>
              ))}
            </div>
          </div>

          {/* API Key if Gemini or OpenAI */}
          {(settings.provider === 'gemini' || settings.provider === 'openai') && (
            <div className="space-y-3 p-3.5 bg-zinc-950/80 border border-zinc-800 rounded-xl">
              <div>
                <label className="block text-xs font-semibold text-zinc-400 mb-1 flex items-center space-x-1.5">
                  <Key className="w-3.5 h-3.5" />
                  <span>API Key</span>
                </label>
                <input
                  type="password"
                  value={settings.apiKey || ''}
                  onChange={(e) => setSettings({ ...settings, apiKey: e.target.value })}
                  placeholder={`Enter your ${settings.provider === 'gemini' ? 'Google Gemini' : 'OpenAI'} API key`}
                  className="w-full px-3 py-2 bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 text-xs focus:outline-none focus:border-amber-500"
                />
              </div>

              {settings.provider === 'openai' && (
                <div>
                  <label className="block text-xs font-semibold text-zinc-400 mb-1 flex items-center space-x-1.5">
                    <Globe className="w-3.5 h-3.5" />
                    <span>Custom Endpoint</span>
                  </label>
                  <input
                    type="text"
                    value={settings.customEndpoint || ''}
                    onChange={(e) => setSettings({ ...settings, customEndpoint: e.target.value })}
                    placeholder="https://api.openai.com/v1"
                    className="w-full px-3 py-2 bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 text-xs focus:outline-none focus:border-amber-500"
                  />
                </div>
              )}
            </div>
          )}

          {/* Custom Prompt / Glossary */}
          <div>
            <label className="block text-xs font-semibold text-zinc-300 uppercase tracking-wider mb-2 flex items-center justify-between">
              <span>Novel-Specific Prompt / Glossary</span>
              <span className="text-[10px] text-amber-400 font-normal">Cultivation & names glossary</span>
            </label>
            <textarea
              rows={3}
              value={settings.customPrompt || ''}
              onChange={(e) => setSettings({ ...settings, customPrompt: e.target.value })}
              placeholder="e.g. Translate 'Dantian' as 'Core Reservoir', keep character honorifics like 'Senior-Brother Lin'..."
              className="w-full px-4 py-2.5 bg-zinc-950 border border-zinc-800 rounded-xl text-zinc-100 text-xs placeholder-zinc-500 focus:outline-none focus:border-amber-500 transition"
            />
          </div>
        </div>

        <div className="flex items-center justify-end space-x-3 pt-5 border-t border-zinc-800/80 mt-5">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 rounded-xl transition"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            className="flex items-center space-x-2 px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-bold text-sm rounded-xl transition shadow-lg shadow-amber-500/20"
          >
            {savedSuccess ? (
              <>
                <Check className="w-4 h-4 text-emerald-950" />
                <span>Saved!</span>
              </>
            ) : (
              <span>Save Settings</span>
            )}
          </button>
        </div>
      </div>
    </div>
  );
};
