<script setup lang="ts">
defineProps<{
  audioEnabled: boolean
  audioVolume: number
}>()

const emit = defineEmits<{
  toggleAudioEnabled: []
  updateAudioVolume: [event: Event]
}>()
</script>

<template>
  <section class="settings-section">
    <h3 class="settings-section-title">界面音效</h3>

    <div class="toggle-row">
      <span class="slider-name">启用 UI 音效</span>
      <label class="toggle">
        <input :checked="audioEnabled" type="checkbox" class="toggle-input" @change="emit('toggleAudioEnabled')" />
        <span class="toggle-track"><span class="toggle-thumb" /></span>
      </label>
    </div>

    <div class="slider-row">
      <label class="slider-label">
        <span class="slider-name">音效音量</span>
        <span class="slider-value">{{ audioVolume.toFixed(2) }}</span>
      </label>
      <input
        :value="audioVolume"
        type="range"
        min="0"
        max="1"
        step="0.01"
        class="settings-slider"
        @input="emit('updateAudioVolume', $event)"
      />
    </div>
  </section>
</template>
