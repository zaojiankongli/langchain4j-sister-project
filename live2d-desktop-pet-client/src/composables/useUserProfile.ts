import { readonly, shallowRef } from 'vue'
import { get } from '../utils/apiClient'
import type { ApiResult } from '../types/api'

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export interface UserProfileLevel {
  currentLevel: number
  currentExp: number
  levelUpExp: number
  totalExp: number
}

export interface UserProfileEmotion {
  pleasure: number
  arousal: number
  dominance: number
  moodDescription: string
}

export interface UserProfile {
  username?: string
  avatarUrl?: string
  meet_days: number
  message_count: number
  first_chat_time?: string
  current_level: number
  current_exp: number
  level_up_exp: number
  total_exp: number
  pleasure: number
  arousal: number
  dominance: number
  mood_description: string
  interest_tags: string[]
  levelInfo?: UserProfileLevel
  latestEmotion?: UserProfileEmotion
}

/* ------------------------------------------------------------------ */
/*  Composable                                                         */
/* ------------------------------------------------------------------ */

const profile = shallowRef<UserProfile | null>(null)
const isLoading = shallowRef(false)
const loadError = shallowRef('')

async function fetchProfile(): Promise<void> {
  isLoading.value = true
  loadError.value = ''
  try {
    const res = await get<ApiResult<UserProfile>>('/api/user/profile')
    if (res.code === 200 && res.data) {
      profile.value = res.data
    } else {
      loadError.value = res.message || '加载资料失败'
    }
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    isLoading.value = false
  }
}

export function useUserProfile() {
  return {
    profile: readonly(profile),
    isLoading: readonly(isLoading),
    loadError: readonly(loadError),
    fetchProfile,
  }
}
