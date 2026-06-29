<script setup lang="ts">
import { computed, onMounted, onUnmounted, shallowRef, watch } from 'vue'
import { getCurrentWindow } from '@tauri-apps/api/window'
import PetMusicPanel from './PetMusicPanel.vue'
import { usePetMusicLibrary } from '../../composables/usePetMusicLibrary'
import { usePetMusicPlayer } from '../../composables/usePetMusicPlayer'
import { usePetMusicBackground } from '../../composables/usePetMusicBackground'
import { useClientSettings } from '../../composables/useClientSettings'

const petMusicLibrary = usePetMusicLibrary()
const { clientSettings } = useClientSettings()
const petMusicPlayer = usePetMusicPlayer(petMusicLibrary.tracks, clientSettings.value.music.defaultVolume)
const currentTrackCoverUrl = computed(() => petMusicPlayer.currentTrack.value?.coverUrl ?? null)
const petMusicBackground = usePetMusicBackground({ coverUrl: currentTrackCoverUrl })
const searchQuery = shallowRef('')
const debouncedQuery = shallowRef('')
let searchTimer: ReturnType<typeof setTimeout> | null = null
const backgroundPickerOpen = shallowRef(false)

interface LyricLine {
  time: number
  text: string
}

// 搜索防抖 200ms
watch(searchQuery, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    debouncedQuery.value = value
  }, 200)
})

// 预计算小写搜索字段缓存，避免每次过滤重复 toLowerCase()
const trackSearchIndex = computed(() => {
  return petMusicLibrary.tracks.value.map((track) => ({
    track,
    haystack: `${track.title} ${track.artist} ${track.album} ${track.fileName}`.toLowerCase(),
  }))
})

const visibleTracks = computed(() => {
  const query = debouncedQuery.value.trim().toLowerCase()
  if (!query) {
    return petMusicLibrary.tracks.value
  }

  return trackSearchIndex.value
    .filter((entry) => entry.haystack.includes(query))
    .map((entry) => entry.track)
})

const moodCaptionTitle = computed(() => {
  const track = petMusicPlayer.currentTrack.value
  if (!track) {
    return '把今晚的风景留给一首歌。'
  }

  return `这段旋律正在陪你走过 ${track.title}`
})

const moodCaptionSubtitle = computed(() => {
  const track = petMusicPlayer.currentTrack.value
  if (!track) {
    return 'Choose a track and let the night begin.'
  }

  return `${track.artist} · ${track.album}`
})

const lyricLines = computed<LyricLine[]>(() => parseLyricLines(petMusicPlayer.currentTrack.value?.lyrics ?? ''))

const currentLyricLine = computed(() => {
  const lines = lyricLines.value
  if (lines.length === 0) return ''

  const currentTime = petMusicPlayer.currentTime.value
  let activeLine = lines[0]
  for (const line of lines) {
    if (line.time > currentTime) break
    activeLine = line
  }
  return activeLine.text
})

const currentLyricIndex = computed(() => {
  const lines = lyricLines.value
  if (lines.length === 0) return -1

  const currentTime = petMusicPlayer.currentTime.value
  let activeIndex = 0
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].time > currentTime) break
    activeIndex = i
  }
  return activeIndex
})

const captionTitle = computed(() => currentLyricLine.value || moodCaptionTitle.value)
const captionSubtitle = computed(() => {
  if (currentLyricLine.value) return 'Lyrics'
  if (petMusicPlayer.currentTrack.value?.lyrics) return '暂无匹配歌词'
  return moodCaptionSubtitle.value
})

function handlePlayVisibleTrack(trackId: string): void {
  const fullIndex = petMusicLibrary.tracks.value.findIndex((item) => item.id === trackId)
  if (fullIndex >= 0) {
    void petMusicPlayer.playTrack(fullIndex, { restart: petMusicPlayer.currentTrackId.value === trackId })
  }
}

function parseLyricLines(rawLyrics: string): LyricLine[] {
  const lines = rawLyrics
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)

  const parsed: LyricLine[] = []
  const plainLines: string[] = []

  for (const line of lines) {
    const matches = [...line.matchAll(/\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g)]
    const text = line.replace(/\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g, '').trim()
    if (matches.length === 0) {
      plainLines.push(line)
      continue
    }
    if (!text) continue

    for (const match of matches) {
      const minutes = Number(match[1])
      const seconds = Number(match[2])
      const fraction = Number((match[3] ?? '').padEnd(3, '0') || 0) / 1000
      parsed.push({ time: minutes * 60 + seconds + fraction, text })
    }
  }

  if (parsed.length > 0) {
    return parsed.sort((a, b) => a.time - b.time)
  }

  return plainLines.slice(0, 2).map((text, index) => ({ time: index, text }))
}

async function closeWindow(): Promise<void> {
  await getCurrentWindow().close()
}

async function minimizeWindow(): Promise<void> {
  await getCurrentWindow().minimize()
}

function isInteractiveHotkeyTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && target.closest('button,input,textarea,select,a,[role="slider"]') !== null
}

function handleKeydown(e: KeyboardEvent): void {
  if (isInteractiveHotkeyTarget(e.target)) return

  if (e.key === 'Escape') {
    void closeWindow()
  } else if (e.key === ' ' || e.code === 'Space') {
    e.preventDefault()
    void petMusicPlayer.togglePlay()
  } else if (e.key === 'ArrowRight') {
    void petMusicPlayer.nextTrack()
  } else if (e.key === 'ArrowLeft') {
    void petMusicPlayer.previousTrack()
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    petMusicPlayer.setVolume(petMusicPlayer.volume.value + 0.05)
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    petMusicPlayer.setVolume(petMusicPlayer.volume.value - 0.05)
  } else if (e.key === 'm' || e.key === 'M') {
    petMusicPlayer.toggleMute()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  if (petMusicLibrary.selectedDirectory.value) {
    void petMusicLibrary.scanDirectory(petMusicLibrary.selectedDirectory.value)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<template>
  <main class="music-window-shell">
    <PetMusicPanel
      :tracks="visibleTracks"
      :selected-directory="petMusicLibrary.selectedDirectory.value"
      :is-scanning="petMusicLibrary.isScanning.value"
      :load-error="petMusicLibrary.loadError.value"
      :track-count-label="petMusicLibrary.trackCountLabel.value"
      :search-query="searchQuery"
      :current-track-title="petMusicPlayer.currentTrack.value?.title ?? ''"
      :current-track-artist="petMusicPlayer.currentTrack.value?.artist ?? ''"
      :current-track-album="petMusicPlayer.currentTrack.value?.album ?? ''"
      :current-track-cover-url="petMusicPlayer.currentTrack.value?.coverUrl ?? null"
      :current-track-id="petMusicPlayer.currentTrack.value?.id ?? null"
      :playing="petMusicPlayer.playing.value"
      :player-error="petMusicPlayer.playerError.value"
      :progress="petMusicPlayer.progress.value"
      :time-label="petMusicPlayer.timeLabel.value"
      :duration-label="petMusicPlayer.durationLabel.value"
      :play-mode-label="petMusicPlayer.playModeLabel.value"
      :background-visible="backgroundPickerOpen"
      :resolved-background-mode="petMusicBackground.resolvedMode.value"
      :background-presets="petMusicBackground.presets.value"
      :custom-background-path="petMusicBackground.customPath.value"
      :overlay-opacity="petMusicBackground.overlayOpacity.value"
      :background-style="petMusicBackground.backgroundStyle.value"
      :lyric-lines="lyricLines"
      :current-lyric-index="currentLyricIndex"
      :mood-caption-title="captionTitle"
      :mood-caption-subtitle="captionSubtitle"
      @close="closeWindow()"
      @minimize="minimizeWindow()"
      @choose-directory="petMusicLibrary.chooseDirectory()"
      @choose-files="petMusicLibrary.chooseFiles()"
      @update:search-query="searchQuery = $event"
      @toggle-play="petMusicPlayer.togglePlay()"
      @next="petMusicPlayer.nextTrack()"
      @previous="petMusicPlayer.previousTrack()"
      @cycle-play-mode="petMusicPlayer.cyclePlayMode()"
      @play-track="handlePlayVisibleTrack($event)"
      @seek="petMusicPlayer.seek($event)"
      @toggle-background-picker="backgroundPickerOpen = !backgroundPickerOpen"
      @close-background-picker="backgroundPickerOpen = false"
      @select-background-mode="petMusicBackground.selectMode($event)"
      @choose-custom-background="petMusicBackground.chooseCustomBackground()"
      @update-overlay-opacity="petMusicBackground.setOverlayOpacity($event)"
    />
  </main>
</template>

<style scoped>
.music-window-shell {
  min-height: 100svh;
}
</style>
