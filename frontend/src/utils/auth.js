/**
 * 认证工具函数
 * 通过 safeGet/safeSet 封装直接访问 localStorage，与 auth store 保持同步。
 *
 * 注意：token 的写入和清除应统一通过 stores/auth.js 的 setTokens() / clearAuth() 完成，
 * 此模块仅提供只读查询（getAccessToken / getUserId），避免双源写入导致状态不一致。
 */

import { STORAGE_KEYS } from '@/config/storage'
import { safeGetJSON } from '@/utils/storage'
import { getUserIdFromToken } from '@/utils/jwt'
import { getAccessTokenCache } from '@/utils/tokenCache'

/**
 * 获取访问令牌
 * @returns {string|null}
 */
export function getAccessToken() {
  return getAccessTokenCache()
}

/**
 * 获取用户 ID（从用户信息或 token 中解析）
 * @returns {string|null}
 */
export function getUserId() {
  const userData = safeGetJSON(STORAGE_KEYS.USER)
  if (userData && (userData.id || userData.userId)) {
    return userData.id || userData.userId;
  }

  const token = getAccessToken();
  if (token) return getUserIdFromToken(token);

  return null;
}
