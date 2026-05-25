import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { STORAGE_KEYS } from '@/config/storage'
import { API } from '@/config/api'
import request from '@/utils/request'

/**
 * 认证状态管理 Store
 *
 * 统一管理 token 生命周期，与 localStorage 保持双向同步。
 * 作为 auth.js 工具函数的后备数据源，逐步替代直接 localStorage 访问。
 */
export const useAuthStore = defineStore('auth', () => {
  // ── 状态 ──
  const accessToken = ref(localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN) || '')
  const refreshToken = ref(localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN) || '')
  const user = ref(parseUser())
  const loading = ref(false)
  const error = ref('')

  // ── 计算属性 ──
  const isAuthenticated = computed(() => !!accessToken.value)
  const userId = computed(() => user.value?.id || parseUserIdFromToken(accessToken.value))
  const username = computed(() => user.value?.username || '')

  // ── 内部工具 ──
  function parseUser() {
    try {
      const raw = localStorage.getItem(STORAGE_KEYS.USER)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  function parseUserIdFromToken(token) {
    if (!token) return null
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      return payload.sub || payload.userId || payload.id
    } catch {
      return null
    }
  }

  function syncLocalStorage() {
    if (accessToken.value) {
      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken.value)
    } else {
      localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
    }
    if (refreshToken.value) {
      localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken.value)
    } else {
      localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
    }
    if (user.value) {
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user.value))
    } else {
      localStorage.removeItem(STORAGE_KEYS.USER)
    }
  }

  // ── 动作 ──
  function setTokens(token, refresh, userData) {
    accessToken.value = token
    refreshToken.value = refresh
    if (userData) user.value = userData
    error.value = ''
    syncLocalStorage()
  }

  function clearAuth() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    error.value = ''
    syncLocalStorage()
  }

  async function login(email, code, username) {
    loading.value = true
    error.value = ''
    try {
      const res = await request.post(API.AUTH_LOGIN, { email, code, username })
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

  async function refreshTokens() {
    if (!refreshToken.value) throw new Error('无 refreshToken')
    try {
      const res = await request.post(API.AUTH_REFRESH, { refreshToken: refreshToken.value })
      const data = res.data || res
      accessToken.value = data.accessToken
      refreshToken.value = data.refreshToken
      syncLocalStorage()
      return res
    } catch (e) {
      clearAuth()
      throw e
    }
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
