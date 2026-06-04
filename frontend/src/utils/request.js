import axios from 'axios';
import router from '@/router';
import { STORAGE_KEYS } from '@/config/storage';
import { API } from '@/config/api';
import { safeGet, safeSet, safeRemove } from '@/utils/storage';

// ── Token 续期管理 ──
const REFRESH_THRESHOLD_MS = 5 * 60 * 1000 // 剩余不足 5 分钟就提前刷新
let isRefreshing = false
let refreshPromise = null
let pendingRequests = []

/**
 * base64url 解码（JWT 使用 base64url，非标准 base64）
 */
export function base64UrlDecode(str) {
  // 替换 URL-safe 字符为标准 base64，补齐 padding
  str = str.replace(/-/g, '+').replace(/_/g, '/')
  while (str.length % 4) str += '='
  return atob(str)
}

/**
 * 解码 JWT payload，返回 { exp, sub, ... } 或 null
 */
function decodeToken(token) {
  try {
    return JSON.parse(base64UrlDecode(token.split('.')[1]))
  } catch (error) {
    console.warn('JWT 解码失败:', error, token)
    return null
  }
}

/**
 * 检查 access token 是否已过期或即将过期
 */
function isTokenExpiringSoon() {
  const token = safeGet(STORAGE_KEYS.ACCESS_TOKEN)
  if (!token) return false
  const payload = decodeToken(token)
  if (!payload?.exp) return false
  const remaining = payload.exp * 1000 - Date.now()
  // 已过期或剩余不足阈值时触发刷新
  return remaining < REFRESH_THRESHOLD_MS
}

/**
 * 执行刷新（单例，防止并发重复刷新）
 * 委托给 auth store 实现单一事实来源
 * 注意：刷新失败不会踢出登录，由后续 401 响应拦截器统一处理
 */
async function doRefresh() {
  if (refreshPromise) return refreshPromise

  // 懒加载 store 以避免循环依赖
  const useAuthStore = (await import('@/stores/auth')).useAuthStore
  const authStore = useAuthStore()

  const refreshToken = safeGet(STORAGE_KEYS.REFRESH_TOKEN)
  if (!refreshToken) {
    refreshPromise = Promise.reject(new Error('无 refreshToken'))
    refreshPromise = refreshPromise.finally(() => { refreshPromise = null })
    return refreshPromise
  }

  refreshPromise = authStore.refreshTokens()
    .then(res => {
      const data = res.data || res
      return data.accessToken
    })
    .finally(() => {
      refreshPromise = null
    })

  return refreshPromise
}

// ── 创建 axios 实例 ──
const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

// ── 请求拦截器：自动续期 + 添加 token ──
request.interceptors.request.use(
  async (config) => {
    // 无 token 或 _skipRefresh 标记的请求，跳过 token 续期检查直接放行
    // Login.vue 的 发送验证码/登录请求 使用 _skipRefresh: true 避免用陈旧 token 触发刷新
    const accessToken = safeGet(STORAGE_KEYS.ACCESS_TOKEN)
    if (!accessToken || config._skipRefresh) {
      if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
      return config
    }

    // 如果 access token 快过期了，先刷新再发请求
    if (isTokenExpiringSoon()) {
      try {
        const newToken = await doRefresh()
        config.headers.Authorization = `Bearer ${newToken}`
        return config
      } catch (error) {
        console.warn('Token 刷新失败:', error)
      }
    }

    config.headers.Authorization = `Bearer ${accessToken}`
    return config
  },
  (error) => Promise.reject(error)
)

// ── 刷新队列管理（用于 401 场景） ──
function onTokenRefreshed(newAccessToken) {
  pendingRequests.forEach(({ config, resolve }) => {
    config.headers.Authorization = `Bearer ${newAccessToken}`
    resolve(request(config))
  })
  pendingRequests = []
}

function onRefreshFailed(error) {
  pendingRequests.forEach(({ reject }) => reject(error))
  pendingRequests = []
}

// ── 响应拦截器：滑动过期 + 401 自动重试 ──
request.interceptors.response.use(
  (response) => {
    const newAccessToken = response.headers?.['new-access-token']
    if (newAccessToken) {
      safeSet(STORAGE_KEYS.ACCESS_TOKEN, newAccessToken)
    }
    return response.data
  },
  (error) => {
    const originalConfig = error.config

    if (error.response?.status === 401 && !originalConfig._retry) {
  const refreshToken = safeGet(STORAGE_KEYS.REFRESH_TOKEN)

      if (refreshToken) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            pendingRequests.push({ config: originalConfig, resolve, reject })
          })
        }

        originalConfig._retry = true
        isRefreshing = true

        return doRefresh()
          .then((newToken) => {
            onTokenRefreshed(newToken)
            originalConfig.headers.Authorization = `Bearer ${newToken}`
            return request(originalConfig)
          })
          .catch((refreshError) => {
            onRefreshFailed(refreshError)
            return Promise.reject(refreshError)
          })
          .finally(() => {
            isRefreshing = false
          })
      }

      safeRemove(STORAGE_KEYS.ACCESS_TOKEN)
      safeRemove(STORAGE_KEYS.REFRESH_TOKEN)
      safeRemove(STORAGE_KEYS.USER)
      router.push({ name: 'Login' }).catch(() => {})
    }

    return Promise.reject(error.response?.data || error)
  }
)

export default request;
