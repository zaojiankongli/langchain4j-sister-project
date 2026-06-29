/**
 * Unified mood emoji mapping — single source of truth for all components.
 * Covers all mood labels used across the application.
 */
export const MOOD_EMOJI_MAP: Record<string, string> = {
  happy: '\u{1F60A}',
  sad: '\u{1F622}',
  thinking: '\u{1F914}',
  surprised: '\u{1F62E}',
  annoyed: '\u{1F624}',
  neutral: '\u{1F610}',
  loving: '\u{1F970}',
  excited: '\u{1F929}',
  calm: '\u{1F60C}',
  angry: '\u{1F620}',
  confused: '\u{1F914}',
  sleepy: '\u{1F634}',
  grateful: '\u{1F64F}',
  love: '\u{1F970}',
}

const DEFAULT_EMOJI = '\u{1F610}'

/** Look up the emoji for a mood label. Returns neutral face for unknown labels. */
export function getMoodEmoji(moodLabel: string): string {
  return MOOD_EMOJI_MAP[moodLabel] ?? DEFAULT_EMOJI
}

/**
 * Mood color mapping for visual indicators (e.g. PAD bar accents).
 */
export const MOOD_COLOR_MAP: Record<string, string> = {
  happy: '#7edfa0',
  sad: '#8fd7ff',
  thinking: '#ffd166',
  surprised: '#f2b35f',
  annoyed: '#ff8f7c',
  neutral: '#a89f94',
  loving: '#ff8f9c',
}

const DEFAULT_COLOR = '#a89f94'

/** Look up the color for a mood label. Returns muted grey for unknown labels. */
export function getMoodColor(moodLabel: string): string {
  return MOOD_COLOR_MAP[moodLabel] ?? DEFAULT_COLOR
}
