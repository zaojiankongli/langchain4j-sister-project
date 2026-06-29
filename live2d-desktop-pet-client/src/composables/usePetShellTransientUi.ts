import { computed, ref, type ShallowRef } from 'vue'

interface UsePetShellTransientUiOptions {
  debugPanelOpen: ShallowRef<boolean>
  showMusic: ShallowRef<boolean>
  showChatHistory: ShallowRef<boolean>
  showSettings: ShallowRef<boolean>
  petInteraction: {
    handleCanvasTap: (x: number, y: number) => void
  }
  startWindowDrag: () => void
  openMusicWindow: () => Promise<unknown>
  openSettingsWindow: () => Promise<unknown>
  openDebugPanel: () => Promise<void>
  focusChatInput: () => void
  handleRadialMenuClick: (id: string) => void
  onPetTap?: () => void
}

export function usePetShellTransientUi(options: UsePetShellTransientUiOptions) {
  const {
    debugPanelOpen,
    showMusic,
    showChatHistory,
    showSettings,
    petInteraction,
    startWindowDrag,
    openMusicWindow,
    openSettingsWindow,
    openDebugPanel,
    focusChatInput,
    handleRadialMenuClick,
    onPetTap,
  } = options

  const menuOpen = ref(false)

  const activePetMenuItem = computed<string | null>(() => {
    if (showChatHistory.value) return 'today'
    if (showMusic.value) return 'music'
    if (showSettings.value) return 'settings'
    return null
  })

  function closeTransientUi(): void {
    menuOpen.value = false
  }

  function handlePetCanvasTap(x: number, y: number): void {
    petInteraction.handleCanvasTap(x, y)
    onPetTap?.()
    if (!debugPanelOpen.value) {
      menuOpen.value = !menuOpen.value
    }
  }

  function handlePetDragIntent(): void {
    if (!debugPanelOpen.value) {
      closeTransientUi()
    }
    void startWindowDrag()
  }

  function handlePetMenuItemClick(id: string): void {
    menuOpen.value = false

    if (id === 'debug') {
      void openDebugPanel()
      return
    }

    if (id === 'chat') {
      focusChatInput()
      return
    }

    if (id === 'music') {
      showMusic.value = false
      void openMusicWindow()
      return
    }

    if (id === 'settings') {
      showSettings.value = false
      void openSettingsWindow()
      return
    }

    handleRadialMenuClick(id)
  }

  return {
    menuOpen,
    activePetMenuItem,
    closeTransientUi,
    handlePetCanvasTap,
    handlePetDragIntent,
    handlePetMenuItemClick,
  }
}
