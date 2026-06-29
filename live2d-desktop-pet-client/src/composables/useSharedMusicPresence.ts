import { computed, onMounted, onUnmounted, shallowRef } from 'vue'

export interface SharedMusicPresence {
  playing: boolean
  title: string
  artist: string
}

const STORAGE_KEY = 'desktop-pet.music.presence'

function readPresence(): SharedMusicPresence {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return {
      playing: false,
      title: '',
      artist: '',
    }
  }

  try {
    const parsed = JSON.parse(raw) as Partial<SharedMusicPresence>
    return {
      playing: parsed.playing === true,
      title: typeof parsed.title === 'string' ? parsed.title : '',
      artist: typeof parsed.artist === 'string' ? parsed.artist : '',
    }
  } catch {
    return {
      playing: false,
      title: '',
      artist: '',
    }
  }
}

export function persistSharedMusicPresence(presence: SharedMusicPresence): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(presence))
}

export function useSharedMusicPresence() {
  const presence = shallowRef<SharedMusicPresence>(readPresence())

  function syncPresence(): void {
    presence.value = readPresence()
  }

  function handleStorage(event: StorageEvent): void {
    if (event.key === STORAGE_KEY) {
      syncPresence()
    }
  }

  onMounted(() => {
    window.addEventListener('storage', handleStorage)
  })

  onUnmounted(() => {
    window.removeEventListener('storage', handleStorage)
  })

  const listeningLabel = computed(() => {
    if (!presence.value.playing) {
      return ''
    }

    return presence.value.artist
      ? `${presence.value.title} · ${presence.value.artist}`
      : presence.value.title
  })

  return {
    presence,
    listeningLabel,
    syncPresence,
  }
}
