import { computed, shallowRef } from 'vue'

export function useSocketReconnectMeta() {
  const reconnectAttempt = shallowRef(0)
  const reconnectMaxAttempts = shallowRef(5)
  const reconnectDelayMs = shallowRef<number | null>(null)

  const isReconnecting = computed(() => reconnectAttempt.value > 0 && reconnectDelayMs.value !== null)

  const reconnectHint = computed(() => {
    if (!isReconnecting.value) {
      return ''
    }

    const delayMs = reconnectDelayMs.value
    if (delayMs === null) {
      return ''
    }

    const seconds = Math.max(1, Math.round(delayMs / 1000))
    return `Reconnecting · retry ${reconnectAttempt.value}/${reconnectMaxAttempts.value} in ${seconds}s`
  })

  return {
    reconnectAttempt,
    reconnectMaxAttempts,
    reconnectDelayMs,
    isReconnecting,
    reconnectHint,
  }
}
