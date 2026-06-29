export type MusicEffectMode = 'quiet' | 'rain' | 'breath' | 'aurora' | 'fireflies' | 'mist' | 'stars'

export const MUSIC_EFFECT_STORAGE_KEY = 'desktop-pet.music.effect-mode'

export const MUSIC_EFFECT_MODES: readonly MusicEffectMode[] = ['quiet', 'rain', 'breath', 'aurora', 'fireflies', 'mist', 'stars']

export const MUSIC_EFFECT_LABELS: Record<MusicEffectMode, string> = {
  quiet: '静谧',
  rain: '细雨',
  breath: '明暗',
  aurora: '极光',
  fireflies: '萤火',
  mist: '薄雾',
  stars: '星幕',
}

export const DEFAULT_MUSIC_EFFECT_MODE: MusicEffectMode = 'quiet'

export function cycleMusicEffectMode(mode: MusicEffectMode): MusicEffectMode {
  const index = MUSIC_EFFECT_MODES.indexOf(mode)
  return MUSIC_EFFECT_MODES[(index + 1) % MUSIC_EFFECT_MODES.length]
}
