import { computed, type ShallowRef } from 'vue'

interface UsePetShellDisplayStateOptions {
  status: ShallowRef<'idle' | 'loading' | 'loaded' | 'failed'>
}

export function usePetShellDisplayState(options: UsePetShellDisplayStateOptions) {
  const { status } = options

  const statusLabel = computed(() => {
    if (status.value === 'loaded') return 'Model loaded'
    if (status.value === 'failed') return 'Load failed gracefully'
    if (status.value === 'loading') return 'Loading model'

    return 'Ready'
  })

  return {
    statusLabel,
  }
}
