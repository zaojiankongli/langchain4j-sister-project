<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { getCurrentWindow } from '@tauri-apps/api/window'
import PetSettingsPanel from './PetSettingsPanel.vue'
import { usePetSettings } from '../../composables/usePetSettings'

const petSettings = usePetSettings()

async function closeWindow(): Promise<void> {
  await getCurrentWindow().close()
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    void closeWindow()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  void petSettings.loadSettings()
  void petSettings.loadPresets()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <main class="settings-window-shell">
    <PetSettingsPanel
      :settings="petSettings.currentSettings.value"
      :is-saving="petSettings.isSaving.value"
      :settings-error="petSettings.settingsError.value"
      :presets="petSettings.presets.value"
      @save="petSettings.saveSettings"
    />
  </main>
</template>

<style scoped>
.settings-window-shell {
  min-height: 100svh;
  background: rgba(25, 23, 31, 0.98);
}
</style>
