import { STORAGE_KEYS } from '@/config/storage'
import { safeGet, safeGetJSON } from '@/utils/storage'
import { getUserIdFromToken } from '@/utils/jwt'
import { getAccessTokenCache } from '@/utils/tokenCache'

export function getAccessToken() {
  return getAccessTokenCache()
}

export function getUserId() {
  const userData = safeGetJSON(STORAGE_KEYS.USER)
  if (userData && (userData.id || userData.userId)) {
    return userData.id || userData.userId
  }

  const token = getAccessToken()
  if (token) {
    return getUserIdFromToken(token)
  }

  return null
}

export function isProfileComplete() {
  return safeGet(STORAGE_KEYS.PROFILE_COMPLETE, '') === 'true'
}
