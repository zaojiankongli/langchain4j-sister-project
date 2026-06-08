/**
 * JWT helpers shared by auth utilities, stores, and request interceptors.
 *
 * JWT payloads use base64url encoding, not plain base64. Using atob()
 * directly on token segments can fail when payloads contain URL-safe
 * characters or omit padding.
 */

export function base64UrlDecode(str) {
  if (!str) return ''
  let normalized = str.replace(/-/g, '+').replace(/_/g, '/')
  while (normalized.length % 4) normalized += '='
  return atob(normalized)
}

export function decodeJwtPayload(token) {
  if (!token) return null
  const [, payload] = token.split('.')
  if (!payload) return null
  try {
    return JSON.parse(base64UrlDecode(payload))
  } catch (error) {
    console.warn('JWT payload 解码失败:', error)
    return null
  }
}

export function getUserIdFromToken(token) {
  const payload = decodeJwtPayload(token)
  return payload?.sub || payload?.userId || payload?.id || null
}

export function isTokenUsable(token) {
  if (!token) return false
  const payload = decodeJwtPayload(token)
  if (!payload) return false

  if (typeof payload.exp === 'number') {
    return payload.exp * 1000 > Date.now()
  }

  return true
}
