/**
 * Live2D × 聊天消息联动
 *
 * - 用户消息  → Live2D 侧耳倾听（💭 前缀，短提示）
 * - AI 回复    → Live2D 逐句说出（长气泡，高优先级）
 * - 主动唤醒   → Live2D 特殊提示（🎵 前缀）
 * - 定时提醒   → Live2D 通知式提示（⏰ 前缀，中优先级）
 *
 * 队列机制：同角色连续消息合并为一条显示，避免气泡轰炸。
 * 生命周期：通过 generation 计数器防止卸载后的回调污染。
 *
 * @param {import('vue').Ref<object|null>} oml2dRef — oml2d 实例 ref
 * @returns {{ pushMessage, clearQueue, isActive, dispose }}
 */
export function useLive2dChat(oml2dRef) {
  let queueTimer = null
  let messageQueue = []
  let retryCount = 0
  const QUEUE_INTERVAL = 2800
  const MAX_RETRIES = 20
  let _alive = true
  let _gen = 0

  // ── 常量映射 ──
  const PREFIX_MAP = {
    user: '💭 ',
    ai: '',
    system: '🎵 ',
    notification: '⏰ ',
  }
  const PRIORITY_MAP = {
    user: 3,
    ai: 4,
    system: 4,
    notification: 3,
  }

  /**
   * 推送消息到 Live2D 气泡队列
   * @param {string} text     - 消息内容
   * @param {'user'|'ai'|'system'|'notification'} role - 消息角色
   */
  function pushMessage(text, role = 'system') {
    if (!_alive || !oml2dRef.value || !text) return

    // 同角色合并：如果队尾消息角色相同，合并文本而非追加条目
    const lastIdx = messageQueue.length - 1
    if (lastIdx >= 0 && messageQueue[lastIdx].role === role) {
      messageQueue[lastIdx].text += '\n' + text
      return
    }

    messageQueue.push({ text, role })
    processQueue()
  }

  /** 队列处理 */
  function processQueue() {
    if (queueTimer || messageQueue.length === 0) return

    const item = messageQueue.shift()
    const gen = _gen

    // oml2d 实例尚未就绪 → 重试等待
    const oml2d = oml2dRef.value
    if (!oml2d) {
      if (retryCount >= MAX_RETRIES) return
      retryCount++
      queueTimer = setTimeout(() => {
        if (gen !== _gen) return
        queueTimer = null
        processQueue()
      }, 500)
      return
    }
    retryCount = 0
    if (gen !== _gen) return

    const prefix = PREFIX_MAP[item.role] || ''
    const priority = PRIORITY_MAP[item.role] || 3

    // 截断单行超长文本，保留多行合并的完整性
    let display = item.text
    const firstNewline = display.indexOf('\n')
    if (firstNewline > 80) {
      // 首行超长，只取前 80 字符
      display = display.substring(0, 80) + '…'
    } else if (display.length > 80) {
      display = display.substring(0, 80) + '…'
    }

    // 持续时间：AI 消息按长度动态，其他统一
    const duration = item.role === 'ai'
      ? Math.min(8000, Math.max(2500, 3000 + display.length * 30))
      : 4000

    oml2d.tipsMessage(prefix + display, duration, priority)
    // tipsMessage 后二次校验：若队列在此期间被清除，跳过后续调度
    if (gen !== _gen) return

    queueTimer = setTimeout(() => {
      // 注意：此回调不含任何 await，_gen 不可能在执行中途变更；
      // 但保留 gen !== _gen 检查以防御未来重构引入异步路径
      if (gen !== _gen) return
      queueTimer = null
      processQueue()
    }, QUEUE_INTERVAL)
  }

  /** 清空队列 + 立即隐藏当前气泡（不 bump _gen，避免误杀正在执行的回调） */
  function clearQueue() {
    messageQueue = []
    if (queueTimer) { clearTimeout(queueTimer); queueTimer = null }
    if (oml2dRef.value) oml2dRef.value.clearTips()
  }

  function dispose() {
    _alive = false
    _gen++ // 使所有待定 setTimeout 回调失效
    clearQueue()
  }

  return { pushMessage, clearQueue, dispose }
}
