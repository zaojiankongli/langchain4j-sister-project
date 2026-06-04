/**
 * 安全的 localStorage 封装
 *
 * 在所有隐私/无痕模式下，或者配额超限时，localStorage 会抛出 DOMException。
 * 使用这三个函数替代直接调用 localStorage，确保应用不会因为存储不可用而崩溃。
 */

export function safeGet(key, fallback = null) {
  try {
    const v = localStorage.getItem(key)
    return v !== null ? v : fallback
  } catch {
    return fallback
  }
}

export function safeSet(key, value) {
  try {
    localStorage.setItem(key, value)
    return true
  } catch {
    return false
  }
}

export function safeRemove(key) {
  try {
    localStorage.removeItem(key)
    return true
  } catch {
    return false
  }
}

/**
 * 安全的 JSON.parse(localStorage.getItem(...)) —— 双层防御
 */
export function safeGetJSON(key, fallback = null) {
  try {
    const v = localStorage.getItem(key)
    if (v === null) return fallback
    return JSON.parse(v)
  } catch {
    return fallback
  }
}

/**
 * 安全的 JSON.stringify + setItem
 */
export function safeSetJSON(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
    return true
  } catch {
    return false
  }
}
