import { computed, type ShallowRef } from 'vue'
import type { useChatMessages } from './useChatMessages'
import { usePetMessageModeChatModule } from './usePetMessageModeChatModule'
import { usePetRealtimeChatModule } from './usePetRealtimeChatModule'
import type { PetRuntimeState } from './usePetRuntimeState'

interface UsePetChatModuleOptions {
  authToken: ShallowRef<string>
  socketStatus: ShallowRef<string>
  petRuntimeState: ShallowRef<PetRuntimeState>
  chatMessages: ReturnType<typeof useChatMessages>
  sendChatMessage: (text: string, imageUrl?: string) => boolean
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
  startRealtimeSession: () => boolean
  sendAudioChunk: (audioBase64: string) => boolean
  stopRealtimeSession: () => boolean
}

export function usePetChatModule(options: UsePetChatModuleOptions) {
  const {
    authToken,
    socketStatus,
    petRuntimeState,
    chatMessages,
    sendChatMessage,
    showToast,
    startRealtimeSession,
    sendAudioChunk,
    stopRealtimeSession,
  } = options

  const {
    sendUiChat,
    sendPetInput,
  } = usePetMessageModeChatModule({
    chatMessages,
    sendChatMessage,
    showToast,
  })

  const {
    realtimeAudioStream,
    canToggleRealtime,
    toggleRealtimeAudio,
  } = usePetRealtimeChatModule({
    authToken,
    socketStatus,
    petRuntimeState,
    showToast,
    startRealtimeSession,
    sendAudioChunk,
    stopRealtimeSession,
  })

  const chatLayoutState = computed<'connected' | 'disconnected'>(() => {
    return socketStatus.value === 'connected' ? 'connected' : 'disconnected'
  })

  return {
    realtimeAudioStream,
    canToggleRealtime,
    chatLayoutState,
    toggleRealtimeAudio,
    sendUiChat,
    sendPetInput,
  }
}
