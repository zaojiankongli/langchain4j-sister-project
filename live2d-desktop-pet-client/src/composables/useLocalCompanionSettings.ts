import { computed } from 'vue'
import { useClientSettings, type LocalCompanionSettings } from './useClientSettings'

export type { LocalCompanionSettings } from './useClientSettings'

export function useLocalCompanionSettings() {
  const { clientSettings, updateClientSettings } = useClientSettings()
  const localCompanionSettings = computed(() => clientSettings.value.localCompanion)

  function updateLocalCompanionSettings(patch: Partial<LocalCompanionSettings>): void {
    updateClientSettings({ localCompanion: patch })
  }

  return {
    localCompanionSettings,
    updateLocalCompanionSettings,
  }
}
