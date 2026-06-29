import { shallowRef } from 'vue'

export type NotificationCategory = 'mail' | 'mood' | 'disconnect' | 'message'

export interface NotificationPrefs {
  enabled: boolean
  categories: Record<NotificationCategory, boolean>
}

export interface LocalCompanionSettings {
  enabled: boolean
  autoRotateMessages: boolean
  messageRotationSeconds: number
  tapMotionsEnabled: boolean
}

export type MusicBackgroundMode = 'preset-dusk' | 'preset-sparkle' | 'cover' | 'custom'

export interface MusicBackgroundSettings {
  mode: MusicBackgroundMode
  customPath: string
  overlayOpacity: number
}

export interface MusicSettings {
  directory: string | null
  background: MusicBackgroundSettings
  defaultVolume: number
}

export interface AudioSettings {
  enabled: boolean
  volume: number
}

export interface WindowSettings {
  musicWindowOpen: boolean
}

export interface PetDisplaySettings {
  scale: number
  opacity: number
}

export interface WindowBehaviorSettings {
  clickThrough: boolean
  musicWindowOpen: boolean
}

export interface ClientSettings {
  audio: AudioSettings
  music: MusicSettings
  notifications: NotificationPrefs
  localCompanion: LocalCompanionSettings
  window: WindowSettings
  petDisplay: PetDisplaySettings
  windowBehavior: WindowBehaviorSettings
}

export interface ClientSettingsPatch {
  audio?: Partial<AudioSettings>
  music?: Partial<Omit<MusicSettings, 'background'>> & {
    background?: Partial<MusicBackgroundSettings>
  }
  notifications?: Partial<Omit<NotificationPrefs, 'categories'>> & {
    categories?: Partial<Record<NotificationCategory, boolean>>
  }
  localCompanion?: Partial<LocalCompanionSettings>
  window?: Partial<WindowSettings>
  petDisplay?: Partial<PetDisplaySettings>
  windowBehavior?: Partial<WindowBehaviorSettings>
}

const CLIENT_SETTINGS_KEY = 'desktop-pet.client-settings'

const LEGACY_KEYS = {
  localCompanion: 'desktop-pet.local-companion-settings',
  notificationPrefs: 'desktop-pet.notification-prefs',
  musicDirectory: 'desktop-pet.music.directory',
  musicBackgroundMode: 'desktop-pet.music.background.mode',
  musicBackgroundCustomPath: 'desktop-pet.music.background.custom-path',
  musicBackgroundOverlayOpacity: 'desktop-pet.music.background.overlay-opacity',
  petDisplay: 'desktop-pet.display-preferences',
  musicVolume: 'desktop-pet.music.volume',
}

export const DEFAULT_CLIENT_SETTINGS: ClientSettings = {
  audio: {
    enabled: true,
    volume: 0.8,
  },
  music: {
    directory: null,
    background: {
      mode: 'preset-dusk',
      customPath: '',
      overlayOpacity: 0.18,
    },
    defaultVolume: 0.7,
  },
  notifications: {
    enabled: true,
    categories: {
      mail: true,
      mood: true,
      disconnect: true,
      message: true,
    },
  },
  localCompanion: {
    enabled: true,
    autoRotateMessages: true,
    messageRotationSeconds: 12,
    tapMotionsEnabled: true,
  },
  window: {
    musicWindowOpen: false,
  },
  petDisplay: {
    scale: 1.0,
    opacity: 1.0,
  },
  windowBehavior: {
    clickThrough: false,
    musicWindowOpen: false,
  },
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object'
}

function readJsonRecord(key: string): Record<string, unknown> | null {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    return isRecord(parsed) ? parsed : null
  } catch {
    return null
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function readBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback
}

function readNumber(value: unknown, fallback: number, min: number, max: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? clamp(value, min, max) : fallback
}

function readString(value: unknown, fallback: string): string {
  return typeof value === 'string' ? value : fallback
}

function readNullableString(value: unknown, fallback: string | null): string | null {
  return typeof value === 'string' ? value : fallback
}

function readMusicBackgroundMode(value: unknown, fallback: MusicBackgroundMode): MusicBackgroundMode {
  if (value === 'preset-dusk' || value === 'preset-sparkle' || value === 'cover' || value === 'custom') {
    return value
  }

  return fallback
}

function readNotifications(value: unknown): NotificationPrefs {
  const source = isRecord(value) ? value : {}
  const categories = isRecord(source.categories) ? source.categories : {}
  const defaults = DEFAULT_CLIENT_SETTINGS.notifications

  return {
    enabled: readBoolean(source.enabled, defaults.enabled),
    categories: {
      mail: readBoolean(categories.mail, defaults.categories.mail),
      mood: readBoolean(categories.mood, defaults.categories.mood),
      disconnect: readBoolean(categories.disconnect, defaults.categories.disconnect),
      message: readBoolean(categories.message, defaults.categories.message),
    },
  }
}

function readLocalCompanion(value: unknown): LocalCompanionSettings {
  const source = isRecord(value) ? value : {}
  const defaults = DEFAULT_CLIENT_SETTINGS.localCompanion

  return {
    enabled: readBoolean(source.enabled, defaults.enabled),
    autoRotateMessages: readBoolean(source.autoRotateMessages, defaults.autoRotateMessages),
    messageRotationSeconds: readNumber(source.messageRotationSeconds, defaults.messageRotationSeconds, 5, 60),
    tapMotionsEnabled: readBoolean(source.tapMotionsEnabled, defaults.tapMotionsEnabled),
  }
}

function readMusic(value: unknown): MusicSettings {
  const source = isRecord(value) ? value : {}
  const background = isRecord(source.background) ? source.background : {}
  const defaults = DEFAULT_CLIENT_SETTINGS.music

  return {
    directory: readNullableString(source.directory, defaults.directory),
    defaultVolume: readNumber(source.defaultVolume, defaults.defaultVolume, 0, 1),
    background: {
      mode: readMusicBackgroundMode(background.mode, defaults.background.mode),
      customPath: readString(background.customPath, defaults.background.customPath),
      overlayOpacity: readNumber(background.overlayOpacity, defaults.background.overlayOpacity, 0.2, 0.8),
    },
  }
}

function readAudio(value: unknown): AudioSettings {
  const source = isRecord(value) ? value : {}
  const defaults = DEFAULT_CLIENT_SETTINGS.audio

  return {
    enabled: readBoolean(source.enabled, defaults.enabled),
    volume: readNumber(source.volume, defaults.volume, 0, 1),
  }
}

function readWindow(value: unknown): WindowSettings {
  const source = isRecord(value) ? value : {}
  const defaults = DEFAULT_CLIENT_SETTINGS.window

  return {
    musicWindowOpen: readBoolean(source.musicWindowOpen, defaults.musicWindowOpen),
  }
}

function readPetDisplay(value: unknown): PetDisplaySettings {
  const source = isRecord(value) ? value : {}
  const defaults = DEFAULT_CLIENT_SETTINGS.petDisplay

  return {
    scale: readNumber(source.scale, defaults.scale, 0.5, 1.5),
    opacity: readNumber(source.opacity, defaults.opacity, 0.3, 1.0),
  }
}

function readWindowBehavior(value: unknown): WindowBehaviorSettings {
  const source = isRecord(value) ? value : {}
  const defaults = DEFAULT_CLIENT_SETTINGS.windowBehavior

  return {
    clickThrough: readBoolean(source.clickThrough, defaults.clickThrough),
    musicWindowOpen: readBoolean(source.musicWindowOpen, defaults.musicWindowOpen),
  }
}

function readLegacySettings(): ClientSettingsPatch {
  const localCompanion = readJsonRecord(LEGACY_KEYS.localCompanion)
  const notificationPrefs = readJsonRecord(LEGACY_KEYS.notificationPrefs)
  const musicDirectory = localStorage.getItem(LEGACY_KEYS.musicDirectory)
  const musicBackgroundMode = localStorage.getItem(LEGACY_KEYS.musicBackgroundMode)
  const musicBackgroundCustomPath = localStorage.getItem(LEGACY_KEYS.musicBackgroundCustomPath)
  const musicBackgroundOverlayOpacityRaw = localStorage.getItem(LEGACY_KEYS.musicBackgroundOverlayOpacity)
  const musicBackground: Partial<MusicBackgroundSettings> = {}

  if (musicBackgroundMode !== null) {
    musicBackground.mode = readMusicBackgroundMode(musicBackgroundMode, DEFAULT_CLIENT_SETTINGS.music.background.mode)
  }

  if (musicBackgroundCustomPath !== null) {
    musicBackground.customPath = musicBackgroundCustomPath
  }

  if (musicBackgroundOverlayOpacityRaw !== null) {
    musicBackground.overlayOpacity = readNumber(Number(musicBackgroundOverlayOpacityRaw), DEFAULT_CLIENT_SETTINGS.music.background.overlayOpacity, 0.2, 0.8)
  }

  const music: ClientSettingsPatch['music'] = {}
  if (musicDirectory !== null) {
    music.directory = musicDirectory
  }
  if (Object.keys(musicBackground).length > 0) {
    music.background = musicBackground
  }

  // --- New legacy migrations ---
  const oldVolumeRaw = localStorage.getItem(LEGACY_KEYS.musicVolume)
  let migratedMusicVolume: number | undefined
  if (oldVolumeRaw !== null) {
    migratedMusicVolume = readNumber(Number(oldVolumeRaw), DEFAULT_CLIENT_SETTINGS.music.defaultVolume, 0, 1)
    localStorage.removeItem(LEGACY_KEYS.musicVolume)
  }

  let migratedPetDisplay: PetDisplaySettings | undefined
  const oldDisplay = readJsonRecord(LEGACY_KEYS.petDisplay)
  if (oldDisplay !== null) {
    migratedPetDisplay = {
      scale: readNumber(oldDisplay.scale, DEFAULT_CLIENT_SETTINGS.petDisplay.scale, 0.5, 1.5),
      opacity: readNumber(oldDisplay.opacity, DEFAULT_CLIENT_SETTINGS.petDisplay.opacity, 0.3, 1.0),
    }
    localStorage.removeItem(LEGACY_KEYS.petDisplay)
  }

  // Remove legacy keys that were already merged into the unified key
  ;[LEGACY_KEYS.musicDirectory, LEGACY_KEYS.musicBackgroundMode, LEGACY_KEYS.musicBackgroundCustomPath, LEGACY_KEYS.musicBackgroundOverlayOpacity].forEach((key) => {
    localStorage.removeItem(key)
  })

  return {
    music: Object.keys(music).length > 0 || migratedMusicVolume !== undefined
      ? { ...music, ...(migratedMusicVolume !== undefined ? { defaultVolume: migratedMusicVolume } : {}) }
      : undefined,
    notifications: notificationPrefs ? readNotifications(notificationPrefs) : undefined,
    localCompanion: localCompanion ? readLocalCompanion(localCompanion) : undefined,
    ...(migratedPetDisplay !== undefined ? { petDisplay: migratedPetDisplay } : {}),
  }
}

function normalizeSettings(value: unknown, includeLegacy: boolean): ClientSettings {
  const source = isRecord(value) ? value : {}
  const normalized: ClientSettings = {
    audio: readAudio(source.audio),
    music: readMusic(source.music),
    notifications: readNotifications(source.notifications),
    localCompanion: readLocalCompanion(source.localCompanion),
    window: readWindow(source.window),
    petDisplay: readPetDisplay(source.petDisplay),
    windowBehavior: readWindowBehavior(source.windowBehavior),
  }

  return includeLegacy ? mergeSettings(normalized, readLegacySettings()) : normalized
}

function loadClientSettings(): ClientSettings {
  const raw = localStorage.getItem(CLIENT_SETTINGS_KEY)
  if (!raw) {
    return normalizeSettings(null, true)
  }

  try {
    return normalizeSettings(JSON.parse(raw), false)
  } catch {
    return normalizeSettings(null, true)
  }
}

function mergeSettings(current: ClientSettings, patch: ClientSettingsPatch): ClientSettings {
  const next: ClientSettings = {
    audio: {
      ...current.audio,
      ...(patch.audio ?? {}),
    },
    music: {
      ...current.music,
      ...(patch.music ?? {}),
      background: {
        ...current.music.background,
        ...(patch.music?.background ?? {}),
      },
    },
    notifications: {
      ...current.notifications,
      ...(patch.notifications ?? {}),
      categories: {
        ...current.notifications.categories,
        ...(patch.notifications?.categories ?? {}),
      },
    },
    localCompanion: {
      ...current.localCompanion,
      ...(patch.localCompanion ?? {}),
    },
    window: {
      ...current.window,
      ...(patch.window ?? {}),
    },
    petDisplay: {
      ...current.petDisplay,
      ...(patch.petDisplay ?? {}),
    },
    windowBehavior: {
      ...current.windowBehavior,
      ...(patch.windowBehavior ?? {}),
    },
  }

  next.audio.volume = clamp(next.audio.volume, 0, 1)
  next.music.defaultVolume = clamp(next.music.defaultVolume, 0, 1)
  next.localCompanion.messageRotationSeconds = clamp(next.localCompanion.messageRotationSeconds, 5, 60)
  next.music.background.overlayOpacity = clamp(next.music.background.overlayOpacity, 0.2, 0.8)
  next.petDisplay.scale = clamp(next.petDisplay.scale, 0.5, 1.5)
  next.petDisplay.opacity = clamp(next.petDisplay.opacity, 0.3, 1.0)

  return next
}

const clientSettings = shallowRef<ClientSettings>(loadClientSettings())

/** Cross-window sync: listen for settings changes from other Tauri windows */
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e: StorageEvent) => {
    if (e.key === CLIENT_SETTINGS_KEY && e.newValue) {
      try {
        clientSettings.value = normalizeSettings(JSON.parse(e.newValue), false)
      } catch {
        // Ignore malformed data from other windows
      }
    }
  })
}

function persistClientSettings(settings: ClientSettings): void {
  localStorage.setItem(CLIENT_SETTINGS_KEY, JSON.stringify(settings))
}

export function useClientSettings() {
  function updateClientSettings(patch: ClientSettingsPatch): void {
    const nextSettings = mergeSettings(clientSettings.value, patch)
    clientSettings.value = nextSettings
    persistClientSettings(nextSettings)
  }

  return {
    clientSettings,
    updateClientSettings,
  }
}
