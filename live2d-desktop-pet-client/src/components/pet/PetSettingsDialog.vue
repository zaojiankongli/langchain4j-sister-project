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
  close: []
}>()

const form = usePetSettingsForm({ settings: () => props.settings })

onMounted(() => {
  document.body.style.overflow = 'hidden'
})

onUnmounted(() => {
  document.body.style.overflow = ''
})
</script>

<template>
  <div
    class="settings-overlay"
    tabindex="-1"
    @click.self="emit('close')"
    @keydown.escape="emit('close')"
  >
    <div class="settings-card" role="dialog" aria-modal="true" aria-label="宠物设置" @keydown.tab="handleTabTrap">
      <button
        class="settings-close"
        type="button"
        aria-label="关闭"
        @click="emit('close')"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 18 18"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M4 4L14 14M14 4L4 14"
            stroke="currentColor"
            stroke-width="1.5"
            stroke-linecap="round"
          />
        </svg>
      </button>

      <PetSettingsContent
        :form="form"
        :presets="presets"
        :is-saving="isSaving"
        :settings-error="settingsError"
        @save="emit('save', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.settings-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.settings-card {
  position: relative;
  display: flex;
  flex-direction: column;
  width: min(90vw, 34rem);
  max-height: min(90vh, 42rem);
  padding: var(--space-7) var(--space-6) var(--space-5);
  background: rgba(25, 23, 31, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.settings-close {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  color: var(--color-text-muted);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    background var(--duration-fast) ease;
}

.settings-close:hover {
  color: var(--color-text);
  background: var(--color-surface-subtle);
}
</style>
