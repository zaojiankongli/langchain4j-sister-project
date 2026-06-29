import { get } from '../utils/apiClient'
import { watch, type Ref, type ShallowRef } from 'vue'
import type { EmotionState } from '../types/emotion'
import type { ReturnTypeUseAuthFlow } from './usePetShellBootstrap.types'
import type { ReturnTypeUseChatMessages } from './usePetShellBootstrap.types'
import type { ReturnTypeUseMoodHistory } from './usePetShellBootstrap.types'
import type { ReturnTypeUsePetSettings } from './usePetShellBootstrap.types'
import type { ReturnTypeUsePetMailbox } from './usePetShellBootstrap.types'
import type { ReturnTypeUseUserProfile } from './usePetShellBootstrap.types'
import type { ReturnTypeUseRecommendations } from './usePetShellBootstrap.types'
import type { NotificationCategory } from './usePetNotifications'
import type { PetSocketStatus } from '../ws/petStompClient'

interface UsePetShellBootstrapOptions {
  userId: ShallowRef<number | null>
  moodLabel: ShallowRef<string>
  moodDescription: ShallowRef<string>
  pleasure: ShallowRef<number>
  arousal: ShallowRef<number>
  dominance: ShallowRef<number>
  showAuthModal: Ref<boolean>
  socketStatus: ShallowRef<PetSocketStatus>
  authFlow: ReturnTypeUseAuthFlow
  chatMessages: ReturnTypeUseChatMessages
  moodHistory: ReturnTypeUseMoodHistory
  petSettings: ReturnTypeUsePetSettings
  petMailbox: ReturnTypeUsePetMailbox
  userProfile: ReturnTypeUseUserProfile
  petRecommendations: ReturnTypeUseRecommendations
  reconnectAttempt: ShallowRef<number>
  reconnectDelayMs: ShallowRef<number | null>
  connectSocket: () => void
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
  notify: (category: NotificationCategory, title: string, body?: string) => Promise<void>
}

async function fetchInitialEmotion(
  userId: number,
  moodLabel: ShallowRef<string>,
  moodDescription: ShallowRef<string>,
  pleasure: ShallowRef<number>,
  arousal: ShallowRef<number>,
  dominance: ShallowRef<number>,
): Promise<void> {
  try {
    const data = await get<EmotionState>(`/api/emotion/${userId}/current`)
    moodLabel.value = data.moodLabel
    pleasure.value = data.pleasure
    arousal.value = data.arousal
    dominance.value = data.dominance
    if (data.moodDescription !== undefined) {
      moodDescription.value = data.moodDescription
    }
  } catch (err) {
    console.error('Failed to fetch initial emotion:', err)
  }
}

export function usePetShellBootstrap(options: UsePetShellBootstrapOptions) {
  const {
    userId,
    moodLabel,
    moodDescription,
    pleasure,
    arousal,
    dominance,
    showAuthModal,
    socketStatus,
    authFlow,
    chatMessages,
    moodHistory,
    petSettings,
    petMailbox,
    userProfile,
    petRecommendations,
    reconnectAttempt,
    reconnectDelayMs,
    connectSocket,
    showToast,
    notify,
  } = options

  watch(authFlow.isAuthenticated, (authed) => {
    if (!authed) {
      return
    }

    showAuthModal.value = false
    chatMessages.clearStream()
    connectSocket()
    void chatMessages.loadHistory()
    void moodHistory.loadHistory()
    void petSettings.loadPresets()
    void petSettings.loadSettings()
    void petMailbox.fetchMails()
    void userProfile.fetchProfile()
    void petRecommendations.fetchRecommendations()
  })

  watch(socketStatus, (status, oldStatus) => {
    if (status === 'connected' && authFlow.isAuthenticated.value && userId.value !== null) {
      void fetchInitialEmotion(userId.value, moodLabel, moodDescription, pleasure, arousal, dominance)
    }

    if (status === 'connected' && oldStatus === 'connecting') {
      showToast('连接成功', 'success')
    } else if (status === 'error') {
      showToast('连接失败', 'error')
    } else if (status === 'disconnected' && authFlow.isAuthenticated.value) {
      if (reconnectAttempt.value > 0 && reconnectDelayMs.value !== null) {
        showToast('正在重连', 'info')
      } else if (oldStatus === 'connected') {
        showToast('已断开', 'info')
        void notify('disconnect', '连接断开', '与服务器的连接已断开')
      }
    }
  })

  watch(petSettings.isSaving, (isSaving, wasSaving) => {
    if (wasSaving && !isSaving && !petSettings.settingsError.value) {
      showToast('保存成功', 'success')
    }
  })

  watch(moodLabel, (newMood, oldMood) => {
    if (newMood && oldMood && newMood !== oldMood) {
      void notify('mood', '心情变化', `心情变成了: ${newMood}`)
    }
  })

  watch(petMailbox.unreadCount, (count, prevCount) => {
    if (count > (prevCount ?? 0) && count > 0) {
      void notify('mail', '新信件', `你有 ${count} 封未读信件`)
    }
  })
}
