import { computed, onUnmounted, shallowRef, watch } from 'vue'
import { convertFileSrc } from '@tauri-apps/api/core'
import { open } from '@tauri-apps/plugin-dialog'
import { readDir, readFile, writeTextFile, readTextFile, exists, remove } from '@tauri-apps/plugin-fs'
import { join, appLocalDataDir } from '@tauri-apps/api/path'
import { formatTrackDuration, readTrackTagsFromBytes } from '../utils/musicMetadata.js'
import { useClientSettings } from './useClientSettings'

export interface LocalTrack {
  id: string
  path: string
  fileName: string
  title: string
  artist: string
  album: string
  ext: string
  durationSeconds: number | null
  durationLabel: string
  coverUrl: string | null
  coverChecked: boolean
  lyricsPath: string | null
  lyrics: string
}

/** 缓存用的数据结构 —— 不含 coverUrl（blob URL 不可持久化） */
interface CachedTrack {
  id: string
  path: string
  fileName: string
  title: string
  artist: string
  album: string
  ext: string
  durationSeconds: number | null
  durationLabel: string
  coverChecked: boolean
  lyricsPath: string | null
  lyrics: string
}

interface LibraryCache {
  directory: string
  scannedAt: number
  tracks: CachedTrack[]
}

const AUDIO_EXTENSIONS = new Set(['.mp3', '.wav', '.ogg', '.m4a'])
const LYRIC_EXTENSIONS = ['.lrc', '.txt'] as const
const CACHE_FILE_NAME = 'music-library-cache.json'

let cacheFilePath: string | null = null

async function getCacheFilePath(): Promise<string> {
  if (cacheFilePath) return cacheFilePath
  const dataDir = await appLocalDataDir()
  cacheFilePath = await join(dataDir, CACHE_FILE_NAME)
  return cacheFilePath
}

function parseTrackName(rawName: string): Pick<LocalTrack, 'title' | 'artist' | 'album'> {
  // 去掉常见的前缀序号：01. / 01 - / 01_ / 1. 等
  const normalized = rawName
    .replace(/[_]+/g, ' ')
    .replace(/^\d{1,3}[\s._-]+/, '')
    .trim()
  const separators = [' - ', ' — ', ' – ', ' —', '- ']

  for (const separator of separators) {
    if (!normalized.includes(separator)) {
      continue
    }

    const [artistPart, ...titleParts] = normalized.split(separator)
    const artist = artistPart.trim()
    const title = titleParts.join(separator).trim()

    if (artist && title) {
      return {
        title,
        artist,
        album: '本地音乐',
      }
    }
  }

  return {
    title: normalized,
    artist: '未知艺术家',
    album: '本地音乐',
  }
}

async function readTrackTags(path: string): Promise<Partial<Pick<LocalTrack, 'title' | 'artist' | 'album'>> & { cover?: { mimeType: string; bytes: Uint8Array } }> {
  try {
    const bytes = await readFile(path)
    return readTrackTagsFromBytes(bytes)
  } catch {
    return {}
  }
}

async function readLyrics(path: string | null): Promise<string> {
  if (!path) return ''
  try {
    return await readTextFile(path)
  } catch {
    return ''
  }
}

function createCoverUrl(cover: { mimeType: string; bytes: Uint8Array } | undefined): string | null {
  if (!cover || cover.bytes.length === 0) {
    return null
  }

  const normalizedBytes = new Uint8Array(cover.bytes)
  return URL.createObjectURL(new Blob([normalizedBytes], { type: cover.mimeType }))
}

async function readTrackDuration(path: string): Promise<number | null> {
  const src = convertFileSrc(path)

  return await new Promise((resolve) => {
    const audio = new Audio()

    const cleanup = () => {
      audio.removeAttribute('src')
      audio.load()
    }

    audio.preload = 'metadata'
    audio.addEventListener('loadedmetadata', () => {
      const duration = Number.isFinite(audio.duration) ? audio.duration : null
      cleanup()
      resolve(duration)
    }, { once: true })
    audio.addEventListener('error', () => {
      cleanup()
      resolve(null)
    }, { once: true })

    audio.src = src
  })
}

async function enrichTrack(track: LocalTrack): Promise<LocalTrack> {
  try {
    const [tags, durationSeconds, lyrics] = await Promise.all([
      readTrackTags(track.path),
      readTrackDuration(track.path),
      readLyrics(track.lyricsPath),
    ])

    return {
      ...track,
      title: tags.title || track.title,
      artist: tags.artist || track.artist,
      album: tags.album || track.album,
      durationSeconds,
      durationLabel: formatTrackDuration(durationSeconds),
      coverUrl: createCoverUrl(tags.cover),
      coverChecked: true,
      lyrics,
    }
  } catch {
    // 单曲 enrich 失败不影响整体，返回原始 track
    return track
  }
}

function getExtension(fileName: string): string {
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex >= 0 ? fileName.slice(dotIndex).toLowerCase() : ''
}

function getBaseName(fileName: string): string {
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex >= 0 ? fileName.slice(0, dotIndex) : fileName
}

function createTrackFromPath(path: string, fileNameWithExt: string, lyricsPath: string | null): LocalTrack | null {
  const ext = getExtension(fileNameWithExt)
  if (!AUDIO_EXTENSIONS.has(ext)) return null

  const fileName = getBaseName(fileNameWithExt)
  const parsed = parseTrackName(fileName)

  return {
    id: path,
    path,
    fileName,
    title: parsed.title,
    artist: parsed.artist,
    album: parsed.album,
    ext,
    durationSeconds: null,
    durationLabel: '--:--',
    coverUrl: null,
    coverChecked: false,
    lyricsPath,
    lyrics: '',
  }
}

async function mapWithConcurrency<T, R>(
  items: readonly T[],
  limit: number,
  mapper: (item: T) => Promise<R>,
): Promise<R[]> {
  const results = new Array<R>(items.length)
  let nextIndex = 0

  async function worker(): Promise<void> {
    while (nextIndex < items.length) {
      const currentIndex = nextIndex
      nextIndex += 1
      results[currentIndex] = await mapper(items[currentIndex])
    }
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, () => worker()))
  return results
}

async function walkDirectory(dir: string): Promise<LocalTrack[]> {
  const entries = await readDir(dir)
  const tracks: LocalTrack[] = []
  const lyricPathsByBaseName = new Map<string, string>()

  for (const entry of entries) {
    if (!entry.isFile) continue
    const ext = getExtension(entry.name)
    if (!LYRIC_EXTENSIONS.includes(ext as typeof LYRIC_EXTENSIONS[number])) continue
    lyricPathsByBaseName.set(getBaseName(entry.name).toLowerCase(), await join(dir, entry.name))
  }

  for (const entry of entries) {
    const fullPath = await join(dir, entry.name)

    if (entry.isDirectory) {
      tracks.push(...await walkDirectory(fullPath))
      continue
    }

    if (!entry.isFile) {
      continue
    }

    const lyricsPath = lyricPathsByBaseName.get(getBaseName(entry.name).toLowerCase()) ?? null
    const track = createTrackFromPath(fullPath, entry.name, lyricsPath)
    if (track) tracks.push(track)
  }

  return tracks.sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'))
}

async function mapSelectedFile(path: string): Promise<LocalTrack | null> {
  const normalized = path.replace(/\\/g, '/')
  const slashIndex = normalized.lastIndexOf('/')
  const fileName = slashIndex >= 0 ? normalized.slice(slashIndex + 1) : normalized
  const basePath = slashIndex >= 0 ? path.slice(0, slashIndex + 1) : ''
  const baseName = getBaseName(fileName)
  let lyricsPath: string | null = null

  for (const ext of LYRIC_EXTENSIONS) {
    const candidate = `${basePath}${baseName}${ext}`
    if (await exists(candidate)) {
      lyricsPath = candidate
      break
    }
  }

  return createTrackFromPath(path, fileName, lyricsPath)
}

/* ── 缓存读写 ── */

async function saveLibraryCache(directory: string, tracks: readonly LocalTrack[]): Promise<void> {
  try {
    const cachePath = await getCacheFilePath()
    const cache: LibraryCache = {
      directory,
      scannedAt: Date.now(),
      tracks: tracks.map((t) => ({
        id: t.id,
        path: t.path,
        fileName: t.fileName,
        title: t.title,
        artist: t.artist,
        album: t.album,
        ext: t.ext,
        durationSeconds: t.durationSeconds,
        durationLabel: t.durationLabel,
        coverChecked: t.coverChecked,
        lyricsPath: t.lyricsPath,
        lyrics: t.lyrics,
      })),
    }
    await writeTextFile(cachePath, JSON.stringify(cache))
  } catch {
    // 缓存写入失败不阻塞正常流程
  }
}

async function loadLibraryCache(directory: string): Promise<LocalTrack[] | null> {
  try {
    const cachePath = await getCacheFilePath()
    const fileExists = await exists(cachePath)
    if (!fileExists) return null

    const raw = await readTextFile(cachePath)
    const cache: LibraryCache = JSON.parse(raw)

    // 只有目录匹配 + 缓存不超过 7 天才使用
    if (cache.directory !== directory) return null
    if (Date.now() - cache.scannedAt > 7 * 24 * 60 * 60 * 1000) return null

    return cache.tracks.map((c) => ({
      ...c,
      coverUrl: null, // 缓存不包含封面，需重新扫描才有
      coverChecked: c.coverChecked ?? false,
      durationSeconds: c.durationSeconds,
      durationLabel: c.durationLabel || formatTrackDuration(c.durationSeconds),
      lyricsPath: c.lyricsPath ?? null,
      lyrics: c.lyrics ?? '',
    }))
  } catch {
    return null
  }
}

async function removeLibraryCache(): Promise<void> {
  try {
    const cachePath = await getCacheFilePath()
    const fileExists = await exists(cachePath)
    if (fileExists) {
      await remove(cachePath)
    }
  } catch {
    // ignore
  }
}

export function usePetMusicLibrary() {
  const { clientSettings, updateClientSettings } = useClientSettings()
  const selectedDirectory = shallowRef<string | null>(clientSettings.value.music.directory)
  const tracks = shallowRef<LocalTrack[]>([])
  const isScanning = shallowRef(false)
  const loadError = shallowRef('')
  const trackCountLabel = computed(() => `${tracks.value.length} 首曲目`)

  function revokeTrackUrls(items: readonly LocalTrack[]): void {
    for (const item of items) {
      if (item.coverUrl) {
        URL.revokeObjectURL(item.coverUrl)
      }
    }
  }

  async function chooseDirectory(): Promise<void> {
    const selected = await open({
      directory: true,
      multiple: false,
      recursive: true,
      title: '选择音乐目录',
    })

    if (!selected || Array.isArray(selected)) {
      return
    }

    selectedDirectory.value = selected
    updateClientSettings({ music: { directory: selected } })
    await scanDirectory(selected)
  }

  async function chooseFiles(): Promise<void> {
    const selected = await open({
      directory: false,
      multiple: true,
      title: '选择音乐文件',
      filters: [{ name: '音频文件', extensions: ['mp3', 'wav', 'ogg', 'm4a'] }],
    })

    if (!selected) return
    const paths = Array.isArray(selected) ? selected : [selected]
    await scanFiles(paths)
  }

  /** 安全替换曲库 —— 自动 revoke 旧封面 Blob URL */
  function replaceTracks(newTracks: LocalTrack[]): void {
    revokeTrackUrls(tracks.value)
    tracks.value = newTracks
  }

  async function scanDirectory(directory: string): Promise<void> {
    // 防重入：如果正在扫描则跳过
    if (isScanning.value) return

    isScanning.value = true
    loadError.value = ''

    // 先尝试从缓存加载（快速路径）
    const cached = await loadLibraryCache(directory)
    if (cached && cached.length > 0) {
      replaceTracks(cached)
      isScanning.value = false
      // 后台静默刷新（补封面等信息）
      void refreshTracksInBackground(directory)
      return
    }

    // 缓存未命中，完整扫描
    try {
      const scannedTracks = await walkDirectory(directory)
      replaceTracks(await mapWithConcurrency(scannedTracks, 4, enrichTrack))
      // 保存到缓存
      void saveLibraryCache(directory, tracks.value)
    } catch (error) {
      replaceTracks([])
      loadError.value = error instanceof Error ? error.message : String(error)
    } finally {
      isScanning.value = false
    }
  }

  async function scanFiles(paths: readonly string[]): Promise<void> {
    if (isScanning.value) return

    isScanning.value = true
    loadError.value = ''
    selectedDirectory.value = null
    updateClientSettings({ music: { directory: null } })

    try {
      const mapped = await Promise.all(paths.map((path) => mapSelectedFile(path)))
      const scannedTracks = mapped.filter((track): track is LocalTrack => track !== null)
      replaceTracks(await mapWithConcurrency(scannedTracks, 4, enrichTrack))
      void removeLibraryCache()
    } catch (error) {
      replaceTracks([])
      loadError.value = error instanceof Error ? error.message : String(error)
    } finally {
      isScanning.value = false
    }
  }

  /** 后台静默刷新曲目信息（补封面等） */
  async function refreshTracksInBackground(directory: string): Promise<void> {
    try {
      const scannedTracks = await walkDirectory(directory)
      // 比对文件名集合，判断是否有变化
      if (scannedTracks.length === tracks.value.length) {
        const scannedIds = new Set(scannedTracks.map((t) => t.id))
        const allMatch = tracks.value.every((t) => scannedIds.has(t.id))
        const missingCoverMetadata = tracks.value.some((track) => !track.coverChecked)
        if (allMatch && !missingCoverMetadata) {
          return
        }
      }
      const enriched = await mapWithConcurrency(scannedTracks, 4, enrichTrack)
      replaceTracks(enriched)
      void saveLibraryCache(directory, tracks.value)
    } catch {
      // 后台静默失败
    }
  }

  function clearLibrary(): void {
    selectedDirectory.value = null
    updateClientSettings({ music: { directory: null } })
    replaceTracks([])
    loadError.value = ''
    void removeLibraryCache()
  }

  // 初始加载：如果有存储的目录，尝试从缓存恢复
  if (selectedDirectory.value) {
    void scanDirectory(selectedDirectory.value)
  }

  watch(() => clientSettings.value.music.directory, (directory) => {
    if (directory === selectedDirectory.value) {
      return
    }

    selectedDirectory.value = directory
    if (directory) {
      void scanDirectory(directory)
      return
    }

    replaceTracks([])
    loadError.value = ''
    void removeLibraryCache()
  })

  onUnmounted(() => {
    revokeTrackUrls(tracks.value)
  })

  return {
    selectedDirectory,
    tracks,
    trackCountLabel,
    isScanning,
    loadError,
    chooseDirectory,
    chooseFiles,
    scanDirectory,
    scanFiles,
    clearLibrary,
  }
}
