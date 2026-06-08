import { ref, nextTick } from 'vue'
import { getUserId } from '@/utils/auth'
import request from '@/utils/request'
import { API } from '@/config/api'

/**
 * 消息 CRUD + 懒加载 + 日期辅助
 *
 * 架构：
 *   earlierMessages — 更早日期消息（向上滚动懒加载）
 *   historyMessages — 今日已持久化消息
 *   messages       — 当前会话消息（尚未持久化）
 *
 * @param {() => boolean} [aliveCheck] - 可选的生命周期检测函数（如 () => _isAlive），
 *   async 操作完成后若 aliveCheck() 返回 false 则放弃修改 ref，防止已卸载组件状态泄露
 */
export function useChatMessages(aliveCheck) {
  const messages = ref([])
  const historyMessages = ref([])
  const earlierMessages = ref([])
  const historyLoaded = ref(false)
  const isLoadingMore = ref(false)
  const noMoreMessages = ref(false)
  const currentMessage = ref(null)
  const interactionState = ref('idle')
  const isSending = ref(false)
  const messageListRef = ref(null)

  let loadCursorDate = null // 下一批要加载的日期游标
  let _fetchingToday = false // 防止 fetchTodayMessages 并发

  // ── 持久化 ──
  // 主缓存：历史 + 当前（混合兜底）
  function storageKey() {
    const d = new Date()
    return `zeeva-chat-${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
  }
  // 专属键：只存当前会话 messages，刷新后还原
  function sessionKey() {
    return storageKey() + '-session'
  }

  // ── localStorage 写入聚合并调度 ──
  let _storageScheduled = false

  function commitToStorage() {
    try {
      const all = [...historyMessages.value, ...messages.value].slice(-200)
      localStorage.setItem(storageKey(), JSON.stringify(all))
      localStorage.setItem(sessionKey(), JSON.stringify(messages.value.slice(-100)))
    } catch (e) {
      if (e instanceof DOMException && e.name === 'QuotaExceededError') {
        try {
          const prefix = 'zeeva-chat-'
          for (let i = localStorage.length - 1; i >= 0; i--) {
            const key = localStorage.key(i)
            if (key && key.startsWith(prefix) && key !== storageKey() && key !== sessionKey()) {
              localStorage.removeItem(key)
            }
          }
          const all = [...historyMessages.value, ...messages.value].slice(-200)
          localStorage.setItem(storageKey(), JSON.stringify(all))
          localStorage.setItem(sessionKey(), JSON.stringify(messages.value.slice(-100)))
        } catch (e2) {
          console.warn('useChatMessages: 存储配额已满，无法保存', e2)
        }
      } else {
        console.warn('useChatMessages:', e)
      }
    }
  }

  function saveToStorage() {
    if (_storageScheduled) return // 同一 tick 内多次调用合并为一次写入
    _storageScheduled = true
    const flush = () => {
      _storageScheduled = false
      commitToStorage()
    }

    if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
      window.requestIdleCallback(flush, { timeout: 1000 })
    } else {
      window.setTimeout(flush, 250)
    }
  }

  // 从专属键还原当前会话消息（去重：剔除 historyMessages 已有的）
  function restoreSessionMessages() {
    try {
      const raw = localStorage.getItem(sessionKey())
      if (!raw) return
      const cached = JSON.parse(raw)
      if (!Array.isArray(cached) || cached.length === 0) return
      const historyIds = new Set(historyMessages.value.map(m => m.id))
      messages.value = cached.filter(m => !historyIds.has(m.id))
    } catch (e) { console.warn('useChatMessages:', e) }
  }

  // 旧兜底：主缓存中服务器未确认的消息补进来
  function loadFromStorage() {
    try {
      const raw = localStorage.getItem(storageKey())
      if (!raw) return
      const cached = JSON.parse(raw)
      if (!Array.isArray(cached) || cached.length === 0) return
      const historyIds = new Set(historyMessages.value.map(m => m.id))
      const unsent = cached.filter(m => !historyIds.has(m.id))
      if (unsent.length > 0) {
        // 只补不在 messages 中的
        const msgIds = new Set(messages.value.map(m => m.id))
        const missing = unsent.filter(m => !msgIds.has(m.id))
        if (missing.length > 0) {
          messages.value = [...missing, ...messages.value]
        }
      }
    } catch (e) { console.warn('useChatMessages:', e) }
  }

  // ── 获取今日消息 ──
  async function fetchTodayMessages() {
    if (historyLoaded.value) return
    if (_fetchingToday) return // 防并发
    _fetchingToday = true
    const userId = getUserId()
    if (!userId) { _fetchingToday = false; return }
    let loadedFromServer = false
    try {
      const today = new Date()
      const dateStr =
        today.getFullYear() + '-' +
        String(today.getMonth() + 1).padStart(2, '0') + '-' +
        String(today.getDate()).padStart(2, '0')
      const res = await request.get(API.MESSAGES_BY_DATE(userId), { params: { date: dateStr } })
      if (aliveCheck && !aliveCheck()) return
      if (res.code === 200 && Array.isArray(res.data)) {
        historyMessages.value = res.data
        loadedFromServer = true
      }
      // 还原当前会话（从专属键），再补旧兜底
      restoreSessionMessages()
      loadFromStorage()
    } catch (e) {
      // 后端失败时从本地缓存恢复
      console.warn('useChatMessages fetchTodayMessages:', e)
      restoreSessionMessages()
      loadFromStorage()
    } finally {
      if (loadedFromServer) {
        historyLoaded.value = true
      }
      _fetchingToday = false
    }
  }

  // ── 懒加载更早消息（逐天回退，最多 7 天） ──
  async function loadEarlierMessages() {
    if (isLoadingMore.value || noMoreMessages.value) return false
    isLoadingMore.value = true
    const userId = getUserId()
    if (!userId) { isLoadingMore.value = false; return false }

    let attempts = 0
    const cursor = new Date(loadCursorDate || Date.now())
    try {
      while (attempts < 7) {
        attempts++
        cursor.setDate(cursor.getDate() - 1)
        const dateStr =
          cursor.getFullYear() + '-' +
          String(cursor.getMonth() + 1).padStart(2, '0') + '-' +
          String(cursor.getDate()).padStart(2, '0')
        const res = await request.get(API.MESSAGES_BY_DATE(userId), { params: { date: dateStr } })
        if (aliveCheck && !aliveCheck()) return false
        if (res.code === 200 && Array.isArray(res.data) && res.data.length > 0) {
          earlierMessages.value = [...res.data.map(m => ({ ...m, _batchDate: dateStr })), ...earlierMessages.value]
          loadCursorDate = cursor.getTime()
          return true
        }
      }
      noMoreMessages.value = true
      return false
    } catch (e) {
      console.warn('useChatMessages loadEarlierMessages:', e)
      return false
    } finally {
      isLoadingMore.value = false
    }
  }

  // ── 滚动（Promise coalescing，同一 tick 内多次调用只滚一次） ──
  let scrollCoalesce = false
  async function scrollToBottom() {
    if (scrollCoalesce) return // 同一 tick 已排队，跳过
    scrollCoalesce = true
    await nextTick()
    const flushScroll = () => {
      if (messageListRef.value) {
        messageListRef.value.scrollTop = messageListRef.value.scrollHeight
      }
      scrollCoalesce = false
    }

    if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(flushScroll)
    } else {
      flushScroll()
    }
  }

  // ── 消息 ID 生成（Date.now() + 单调计数器，消除同毫秒碰撞） ──
  let _msgIdCounter = 0
  function nextMsgId() {
    return `${Date.now()}-${++_msgIdCounter}`
  }

  // ── 消息工厂 ──
  function addUserMessage(text) {
    messages.value.push({
      id: nextMsgId(), role: 'user', type: 'text',
      content: text, timestamp: new Date().toISOString()
    })
    saveToStorage()
  }

  function addImageMessage(fileUrl) {
    messages.value.push({
      id: nextMsgId(), role: 'user', type: 'image',
      content: fileUrl, timestamp: new Date().toISOString()
    })
    saveToStorage()
  }

  function addAiMessage(content, isComplete) {
    const msg = {
      id: nextMsgId(), role: 'ai', type: 'text',
      content, isTemp: !isComplete, isComplete,
      timestamp: new Date().toISOString()
    }
    messages.value.push(msg)
    saveToStorage()
    return msg
  }

  function addErrorBubble(text) {
    messages.value.push({
      id: nextMsgId(), role: 'ai', type: 'text',
      content: text, isComplete: true, isError: true,
      timestamp: new Date().toISOString()
    })
    saveToStorage()
  }

  // ── 流式控制 ──
  function completeCurrentMessage() {
    if (currentMessage.value) {
      currentMessage.value.isComplete = true
      currentMessage.value.isTemp = false
    }
    currentMessage.value = null
    isSending.value = false
    interactionState.value = 'idle'
  }

  function setCurrentMessage(msg) {
    currentMessage.value = msg
    isSending.value = true
  }

  // ── 日期辅助 ──
  function formatTime(iso) {
    if (!iso) return ''
    const d = new Date(iso)
    return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
  }

  function formatDateLabel(iso) {
    if (!iso) return ''
    const d = new Date(iso), today = new Date(), yesterday = new Date()
    yesterday.setDate(yesterday.getDate() - 1)
    if (d.toDateString() === today.toDateString()) return '今天'
    if (d.toDateString() === yesterday.toDateString()) return '昨天'
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return d.getFullYear() === today.getFullYear() ? `${mm}月${dd}日` : `${d.getFullYear()}.${mm}.${dd}`
  }

  function isNewDateGroup(msg, prevMsg) {
    if (!msg?.timestamp) return false
    if (!prevMsg?.timestamp) return true
    return msg.timestamp.substring(0, 10) !== prevMsg.timestamp.substring(0, 10)
  }

  return {
    messages, historyMessages, earlierMessages,
    historyLoaded, isLoadingMore, noMoreMessages,
    currentMessage, interactionState, isSending, messageListRef,
    loadFromStorage, saveToStorage, restoreSessionMessages, fetchTodayMessages, loadEarlierMessages,
    scrollToBottom, addUserMessage, addImageMessage, addAiMessage, addErrorBubble,
    completeCurrentMessage, setCurrentMessage, nextMsgId,
    formatTime, formatDateLabel, isNewDateGroup,
  }
}
