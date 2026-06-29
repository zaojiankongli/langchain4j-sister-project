import axios from 'axios'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { STORAGE_KEYS } from '@/config/storage'
import { safeGet, safeRemove } from '@/utils/storage'
import { getAccessToken } from '@/utils/auth'
import { setAccessTokenCache } from '@/utils/tokenCache'
import { recordAuthMetric, recordRequestMetric } from '@/utils/metrics'

export { base64UrlDecode } from '@/utils/jwt'

let refreshPromise = null

async function doRefreshImpl() {
  const authStore = useAuthStore()

  if (!authStore.refreshToken) {
    throw new Error('missing refresh token')
  }

  const res = await authStore.refreshTokens()
  const data = res.data || res
  return data.accessToken || authStore.accessToken
}

function doRefresh() {
  if (refreshPromise) {
    return refreshPromise
  }
  refreshPromise = doRefreshImpl().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

function normalizeRequestError(error) {
  const responseData = error?.response?.data

  if (responseData && typeof responseData === 'object' && !Array.isArray(responseData)) {
    return {
      ...responseData,
      message: responseData.message || error.message || '请求失败',
      code: responseData.code ?? error.code,
      status: error.response?.status,
      name: error.name,
      response: error.response,
      config: error.config,
    }
  }

  if (responseData !== undefined) {
    return {
      message: error?.message || '请求失败',
      code: error?.code,
      status: error.response?.status,
      name: error?.name,
      data: responseData,
      response: error.response,
      config: error.config,
    }
  }

  return error
}

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

request.interceptors.request.use(
  async (config) => {
    config.metadata = {
      ...(config.metadata || {}),
      startedAt: Date.now(),
      retried: Boolean(config._retry),
    }

    const accessToken = getAccessToken() || safeGet(STORAGE_KEYS.ACCESS_TOKEN)
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    const startedAt = response.config?.metadata?.startedAt || Date.now()
    recordRequestMetric({
      url: response.config?.url || '',
      method: response.config?.method || 'get',
      statusCode: response.status,
      durationMs: Date.now() - startedAt,
      success: true,
      retried: Boolean(response.config?._retry),
    })

    const newAccessToken = response.headers?.['new-access-token']
    if (newAccessToken) {
      recordAuthMetric('refresh_header_token')
      try {
        const authStore = useAuthStore()
        authStore.setTokens(
          newAccessToken,
          authStore.refreshToken,
          authStore.user,
          authStore.profileComplete,
        )
      } catch {
        setAccessTokenCache(newAccessToken)
        try {
          localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, newAccessToken)
        } catch {
          // Ignore storage fallback failures.
        }
      }
    }

    return response.data
  },
  (error) => {
    const startedAt = error.config?.metadata?.startedAt || Date.now()
    recordRequestMetric({
      url: error.config?.url || '',
      method: error.config?.method || 'get',
      statusCode: error.response?.status || 0,
      durationMs: Date.now() - startedAt,
      success: false,
      retried: Boolean(error.config?._retry),
      message: error.message || 'request failed',
    })

    const originalConfig = error.config

    if (error.response?.status === 401 && originalConfig && !originalConfig._retry && !originalConfig._skipRefresh) {
      recordAuthMetric('401_received', { url: originalConfig.url || '' })
      const refreshToken = safeGet(STORAGE_KEYS.REFRESH_TOKEN)

      if (refreshToken) {
        originalConfig._retry = true
        recordAuthMetric('refresh_attempt', { url: originalConfig.url || '' })

        return doRefresh()
          .then((newToken) => {
            recordAuthMetric('refresh_success', { url: originalConfig.url || '' })
            originalConfig.headers.Authorization = `Bearer ${newToken}`
            return request(originalConfig)
          })
          .catch((refreshError) => {
            recordAuthMetric('refresh_failed', {
              url: originalConfig.url || '',
              message: refreshError?.message || 'refresh failed',
            })
            return Promise.reject(normalizeRequestError(refreshError))
          })
      }

      try {
        recordAuthMetric('refresh_missing', { url: originalConfig.url || '' })
        useAuthStore().clearAuth()
      } catch {
        setAccessTokenCache('')
        safeRemove(STORAGE_KEYS.ACCESS_TOKEN)
        safeRemove(STORAGE_KEYS.REFRESH_TOKEN)
        safeRemove(STORAGE_KEYS.USER)
        safeRemove(STORAGE_KEYS.PROFILE_COMPLETE)
      }
      router.push({ name: 'Login' }).catch(() => {})
    }

    return Promise.reject(normalizeRequestError(error))
  },
)

export default request
