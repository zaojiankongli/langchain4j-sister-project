import { STORAGE_KEYS } from '@/config/storage'
import { safeGet } from '@/utils/storage'

let accessTokenCache = safeGet(STORAGE_KEYS.ACCESS_TOKEN, '') || ''

export function getAccessTokenCache() {
  if (accessTokenCache) return accessTokenCache
  accessTokenCache = safeGet(STORAGE_KEYS.ACCESS_TOKEN, '') || ''
  return accessTokenCache
}

export function setAccessTokenCache(token) {
  accessTokenCache = token || ''
}
