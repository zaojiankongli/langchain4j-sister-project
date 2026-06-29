<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import type { PersonalityPreset, UserSettings } from '../../types/settings'
import { usePetSettingsForm } from '../../composables/usePetSettingsForm'
import { handleTabTrap } from '../../utils/focusTrap'
import PetSettingsContent from './PetSettingsContent.vue'

const props = defineProps<{
  settings: UserSettings | null
  isSaving: boolean
  settingsError: string
  presets?: PersonalityPreset[]
}>()

const emit = defineEmits<{
  save: [settings: Partial<UserSettings>]
}>()

const form = usePetSettingsForm({ settings: () => props.settings })

function handleSave(): void {
  emit('save', form.handleSave())
}

function handleKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    // Window handles its own close.
    return
  }

  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    handleSave()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="settings-panel" tabindex="-1" @keydown.tab="handleTabTrap">
    <PetSettingsContent
      :form="form"
      :presets="presets"
      :is-saving="isSaving"
      :settings-error="settingsError"
      @save="emit('save', $event)"
    />
  </div>
</template>

<style scoped>
.settings-panel {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding: var(--space-7) var(--space-6) var(--space-5);
  background: rgba(25, 23, 31, 0.98);
  color: var(--color-text);
  overflow: hidden;
}
</style>
