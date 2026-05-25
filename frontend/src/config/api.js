/**
 * API 端点定义
 *
 * 所有后端 REST 接口集中定义在此，组件中不允许出现硬编码路径。
 * 函数形式用于动态路径（如包含 userId），字符串形式用于固定路径。
 */

const API_BASE = '/api'

export const API = {
  // ── 认证 ──
  AUTH_SEND_CODE: '/auth/send-code',
  AUTH_LOGIN: '/auth/login',
  AUTH_REFRESH: '/auth/refresh',
  AUTH_LOGOUT: '/auth/logout',
  AUTH_COMPLETE_PROFILE: '/auth/complete-profile',

  // ── 用户 ──
  USER_AVATAR: '/user/avatar',
  USER_PROFILE: (userId) => `/user/${userId}/profile`,
  USER_UPDATE_BASIC: '/user/updateBasic',
  USER_UPDATE_HOBBIES: '/user/updateHobbies',
  USER_UPDATE_AI_TYPE: '/user/updateAIType',

  // ── AI 推荐 ──
  AI_RECOM: '/ai/recom',
  AI_RECOM_CLICK: '/ai/recom/click',

  // ── 情感 ──
  EMOTION_EVOLUTION: (userId) => `/emotion/${userId}/evolution`,
  EMOTION_HISTORY: (userId) => `/emotion/${userId}/history`,

  // ── 记忆/锚点 ──
  ANCHOR_LIST: '/ai/anchor/list',
  MEMORY_LIST: '/ai/memory/list',

  // ── 消息 ──
  MESSAGES_BY_DATE: (userId) => `/messages/${userId}/by-date`,

  // ── 邮件 ──
  MAIL_LIST: (userId) => `/mails?userId=${userId}`,
  MAIL_READ: (mailId) => `/mails/${mailId}/read`,
  MAIL_READ_ALL: '/mails/read-all',
  MAIL_DETAIL: (id) => `/mails/${id}/detail`,
  MY_PROFILE: '/user/profile',

  // ── 工具 ──
  getFullUrl(path) {
    return API_BASE + path
  },
}

export const WS = {
  CHAT: '/ws/chat',
  SEND_CHAT: '/app/chat',
}
