/**
 * 认证工具函数
 * 直接使用 localStorage 管理 token，避免 Pinia 状态同步问题
 */

import { STORAGE_KEYS } from '@/config/storage'

/**
 * 获取访问令牌
 * @returns {string|null}
 */
export function getAccessToken() {
  return localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
}

/**
 * 设置 token 和用户信息
 * @param {string} accessToken - 访问令牌
 * @param {string} refreshToken - 刷新令牌
 * @param {Object} user - 用户信息
 */
export function setToken(accessToken, refreshToken, user) {
  localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
  localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
  if (user) {
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
  }
}

/**
 * 清除所有认证信息（退出登录）
 */
export function clearToken() {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.USER);
}

/**
 * 获取用户 ID（从用户信息或 token 中解析）
 * @returns {string|null}
 */
export function getUserId() {
  const userStr = localStorage.getItem(STORAGE_KEYS.USER);
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      if (user && (user.id || user.userId)) {
        return user.id || user.userId;
      }
    } catch {
    }
  }

  const token = getAccessToken();
  if (token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.sub || payload.userId || payload.id;
    } catch {
    }
  }

  return null;
}
