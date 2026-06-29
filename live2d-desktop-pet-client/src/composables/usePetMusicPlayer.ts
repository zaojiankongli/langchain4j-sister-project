import { computed, onUnmounted, shallowRef, watch, type ShallowRef } from 'vue'
import { convertFileSrc } from '@tauri-apps/api/core'
import type { LocalTrack } from './usePetMusicLibrary'
import { persistSharedMusicPresence } from './useSharedMusicPresence'

export type RepeatMode = 'off' | 'list' | 'single'

const STORAGE_KEYS = {
  volume: 'desktop-pet.music.volume',
  shuffle: 'desktop-pet.music.shuffle',
  repeatMode: 'desktop-pet.music.repeat-mode',
  index: 'desktop-pet.music.index',
  currentTrackId: 'desktop-pet.music.current-track-id',
  currentTime: 'desktop-pet.music.current-time',
} as const

export function usePetMusicPlayer(tracks: ShallowRef<LocalTrack[]>, defaultVolume?: number) {
  const currentIndex = shallowRef(Number(localStorage.getItem(STORAGE_KEYS.index) ?? -1))
  const currentTrackId = shallowRef(localStorage.getItem(STORAGE_KEYS.currentTrackId) ?? '')
  const playing = shallowRef(false)
  const volume = shallowRef(Number(localStorage.getItem(STORAGE_KEYS.volume) ?? defaultVolume ?? 0.7))
  const shuffle = shallowRef(localStorage.getItem(STORAGE_KEYS.shuffle) === 'true')
  const repeatMode = shallowRef<RepeatMode>((localStorage.getItem(STORAGE_KEYS.repeatMode) as RepeatMode | null) ?? 'list')
  const playerError = shallowRef('')
  const currentTime = shallowRef(Number(localStorage.getItem(STORAGE_KEYS.currentTime) ?? 0))
  const duration = shallowRef(0)
  const muted = shallowRef(false)
  const audio = typeof Audio !== 'undefined' ? new Audio() : null
  const preloadAudio = typeof Audio !== 'undefined' ? new Audio() : null
  let preloadedIndex = -1
  let persistScheduled = false
  let pendingPersistTimer: ReturnType<typeof setTimeout> | null = null
  const PERSIST_DELAY = 3000
  let shuffleOrder: number[] = []
  let shufflePosition = -1
  let playRequestId = 0

  const currentTrack = computed<LocalTrack | null>(() => {
    if (currentIndex.value < 0 || currentIndex.value >= tracks.value.length) {
      return null
    }
    return tracks.value[currentIndex.value]
  })

  const progress = computed(() => {
    if (duration.value <= 0) {
      return 0
    }

    return Math.min(1, currentTime.value / duration.value)
  })

  const timeLabel = computed(() => formatTime(currentTime.value))
  const durationLabel = computed(() => formatTime(duration.value))

  function persistState(): void {
    persistScheduled = false
    if (pendingPersistTimer) {
      clearTimeout(pendingPersistTimer)
      pendingPersistTimer = null
    }
    localStorage.setItem(STORAGE_KEYS.volume, String(volume.value))
    localStorage.setItem(STORAGE_KEYS.shuffle, String(shuffle.value))
    localStorage.setItem(STORAGE_KEYS.repeatMode, repeatMode.value)
    localStorage.setItem(STORAGE_KEYS.index, String(currentIndex.value))
    localStorage.setItem(STORAGE_KEYS.currentTrackId, currentTrackId.value)
    localStorage.setItem(STORAGE_KEYS.currentTime, String(currentTime.value))
  }

  /** 空闲时持久化 —— timeupdate 调用，不阻塞渲染 */
  function schedulePersist(): void {
    if (persistScheduled) return
    persistScheduled = true
    if (typeof requestIdleCallback !== 'undefined') {
      requestIdleCallback(() => persistState(), { timeout: PERSIST_DELAY })
    } else {
      if (pendingPersistTimer) clearTimeout(pendingPersistTimer)
      pendingPersistTimer = setTimeout(persistState, PERSIST_DELAY)
    }
  }

  function publishPresence(): void {
    persistSharedMusicPresence({
      playing: playing.value,
      title: currentTrack.value?.title ?? '',
      artist: currentTrack.value?.artist ?? '',
    })
  }

  async function playTrack(index: number, options: { restart?: boolean } = {}): Promise<void> {
    const track = tracks.value[index]
    if (!audio || !track) return

    const previousTrackId = currentTrack.value?.id ?? null
    const requestId = ++playRequestId
    currentIndex.value = index
    currentTrackId.value = track.id
    playerError.value = ''
    audio.src = convertFileSrc(track.path)
    audio.volume = volume.value
    audio.loop = repeatMode.value === 'single'

    if (!options.restart && previousTrackId === track.id && currentTime.value > 0) {
      audio.currentTime = currentTime.value
    } else {
      currentTime.value = 0
      audio.currentTime = 0
    }

    await attemptPlay(2, requestId)
  }

  /** 带重试的播放 —— AbortError 因快速切歌导致时自动重试 */
  async function attemptPlay(retries: number, requestId: number): Promise<void> {
    if (!audio) return
    try {
      await audio.play()
      if (requestId !== playRequestId) return
      playing.value = true
      persistState()
      publishPresence()
      updateMediaSession()
      updateMediaSessionPlaybackState()
      // 播放稳定后预加载下一首
      const nextIdx = peekNextIndex()
      if (nextIdx >= 0) preloadTrack(nextIdx)
    } catch (error) {
      const errName = error instanceof Error ? error.name : String(error)
      // AbortError 是快速切歌造成的正常现象，重试一次即可
      if (errName === 'AbortError' && retries > 0) {
        await new Promise((r) => setTimeout(r, 50))
        if (requestId !== playRequestId) return
        await attemptPlay(retries - 1, requestId)
        return
      }
      if (requestId !== playRequestId) return
      playing.value = false
      playerError.value = error instanceof Error ? error.message : String(error)
      publishPresence()
    }
  }

  async function togglePlay(): Promise<void> {
    if (!audio) return

    if (playing.value) {
      audio.pause()
      // playing = false 由 'pause' 事件监听统一处理
      return
    }

    if (currentIndex.value >= 0) {
      await playTrack(currentIndex.value)
      return
    }

    if (tracks.value.length > 0) {
      await playTrack(0)
    }
  }

  /** 预加载指定曲目（仅下载音频数据） */
  function preloadTrack(index: number): void {
    if (!preloadAudio || index < 0 || index >= tracks.value.length) return
    if (index === preloadedIndex) return
    preloadAudio.preload = 'auto'
    preloadAudio.src = convertFileSrc(tracks.value[index].path)
    preloadAudio.load()
    preloadedIndex = index
  }

  /** 计算下一首的索引（供预加载用） */
  function peekNextIndex(): number {
    if (tracks.value.length <= 1) return -1
    if (shuffle.value) {
      if (shufflePosition + 1 < shuffleOrder.length) {
        return shuffleOrder[shufflePosition + 1]
      }
      return -1
    }
    return currentIndex.value >= 0
      ? (currentIndex.value + 1) % tracks.value.length
      : 0
  }

  /** 生成新的随机播放顺序（Fisher-Yates 洗牌） */
  function regenerateShuffleOrder(): void {
    const len = tracks.value.length
    shuffleOrder = Array.from({ length: len }, (_, i) => i)
    for (let i = len - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      const temp = shuffleOrder[i]
      shuffleOrder[i] = shuffleOrder[j]
      shuffleOrder[j] = temp
    }
    // 把当前曲目放到最前面，避免重复
    const currentIdx = shuffleOrder.indexOf(currentIndex.value)
    if (currentIdx > 0) {
      shuffleOrder[currentIdx] = shuffleOrder[0]
      shuffleOrder[0] = currentIndex.value
    }
    shufflePosition = 0
  }

  async function nextTrack(): Promise<void> {
    if (tracks.value.length === 0) return

    if (tracks.value.length === 1) {
      await playTrack(0, { restart: true })
      return
    }

    if (shuffle.value && tracks.value.length > 1) {
      if (shuffleOrder.length !== tracks.value.length) {
        regenerateShuffleOrder()
      }
      shufflePosition++
      if (shufflePosition >= shuffleOrder.length) {
        regenerateShuffleOrder()
        shufflePosition = 1
      }
      await playTrack(shuffleOrder[shufflePosition])
      return
    }

    const next = currentIndex.value >= 0 ? (currentIndex.value + 1) % tracks.value.length : 0
    await playTrack(next)
  }

  async function previousTrack(): Promise<void> {
    if (tracks.value.length === 0) return

    if (tracks.value.length === 1) {
      await playTrack(0, { restart: true })
      return
    }

    // 自适应阈值：取 3 秒和总时长 10% 中的较大值
    const threshold = Math.max(3, duration.value * 0.1)
    if (audio && currentTime.value > threshold) {
      seek(0)
      return
    }

    // 随机模式下回退到上一首
    if (shuffle.value && shuffleOrder.length > 0) {
      if (shufflePosition > 0) {
        shufflePosition--
        await playTrack(shuffleOrder[shufflePosition])
      }
      // shufflePosition === 0 说明在随机队列起点，不做操作
      return
    }

    const previous = currentIndex.value > 0 ? currentIndex.value - 1 : tracks.value.length - 1
    await playTrack(previous)
  }

  function setVolume(nextVolume: number): void {
    volume.value = Math.min(1, Math.max(0, nextVolume))
    if (audio) {
      audio.volume = volume.value
    }
    persistState()
  }

  function toggleMute(): void {
    muted.value = !muted.value
    if (audio) {
      audio.muted = muted.value
    }
    publishPresence()
  }

  const playModeLabel = computed<string>(() => {
    if (repeatMode.value === 'single') return '单曲循环'
    if (shuffle.value) return '随机播放'
    if (repeatMode.value === 'off') return '关闭循环'
    return '顺序播放'
  })

  function cyclePlayMode(): void {
    // 顺序 → 随机 → 单曲循环 → 顺序
    if (repeatMode.value === 'single') {
      // 单曲循环 → 顺序
      repeatMode.value = 'list'
      shuffle.value = false
    } else if (shuffle.value) {
      // 随机 → 单曲循环
      shuffle.value = false
      repeatMode.value = 'single'
    } else {
      // 顺序 → 随机
      shuffle.value = true
      repeatMode.value = 'list'
    }

    if (audio) {
      audio.loop = repeatMode.value === 'single'
    }
    persistState()
  }

  function seek(nextProgress: number): void {
    if (!audio || duration.value <= 0) {
      return
    }

    const clamped = Math.min(1, Math.max(0, nextProgress))
    const nextTime = duration.value * clamped
    audio.currentTime = nextTime
    currentTime.value = nextTime
    persistState()
  }

  /** 更新系统媒体会话信息（Windows 任务栏 / 媒体键） */
  function updateMediaSession(): void {
    if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return
    const track = currentTrack.value
    if (!track) return
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artist,
      album: track.album,
    })
    navigator.mediaSession.setActionHandler('play', () => void togglePlay())
    navigator.mediaSession.setActionHandler('pause', () => void togglePlay())
    navigator.mediaSession.setActionHandler('previoustrack', () => void previousTrack())
    navigator.mediaSession.setActionHandler('nexttrack', () => void nextTrack())
  }

  function updateMediaSessionPlaybackState(): void {
    if (typeof navigator === 'undefined' || !('mediaSession' in navigator)) return
    navigator.mediaSession.playbackState = playing.value ? 'playing' : 'paused'
  }

  if (audio) {
    audio.volume = volume.value
    audio.loop = repeatMode.value === 'single'
    audio.addEventListener('ended', () => {
      if (repeatMode.value === 'single') {
        return // loop 由 audio.loop 处理
      }
      if (repeatMode.value === 'list') {
        void nextTrack()
        return
      }
      // repeatMode === 'off'：到末尾就停止
      if (currentIndex.value >= tracks.value.length - 1) {
        playing.value = false
        publishPresence()
        return
      }
      void nextTrack()
    })
    audio.addEventListener('pause', () => {
      playing.value = false
      publishPresence()
      updateMediaSessionPlaybackState()
      persistState()
    })
    audio.addEventListener('play', () => {
      playing.value = true
      publishPresence()
      updateMediaSessionPlaybackState()
    })
    audio.addEventListener('timeupdate', () => {
      currentTime.value = audio.currentTime
      schedulePersist()
    })
    audio.addEventListener('loadedmetadata', () => {
      duration.value = Number.isFinite(audio.duration) ? audio.duration : 0
      if (currentTime.value > 0 && currentTime.value < duration.value) {
        audio.currentTime = currentTime.value
      }
    })
  }

  watch(tracks, (nextTracks) => {
    // 曲库变化时重置随机队列
    shuffleOrder = []
    shufflePosition = -1

    if (nextTracks.length === 0) {
      currentIndex.value = -1
      currentTrackId.value = ''
      playRequestId++
      if (audio) {
        audio.pause()
        audio.src = ''
      }
      playing.value = false
      currentTime.value = 0
      duration.value = 0
      persistState()
      publishPresence()
      return
    }

    if (currentTrackId.value) {
      const restoredIndex = nextTracks.findIndex((track) => track.id === currentTrackId.value)
      if (restoredIndex >= 0) {
        currentIndex.value = restoredIndex
        persistState()
        return
      }

      const missingPersistedTrack = currentTrackId.value && restoredIndex < 0
      if (missingPersistedTrack) {
        currentIndex.value = -1
        currentTrackId.value = ''
        currentTime.value = 0
        duration.value = 0
        if (audio) {
          audio.pause()
          audio.src = ''
        }
        playing.value = false
        persistState()
        publishPresence()
        return
      }
    }

    if (currentIndex.value >= 0 && currentIndex.value < nextTracks.length) {
      currentTrackId.value = nextTracks[currentIndex.value].id
      persistState()
      return
    }

    if (currentIndex.value >= nextTracks.length) {
      currentIndex.value = 0
      currentTrackId.value = nextTracks[0].id
      persistState()
    }
  }, { immediate: true })

  onUnmounted(() => {
    audio?.pause()
    if (pendingPersistTimer) clearTimeout(pendingPersistTimer)
    if (persistScheduled) persistState()
    // 清理 MediaSession
    if (typeof navigator !== 'undefined' && 'mediaSession' in navigator) {
      navigator.mediaSession.metadata = null
      navigator.mediaSession.setActionHandler('play', null)
      navigator.mediaSession.setActionHandler('pause', null)
      navigator.mediaSession.setActionHandler('previoustrack', null)
      navigator.mediaSession.setActionHandler('nexttrack', null)
    }
    persistSharedMusicPresence({ playing: false, title: '', artist: '' })
  })

  return {
    currentTrack,
    currentTrackId,
    playing,
    volume,
    playerError,
    currentTime,
    progress,
    timeLabel,
    durationLabel,
    playTrack,
    togglePlay,
    nextTrack,
    previousTrack,
    setVolume,
    toggleMute,
    cyclePlayMode,
    playModeLabel,
    seek,
  }
}

/** 格式化秒数为 MM:SS —— 注意与 utils/formatTime.ts 的 ISO 时间格式化不同 */
function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) {
    return '00:00'
  }

  const totalSeconds = Math.floor(seconds)
  const minutes = Math.floor(totalSeconds / 60)
  const remainingSeconds = totalSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`
}
