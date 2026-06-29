<script setup lang="ts">
import type { MusicBackgroundMode, ResolvedMusicBackgroundMode } from '../../../composables/usePetMusicBackground'

defineProps<{
  visible: boolean
  mode: ResolvedMusicBackgroundMode
  overlayOpacity: number
  presets: ReadonlyArray<{ id: Exclude<MusicBackgroundMode, 'custom'>; label: string; description: string }>
  customPath: string
}>()

const emit = defineEmits<{
  close: []
  selectMode: [mode: MusicBackgroundMode]
  chooseCustomBackground: []
  updateOverlayOpacity: [value: number]
}>()
</script>

<template>
  <Transition name="picker-fade">
    <section v-if="visible" class="picker-panel">
      <div class="picker-header">
        <div>
          <p class="picker-kicker">背景设置</p>
          <h3 class="picker-title">为这段回忆换个夜色</h3>
        </div>
        <button class="picker-close" type="button" @click="emit('close')">关闭</button>
      </div>

      <div class="picker-grid">
        <div
          class="picker-status"
          :class="{
            'picker-status--active': mode === 'cover',
            'picker-status--waiting': mode === 'cover-unavailable',
          }"
        >
          {{ mode === 'cover' ? '歌曲封面背景已启用' : mode === 'cover-unavailable' ? '已选择歌曲封面背景，等待带封面的曲目' : '当前使用默认背景图' }}
        </div>

        <button
          v-for="preset in presets"
          :key="preset.id"
          class="picker-option"
          :class="{ 'picker-option--active': mode === preset.id || (mode === 'cover-unavailable' && preset.id === 'cover') }"
          type="button"
          @click="emit('selectMode', preset.id)"
        >
          <span class="picker-option-title">{{ preset.label }}</span>
          <span class="picker-option-desc">{{ preset.description }}</span>
        </button>

        <button
          class="picker-option"
          :class="{ 'picker-option--active': mode === 'custom' }"
          type="button"
          @click="emit('chooseCustomBackground')"
        >
          {{ customPath ? '更换自定义背景' : '选择自定义背景' }}
        </button>
      </div>

      <label class="picker-slider">
        <span class="picker-slider-label">遮罩强度</span>
        <input
          :value="overlayOpacity"
          type="range"
          min="0.2"
          max="0.8"
          step="0.02"
          @input="emit('updateOverlayOpacity', Number(($event.target as HTMLInputElement).value))"
        >
      </label>
    </section>
  </Transition>
</template>

<style scoped>
.picker-panel {
  display: grid;
  gap: 1rem;
  padding: 1.1rem;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 1.55rem;
  background: linear-gradient(180deg, rgba(11, 17, 30, 0.7), rgba(11, 17, 30, 0.58));
  backdrop-filter: blur(22px);
  box-shadow: 0 1.2rem 2.8rem rgba(6, 10, 22, 0.28);
}

.picker-header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
}

.picker-kicker,
.picker-title,
.picker-slider-label {
  margin: 0;
}

.picker-kicker {
  color: rgba(198, 226, 255, 0.72);
  font-size: 0.72rem;
}

.picker-title {
  color: #f4f7ff;
  font-size: 1rem;
}

.picker-grid {
  display: grid;
  gap: 0.7rem;
  grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
}

.picker-status {
  grid-column: 1 / -1;
  border-radius: 1rem;
  padding: 0.78rem 0.9rem;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(222, 236, 255, 0.76);
  font-size: 0.78rem;
}

.picker-status--active {
  background: linear-gradient(135deg, rgba(108, 188, 255, 0.18), rgba(255, 178, 122, 0.12));
  color: #f4f7ff;
}

.picker-status--waiting {
  background: rgba(255, 214, 153, 0.12);
  color: rgba(255, 233, 202, 0.9);
}

.picker-option,
.picker-close {
  border-radius: 1rem;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.08);
  color: #edf4ff;
  cursor: pointer;
}

.picker-option {
  display: grid;
  justify-items: start;
  gap: 0.24rem;
  padding: 0.9rem 0.92rem;
}

.picker-option-title {
  font-weight: 700;
}

.picker-option-desc {
  font-size: 0.7rem;
  opacity: 0.72;
}

.picker-option--active {
  background: linear-gradient(135deg, rgba(132, 198, 255, 0.94), rgba(255, 184, 126, 0.94));
  color: #11213f;
}

.picker-close {
  padding: 0.5rem 0.8rem;
}

.picker-slider {
  display: grid;
  gap: 0.55rem;
}

.picker-slider-label {
  color: rgba(218, 231, 255, 0.78);
  font-size: 0.78rem;
}
</style>
