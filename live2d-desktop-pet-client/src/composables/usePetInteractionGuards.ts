import { computed, type ShallowRef } from 'vue'
import type { PetRuntimeState } from './usePetRuntimeState'
import type { PetSocketStatus } from '../ws/petStompClient'

interface UsePetInteractionGuardsOptions {
  authToken: ShallowRef<string>
  chatInput: ShallowRef<string>
  socketStatus: ShallowRef<PetSocketStatus>
  isSocketReconnecting: ShallowRef<boolean>
  petRuntimeState: ShallowRef<PetRuntimeState>
}

export function usePetInteractionGuards(options: UsePetInteractionGuardsOptions) {
  const { authToken, chatInput, socketStatus, isSocketReconnecting, petRuntimeState } = options

  const canSendChat = computed(
    () =>
      socketStatus.value === 'connected' &&
      chatInput.value.trim().length > 0 &&
      petRuntimeState.value !== 'thinking' &&
      petRuntimeState.value !== 'speaking' &&
      petRuntimeState.value !== 'settling',
  )

  const canConnectSocket = computed(
    () =>
      authToken.value.trim().length > 0 &&
      !isSocketReconnecting.value &&
      (socketStatus.value === 'idle' || socketStatus.value === 'disconnected' || socketStatus.value === 'error'),
  )

  const canDisconnectSocket = computed(
    () => socketStatus.value === 'connected' || socketStatus.value === 'connecting' || isSocketReconnecting.value,
  )

  return {
    canSendChat,
    canConnectSocket,
    canDisconnectSocket,
  }
}
