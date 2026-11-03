import { TranslationSettings } from '../types';

// In-memory cache for paragraph translations: `${targetLang}:${hash}` -> translated text
const translationCache = new Map<string, string>();

function hashString(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0;
  }
  return hash.toString(36);
}

export async function translateText(
  text: string,
  settings: TranslationSettings
): Promise<string> {
  const trimmed = text.trim();
  if (!trimmed) return text;

  const cacheKey = `${settings.targetLang}_${hashString(trimmed)}`;
  if (translationCache.has(cacheKey)) {
    return translationCache.get(cacheKey)!;
  }

  try {
    let result = '';

    if (settings.provider === 'google-free') {
      const source = settings.sourceLang || 'auto';
      const target = settings.targetLang || 'es';
      const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=${encodeURIComponent(
        source
      )}&tl=${encodeURIComponent(target)}&dt=t&q=${encodeURIComponent(trimmed)}`;

      const res = await fetch(url);
      if (!res.ok) throw new Error(`Translate API error: ${res.statusText}`);
      const data = await res.json();
      // data[0] is array of [[translated_segment, original_segment], ...]
      if (Array.isArray(data) && Array.isArray(data[0])) {
        result = data[0].map((item: unknown[]) => item[0]).join('');
      } else {
        result = trimmed;
      }
    } else if (settings.provider === 'gemini' && settings.apiKey) {
      // Direct call to Gemini API
      const prompt = `${settings.customPrompt || 'Translate this novel text into ' + settings.targetLang}. Maintain literary flair and tone.\n\nOriginal text:\n${trimmed}`;
      const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${settings.apiKey}`;
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
        }),
      });
      const data = await res.json();
      result = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim() || trimmed;
    } else if (settings.provider === 'openai' && settings.apiKey) {
      const endpoint = (settings.customEndpoint || 'https://api.openai.com/v1').replace(/\/+$/, '');
      const model = settings.customModel || 'gpt-4o-mini';
      const prompt = `${settings.customPrompt || 'Translate the following novel text into ' + settings.targetLang}. Keep formatting identical.`;
      
      const res = await fetch(`${endpoint}/chat/completions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${settings.apiKey}`,
        },
        body: JSON.stringify({
          model,
          messages: [
            { role: 'system', content: prompt },
            { role: 'user', content: trimmed },
          ],
        }),
      });
      const data = await res.json();
      result = data.choices?.[0]?.message?.content?.trim() || trimmed;
    } else {
      // Fallback: mock translated simulation if no API key provided
      result = `[${settings.targetLang.toUpperCase()}] ${trimmed}`;
    }

    if (result) {
      translationCache.set(cacheKey, result);
    }
    return result;
  } catch (err) {
    console.warn('Translation failed, falling back:', err);
    return `[Translated: ${trimmed.slice(0, 40)}...]`;
  }
}

export const SUPPORTED_LANGUAGES = [
  { code: 'en', name: 'English' },
  { code: 'es', name: 'Spanish (Español)' },
  { code: 'ru', name: 'Russian (Русский)' },
  { code: 'fr', name: 'French (Français)' },
  { code: 'de', name: 'German (Deutsch)' },
  { code: 'it', name: 'Italian (Italiano)' },
  { code: 'pt', name: 'Portuguese (Português)' },
  { code: 'zh', name: 'Chinese (中文)' },
  { code: 'ja', name: 'Japanese (日本語)' },
  { code: 'ko', name: 'Korean (한국어)' },
  { code: 'id', name: 'Indonesian (Bahasa Indonesia)' },
  { code: 'vi', name: 'Vietnamese (Tiếng Việt)' },
  { code: 'ar', name: 'Arabic (العربية)' },
  { code: 'hi', name: 'Hindi (हिन्दी)' },
  { code: 'tr', name: 'Turkish (Türkçe)' },
  { code: 'pl', name: 'Polish (Polski)' },
];
