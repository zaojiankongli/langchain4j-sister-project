<script setup lang="ts">
import { computed } from 'vue'
import { getCoverPlaceholder, getCoverIcon } from '../../../utils/coverPlaceholders'
import PetMusicProgressBar from './PetMusicProgressBar.vue'

const props = defineProps<{
  trackTitle: string
  trackArtist: string
  trackAlbum: string
  trackCoverUrl: string | null
  playerError: string
  loadError: string
  playing: boolean
  progress: number
  timeLabel: string
  durationLabel: string
}>()

const emit = defineEmits<{
  togglePlay: []
  next: []
  previous: []
  seek: [progress: number]
}>()

const fallbackGradient = computed(() =>
  getCoverPlaceholder(props.trackTitle, props.trackArtist),
)

const fallbackIcon = computed(() => getCoverIcon(props.trackTitle))

const hasCover = computed(() => props.trackCoverUrl !== null && props.trackCoverUrl !== '')
</script>

<template>
  <section class="player-card">
    <!-- 卡片微光扫过 -->
    <span class="card-shimmer" />
    <!-- 左侧：专辑封面 + 黑胶唱片装饰 -->
    <div class="player-cover" :class="{ 'player-cover--playing': playing }">
      <!-- 旋转黑胶底纹（仅播放时可见） -->
      <div class="cover-vinyl" :class="{ 'cover-vinyl--spin': playing }" />
      <img
        v-if="hasCover"
        :src="trackCoverUrl!"
        :alt="trackTitle || '专辑封面'"
        class="player-cover-img"
      />
      <div v-else class="player-cover-fallback" :style="{ background: fallbackGradient }">
        <span class="player-cover-icon">{{ fallbackIcon }}</span>
      </div>
      <!-- 封面外发光 -->
      <div class="cover-glow" :class="{ 'cover-glow--breath': playing }" />
    </div>

    <!-- 右侧：信息 + 控件 — 全部左对齐同一竖线 -->
    <div class="player-info">
      <!-- 错误 -->
      <p v-if="playerError" class="player-error">{{ playerError }}</p>
      <p v-else-if="loadError" class="player-error">{{ loadError }}</p>

      <!-- 歌名（允许换行，最多3行，超出省略） -->
      <h1 class="player-title">{{ trackTitle || '还没有开始播放' }}</h1>

      <!-- 歌手 + 专辑 -->
      <p class="player-artist">{{ trackArtist || '未知艺术家' }}</p>
      <p v-if="trackAlbum && trackAlbum !== '本地音乐'" class="player-album">{{ trackAlbum }}</p>

      <!-- 三个播放按钮 — 参考图风格：侧钮空心白框，主钮实心白圆 -->
      <div class="player-buttons">
        <!-- 上一首：空心白框圆 + 快退图标 -->
        <button type="button" class="btn-transport btn-prev" aria-label="上一首" @click="$emit('previous')" title="上一首">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="12 20 4 12 12 4 12 20" fill="currentColor" />
            <polygon points="20 20 12 12 20 4 20 20" fill="currentColor" />
          </svg>
        </button>
        <!-- 播放/暂停：实心白圆 + 呼吸光环 -->
        <button type="button" class="btn-transport btn-play" :aria-label="playing ? '暂停播放' : '开始播放'" @click="$emit('togglePlay')" :title="playing ? '暂停' : '播放'">
          <span class="btn-play-ring" :class="{ 'btn-play-ring--pulse': playing }" />
          <svg v-if="playing" width="22" height="22" viewBox="0 0 24 24" fill="#2a2a4a">
            <rect x="7.5" y="5" width="3" height="14" rx="1.5" />
            <rect x="13.5" y="5" width="3" height="14" rx="1.5" />
          </svg>
          <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="#2a2a4a">
            <polygon points="7 5 19 12 7 19 7 5" />
          </svg>
        </button>
        <!-- 下一首：空心白框圆 + 快进图标 -->
        <button type="button" class="btn-transport btn-next" aria-label="下一首" @click="$emit('next')" title="下一首">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="12 4 20 12 12 20 12 4" fill="currentColor" />
            <polygon points="4 4 12 12 4 20 4 4" fill="currentColor" />
          </svg>
        </button>
      </div>

      <!-- 进度条 -->
      <div class="player-progress">
        <PetMusicProgressBar :progress="progress" @seek="$emit('seek', $event)" />
        <div class="player-time-row">
          <span class="player-time">{{ timeLabel }}</span>
          <span class="player-time">{{ durationLabel }}</span>
        </div>
      </div>

    </div>
  </section>
</template>

<style scoped>
/* ====== 卡片容器 ====== */
.player-card {
  position: relative;
  display: flex;
  gap: 3rem;
  align-items: center;
  padding: 3rem 4rem;
  border-radius: 1rem;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow:
    0 1.5rem 3rem rgba(0, 0, 0, 0.2),
    0 0 2rem rgba(255, 255, 255, 0.03),
    inset 0 1px 0 rgba(255, 255, 255, 0.03);
  box-sizing: border-box;
  overflow: hidden;
}

/* 卡片顶部柔和的高光扫过 */
.player-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 6%;
  width: 40%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
  pointer-events: none;
}

/* 对角线光斑 — 周期性缓缓扫过卡片 */
.card-shimmer {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  border-radius: inherit;
}

.card-shimmer::after {
  content: '';
  position: absolute;
  top: -80%;
  left: -80%;
  width: 60%;
  height: 260%;
  background: linear-gradient(
    105deg,
    transparent 35%,
    rgba(255, 255, 255, 0.04) 45%,
    rgba(255, 255, 255, 0.07) 50%,
    rgba(255, 255, 255, 0.04) 55%,
    transparent 65%
  );
  transform: translateX(0) rotate(18deg);
  opacity: 0.22;
}

/* ====== 封面 ====== */
.player-cover {
  position: relative;
  flex-shrink: 0;
  width: 260px;
  height: 260px;
  border-radius: 0.5rem;
  overflow: visible;
}

.player-cover-img {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 0.5rem;
  box-shadow: 0 0.8rem 2.4rem rgba(0, 0, 0, 0.45);
}

.player-cover-fallback {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  border-radius: 0.5rem;
  box-shadow: 0 0.8rem 2.4rem rgba(0, 0, 0, 0.45);
}

.player-cover-icon {
  font-size: 4rem;
  filter: drop-shadow(0 0.4rem 0.6rem rgba(0, 0, 0, 0.35));
}

/* 黑胶唱片底纹 — 封面背后，播放时旋转 */
.cover-vinyl {
  position: absolute;
  inset: -12%;
  z-index: 0;
  border-radius: 50%;
  background: conic-gradient(
    from 0deg,
    rgba(40, 42, 58, 0.6) 0deg 4deg,
    rgba(60, 63, 78, 0.45) 4deg 6deg,
    rgba(30, 32, 48, 0.7) 6deg 8deg,
    rgba(52, 55, 70, 0.5) 8deg 10deg,
    rgba(40, 42, 58, 0.6) 10deg 360deg
  );
  box-shadow: 0 0 1.5rem rgba(0, 0, 0, 0.3);
  opacity: 0;
  transition: opacity 0.4s ease;
}

.player-cover--playing .cover-vinyl {
  opacity: 1;
}

.cover-vinyl--spin {
  animation: vinyl-spin 4s linear infinite;
}

/* 封面外发光呼吸 */
.cover-glow {
  position: absolute;
  inset: -8px;
  z-index: -1;
  border-radius: 0.75rem;
  background: radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.12), transparent 70%);
  opacity: 0;
  transition: opacity 0.5s ease;
  pointer-events: none;
}

.cover-glow--breath {
  opacity: 1;
  animation: cover-breathe 8s ease-in-out infinite;
}

@keyframes vinyl-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes cover-breathe {
  0%, 100% { opacity: 0.28; }
  50% { opacity: 0.38; }
}

/* ====== 右侧信息区 ====== */
.player-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.3rem;
}

.player-error {
  margin: 0;
  color: rgba(255, 160, 160, 0.9);
  font-size: 0.75rem;
}

/* 歌名 — 允许换行，最多 3 行，超长省略 */
.player-title {
  margin: 0;
  color: #fff7fb;
  font-size: clamp(1.7rem, 2.5vw, 2.4rem);
  font-weight: 600;
  line-height: 1.25;
  letter-spacing: 0.02em;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  word-break: break-word;
}

/* 歌手 */
.player-artist {
  margin: 0;
  font-size: 0.9rem;
  color: rgba(220, 229, 244, 0.7);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 专辑 */
.player-album {
  margin: 0;
  font-size: 0.78rem;
  color: rgba(220, 229, 244, 0.45);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ====== 按钮组 — 播放按钮中轴与上方歌名文字中轴对齐 ====== */
.player-buttons {
  display: flex;
  align-items: center;
  gap: 1.2rem;
  margin: 0.5rem 0;
  padding-left: 2.5rem;
}

/* 公共 transport 按钮 */
.btn-transport {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 180ms ease, background-color 180ms ease, color 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
  flex-shrink: 0;
}

/* 侧钮 — 空心白框圆（参考图风格） */
.btn-prev,
.btn-next {
  width: 2.8rem;
  height: 2.8rem;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.65);
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  padding: 0;
}

.btn-prev:hover,
.btn-next:hover {
  border-color: #fff;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  transform: scale(1.1);
}

/* 主钮 — 实心白圆（参考图风格） */
.btn-play {
  position: relative;
  width: 4rem;
  height: 4rem;
  border-radius: 50%;
  border: none;
  background: #fff;
  padding: 0;
  box-shadow: 0 0.3rem 1.2rem rgba(0, 0, 0, 0.35);
  z-index: 0;
}

.btn-play:hover {
  transform: scale(1.08);
  box-shadow: 0 0.4rem 1.6rem rgba(0, 0, 0, 0.45);
}

/* 播放按钮呼吸光环 */
.btn-play-ring {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.3);
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.btn-play-ring--pulse {
  opacity: 1;
  animation: ring-pulse 2s ease-out infinite;
}

@keyframes ring-pulse {
  0% {
    inset: -4px;
    opacity: 1;
    border-width: 1.5px;
  }
  100% {
    inset: -14px;
    opacity: 0;
    border-width: 0.5px;
  }
}

/* ====== 进度条 ====== */
.player-progress {
  margin-top: 0.3rem;
}

.player-time-row {
  display: flex;
  justify-content: space-between;
  margin-top: 0.15rem;
}

.player-time {
  color: rgba(220, 229, 244, 0.38);
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
}

/* ====== 响应式 ====== */
@media (max-width: 760px) {
  .player-card {
    flex-direction: column;
    gap: 1.5rem;
    padding: 1.5rem 1.5rem;
  }

  .player-cover {
    width: 180px;
    height: 180px;
    align-self: center;
  }

  .player-title {
    font-size: 1.3rem;
    text-align: center;
  }

  .player-artist,
  .player-album {
    text-align: center;
  }

  .player-buttons {
    justify-content: center;
  }
}
</style>
