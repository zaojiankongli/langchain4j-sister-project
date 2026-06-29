import { computed, watch, type ShallowRef } from 'vue'
import type { PetRuntimeState } from './usePetRuntimeState'
import { usePetRealtimeAudioStream } from './usePetRealtimeAudioStream'

interface UsePetRealtimeChatModuleOptions {
  authToken: ShallowRef<string>
  socketStatus: ShallowRef<string>
  petRuntimeState: ShallowRef<PetRuntimeState>
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
  startRealtimeSession: () => boolean
  sendAudioChunk: (audioBase64: string) => boolean
  stopRealtimeSession: () => boolean
}

export function usePetRealtimeChatModule(options: UsePetRealtimeChatModuleOptions) {
  const {
    authToken,
    socketStatus,
    petRuntimeState,
    showToast,
    startRealtimeSession,
    sendAudioChunk,
    stopRealtimeSession,
  } = options

  const realtimeAudioStream = usePetRealtimeAudioStream({
    socketStatus,
    startRealtimeSession,
    sendAudioChunk,
    stopRealtimeSession,
  })

  watch(realtimeAudioStream.micError, (error) => {
    if (error) {
      showToast(`实时语音不可用：${error}`, 'error')
    }
  })

  watch(petRuntimeState, (state) => {
    if (state === 'error') {
      realtimeAudioStream.stop()
    }
  })

  const canToggleRealtime = computed(() => {
    return authToken.value.trim().length > 0 && socketStatus.value === 'connected'
  })

  async function toggleRealtimeAudio(): Promise<void> {
    if (realtimeAudioStream.isStreaming.value) {
      realtimeAudioStream.stop()
      return
    }
    const started = await realtimeAudioStream.start()
    if (!started) {
      return
    }
  }

  return {
    realtimeAudioStream,
    canToggleRealtime,
    toggleRealtimeAudio,
  }
}
