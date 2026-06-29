import { computed, type ComputedRef, type ShallowRef } from 'vue'
import type { StateBadge } from '../components/pet/PetStatusStrip.vue'
import type { PetRuntimeState } from './usePetRuntimeState'

interface UsePetStatusBadgesOptions {
  debugPanelOpen: ShallowRef<boolean>
  petRuntimeState: ShallowRef<PetRuntimeState>
  moodLabel: ShallowRef<string>
  musicListeningLabel: ComputedRef<string>
  socketStatusVisualState: ComputedRef<'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'>
  socketStatusLabel: ComputedRef<string>
  status: ShallowRef<'idle' | 'loading' | 'loaded' | 'failed'>
  statusLabel: ComputedRef<string>
}

export function usePetStatusBadges(options: UsePetStatusBadgesOptions) {
  const { debugPanelOpen, petRuntimeState, moodLabel, musicListeningLabel, socketStatusVisualState, socketStatusLabel, status, statusLabel } = options

  const stateBadges = computed<StateBadge[]>(() => {
    const fullBadges: StateBadge[] = [
      {
        label: 'Pet',
        value: petRuntimeState.value,
        tone:
          petRuntimeState.value === 'error'
            ? 'danger'
            : petRuntimeState.value === 'idle'
              ? 'neutral'
              : 'active',
      },
      {
        label: 'Live2D',
        value: statusLabel.value,
        tone: status.value === 'failed' ? 'danger' : status.value === 'loaded' ? 'success' : 'warning',
      },
      {
        label: 'STOMP',
        value: socketStatusLabel.value,
        tone: socketStatusVisualState.value === 'connected' ? 'success' : socketStatusVisualState.value === 'error' ? 'danger' : socketStatusVisualState.value === 'connecting' ? 'warning' : 'neutral',
      },
      {
        label: 'Mood',
        value: moodLabel.value,
        tone: 'active',
      },
      ...(musicListeningLabel.value
        ? [{
            label: 'Music',
            value: musicListeningLabel.value,
            tone: 'active' as const,
          }]
        : []),
    ]

    if (debugPanelOpen.value) {
      return fullBadges
    }

    if (petRuntimeState.value === 'idle' && moodLabel.value === 'neutral') {
      return []
    }

    return fullBadges.filter((badge) => badge.label === 'Pet' || badge.label === 'Mood' || badge.label === 'Music')
  })

  return {
    stateBadges,
  }
}
