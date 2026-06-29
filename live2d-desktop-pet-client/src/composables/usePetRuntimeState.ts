import { shallowRef } from 'vue'

export type PetRuntimeState = 'idle' | 'thinking' | 'speaking' | 'settling' | 'listening' | 'error'

export function usePetRuntimeState() {
  const moodLabel = shallowRef('neutral')
  const petRuntimeState = shallowRef<PetRuntimeState>('idle')
  const lastSemanticEvent = shallowRef('Waiting for semantic events')
  const moodDescription = shallowRef('')
  const pleasure = shallowRef(0)
  const arousal = shallowRef(0.5)
  const dominance = shallowRef(0)

  return {
    moodLabel,
    petRuntimeState,
    lastSemanticEvent,
    moodDescription,
    pleasure,
    arousal,
    dominance,
  }
}
