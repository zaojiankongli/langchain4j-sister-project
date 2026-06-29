import { computed, ref, watch } from 'vue'
import { open } from '@tauri-apps/plugin-dialog'
import type { UserSettings, OCEANPersonality, PersonalityPreset } from '../types/settings'
import { usePetNotifications, CATEGORY_LABELS, type NotificationCategory } from './usePetNotifications'
import { useLocalCompanionSettings } from './useLocalCompanionSettings'
import { useClientSettings, type MusicBackgroundMode } from './useClientSettings'

interface OCEANDim {
  key: keyof OCEANPersonality
  label: string
}

export interface UsePetSettingsFormOptions {
  settings: () => UserSettings | null
}

export type PetSettingsForm = ReturnType<typeof usePetSettingsForm>

export function usePetSettingsForm(options: UsePetSettingsFormOptions) {
  const personality = ref<OCEANPersonality>({
    openness: 0.5,
    conscientiousness: 0.5,
    extraversion: 0.5,
    agreeableness: 0.5,
    neuroticism: 0.5,
  })
  const sensitivity = ref(0.5)
  const decayRate = ref(0.5)
  const regressionRate = ref(0.5)
  const ttsEnabled = ref(false)
  const ttsVolume = ref(0.8)
  const ttsSpeed = ref(1.0)
  const proactiveEnabled = ref(false)
  const proactiveInterval = ref(180)
  const themeId = ref<number | undefined>(undefined)

  const { prefs: notifPrefs, toggleCategory, updatePrefs: updateNotifPrefs, requestNotificationPermission } = usePetNotifications()
  const notifCategories = Object.keys(CATEGORY_LABELS) as NotificationCategory[]
  const { localCompanionSettings, updateLocalCompanionSettings } = useLocalCompanionSettings()
  const { clientSettings, updateClientSettings } = useClientSettings()

  const musicBackground = computed(() => clientSettings.value.music.background)
  const musicDirectoryLabel = computed(() => clientSettings.value.music.directory ?? '未选择')
  const musicBackgroundModes: Array<{ id: MusicBackgroundMode; label: string }> = [
    { id: 'preset-dusk', label: '薄暮微光' },
    { id: 'preset-sparkle', label: '晨雾轻纱' },
    { id: 'cover', label: '歌曲封面' },
    { id: 'custom', label: '自定义图片' },
  ]

  const audioEnabled = computed(() => clientSettings.value.audio.enabled)
  const audioVolume = computed(() => clientSettings.value.audio.volume)
  const petDisplay = computed(() => clientSettings.value.petDisplay)
  const windowBehavior = computed(() => clientSettings.value.windowBehavior)

  const oceanDims: OCEANDim[] = [
    { key: 'openness', label: '开放性' },
    { key: 'conscientiousness', label: '尽责性' },
    { key: 'extraversion', label: '外向性' },
    { key: 'agreeableness', label: '宜人性' },
    { key: 'neuroticism', label: '神经质' },
  ]

  watch(() => options.settings(), (s) => {
    if (s) {
      personality.value = { ...s.personality }
      sensitivity.value = s.sensitivity
      decayRate.value = s.decayRate
      regressionRate.value = s.regressionRate
      ttsEnabled.value = s.tts.enabled
      ttsVolume.value = s.tts.volume
      ttsSpeed.value = s.tts.speed
      proactiveEnabled.value = s.proactive.enabled
      proactiveInterval.value = s.proactive.interval
      themeId.value = s.themeId
    }
  }, { immediate: true })

  function applyPreset(preset: PersonalityPreset): void {
    personality.value = { ...preset.personality }
    sensitivity.value = preset.sensitivity
    decayRate.value = preset.decayRate
    regressionRate.value = preset.regressionRate
  }

  function formatProactiveInterval(seconds: number): string {
    const minutes = Math.round(seconds / 60)
    return `${minutes}分钟`
  }

  function handleSave(): Partial<UserSettings> {
    return {
      personality: { ...personality.value },
      sensitivity: sensitivity.value,
      decayRate: decayRate.value,
      regressionRate: regressionRate.value,
      tts: {
        enabled: ttsEnabled.value,
        volume: ttsVolume.value,
        speed: ttsSpeed.value,
      },
      proactive: {
        enabled: proactiveEnabled.value,
        interval: proactiveInterval.value,
      },
      themeId: themeId.value,
    }
  }

  function onNotifMasterToggle(): void {
    updateNotifPrefs({ enabled: !notifPrefs.value.enabled })
  }

  function onNotifCategoryToggle(cat: NotificationCategory): void {
    toggleCategory(cat)
  }

  async function onGrantPermission(): Promise<void> {
    await requestNotificationPermission()
  }

  function onLocalCompanionEnabledToggle(): void {
    updateLocalCompanionSettings({ enabled: !localCompanionSettings.value.enabled })
  }

  function onLocalCompanionAutoRotateToggle(): void {
    updateLocalCompanionSettings({ autoRotateMessages: !localCompanionSettings.value.autoRotateMessages })
  }

  function onLocalCompanionTapMotionsToggle(): void {
    updateLocalCompanionSettings({ tapMotionsEnabled: !localCompanionSettings.value.tapMotionsEnabled })
  }

  function onLocalCompanionRotationSecondsInput(event: Event): void {
    updateLocalCompanionSettings({ messageRotationSeconds: Number((event.target as HTMLInputElement).value) })
  }

  async function chooseMusicDirectory(): Promise<void> {
    const selected = await open({
      directory: true,
      multiple: false,
      recursive: true,
      title: '选择音乐目录',
    })

    if (!selected || Array.isArray(selected)) return
    updateClientSettings({ music: { directory: selected } })
  }

  function clearMusicDirectory(): void {
    updateClientSettings({ music: { directory: null } })
  }

  function onMusicBackgroundModeChange(event: Event): void {
    updateClientSettings({ music: { background: { mode: (event.target as HTMLSelectElement).value as MusicBackgroundMode } } })
  }

  async function chooseMusicBackground(): Promise<void> {
    const selected = await open({
      multiple: false,
      title: '选择播放器背景图',
      filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'webp'] }],
    })

    if (!selected || Array.isArray(selected)) return
    updateClientSettings({ music: { background: { mode: 'custom', customPath: selected } } })
  }

  function clearMusicBackground(): void {
    updateClientSettings({ music: { background: { customPath: '', mode: 'preset-dusk' } } })
  }

  function onMusicOverlayOpacityInput(event: Event): void {
    updateClientSettings({ music: { background: { overlayOpacity: Number((event.target as HTMLInputElement).value) } } })
  }

  function onAudioEnabledToggle(): void {
    updateClientSettings({ audio: { enabled: !audioEnabled.value } })
  }

  function onAudioVolumeInput(event: Event): void {
    updateClientSettings({ audio: { volume: Number((event.target as HTMLInputElement).value) } })
  }

  function onPetDisplayScaleInput(event: Event): void {
    updateClientSettings({ petDisplay: { scale: Number((event.target as HTMLInputElement).value) } })
  }

  function onPetDisplayOpacityInput(event: Event): void {
    updateClientSettings({ petDisplay: { opacity: Number((event.target as HTMLInputElement).value) } })
  }

  function onWindowClickThroughToggle(): void {
    updateClientSettings({ windowBehavior: { clickThrough: !windowBehavior.value.clickThrough } })
  }

  return {
    personality,
    sensitivity,
    decayRate,
    regressionRate,
    ttsEnabled,
    ttsVolume,
    ttsSpeed,
    proactiveEnabled,
    proactiveInterval,
    themeId,
    notifPrefs,
    notifCategories,
    localCompanionSettings,
    musicBackground,
    musicDirectoryLabel,
    musicBackgroundModes,
    audioEnabled,
    audioVolume,
    petDisplay,
    windowBehavior,
    oceanDims,
    applyPreset,
    formatProactiveInterval,
    handleSave,
    onNotifMasterToggle,
    onNotifCategoryToggle,
    onGrantPermission,
    onLocalCompanionEnabledToggle,
    onLocalCompanionAutoRotateToggle,
    onLocalCompanionTapMotionsToggle,
    onLocalCompanionRotationSecondsInput,
    chooseMusicDirectory,
    clearMusicDirectory,
    onMusicBackgroundModeChange,
    chooseMusicBackground,
    clearMusicBackground,
    onMusicOverlayOpacityInput,
    onAudioEnabledToggle,
    onAudioVolumeInput,
    onPetDisplayScaleInput,
    onPetDisplayOpacityInput,
    onWindowClickThroughToggle,
  }
}
