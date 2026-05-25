import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'
import { API } from '@/config/api'
import { useAuthStore } from './auth'

/**
 * 用户资料管理 Store
 *
 * 封装用户资料的 CRUD 操作，管理加载/错误状态，
 * 避免 UserProfile.vue 中 10+ 个分散的 ref 和重复的 try/catch。
 */
export const useUserStore = defineStore('user', () => {
  // ── 状态 ──
  const profile = ref(null)
  const hobbies = ref([])
  const aiTags = ref([])
  const levelInfo = ref(null)
  const latestEmotion = ref(null)
  const loading = ref(false)
  const error = ref('')

  // ── 计算 ──
  const userId = computed(() => useAuthStore().userId)

  // ── 动作 ──
  async function fetchProfile() {
    const uid = userId.value
    if (!uid) return
    loading.value = true
    error.value = ''
    try {
      const res = await request.get(API.USER_PROFILE(uid))
      profile.value = res.user || res
      hobbies.value = res.hobbies || []
      aiTags.value = res.aiTags || res.ai_tags || []
      levelInfo.value = res.levelInfo || null
      latestEmotion.value = res.latestEmotion || null
      return res
    } catch (e) {
      error.value = e?.message || '加载用户资料失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateAvatar(file) {
    loading.value = true
    error.value = ''
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await request.post(API.USER_AVATAR, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (profile.value) profile.value.avatarUrl = res.url || res.avatarUrl
      return res
    } catch (e) {
      error.value = e?.message || '头像上传失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateBasic(data) {
    loading.value = true
    error.value = ''
    try {
      const res = await request.post(API.USER_UPDATE_BASIC, data)
      if (profile.value) Object.assign(profile.value, data)
      return res
    } catch (e) {
      error.value = e?.message || '更新资料失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateHobbies(newHobbies) {
    loading.value = true
    error.value = ''
    try {
      const res = await request.post(API.USER_UPDATE_HOBBIES, { hobbies: newHobbies })
      hobbies.value = [...newHobbies]
      return res
    } catch (e) {
      error.value = e?.message || '更新爱好失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateAIType(aiType) {
    loading.value = true
    error.value = ''
    try {
      const res = await request.post(API.USER_UPDATE_AI_TYPE, { aiType })
      if (profile.value) profile.value.aiType = aiType
      return res
    } catch (e) {
      error.value = e?.message || '更新 AI 类型失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  function clearProfile() {
    profile.value = null
    hobbies.value = []
    aiTags.value = []
    levelInfo.value = null
    latestEmotion.value = null
    error.value = ''
  }

  return {
    profile,
    hobbies,
    aiTags,
    levelInfo,
    latestEmotion,
    loading,
    error,
    userId,
    fetchProfile,
    updateAvatar,
    updateBasic,
    updateHobbies,
    updateAIType,
    clearProfile,
  }
})
