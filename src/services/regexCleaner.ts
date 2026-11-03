import { RegexRule } from '../types';

export function applyRegexRules(text: string, rules: RegexRule[]): string {
  if (!text) return '';
  let cleaned = text;

  for (const rule of rules) {
    if (!rule.isEnabled || !rule.pattern) continue;
    try {
      const regex = new RegExp(rule.pattern, rule.flags || 'g');
      cleaned = cleaned.replace(regex, rule.replacement || '');
    } catch {
      // Invalid regex pattern, skip silently
    }
  }

  // Normalize excessive empty lines
  cleaned = cleaned.replace(/\n{3,}/g, '\n\n');
  return cleaned;
}
