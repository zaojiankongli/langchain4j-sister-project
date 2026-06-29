import { computed } from 'vue'
import { convertFileSrc } from '@tauri-apps/api/core'
import { open } from '@tauri-apps/plugin-dialog'
import { useClientSettings, type MusicBackgroundMode } from './useClientSettings'

export type { MusicBackgroundMode } from './useClientSettings'
export type ResolvedMusicBackgroundMode = MusicBackgroundMode | 'cover-unavailable'

interface BackgroundPreset {
  id: Exclude<MusicBackgroundMode, 'custom'>
  label: string
  description: string
  value: string
}

const DEFAULT_BG_IMAGE = '/music_ui_default.jpg'

const PRESETS: BackgroundPreset[] = [
  {
    id: 'preset-dusk',
    label: '薄暮微光',
    description: '使用默认背景图',
    value: `linear-gradient(rgba(0, 0, 0, 0.06), rgba(0, 0, 0, 0.14)), url(${DEFAULT_BG_IMAGE})`,
  },
  {
    id: 'preset-sparkle',
    label: '晨雾轻纱',
    description: '使用默认背景图',
    value: `linear-gradient(rgba(0, 0, 0, 0.03), rgba(0, 0, 0, 0.08)), url(${DEFAULT_BG_IMAGE})`,
  },
  {
    id: 'cover',
    label: '歌曲封面',
    description: '仅在当前歌曲有封面时生效',
    value: '',
  },
]

interface UsePetMusicBackgroundOptions {
  coverUrl?: { value: string | null }
}

export function usePetMusicBackground(options: UsePetMusicBackgroundOptions = {}) {
  const { coverUrl } = options
  const { clientSettings, updateClientSettings } = useClientSettings()
  const mode = computed(() => clientSettings.value.music.background.mode)
  const customPath = computed(() => clientSettings.value.music.background.customPath)
  const overlayOpacity = computed(() => clientSettings.value.music.background.overlayOpacity)
  const resolvedMode = computed<ResolvedMusicBackgroundMode>(() => {
    if (mode.value === 'cover') {
      return coverUrl?.value ? 'cover' : 'cover-unavailable'
    }

    return mode.value
  })

  const backgroundStyle = computed(() => {
    if (mode.value === 'cover' && coverUrl?.value) {
      return {
        backgroundImage: `linear-gradient(rgba(8, 13, 24, ${overlayOpacity.value}), rgba(8, 13, 24, ${Math.min(overlayOpacity.value + 0.18, 0.9)})), ${toCssUrl(coverUrl.value)}`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }
    }

    if (mode.value === 'custom' && customPath.value) {
      const assetUrl = convertFileSrc(customPath.value)
      return {
        backgroundImage: `linear-gradient(rgba(8, 13, 24, ${overlayOpacity.value}), rgba(8, 13, 24, ${Math.min(overlayOpacity.value + 0.18, 0.9)})), ${toCssUrl(assetUrl)}`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }
    }

    const preset = PRESETS.find((item) => item.id === mode.value && item.value) ?? PRESETS[0]
    return {
      backgroundImage: preset.value,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
    }
  })

  const presets = computed(() => PRESETS)

  function selectMode(nextMode: MusicBackgroundMode): void {
    updateClientSettings({ music: { background: { mode: nextMode } } })
  }

  async function chooseCustomBackground(): Promise<void> {
    const selected = await open({
      multiple: false,
      title: '选择播放器背景图',
      filters: [
        {
          name: 'Images',
          extensions: ['png', 'jpg', 'jpeg', 'webp'],
        },
      ],
    })

    if (!selected || Array.isArray(selected)) {
      return
    }

    updateClientSettings({
      music: {
        background: {
          customPath: selected,
          mode: 'custom',
        },
      },
    })
  }

  function setOverlayOpacity(nextOpacity: number): void {
    updateClientSettings({ music: { background: { overlayOpacity: nextOpacity } } })
  }

  return {
    resolvedMode,
    customPath,
    overlayOpacity,
    presets,
    backgroundStyle,
    selectMode,
    chooseCustomBackground,
    setOverlayOpacity,
  }
}

function toCssUrl(url: string): string {
  const safeUrl = url.replace(/"/g, '%22')
  return `url("${safeUrl}")`
}
