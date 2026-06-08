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
  USER_PROFILE: '/user/profile',          // 后端从 UserContext 获取 userId
  USER_UPDATE_BASIC: '/user/updateBasic',
  USER_UPDATE_HOBBIES: '/user/updateHobbies',
  USER_UPDATE_AI_TYPE: '/user/updateAIType',

  // ── AI 推荐 ──
  AI_RECOM: '/ai/recom',
  AI_RECOM_CLICK: '/ai/recom/click',
  AI_RECOM_GENERATE: '/ai/recom/generate',

  // ── 情感 ──
  EMOTION_GET: (userId) => `/emotion/${userId}`,
  EMOTION_MOOD: (userId) => `/emotion/${userId}/mood`,
  EMOTION_EVOLUTION: (userId) => `/emotion/${userId}/evolution`,
  EMOTION_HISTORY: (userId) => `/emotion/${userId}/history`,

  // ── 记忆/锚点 ──
  ANCHOR_LIST: '/ai/anchor/list',
  MEMORY_LIST: '/ai/memory/list',
  MEMORY_DETAIL: (id) => `/ai/memory/${id}`,
  MEMORY_DATE: (date) => `/ai/memory/date/${date}`,
  MEMORY_SEARCH: '/memory/search',
  MEMORY_SEARCH_BY_DATE: '/memory/search/by-date',

  // ── 消息 ──
  MESSAGES_BY_DATE: (userId) => `/messages/${userId}/by-date`,

  // ── 邮件 ──
  MAIL_LIST: '/mails',                    // 后端从 UserContext 获取 userId
  MAIL_READ: (mailId) => `/mails/${mailId}/read`,
  MAIL_READ_ALL: '/mails/read-all',

  // ── 设置 ──
  SETTINGS_GET: (userId) => `/settings/${userId}`,
  SETTINGS_SAVE: (userId) => `/settings/${userId}`,
  SETTINGS_PRESETS: '/settings/presets',

  // ── AI 图片 ──
  IMAGE_DESCRIBE: '/image/describe',
  IMAGE_EXTRACT_ELEMENTS: '/image/extract-elements',
  IMAGE_GENERATE: '/image/generate',

  // ── 兴趣标签 ──
  INTEREST_TAG_GENERATE: '/interest-tag/generate',

  // ── 图片/文件 ──
  UPLOAD_MESSAGE_IMAGE: '/oss/upload/message-image',
  OSS_UPLOAD_FROM_URL: '/oss/upload/from-url',
  OSS_DELETE: '/oss/delete',
  OSS_PRESIGNED_URL: '/oss/presigned-url',

  // ── 工具 ──
  getFullUrl(path) {
    return API_BASE + path
  },
}

export const WS = {
  CHAT: '/ws/chat',
  SEND_CHAT: '/app/chat',
}
