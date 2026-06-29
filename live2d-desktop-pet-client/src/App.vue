<script setup lang="ts">
import { computed } from 'vue'
import PetShell from './components/pet/PetShell.vue'
import PetMusicWindowApp from './components/pet/PetMusicWindowApp.vue'
import PetSettingsWindowApp from './components/pet/PetSettingsWindowApp.vue'
import { useDesktopDeepLink } from './composables/useDesktopDeepLink'

const isTauriWindow = computed(() => {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
})

const isMusicWindow = computed(() => {
  return isTauriWindow.value && new URLSearchParams(window.location.search).get('window') === 'music'
})

const isSettingsWindow = computed(() => {
  return isTauriWindow.value && new URLSearchParams(window.location.search).get('window') === 'settings'
})

const isPrimaryPetWindow = computed(() => {
  return isTauriWindow.value && !isMusicWindow.value && !isSettingsWindow.value
})

useDesktopDeepLink({
  enabled: isPrimaryPetWindow,
})
</script>

<template>
  <PetMusicWindowApp v-if="isMusicWindow" />
  <PetSettingsWindowApp v-else-if="isSettingsWindow" />
  <PetShell v-else />
</template>
