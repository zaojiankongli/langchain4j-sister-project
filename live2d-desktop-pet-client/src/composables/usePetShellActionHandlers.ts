import type { ShallowRef } from 'vue'
import type { CaptureStatus } from './usePetScreenshot'
import type { ReturnTypeUseAuthFlow } from './usePetShellBootstrap.types'

interface UsePetShellActionHandlersOptions {
  // deprecated: streamText removed — single source of truth is chatMessages
  petRuntimeState: ShallowRef<'idle' | 'thinking' | 'speaking' | 'settling' | 'listening' | 'error'>
  captureStatus: ShallowRef<CaptureStatus>
  takeScreenshot: () => Promise<void>
  startGifRecording: () => void
  stopGifRecording: () => Promise<void>
  panelMenuClick: (id: string, showToast: (msg: string, type: 'success' | 'error' | 'info') => void) => void
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
  disconnectSocket: () => void
  clearAuthTokens: () => void
  authFlow: ReturnTypeUseAuthFlow & { resetAuth: () => void }
  chatMessages: {
    clearStream: () => void
    clearMessages: () => void
  }
  closeAllPanels: () => void
}

export function usePetShellActionHandlers(options: UsePetShellActionHandlersOptions) {
  const {
    petRuntimeState,
    captureStatus,
    takeScreenshot,
    startGifRecording,
    stopGifRecording,
    panelMenuClick,
    showToast,
    disconnectSocket,
    clearAuthTokens,
    authFlow,
    chatMessages,
    closeAllPanels,
  } = options

  function clearPreview() {
    // streamText clear removed — single source of truth is chatMessages
    chatMessages.clearStream()
    if (petRuntimeState.value === 'speaking' || petRuntimeState.value === 'settling') {
      petRuntimeState.value = 'idle'
    }
  }

  function handleRadialMenuClick(id: string): void {
    if (id === 'screenshot') {
      takeScreenshot()
        .then(() => showToast('截图已保存', 'success'))
        .catch(() => showToast('截图失败', 'error'))
      return
    }

    if (id === 'gif-record') {
      if (captureStatus.value === 'recording') {
        stopGifRecording()
          .then(() => showToast('GIF 已保存', 'success'))
          .catch(() => showToast('GIF 保存失败', 'error'))
      } else {
        startGifRecording()
        showToast('开始录制 GIF…', 'info')
      }
      return
    }

    panelMenuClick(id, showToast)
  }

  function handleLogout(): void {
    disconnectSocket()
    clearAuthTokens()
    authFlow.resetAuth()
    authFlow.isAuthenticated.value = false
    chatMessages.clearMessages()
    closeAllPanels()
    showToast('已登出', 'success')
  }

  return {
    clearPreview,
    handleRadialMenuClick,
    handleLogout,
  }
}
