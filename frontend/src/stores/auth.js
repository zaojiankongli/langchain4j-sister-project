import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { STORAGE_KEYS } from '@/config/storage'
import { API } from '@/config/api'
import request from '@/utils/request'
import { safeGet, safeSet, safeRemove, safeGetJSON, safeSetJSON } from '@/utils/storage'
import { getUserIdFromToken } from '@/utils/jwt'
import { setAccessTokenCache } from '@/utils/tokenCache'
import { recordAuthMetric } from '@/utils/metrics'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(safeGet(STORAGE_KEYS.ACCESS_TOKEN, ''))
  const refreshToken = ref(safeGet(STORAGE_KEYS.REFRESH_TOKEN, ''))
  const user = ref(parseUser())
  const profileComplete = ref(safeGet(STORAGE_KEYS.PROFILE_COMPLETE, '') === 'true')
  const loading = ref(false)
  const error = ref('')

  const isAuthenticated = computed(() => !!accessToken.value)
  const isProfileComplete = computed(() => profileComplete.value)
  const userId = computed(() => user.value?.id || getUserIdFromToken(accessToken.value))
  const username = computed(() => user.value?.username || '')

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

    safeSet(STORAGE_KEYS.PROFILE_COMPLETE, profileComplete.value ? 'true' : 'false')
  }

  function setTokens(token, refresh, userData, nextProfileComplete = profileComplete.value) {
    accessToken.value = token
    refreshToken.value = refresh
    if (userData) {
      user.value = userData
    }
    profileComplete.value = Boolean(nextProfileComplete)
    error.value = ''
    setAccessTokenCache(token)
    recordAuthMetric('set_tokens', {
      hasUser: Boolean(userData),
      profileComplete: profileComplete.value,
    })
    syncLocalStorage()
  }

  function clearAuth() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    profileComplete.value = false
    error.value = ''
    setAccessTokenCache('')
    recordAuthMetric('clear_auth')
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
      setTokens(data.accessToken, data.refreshToken, data.user, !data.requiresProfileComplete)
      return res
    } catch (requestError) {
      error.value = requestError?.message || '登录失败'
      throw requestError
    } finally {
      loading.value = false
    }
  }

  let refreshPromise = null

  async function refreshTokens() {
    if (refreshPromise) {
      return refreshPromise
    }
    if (!refreshToken.value) {
      throw new Error('missing refresh token')
    }

    refreshPromise = (async () => {
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
      } catch (requestError) {
        clearAuth()
        throw requestError
      } finally {
        refreshPromise = null
      }
    })()

    return refreshPromise
  }

  function logout() {
    const uid = userId.value
    clearAuth()
    return uid
  }

  function setProfileComplete(value) {
    profileComplete.value = Boolean(value)
    recordAuthMetric('set_profile_complete', { profileComplete: profileComplete.value })
    syncLocalStorage()
  }

  return {
    accessToken,
    refreshToken,
    user,
    profileComplete,
    loading,
    error,
    isAuthenticated,
    isProfileComplete,
    userId,
    username,
    setTokens,
    clearAuth,
    login,
    refreshTokens,
    logout,
    setProfileComplete,
  }
})
