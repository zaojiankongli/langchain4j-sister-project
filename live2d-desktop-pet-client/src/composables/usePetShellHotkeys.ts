import { onMounted, onUnmounted, type Ref, type ShallowRef } from 'vue'

interface UsePetShellHotkeysOptions {
  debugPanelOpen: ShallowRef<boolean>
  toggleDebugPanel: () => void
  takeScreenshot: () => Promise<void>
  focusChatInput: () => void
  openSettingsWindow: () => Promise<unknown>
  showChatHistory: Ref<boolean>
  showMoodHistory: Ref<boolean>
  showMailbox: Ref<boolean>
  panelEscape: () => void
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
}

export function usePetShellHotkeys(options: UsePetShellHotkeysOptions) {
  const {
    debugPanelOpen,
    toggleDebugPanel,
    takeScreenshot,
    focusChatInput,
    openSettingsWindow,
    showChatHistory,
    showMoodHistory,
    showMailbox,
    panelEscape,
    showToast,
  } = options

  function onWindowKeydown(event: KeyboardEvent) {
    const ctrl = event.ctrlKey || event.metaKey

    if (ctrl && event.key === 's') {
      event.preventDefault()
      takeScreenshot()
        .then(() => showToast('截图已保存', 'success'))
        .catch(() => showToast('截图失败', 'error'))
      return
    }

    if (ctrl && event.key === 'k') {
      event.preventDefault()
      focusChatInput()
      return
    }

    if (ctrl && event.key === ',') {
      event.preventDefault()
      void openSettingsWindow()
      return
    }

    if (ctrl && event.key === 'h') {
      event.preventDefault()
      showChatHistory.value = !showChatHistory.value
      return
    }

    if (ctrl && event.key === 'm') {
      event.preventDefault()
      showMoodHistory.value = !showMoodHistory.value
      return
    }

    if (ctrl && event.key === 'i') {
      event.preventDefault()
      showMailbox.value = !showMailbox.value
      return
    }

    if (event.key === 'Escape') {
      if (debugPanelOpen.value) {
        toggleDebugPanel()
        return
      }

      panelEscape()
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onWindowKeydown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', onWindowKeydown)
  })
}
