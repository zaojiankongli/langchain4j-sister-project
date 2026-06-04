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

  // ── 代际计数器：检测过时响应 ──
  let _generation = 0

  /**
   * 执行异步操作，如果期间有新操作启动则丢弃本操作的响应
   */
  async function _withGeneration(asyncFn) {
    const gen = ++_generation
    loading.value = true
    error.value = ''
    try {
      const result = await asyncFn(gen) // 传递 gen 供回调内做写入保护
      if (gen !== _generation) return result // 已过时，静默丢弃
      return result
    } catch (e) {
      if (gen === _generation) error.value = e?.message || '操作失败'
      throw e
    } finally {
      if (gen === _generation) loading.value = false
    }
  }

  // ── 动作 ──
  async function fetchProfile() {
    return _withGeneration(async (gen) => {
      const res = await request.get(API.USER_PROFILE)
      if (res.code !== 200) {
        throw new Error(res.message || '加载用户资料失败')
      }
      const data = res.data || res
      if (gen === _generation) {
        profile.value = data
        hobbies.value = data.hobbies || []
        // 后端 UserProfileVO 返回 interest_tags（@JsonProperty 映射 snake_case）
        aiTags.value = data.interest_tags || data.interestTags || data.aiTags || data.ai_tags || []
        levelInfo.value = data.levelInfo || null
        latestEmotion.value = data.latestEmotion || null
      }
      return res
    })
  }

  async function updateAvatar(file) {
    return _withGeneration(async (gen) => {
      const formData = new FormData()
      formData.append('file', file)
      const res = await request.post(API.USER_AVATAR, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (res.code !== 200) {
        throw new Error(res.message || '头像上传失败')
      }
      if (gen === _generation && profile.value)         profile.value.avatarUrl = res.data
      return res
    })
  }

  async function updateBasic(data) {
    return _withGeneration(async (gen) => {
      const res = await request.post(API.USER_UPDATE_BASIC, data)
      if (res.code !== 200) {
        throw new Error(res.message || '更新资料失败')
      }
      if (gen === _generation && profile.value) Object.assign(profile.value, data)
      return res
    })
  }

  async function updateHobbies(newHobbies) {
    return _withGeneration(async (gen) => {
      const res = await request.post(API.USER_UPDATE_HOBBIES, { hobbies: newHobbies })
      if (res.code !== 200) {
        throw new Error(res.message || '更新爱好失败')
      }
      // newHobbies 来自 UserProfile 时为逗号分隔字符串，拆为数组
      if (gen === _generation) hobbies.value = typeof newHobbies === 'string' ? newHobbies.split(',') : [...newHobbies]
      return res
    })
  }

  async function updateAIType(aiType) {
    return _withGeneration(async (gen) => {
      const res = await request.post(API.USER_UPDATE_AI_TYPE, { ai_type: aiType })
      if (res.code !== 200) {
        throw new Error(res.message || '更新 AI 类型失败')
      }
      if (gen === _generation && profile.value) profile.value.aiType = aiType
      return res
    })
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
