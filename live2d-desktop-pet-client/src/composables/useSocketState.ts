import { computed, shallowRef } from 'vue'
import type { PetSocketStatus } from '../ws/petStompClient'
import { useSocketReconnectMeta } from './useSocketReconnectMeta'

export function useSocketState() {
  const socketStatus = shallowRef<PetSocketStatus>('idle')
  const lastSocketError = shallowRef('')
  const { reconnectAttempt, reconnectMaxAttempts, reconnectDelayMs, isReconnecting, reconnectHint } = useSocketReconnectMeta()

  const socketStatusLabel = computed(() => {
    if (socketStatus.value === 'connected') return 'Connected'

    if (socketStatus.value === 'connecting') return 'Connecting'

    if (isReconnecting.value) {
      return 'Reconnecting'
    }

    if (socketStatus.value === 'disconnected') return 'Disconnected'

    if (socketStatus.value === 'error') return 'Connection error'

    return 'Idle'
  })

  const socketStatusVisualState = computed(() => {
    if (socketStatus.value === 'connected') return 'connected'
    if (socketStatus.value === 'connecting' || isReconnecting.value) return 'connecting'
    if (socketStatus.value === 'error') return 'error'
    if (socketStatus.value === 'disconnected') return 'disconnected'
    return 'idle'
  })

  return {
    socketStatus,
    lastSocketError,
    reconnectAttempt,
    reconnectMaxAttempts,
    reconnectDelayMs,
    isReconnecting,
    reconnectHint,
    socketStatusLabel,
    socketStatusVisualState,
  }
}
