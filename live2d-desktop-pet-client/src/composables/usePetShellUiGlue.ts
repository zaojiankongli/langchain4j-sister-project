import type { Ref, ShallowRef } from 'vue'

interface UsePetShellUiGlueOptions {
  showAuthModal: Ref<boolean>
  showChatHistory: Ref<boolean>
  showMoodHistory: Ref<boolean>
  showSettings: Ref<boolean>
  showMemory: Ref<boolean>
  showMailbox: Ref<boolean>
  showProfile: Ref<boolean>
  authFlow: {
    email: ShallowRef<string>
    code: ShallowRef<string>
    authError: ShallowRef<string>
    sendCode: () => Promise<boolean>
    login: () => Promise<boolean>
    resetAuth: () => void
  }
  chatInput: ShallowRef<string>
  sendChatMessage: (text: string, imageUrl?: string) => void
  debugPanelOpen: ShallowRef<boolean>
  toggleDebugPanel: () => void
  debugPanelRef: Ref<{
    focusPreferredField: (preferToken: boolean) => Promise<void>
  } | null>
  authToken: ShallowRef<string>
  connectSocket: () => void
  disconnectSocket: () => void
  clearPreview: () => void
}

export function usePetShellUiGlue(options: UsePetShellUiGlueOptions) {
  const {
    showAuthModal,
    showChatHistory,
    showMoodHistory,
    showSettings,
    showMemory,
    showMailbox,
    showProfile,
    authFlow,
    chatInput,
    sendChatMessage,
    debugPanelOpen,
    toggleDebugPanel,
    debugPanelRef,
    authToken,
    connectSocket,
    disconnectSocket,
    clearPreview,
  } = options

  function toggleChatHistory(): void {
    showChatHistory.value = !showChatHistory.value
  }

  function toggleMoodHistory(): void {
    showMoodHistory.value = !showMoodHistory.value
  }

  function toggleSettings(): void {
    showSettings.value = !showSettings.value
  }

  function toggleMemory(): void {
    showMemory.value = !showMemory.value
  }

  function toggleMailbox(): void {
    showMailbox.value = !showMailbox.value
  }

  function toggleProfile(): void {
    showProfile.value = !showProfile.value
  }

  function openAuthModal(): void {
    showAuthModal.value = true
  }

  function closeAuthModal(): void {
    showAuthModal.value = false
  }

  function updateAuthEmail(value: string): void {
    authFlow.email.value = value
  }

  function updateAuthCode(value: string): void {
    authFlow.code.value = value
  }

  function clearAuthError(): void {
    authFlow.authError.value = ''
  }

  function onChatSend(text: string, imageUrl?: string): void {
    sendChatMessage(text, imageUrl)
  }

  function sendDebugChat(): void {
    onChatSend(chatInput.value)
  }

  async function openDebugPanel(): Promise<void> {
    if (!debugPanelOpen.value) {
      toggleDebugPanel()
      await debugPanelRef.value?.focusPreferredField(authToken.value.trim().length === 0)
    }
  }

  return {
    toggleChatHistory,
    toggleMoodHistory,
    toggleSettings,
    toggleMemory,
    toggleMailbox,
    toggleProfile,
    openAuthModal,
    closeAuthModal,
    updateAuthEmail,
    updateAuthCode,
    clearAuthError,
    onChatSend,
    sendDebugChat,
    openDebugPanel,
    connectSocket,
    disconnectSocket,
    clearPreview,
    sendCode: authFlow.sendCode,
    login: authFlow.login,
    resetAuth: authFlow.resetAuth,
  }
}
