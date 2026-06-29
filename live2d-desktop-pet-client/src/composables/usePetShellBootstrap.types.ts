import type { Ref, ShallowRef } from 'vue'

export interface ReturnTypeUseAuthFlow {
  isAuthenticated: ShallowRef<boolean>
}

export interface ReturnTypeUseChatMessages {
  clearStream: () => void
  loadHistory: () => Promise<void>
}

export interface ReturnTypeUseMoodHistory {
  loadHistory: () => Promise<void>
}

export interface ReturnTypeUsePetSettings {
  isSaving: ShallowRef<boolean>
  settingsError: ShallowRef<string>
  loadPresets: () => Promise<void>
  loadSettings: () => Promise<void>
}

export interface ReturnTypeUsePetMailbox {
  unreadCount: Ref<number>
  fetchMails: () => Promise<void>
}

export interface ReturnTypeUseUserProfile {
  fetchProfile: () => Promise<void>
}

export interface ReturnTypeUseRecommendations {
  fetchRecommendations: () => Promise<void>
}
