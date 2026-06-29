<script setup lang="ts">
import type { LocalTrack } from '../../../composables/usePetMusicLibrary'

defineProps<{
  tracks: readonly LocalTrack[]
  isScanning: boolean
  activeTrackId: string | null
}>()

defineEmits<{
  playTrack: [trackId: string]
}>()
</script>

<template>
  <section class="track-list-section">
    <div v-if="isScanning" class="panel-loading">
      <span class="spinner" aria-hidden="true" />
      <span>扫描中...</span>
    </div>

    <div v-else-if="tracks.length === 0" class="panel-empty">
      <p class="panel-empty-text">还没有可播放的音乐</p>
      <p class="panel-empty-hint">选择本地目录后会自动扫描音频文件</p>
    </div>

    <div v-else class="track-list" role="list">
      <button
        v-for="(track, index) in tracks"
        :key="track.id"
        class="track-item"
        :class="{ 'track-item--active': activeTrackId === track.id }"
        type="button"
        @click="$emit('playTrack', track.id)"
      >
        <span class="track-index">{{ index + 1 }}</span>
        <span class="track-dot">、</span>
        <span class="track-name">{{ track.title }}-{{ track.artist }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.track-list-section {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.track-list {
  display: flex;
  flex-direction: column;
  gap: 0.04rem;
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.track-item {
  display: flex;
  align-items: center;
  gap: 0.15rem;
  padding: 0.32rem 0.2rem;
  background: transparent;
  border: none;
  border-radius: 0.3rem;
  color: rgba(230, 238, 255, 0.7);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: color 120ms ease, background 120ms ease;
}

.track-item:hover {
  color: #fff7fb;
  background: rgba(255, 255, 255, 0.05);
}

.track-item--active {
  color: #fff7fb;
  background: rgba(255, 255, 255, 0.08);
}

.track-item--active .track-index {
  color: rgba(110, 207, 255, 0.9);
}

.track-index {
  color: rgba(215, 231, 255, 0.4);
  font-size: 0.72rem;
  min-width: 1rem;
  flex-shrink: 0;
  text-align: right;
}

.track-dot {
  color: rgba(215, 231, 255, 0.2);
  font-size: 0.72rem;
  flex-shrink: 0;
}

.track-name {
  font-size: 0.78rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}

.panel-loading,
.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  flex: 1;
  min-height: 8rem;
  color: rgba(255, 236, 244, 0.45);
  font-size: 0.8rem;
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid rgba(255, 244, 238, 0.18);
  border-top-color: #ffc7da;
  border-radius: 50%;
  animation: music-spin 0.6s linear infinite;
}

@keyframes music-spin {
  to { transform: rotate(360deg); }
}
</style>
