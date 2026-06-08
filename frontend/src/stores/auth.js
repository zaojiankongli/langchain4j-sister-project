import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { STORAGE_KEYS } from '@/config/storage'
import { API } from '@/config/api'
import request from '@/utils/request'
import { safeGet, safeSet, safeRemove, safeGetJSON, safeSetJSON } from '@/utils/storage'
import { getUserIdFromToken } from '@/utils/jwt'
import { setAccessTokenCache } from '@/utils/tokenCache'

/**
 * 认证状态管理 Store
 *
 * 统一管理 token 生命周期，与 localStorage 保持双向同步。
 * 作为 auth.js 工具函数的后备数据源，逐步替代直接 localStorage 访问。
 */
export const useAuthStore = defineStore('auth', () => {
  // ── 状态 ──
  const accessToken = ref(safeGet(STORAGE_KEYS.ACCESS_TOKEN, ''))
  const refreshToken = ref(safeGet(STORAGE_KEYS.REFRESH_TOKEN, ''))
  const user = ref(parseUser())
  const loading = ref(false)
  const error = ref('')

  // ── 计算属性 ──
  const isAuthenticated = computed(() => !!accessToken.value)
  const userId = computed(() => user.value?.id || getUserIdFromToken(accessToken.value))
  const username = computed(() => user.value?.username || '')

  // ── 内部工具 ──
    function parseUser() {
      return safeGetJSON(STORAGE_KEYS.USER)
    }

  function syncLocalStorage() {
    if (accessToken.value) {
      safeSet(STORAGE_KEYS.ACCESS_TOKEN, accessToken.value)
    } else {
      safeRemove(STORAGE_KEYS.ACCESS_TOKEN)
    }
    if (refreshToken.value) {
      safeSet(STORAGE_KEYS.REFRESH_TOKEN, refreshToken.value)
    } else {
      safeRemove(STORAGE_KEYS.REFRESH_TOKEN)
    }
    if (user.value) {
      safeSetJSON(STORAGE_KEYS.USER, user.value)
    } else {
      safeRemove(STORAGE_KEYS.USER)
    }
  }

  // ── 动作 ──
  function setTokens(token, refresh, userData) {
    accessToken.value = token
    refreshToken.value = refresh
    if (userData) user.value = userData
    error.value = ''
    setAccessTokenCache(token)
    syncLocalStorage()
  }

  function clearAuth() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    error.value = ''
    setAccessTokenCache('')
    syncLocalStorage()
  }

  async function login(email, code, username) {
    loading.value = true
    error.value = ''
    try {
      const res = await request.post(API.AUTH_LOGIN, { email, code, username })
      if (res.code !== 200) {
        error.value = res.message || '登录失败'
        return res
      }
      const data = res.data || res
      setTokens(data.accessToken, data.refreshToken, data.user)
      return res
    } catch (e) {
      error.value = e?.message || '登录失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  /**
   * 刷新令牌（单例，防止并发重复刷新）
   */
  let _refreshPromise = null

  async function refreshTokens() {
    // 已有进行中的刷新 → 复用
    if (_refreshPromise) return _refreshPromise
    if (!refreshToken.value) throw new Error('无 refreshToken')

    _refreshPromise = (async () => {
      try {
        const res = await request.post(API.AUTH_REFRESH, { refreshToken: refreshToken.value }, { _skipRefresh: true })
        if (res.code !== 200) {
          throw new Error(res.message || '刷新失败')
        }
        const data = res.data || res
        accessToken.value = data.accessToken
        refreshToken.value = data.refreshToken
        syncLocalStorage()
        return res
      } catch (e) {
        clearAuth()
        throw e
      } finally {
        _refreshPromise = null
      }
    })()

    return _refreshPromise
  }

  function logout() {
    const uid = userId.value
    clearAuth()
    return uid
  }

  return {
    // 状态
    accessToken,
    refreshToken,
    user,
    loading,
    error,
    // 计算
    isAuthenticated,
    userId,
    username,
    // 动作
    setTokens,
    clearAuth,
    login,
    refreshTokens,
    logout,
  }
})
