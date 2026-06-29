import { ref, watch } from 'vue'
import type { useChatMessages } from './useChatMessages'
import type { useMoodHistory } from './useMoodHistory'
import type { usePetMemory } from './usePetMemory'
import type { usePetMailbox } from './usePetMailbox'
import type { useUserProfile } from './useUserProfile'
import type { useRecommendations } from './useRecommendations'

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export interface PanelDeps {
  chatMessages: ReturnType<typeof useChatMessages>
  moodHistory: ReturnType<typeof useMoodHistory>
  petMemory: ReturnType<typeof usePetMemory>
  petMailbox: ReturnType<typeof usePetMailbox>
  userProfile: ReturnType<typeof useUserProfile>
  petRecommendations: ReturnType<typeof useRecommendations>
}

/* ------------------------------------------------------------------ */
/*  Composable                                                         */
/* ------------------------------------------------------------------ */

/**
 * Manages all panel visibility state, dedup-loading watchers,
 * radial menu routing, and Escape key cascade.
 *
 * Extracted from PetShell to reduce orchestrator size and improve
 * testability of panel lifecycle logic.
 */
export function usePetPanels(deps: PanelDeps) {
  const { chatMessages, moodHistory, petMemory, petMailbox, userProfile, petRecommendations } = deps

  // ── Visibility refs ──
  const showAuthModal = ref(false)
  const showChatHistory = ref(false)
  const showMoodHistory = ref(false)
  const showSettings = ref(false)
  const showMemory = ref(false)
  const showMusic = ref(false)
  const showMailbox = ref(false)
  const showProfile = ref(false)
  const showRecommend = ref(false)

  // ── Dedup-guarded panel-open watchers ──
  // Skip fetch if data was already loaded by the auth flow.

  watch(showChatHistory, (open) => {
    if (open && !chatMessages.isLoadingHistory.value && chatMessages.messages.value.length === 0) {
      chatMessages.loadHistory()
    }
  })
  watch(showMoodHistory, (open) => {
    if (open && !moodHistory.isLoadingHistory.value && moodHistory.historyEntries.value.length === 0) {
      moodHistory.loadHistory()
    }
  })
  watch(showMemory, (open) => {
    if (open && !petMemory.isLoadingMemory.value && petMemory.memoryEntries.value.length === 0) {
      petMemory.loadMemory()
    }
  })
  watch(showMailbox, (open) => {
    if (open && !petMailbox.isLoading.value && petMailbox.mails.value.length === 0) {
      petMailbox.fetchMails()
    }
  })
  watch(showProfile, (open) => {
    if (open && !userProfile.isLoading.value && !userProfile.profile.value) {
      userProfile.fetchProfile()
    }
  })
  watch(showRecommend, (open) => {
    if (open && !petRecommendations.isLoading.value && petRecommendations.recommendations.value.length === 0) {
      petRecommendations.fetchRecommendations()
    }
  })

  // ── Radial menu routing ──

  function handleRadialMenuClick(id: string): void {
    switch (id) {
      case 'today':
      case 'chat-history':
        showChatHistory.value = !showChatHistory.value
        break
      case 'mood-history':
        showMoodHistory.value = !showMoodHistory.value
        break
      case 'settings':
        showSettings.value = !showSettings.value
        break
      case 'recommend':
        showRecommend.value = !showRecommend.value
        break
      case 'music':
        showMusic.value = !showMusic.value
        break
      case 'mailbox':
        showMailbox.value = !showMailbox.value
        break
    }
  }

  // ── Close all panels (used on logout) ──

  function closeAllPanels(): void {
    showChatHistory.value = false
    showMoodHistory.value = false
    showSettings.value = false
    showMemory.value = false
    showMusic.value = false
    showMailbox.value = false
    showProfile.value = false
    showRecommend.value = false
  }

  // ── Escape key cascade ──
  // Returns true if a panel was closed (caller should stop propagation).

  function handleEscape(): boolean {
    if (showChatHistory.value) { showChatHistory.value = false; return true }
    if (showMoodHistory.value) { showMoodHistory.value = false; return true }
    if (showSettings.value) { showSettings.value = false; return true }
    if (showMemory.value) { showMemory.value = false; return true }
    if (showMusic.value) { showMusic.value = false; return true }
    if (showMailbox.value) { showMailbox.value = false; return true }
    if (showProfile.value) { showProfile.value = false; return true }
    if (showRecommend.value) { showRecommend.value = false; return true }
    return false
  }

  return {
    showAuthModal,
    showChatHistory,
    showMoodHistory,
    showSettings,
    showMemory,
    showMusic,
    showMailbox,
    showProfile,
    showRecommend,
    handleRadialMenuClick,
    closeAllPanels,
    handleEscape,
  }
}
