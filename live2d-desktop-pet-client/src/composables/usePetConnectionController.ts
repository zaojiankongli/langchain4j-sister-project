import type { ShallowRef } from 'vue'
import { connectPetSocket, disconnectPetSocket, sendPetChat, type PetSocketInboundEvent } from '../ws/petStompClient'
import type { Live2DLogEntry } from './usePetDebugLog'
import type { PetRuntimeState } from './usePetRuntimeState'
import type { PetSocketStatus } from '../ws/petStompClient'

interface UsePetConnectionControllerOptions {
  authToken: ShallowRef<string>
  // deprecated: streamText removed — single source of truth is chatMessages
  activeModelPath: ShallowRef<string>
  socketStatus: ShallowRef<PetSocketStatus>
  lastSocketError: ShallowRef<string>
  reconnectAttempt: ShallowRef<number>
  reconnectMaxAttempts: ShallowRef<number>
  reconnectDelayMs: ShallowRef<number | null>
  petRuntimeState: ShallowRef<PetRuntimeState>
  lastSemanticEvent: ShallowRef<string>
  enableAudio: ShallowRef<boolean>
  appendLog: (entry: Omit<Live2DLogEntry, 'time' | 'renderer'>) => void
  handleSocketEvent: (event: PetSocketInboundEvent) => void
  onClearStream?: () => void
}

export function usePetConnectionController(options: UsePetConnectionControllerOptions) {
  const {
    authToken,
    activeModelPath,
    socketStatus,
    lastSocketError,
    reconnectAttempt,
    reconnectMaxAttempts,
    reconnectDelayMs,
    petRuntimeState,
    lastSemanticEvent,
    enableAudio,
    appendLog,
    handleSocketEvent,
    onClearStream,
  } = options

  function connectSocket() {
    if (!authToken.value.trim()) {
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: 'Missing Bearer token for STOMP connection',
      })
      return
    }

    if (socketStatus.value === 'connecting' || socketStatus.value === 'connected') {
      return
    }

    // streamText clear removed — single source of truth is chatMessages
    onClearStream?.()
    lastSocketError.value = ''
    connectPetSocket(() => authToken.value, {
      onStatusChange: (nextStatus) => {
        socketStatus.value = nextStatus
        if (nextStatus === 'error') {
          petRuntimeState.value = 'error'
        }
        if (nextStatus === 'connected' && petRuntimeState.value === 'error') {
          petRuntimeState.value = 'idle'
        }
        appendLog({
          event: 'socket:status',
          modelPath: activeModelPath.value,
          message: nextStatus,
        })
      },
      onReconnectMetaChange: (meta) => {
        reconnectAttempt.value = meta.attempt
        reconnectMaxAttempts.value = meta.maxAttempts
        reconnectDelayMs.value = meta.nextDelayMs
      },
      onMessage: (event) => {
        handleSocketEvent(event)
      },
      onError: (message) => {
        lastSocketError.value = message
        petRuntimeState.value = 'error'
        appendLog({
          event: 'socket:error',
          modelPath: activeModelPath.value,
          message,
        })
      },
    })
  }

  function disconnectSocket() {
    if (socketStatus.value !== 'connected' && socketStatus.value !== 'connecting') {
      return
    }

    petRuntimeState.value = 'idle'
    disconnectPetSocket()
  }

  function sendChatMessage(chatInput: string, imageUrl?: string) {
    const trimmedMessage = chatInput.trim()

    if (!trimmedMessage && !imageUrl) {
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: 'Chat input is empty',
      })
      return false
    }

    if (petRuntimeState.value === 'thinking' || petRuntimeState.value === 'speaking' || petRuntimeState.value === 'settling' || petRuntimeState.value === 'listening') {
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: 'Reply is still in progress',
      })
      return false
    }

    if (!sendPetChat(trimmedMessage, enableAudio.value, imageUrl)) {
      petRuntimeState.value = 'error'
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: 'STOMP client is not connected',
      })
      return false
    }

    // streamText clear removed — single source of truth is chatMessages
    onClearStream?.()
    petRuntimeState.value = 'thinking'
    lastSemanticEvent.value = imageUrl ? 'chat:image-sent' : 'chat:sent'
    return true
  }

  return {
    connectSocket,
    disconnectSocket,
    sendChatMessage,
  }
}
