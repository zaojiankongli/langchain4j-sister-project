import axios from 'axios';
import router from '@/router';
import { STORAGE_KEYS } from '@/config/storage';
import { safeGet, safeRemove } from '@/utils/storage';
import { getAccessToken } from '@/utils/auth'
import { setAccessTokenCache } from '@/utils/tokenCache'
export { base64UrlDecode } from '@/utils/jwt'

// ── Token 续期管理 ──

/**
 * 执行刷新（模块级单例 Promise，防止并发 401 触发多次刷新）
 * 委托给 auth store 实现单一事实来源
 */
let _refreshPromise = null

async function _doRefreshImpl() {
  // 懒加载 store 以避免循环依赖
  const useAuthStore = (await import('@/stores/auth')).useAuthStore
  const authStore = useAuthStore()

  if (!authStore.refreshToken) {
    throw new Error('无 refreshToken')
  }

  const res = await authStore.refreshTokens()
  const data = res.data || res
  return data.accessToken || authStore.accessToken
}

function doRefresh() {
  if (_refreshPromise) return _refreshPromise
  _refreshPromise = _doRefreshImpl().finally(() => { _refreshPromise = null })
  return _refreshPromise
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

// ── 创建 axios 实例 ──
const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

// ── 请求拦截器：自动添加 token ──
request.interceptors.request.use(
  async (config) => {
    const accessToken = getAccessToken() || safeGet(STORAGE_KEYS.ACCESS_TOKEN)
    if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
    return config
  },
  (error) => Promise.reject(error)
)

// ── 响应拦截器：滑动过期 + 401 自动重试 ──
request.interceptors.response.use(
  (response) => {
    const newAccessToken = response.headers?.['new-access-token']
    if (newAccessToken) {
      // 通过 auth store 更新，保证单一数据源
      import('@/stores/auth').then(({ useAuthStore }) => {
        const authStore = useAuthStore()
        authStore.setTokens(newAccessToken, authStore.refreshToken)
      }).catch(() => {
        // 降级：直接写 localStorage
        setAccessTokenCache(newAccessToken)
        try { localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, newAccessToken) } catch { /* localStorage 不可用时静默忽略 */ }
      })
    }
    return response.data
  },
  (error) => {
    const originalConfig = error.config

    if (error.response?.status === 401 && originalConfig && !originalConfig._retry && !originalConfig._skipRefresh) {
      const refreshToken = safeGet(STORAGE_KEYS.REFRESH_TOKEN)

      if (refreshToken) {
        originalConfig._retry = true

        return doRefresh()
          .then((newToken) => {
            originalConfig.headers.Authorization = `Bearer ${newToken}`
            return request(originalConfig)
          })
      }

      // 无 refreshToken，通过 auth store 清除认证状态
      import('@/stores/auth').then(({ useAuthStore }) => {
        useAuthStore().clearAuth()
      }).catch(() => {
        setAccessTokenCache('')
        safeRemove(STORAGE_KEYS.ACCESS_TOKEN)
        safeRemove(STORAGE_KEYS.REFRESH_TOKEN)
        safeRemove(STORAGE_KEYS.USER)
      })
      router.push({ name: 'Login' }).catch(() => {})
    }

    return Promise.reject(normalizeRequestError(error))
  }
)

export default request;
