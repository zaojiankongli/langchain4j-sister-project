import { onUnmounted, shallowRef } from 'vue'

export function usePetToast() {
  const toastMessage = shallowRef('')
  const toastType = shallowRef<'info' | 'success' | 'error'>('info')
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  function showToast(msg: string, type: 'info' | 'success' | 'error' = 'info', duration: number = 3000) {
    if (timeoutId) {
      clearTimeout(timeoutId)
    }
    toastMessage.value = msg
    toastType.value = type
    timeoutId = setTimeout(() => {
      toastMessage.value = ''
    }, duration)
  }

  function clearToast() {
    if (timeoutId) {
      clearTimeout(timeoutId)
      timeoutId = null
    }
    toastMessage.value = ''
  }

  // Prevent stale timer callbacks after component unmount
  onUnmounted(() => {
    if (timeoutId) {
      clearTimeout(timeoutId)
      timeoutId = null
    }
  })

  return {
    toastMessage,
    toastType,
    showToast,
    clearToast,
  }
}
