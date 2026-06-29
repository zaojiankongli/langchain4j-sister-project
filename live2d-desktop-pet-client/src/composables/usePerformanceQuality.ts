import { computed, onMounted, onUnmounted, shallowRef } from 'vue'

export type PerformanceQualityMode = 'full' | 'reduced' | 'hidden'

type WindowWithMatchMedia = Window & {
  matchMedia: (query: string) => MediaQueryList
}

export function usePerformanceQuality() {
  const isPageVisible = shallowRef(true)
  const prefersReducedMotion = shallowRef(false)

  let mediaQuery: MediaQueryList | null = null

  const qualityMode = computed<PerformanceQualityMode>(() => {
    if (!isPageVisible.value) {
      return 'hidden'
    }

    if (prefersReducedMotion.value) {
      return 'reduced'
    }

    return 'full'
  })

  const blurStrength = computed(() => {
    if (qualityMode.value === 'hidden') return 8
    if (qualityMode.value === 'reduced') return 10
    return 12
  })

  const enableRichMotion = computed(() => qualityMode.value === 'full')
  const shouldPauseAmbientMotion = computed(() => qualityMode.value !== 'full')

  function syncVisibility(): void {
    isPageVisible.value = typeof document === 'undefined' ? true : !document.hidden
  }

  function syncReducedMotion(): void {
    prefersReducedMotion.value = mediaQuery?.matches ?? false
  }

  onMounted(() => {
    syncVisibility()

    if (typeof window === 'undefined') {
      return
    }

    const win = window as WindowWithMatchMedia
    mediaQuery = win.matchMedia('(prefers-reduced-motion: reduce)')
    syncReducedMotion()

    document.addEventListener('visibilitychange', syncVisibility)
    mediaQuery.addEventListener('change', syncReducedMotion)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', syncVisibility)
    mediaQuery?.removeEventListener('change', syncReducedMotion)
  })

  return {
    isPageVisible,
    prefersReducedMotion,
    qualityMode,
    blurStrength,
    enableRichMotion,
    shouldPauseAmbientMotion,
  }
}
