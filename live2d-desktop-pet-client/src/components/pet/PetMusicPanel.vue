<script setup lang="ts">
import { computed, ref } from 'vue'
import type { LocalTrack } from '../../composables/usePetMusicLibrary'
import type { MusicBackgroundMode, ResolvedMusicBackgroundMode } from '../../composables/usePetMusicBackground'
import PetMusicLibraryHeader from './music/PetMusicLibraryHeader.vue'
import PetMusicNowPlayingCard from './music/PetMusicNowPlayingCard.vue'
import PetMusicTrackList from './music/PetMusicTrackList.vue'
import PetMusicBackgroundPicker from './music/PetMusicBackgroundPicker.vue'
import PetMusicLyricsPanel from './music/PetMusicLyricsPanel.vue'
import PetMusicCanvasEffects from './music/PetMusicCanvasEffects.vue'
import {
  DEFAULT_MUSIC_EFFECT_MODE,
  cycleMusicEffectMode,
  MUSIC_EFFECT_LABELS,
  MUSIC_EFFECT_STORAGE_KEY,
  type MusicEffectMode,
} from './music/petMusicEffects'

interface LyricLine {
  time: number
  text: string
}

const showAbout = ref(false)
const effectMode = ref<MusicEffectMode>(
  (localStorage.getItem(MUSIC_EFFECT_STORAGE_KEY) as MusicEffectMode | null) ?? DEFAULT_MUSIC_EFFECT_MODE,
)
const effectLabel = computed(() => MUSIC_EFFECT_LABELS[effectMode.value])

function cycleEffect(): void {
  effectMode.value = cycleMusicEffectMode(effectMode.value)
  localStorage.setItem(MUSIC_EFFECT_STORAGE_KEY, effectMode.value)
}

const props = defineProps<{
  tracks: readonly LocalTrack[]
  selectedDirectory: string | null
  isScanning: boolean
  loadError: string
  trackCountLabel: string
  searchQuery: string
  currentTrackTitle: string
  currentTrackArtist: string
  currentTrackAlbum: string
  currentTrackCoverUrl: string | null
  currentTrackId: string | null
  playing: boolean
  playerError: string
  progress: number
  timeLabel: string
  durationLabel: string
  playModeLabel: string
  backgroundVisible: boolean
  resolvedBackgroundMode: ResolvedMusicBackgroundMode
  backgroundPresets: ReadonlyArray<{ id: Exclude<MusicBackgroundMode, 'custom'>; label: string; description: string }>
  customBackgroundPath: string
  overlayOpacity: number
  backgroundStyle: Record<string, string>
  lyricLines: ReadonlyArray<LyricLine>
  currentLyricIndex: number
  moodCaptionTitle: string
  moodCaptionSubtitle: string
}>()

const emit = defineEmits<{
  close: []
  minimize: []
  chooseDirectory: []
  chooseFiles: []
  'update:searchQuery': [value: string]
  togglePlay: []
  next: []
  previous: []
  playTrack: [trackId: string]
  seek: [progress: number]
  cyclePlayMode: []
  toggleBackgroundPicker: []
  closeBackgroundPicker: []
  selectBackgroundMode: [mode: MusicBackgroundMode]
  chooseCustomBackground: []
  updateOverlayOpacity: [value: number]
}>()
</script>

<template>
  <main class="music-scene" :data-effect="effectMode" :style="backgroundStyle">
    <!-- 背景装饰 -->
    <div class="music-scene-backdrop">
      <div class="music-scene-cityline"></div>
      <PetMusicCanvasEffects :effect-mode="effectMode" :playing="props.playing" />
    </div>

    <!-- 主体布局：左歌单 + 右主区 -->
    <div class="music-layout">
      <!-- 左侧歌单 -->
      <aside class="music-sidebar">
        <PetMusicLibraryHeader
          title="歌单"
          subtitle="Playlist"
          section-label="本地音乐"
          :search-query="searchQuery"
          @update:search-query="emit('update:searchQuery', $event)"
          @choose-directory="emit('chooseDirectory')"
          @choose-files="emit('chooseFiles')"
        />

        <div class="sidebar-track-wrap">
          <PetMusicTrackList
            :tracks="tracks"
            :is-scanning="isScanning"
            :active-track-id="currentTrackId"
            @play-track="emit('playTrack', $event)"
          />
        </div>

        <!-- 底部动态播放状态条 -->
        <div class="sidebar-footer">
          <template v-if="playing && currentTrackTitle">
            <div class="sidebar-now-badge">
              <span class="sidebar-now-dot" />
              <span class="sidebar-now-text">正在播放</span>
            </div>
            <p class="sidebar-now-track">{{ currentTrackTitle }}</p>
          </template>
          <template v-else>
            <span class="sidebar-count">{{ trackCountLabel }}</span>
            <span class="sidebar-idle">— 选一首开始 —</span>
          </template>
        </div>
      </aside>

      <!-- 右侧主体区 -->
      <div class="music-main">
        <!-- 大毛玻璃卡片 -->
        <PetMusicNowPlayingCard
          :track-title="currentTrackTitle"
          :track-artist="currentTrackArtist"
          :track-album="currentTrackAlbum"
          :track-cover-url="currentTrackCoverUrl"
          :player-error="playerError"
          :load-error="loadError"
          :playing="playing"
          :progress="progress"
          :time-label="timeLabel"
          :duration-label="durationLabel"
          @toggle-play="emit('togglePlay')"
          @next="emit('next')"
          @previous="emit('previous')"
          @seek="emit('seek', $event)"
        />

        <!-- 底部歌词 -->
        <PetMusicLyricsPanel
          :title="moodCaptionTitle"
          :subtitle="moodCaptionSubtitle"
          :lines="lyricLines"
          :current-index="currentLyricIndex"
        />
      </div>
    </div>

    <!-- 右上角工具栏 -->
    <div class="toolbar">
      <button type="button" class="tool-btn" aria-label="最小化音乐窗口" @click="emit('minimize')" title="最小化">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
      </button>
      <button type="button" class="tool-btn" aria-label="选择音乐目录" @click="emit('chooseDirectory')" title="选择音乐目录">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
        </svg>
      </button>
      <button type="button" class="tool-btn" aria-label="选择音乐文件" @click="emit('chooseFiles')" title="选择音乐文件">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <path d="M9 15h6" />
        </svg>
      </button>
      <button type="button" class="tool-btn" :aria-label="`播放模式：${playModeLabel}`" @click="emit('cyclePlayMode')" :title="playModeLabel">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline v-if="playModeLabel === '顺序播放'" points="8 6 21 6 21 18 8 18 8 6" />
          <template v-else-if="playModeLabel === '随机播放'">
            <polyline points="16 3 21 3 21 8" />
            <line x1="4" y1="20" x2="21" y2="3" />
            <polyline points="21 16 21 21 16 21" />
            <line x1="15" y1="15" x2="21" y2="21" />
            <line x1="4" y1="4" x2="9" y2="9" />
          </template>
          <template v-else-if="playModeLabel === '关闭循环'">
            <polyline points="8 6 21 6 21 18 8 18 8 6" />
            <line x1="3" y1="3" x2="21" y2="21" />
          </template>
          <template v-else>
            <polyline points="17 1 21 5 17 9" />
            <path d="M3 11V9a4 4 0 0 1 4-4h14" />
            <polyline points="7 23 3 19 7 15" />
            <path d="M21 13v2a4 4 0 0 1-4 4H3" />
          </template>
        </svg>
      </button>
      <button
        type="button"
        class="tool-btn tool-btn--effect"
        :aria-label="`切换背景特效：${effectLabel}`"
        @click="cycleEffect"
        :title="`特效：${effectLabel}`"
      >
        <svg v-if="effectMode === 'quiet'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M5 15c2.5-3.5 6.5-3.5 9 0s6.5 3.5 9 0" />
          <path d="M3 11c1.8-2.2 4-3.3 6.5-3.3S14.2 8.8 16 11s4.2 3.3 6 3.3" />
        </svg>
        <svg v-else-if="effectMode === 'rain'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M7 4v16" />
          <path d="M12 3v18" />
          <path d="M17 5v14" />
          <path d="M5 9c1.2 1.8 1.2 3.2 0 5" />
          <path d="M19 8c-1 1.3-1 2.7 0 4" />
        </svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="4.5" />
          <path d="M12 2v3" />
          <path d="M12 19v3" />
          <path d="M2 12h3" />
          <path d="M19 12h3" />
          <path d="M4.5 4.5l2.1 2.1" />
          <path d="M17.4 17.4l2.1 2.1" />
          <path d="M19.5 4.5l-2.1 2.1" />
          <path d="M6.6 17.4l-2.1 2.1" />
        </svg>
      </button>
      <button type="button" class="tool-btn" aria-label="选择自定义背景" @click="emit('chooseCustomBackground')" title="自定义背景">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <polyline points="21 15 16 10 5 21" />
        </svg>
      </button>
      <button type="button" class="tool-btn" aria-label="关于音乐播放器" @click="showAbout = true" title="关于">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="16" x2="12" y2="12" />
          <line x1="12" y1="8" x2="12.01" y2="8" />
        </svg>
      </button>
      <button type="button" class="tool-btn tool-btn--close" aria-label="关闭音乐窗口" @click="emit('close')" title="关闭 (Esc)">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>

    <!-- 背景选择器 -->
    <div class="music-picker-anchor">
      <PetMusicBackgroundPicker
        :visible="backgroundVisible"
        :mode="resolvedBackgroundMode"
        :overlay-opacity="overlayOpacity"
        :presets="backgroundPresets"
        :custom-path="customBackgroundPath"
        @close="emit('closeBackgroundPicker')"
        @select-mode="emit('selectBackgroundMode', $event)"
        @choose-custom-background="emit('chooseCustomBackground')"
        @update-overlay-opacity="emit('updateOverlayOpacity', $event)"
      />
    </div>

    <!-- 关于信息弹窗 -->
    <Teleport to="body">
      <div v-if="showAbout" class="about-backdrop" @click.self="showAbout = false">
        <div class="about-card">
          <h2 class="about-title">桌面宠物 · 音乐播放器</h2>
          <div class="about-body">
            <div class="about-row">
              <span class="about-label">版本</span>
              <span class="about-value">v0.2.0-beta</span>
            </div>
            <div class="about-row">
              <span class="about-label">引擎</span>
              <span class="about-value">Web Audio + Tauri</span>
            </div>
            <div class="about-row">
              <span class="about-label">支持格式</span>
              <span class="about-value">MP3 · WAV · OGG · M4A</span>
            </div>
            <div class="about-row">
              <span class="about-label">曲库路径</span>
              <span class="about-value about-value--path">{{ selectedDirectory || '未选择' }}</span>
            </div>
            <div class="about-row">
              <span class="about-label">已扫描</span>
              <span class="about-value">{{ trackCountLabel }}</span>
            </div>
          </div>
          <button class="about-close" @click="showAbout = false">知道了</button>
        </div>
      </div>
    </Teleport>
  </main>
</template>

<style scoped>
/* ── 基础场景 ── */
.music-scene {
  position: relative;
  min-height: 100svh;
  overflow: hidden;
  isolation: isolate;
}

.music-scene-backdrop {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.music-scene-cityline {
  position: absolute;
}

.music-scene-cityline {
  left: 0;
  right: 0;
  bottom: 0;
  height: 22%;
  background:
    linear-gradient(180deg, transparent, rgba(255, 255, 255, 0.04) 30%, rgba(255, 255, 255, 0.08) 100%),
    linear-gradient(90deg,
      transparent 0 8%, rgba(255, 255, 255, 0.1) 8% 10%, transparent 10% 14%,
      rgba(255, 255, 255, 0.08) 14% 17%, transparent 17% 23%, rgba(255, 255, 255, 0.12) 23% 28%,
      transparent 28% 34%, rgba(255, 255, 255, 0.09) 34% 37%, transparent 37% 46%,
      rgba(255, 255, 255, 0.08) 46% 50%, transparent 50% 59%, rgba(255, 255, 255, 0.12) 59% 65%,
      transparent 65% 73%, rgba(255, 255, 255, 0.1) 73% 77%, transparent 77% 86%,
      rgba(255, 255, 255, 0.12) 86% 91%, transparent 91% 100%);
  opacity: 0.18;
  clip-path: polygon(0 70%, 8% 64%, 13% 68%, 18% 56%, 22% 66%, 31% 52%, 37% 67%, 45% 58%, 53% 69%, 61% 54%, 69% 65%, 76% 57%, 83% 69%, 90% 61%, 100% 72%, 100% 100%, 0 100%);
}

.music-scene[data-effect="rain"] .music-scene-cityline {
  opacity: 0.14;
}

/* ── 主体布局 ── */
.music-layout {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 270px 1fr;
  gap: 2.5rem;
  min-height: 100svh;
  padding: 2rem 2.5rem;
  box-sizing: border-box;
  max-width: 1260px;
  margin: 0 auto;
  align-items: center;
}

/* ── 左侧歌单 ── */
.music-sidebar {
  display: flex;
  flex-direction: column;
  height: calc(100svh - 4rem);
  max-height: 680px;
  padding: 1.2rem 1rem;
  border-radius: 1rem;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.sidebar-track-wrap {
  flex: 1;
  overflow: hidden;
  margin-top: 0.75rem;
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding-top: 0.7rem;
  margin-top: 0.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  min-height: 3rem;
  justify-content: center;
}

/* 正在播放指示 */
.sidebar-now-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.sidebar-now-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4ade80;
  animation: now-dot-pulse 1.4s ease-in-out infinite;
}

@keyframes now-dot-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}

.sidebar-now-text {
  font-size: 0.68rem;
  color: #4ade80;
  letter-spacing: 0.04em;
}

.sidebar-now-track {
  margin: 0;
  font-size: 0.72rem;
  color: rgba(244, 247, 255, 0.65);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.3;
}

.sidebar-count {
  color: rgba(220, 229, 244, 0.3);
  font-size: 0.7rem;
}

.sidebar-idle {
  color: rgba(220, 229, 244, 0.25);
  font-size: 0.68rem;
}

/* ── 右上角工具栏 ── */
.toolbar {
  position: absolute;
  top: 1.5rem;
  right: 2rem;
  z-index: 10;
  display: flex;
  gap: 0.4rem;
}

.tool-btn {
  width: 1.8rem;
  height: 1.8rem;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(220, 229, 244, 0.5);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background-color 140ms ease, color 140ms ease, border-color 140ms ease;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.tool-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  border-color: rgba(255, 255, 255, 0.28);
}

.tool-btn--effect {
  position: relative;
  width: auto;
  padding: 0 0.62rem 0 0.52rem;
  border-radius: 999px;
  gap: 0.38rem;
}

.tool-btn--effect::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(circle at 30% 30%, var(--effect-glow), transparent 58%);
  opacity: 0.9;
  pointer-events: none;
}

.tool-btn--effect-open {
  border-color: color-mix(in srgb, var(--effect-accent) 70%, rgba(255, 255, 255, 0.2));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--effect-accent) 24%, transparent), 0 0.85rem 1.4rem rgba(0, 0, 0, 0.18);
}

.tool-btn--effect .tool-label--effect,
.tool-btn--effect .tool-chevron {
  position: relative;
  z-index: 1;
}

.tool-label--effect {
  font-size: 0.72rem;
  letter-spacing: 0.06em;
}

.tool-chevron {
  font-size: 0.7rem;
  opacity: 0.78;
  transform: translateY(-1px);
}

.tool-label {
  font-size: 0.6rem;
  font-weight: 600;
  margin-left: 1px;
  opacity: 0.8;
}

.tool-btn--close:hover {
  background: rgba(255, 80, 80, 0.25);
  border-color: rgba(255, 100, 100, 0.5);
  color: #ffaaaa;
}

/* ── 关于弹窗 ── */
.about-backdrop {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.about-card {
  background: rgba(30, 33, 50, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 1rem;
  padding: 1.5rem 1.8rem 1.2rem;
  min-width: 280px;
  max-width: 340px;
  box-shadow: 0 1.5rem 3rem rgba(0, 0, 0, 0.4);
  animation: about-in 0.2s ease;
}

@keyframes about-in {
  from { opacity: 0; transform: scale(0.96) translateY(-0.5rem); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.about-title {
  margin: 0 0 1rem;
  font-size: 1rem;
  font-weight: 600;
  color: #fff7fb;
  letter-spacing: 0.02em;
}

.about-body {
  display: grid;
  gap: 0.6rem;
}

.about-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 1rem;
}

.about-label {
  font-size: 0.75rem;
  color: rgba(220, 229, 244, 0.4);
  flex-shrink: 0;
}

.about-value {
  font-size: 0.78rem;
  color: rgba(244, 247, 255, 0.7);
  text-align: right;
}

.about-value--path {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.about-close {
  display: block;
  margin: 1rem auto 0;
  padding: 0.4rem 1.5rem;
  border-radius: 0.4rem;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.06);
  color: rgba(244, 247, 255, 0.7);
  font-size: 0.78rem;
  cursor: pointer;
  transition: background-color 120ms ease, color 120ms ease, border-color 120ms ease;
}

.about-close:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

/* ── 右侧主区 ── */
.music-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
}

/* ── 背景选择器 ── */
.music-picker-anchor {
  position: absolute;
  top: 1rem;
  right: 1.5rem;
  z-index: 10;
}

.music-effect-picker-anchor {
  position: absolute;
  top: 4.8rem;
  right: 2.5rem;
  z-index: 8;
}

@media (prefers-reduced-motion: reduce) {
  .card-shimmer::after,
  .cover-vinyl--spin,
  .cover-glow--breath,
  .btn-play-ring--pulse {
    animation: none !important;
  }
}

/* ── 响应式 ── */
@media (max-width: 900px) {
  .music-layout {
    grid-template-columns: 1fr;
    padding: 1rem;
    gap: 1.2rem;
  }

  .music-sidebar {
    height: auto;
    max-height: 260px;
  }

  .music-main {
    align-items: stretch;
  }
}
</style>
