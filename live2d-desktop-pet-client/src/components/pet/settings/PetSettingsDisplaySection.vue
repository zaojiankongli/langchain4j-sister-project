<script setup lang="ts">
defineProps<{
  scale: number
  opacity: number
}>()

const emit = defineEmits<{
  updateScale: [event: Event]
  updateOpacity: [event: Event]
}>()
</script>

<template>
  <section class="settings-section">
    <h3 class="settings-section-title">桌宠外观</h3>

    <div class="slider-row">
      <label class="slider-label">
        <span class="slider-name">缩放</span>
        <span class="slider-value">{{ scale.toFixed(2) }}</span>
      </label>
      <input
        :value="scale"
        type="range"
        min="0.5"
        max="1.5"
        step="0.01"
        class="settings-slider"
        @input="emit('updateScale', $event)"
      />
    </div>

    <div class="slider-row">
      <label class="slider-label">
        <span class="slider-name">不透明度</span>
        <span class="slider-value">{{ opacity.toFixed(2) }}</span>
      </label>
      <input
        :value="opacity"
        type="range"
        min="0.3"
        max="1.0"
        step="0.01"
        class="settings-slider"
        @input="emit('updateOpacity', $event)"
      />
    </div>
  </section>
</template>
