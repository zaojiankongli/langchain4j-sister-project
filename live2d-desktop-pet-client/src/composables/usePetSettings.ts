import { shallowRef } from 'vue'
import { get, put } from '../utils/apiClient'
import { usePetConfigState } from './usePetConfigState'
import type { PersonalityPreset, UserSettings } from '../types/settings'

export function usePetSettings() {
  const { userId } = usePetConfigState()

  const presets = shallowRef<PersonalityPreset[]>([])
  const currentSettings = shallowRef<UserSettings | null>(null)
  const isLoadingPresets = shallowRef(false)
  const isLoadingSettings = shallowRef(false)
  const isSaving = shallowRef(false)
  const settingsError = shallowRef('')

  async function loadPresets(): Promise<void> {
    isLoadingPresets.value = true
    settingsError.value = ''
    try {
      const response = await get<PersonalityPreset[]>('/api/settings/presets')
      presets.value = response
    } catch (error) {
      console.error('Failed to load presets:', error)
      settingsError.value = '加载性格预设失败'
    } finally {
      isLoadingPresets.value = false
    }
  }

  async function loadSettings(): Promise<void> {
    if (!userId.value) return
    isLoadingSettings.value = true
    settingsError.value = ''
    try {
      const response = await get<UserSettings>(`/api/settings/${userId.value}`)
      currentSettings.value = response
    } catch (error) {
      console.error('Failed to load settings:', error)
      settingsError.value = '加载设置失败'
    } finally {
      isLoadingSettings.value = false
    }
  }

  async function saveSettings(settings: Partial<UserSettings>): Promise<boolean> {
    if (!userId.value) return false
    isSaving.value = true
    settingsError.value = ''
    try {
      await put(`/api/settings/${userId.value}`, settings)
      // Update local state
      if (currentSettings.value) {
        currentSettings.value = { ...currentSettings.value, ...settings }
      }
      return true
    } catch (error) {
      console.error('Failed to save settings:', error)
      settingsError.value = '保存设置失败'
      return false
    } finally {
      isSaving.value = false
    }
  }

  return {
    presets,
    currentSettings,
    isLoadingPresets,
    isLoadingSettings,
    isSaving,
    settingsError,
    loadPresets,
    loadSettings,
    saveSettings,
  }
}
