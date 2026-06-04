<script setup>
import { ref, watch, nextTick, onMounted, onBeforeUnmount, inject } from 'vue'
import {
  connect,
  disconnect,
  sendChat,
  setCallbacks,
  reconnectAttempts,
  MAX_RECONNECT_ATTEMPTS
} from '@/utils/chatWebSocket'
import request from '@/utils/request'
import { API } from '@/config/api'
import { useStreamingAudioPlayer } from '@/composables/useStreamingAudioPlayer'
import { useGsapAnimation } from '@/composables/useGsapAnimation'
import { useChatMessages } from '@/composables/useChatMessages'
import { useLive2dChat } from '@/composables/useLive2dChat'
import { getUserId } from '@/utils/auth'
import { OML2D_KEY } from '@/symbols'
import ChatMessages from './ChatMessages.vue'

// 从父组件（Dashboard.vue）注入 oml2d 实例（响应式 ref）
const oml2dInstance = inject(OML2D_KEY, ref(null))
let pendingLive2dText = ''

const props = defineProps({
  isActive: { type: Boolean, default: true }
})
const emit = defineEmits(['open-history'])

const chatState = ref('collapsed')
const isBoosted = ref(false)
// 刷新后恢复面板状态
try {
  const saved = localStorage.getItem('chat-ui-state')
  if (saved) {
    const parsed = JSON.parse(saved)
    if (parsed.chatState === 'expanded') chatState.value = 'expanded'
    if (parsed.isBoosted) isBoosted.value = true
  }
  } catch (e) { console.warn('ChatWindow:', e) }
const inputText = ref('')
const inputRef = ref(null)
const fileInputRef = ref(null)
let sendTimeout = null // 发送超时定时器，防止 isSending 卡死
let streamSaveTimer = null // 流式内容节流持久化
let fileReplyTimer = null // 图片上传自动回复
let scrollThrottled = false // 滚动加载节流

// WebSocket 连接状态
const connectionStatus = ref('disconnected')
const connectionText = ref('')

// 音频播放
const { init: initAudio, appendAudioChunk, stop: stopAudio, stats: audioStats } = useStreamingAudioPlayer()

// ── 消息 composable ──
const {
  messages, historyMessages, earlierMessages,
  isLoadingMore, noMoreMessages,
  isSending, interactionState, currentMessage, messageListRef,
  loadFromStorage, saveToStorage, fetchTodayMessages, loadEarlierMessages,
  scrollToBottom, addImageMessage, addAiMessage, addErrorBubble,
  completeCurrentMessage, setCurrentMessage,
} = useChatMessages(() => _isAlive)

// ── GSAP composable ──
const { gsap, timeline, entryStagger } = useGsapAnimation()

// ── Live2D 消息气泡联动 ──
const { pushMessage: pushLive2dMessage, dispose: disposeLive2dChat } = useLive2dChat(oml2dInstance)

// --- 外部调用接口 ---
const activateFromBubble = () => {
  chatState.value = 'expanded'
  isBoosted.value = true
  nextTick(() => inputRef.value?.focus())
}

const collapse = () => {
  chatState.value = 'collapsed'
  isBoosted.value = false
}

const toggleBoost = () => {
  if (chatState.value === 'collapsed') {
    // 折叠态 → 展开 + 全屏
    chatState.value = 'expanded'
    isBoosted.value = true
  } else if (isBoosted.value) {
    // 展开 + 全屏 → 收起
    isBoosted.value = false
    chatState.value = 'collapsed'
  } else {
    // 展开（非全屏）→ 全屏
    isBoosted.value = true
  }
}

const openHistory = () => emit('open-history')

defineExpose({ activateFromBubble, collapse })

// --- WebSocket 回调 (在 onMounted 中注册，避免模块级副作用) ---
let disposeCallbacks = null

function handleTextMessage(message) {
  if (!_isAlive || !message?.payload) return
  const { content, isComplete } = message.payload

  // 确保聊天面板展开
  if (chatState.value === 'collapsed') chatState.value = 'expanded'

  if (currentMessage.value && !isComplete) {
    // === 场景 1：流式追加（已有 currentMessage，未结束） ===
    currentMessage.value.content += content
    pendingLive2dText += content
    // 节流失效化：每 2s 落盘一次，防止刷新丢太多
    if (!streamSaveTimer) {
      streamSaveTimer = setTimeout(() => {
        streamSaveTimer = null
        saveToStorage()
      }, 2000)
    }
  } else if (isComplete) {
    // === 场景 2：流式结束 / 单条完整消息 ===
    if (currentMessage.value) {
      // 流式结束：标记已有消息为完成态
      currentMessage.value.isComplete = true
      currentMessage.value.isTemp = false
    } else {
      // 单条完整消息（非流式，如 WakeUp 主动推送）
      const msg = {
        id: message.messageId || Date.now(),
        role: 'ai', type: 'text',
        content, isTemp: false, isComplete: true,
        timestamp: new Date().toISOString()
      }
      messages.value.push(msg)
      pendingLive2dText = content
    }
    // 统一收尾：停止音频播放，清理状态
    stopAudio()
    if (sendTimeout) clearTimeout(sendTimeout)
    if (streamSaveTimer) { clearTimeout(streamSaveTimer); streamSaveTimer = null }
    currentMessage.value = null
    isSending.value = false
    interactionState.value = 'idle'
    saveToStorage()  // 流式结束，持久化完整消息
    if (pendingLive2dText) {
      pushLive2dMessage(pendingLive2dText, 'ai')
      pendingLive2dText = ''
    }
  } else {
    // === 场景 3：首条流式片段（无 currentMessage，未结束） ===
    const newMessage = {
      id: message.messageId || Date.now(),
      role: 'ai', type: 'text',
      content, isTemp: true, isComplete: false,
      timestamp: new Date().toISOString()
    }
    messages.value.push(newMessage)
    currentMessage.value = newMessage
    pendingLive2dText = content
    saveToStorage()  // 首条片段写入缓存
    // Live2D 先展示简短提示
    if (content) pushLive2dMessage(content.length > 50 ? content.substring(0, 50) + '…' : content, 'ai')
  }

  scrollToBottom()
}

async function handleAudioChunk(arrayBuffer) {
  if (!_isAlive) return
  try {
    if (audioStats.value.chunksReceived === 0) {
      await initAudio()
      if (!_isAlive) { stopAudio(); return }
    }
    await appendAudioChunk(arrayBuffer)
  } catch (e) {
    console.error('音频处理异常', e)
  }
}

function handleError(error) {
  if (!_isAlive) return
  if (sendTimeout) clearTimeout(sendTimeout)
  isSending.value = false
  interactionState.value = 'idle'
  // 重置流式消息状态，避免残留的 temp 标记
  currentMessage.value = null
  pendingLive2dText = ''
  // 通知用户发生了错误
  addErrorBubble(error?.message || '连接异常，请重试')
  scrollToBottom()
}

function handleStatusChange(status) {
  if (!_isAlive) return
  const prevStatus = connectionStatus.value
  connectionStatus.value = status
  if (status === 'connected') {
    connectionText.value = ''
    // 从断开状态恢复后，重新拉取今日消息（补全离线期间遗漏）
    if (prevStatus === 'disconnected' || prevStatus === 'error') {
      fetchTodayMessages()
    }
  } else if (status === 'connecting') {
    connectionText.value = reconnectAttempts.value > 0
      ? `重新连接 (${reconnectAttempts.value}/${MAX_RECONNECT_ATTEMPTS})...`
      : '正在连接...'
  } else {
    connectionText.value = reconnectAttempts.value > 0
      ? `连接已断开 (${reconnectAttempts.value}/${MAX_RECONNECT_ATTEMPTS})`
      : '连接已断开'
  }
}

// ── 系统消息：AI 主动唤醒 / 通知（来自 WakeUpScheduler） ──
function handleSystemMessage(message) {
  if (!_isAlive) return
  const content = message.payload?.content || message.payload?.text || ''
  if (!content) return

  // 显示在聊天中（作为 AI 消息）
  addAiMessage(content, true)

  // 推送 Live2D 气泡（系统通知）
  pushLive2dMessage(content, 'system')

  // 展开聊天窗让用户看到
  chatState.value = 'expanded'
  scrollToBottom()
}

// ── 情绪更新 ──
const latestEmotion = ref(null)
function handleEmotionUpdate(emotion) {
  if (!_isAlive) return
  latestEmotion.value = emotion
}

// --- 交互逻辑 ---
const handleFocus = () => {
  chatState.value = 'expanded'
  // 确保输入框可用（防止 isSending 卡死后用户无法重新聚焦）
  if (isSending.value) {
    isSending.value = false
    interactionState.value = 'idle'
    // 不移除 currentMessage，避免后续 WS 分块进入 Scenario 3 产生幽灵消息
    if (sendTimeout) clearTimeout(sendTimeout)
  }
}

let _inputDebounceTimer = null
const handleInput = () => {
  if (_inputDebounceTimer) clearTimeout(_inputDebounceTimer)
  _inputDebounceTimer = setTimeout(() => {
    interactionState.value = inputText.value.trim() ? 'typing' : 'idle'
    _inputDebounceTimer = null
  }, 150)
}

let userMessageIdCounter = 0

const handleSend = () => {
  if (!_isAlive || isSending.value) return  // 防止重复发送
  const text = inputText.value.trim()
  if (!text) return

  // 确保面板展开
  chatState.value = 'expanded'

  // 清除图片上传的延迟回复，防止它在文本发送后乱入
  if (fileReplyTimer) { clearTimeout(fileReplyTimer); fileReplyTimer = null }

  // 追踪这条用户消息，避免 WS 消息插入后用 pop() 误删
  const sentMsgId = `user-${Date.now()}-${++userMessageIdCounter}`
  const sentMsg = {
    id: sentMsgId,
    role: 'user', type: 'text',
    content: text,
    timestamp: new Date().toISOString()
  }
  messages.value.push(sentMsg)
  saveToStorage()

  inputText.value = ''  // 清空输入框
  interactionState.value = 'responding'
  isSending.value = true
  scrollToBottom()

  // 30 秒超时保护：防止 AI 不回复导致 isSending 卡死
  if (sendTimeout) clearTimeout(sendTimeout)
  sendTimeout = setTimeout(() => {
    if (isSending.value) {
      isSending.value = false
      interactionState.value = 'idle'
      currentMessage.value = null
      addErrorBubble('回复超时，请重试')
      scrollToBottom()
    }
  }, 30000)

  const success = connectionStatus.value === 'connected' ? sendChat(text, true) : false

  if (!success) {
    clearTimeout(sendTimeout)
    // 按 ID 精确删除，不依赖 pop()——避免 WS 消息插入后误删
    const idx = messages.value.findIndex(m => m.id === sentMsgId)
    if (idx !== -1) {
      messages.value.splice(idx, 1)
      saveToStorage()
    }
    addErrorBubble('发送失败：WebSocket 未连接')
    isSending.value = false
    interactionState.value = 'idle'
    scrollToBottom()
  }
}

const triggerImageUpload = () => {
  chatState.value = 'expanded'
  fileInputRef.value.click()
}

const fileUrlRef = ref('')

const handleFileChange = async (e) => {
  if (!_isAlive || isSending.value) return  // 防止发送中重复上传
  const file = e.target.files[0]
  if (!file) return

  // 释放上一个 blob URL（防内存泄漏）
  if (fileUrlRef.value) URL.revokeObjectURL(fileUrlRef.value)

  const fileUrl = URL.createObjectURL(file)
  fileUrlRef.value = fileUrl
  addImageMessage(fileUrl)
  interactionState.value = 'responding'
  isSending.value = true
  scrollToBottom()

  try {
    // 上传图片到 OSS
    const formData = new FormData()
    formData.append('file', file)
    const res = await request.post(API.UPLOAD_MESSAGE_IMAGE, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000
    })
    if (!_isAlive) return
    if (res.code === 200 && res.data?.url) {
      const imageUrl = res.data.url
      // 替换本地 blob URL 为真实 URL
      const imgMsg = messages.value.find(m => m.type === 'image' && m.content === fileUrl)
      if (imgMsg) imgMsg.content = imageUrl
      // 通过 WebSocket 发送（可能带用户已输入的文本）
      const text = inputText.value.trim()
      inputText.value = ''
      const wsOk = sendChat(text, true, imageUrl)
      if (!wsOk) {
        // WebSocket 未连接，显示提示
        addErrorBubble('图片已上传，但 WebSocket 未连接，AI 将无法处理')
        fallbackImageReply()
        return
      }
    } else {
      fallbackImageReply()
    }
  } catch (e) {
    console.error('图片上传失败:', e)
    fallbackImageReply()
  } finally {
    isSending.value = false
    if (interactionState.value !== 'typing') interactionState.value = 'idle'
    e.target.value = ''
  }
}

function fallbackImageReply() {
  if (fileReplyTimer) clearTimeout(fileReplyTimer)
  fileReplyTimer = setTimeout(() => {
    fileReplyTimer = null
    addAiMessage('画面已经刻录，有什么想对我说的吗？', true)
    interactionState.value = 'idle'
    scrollToBottom()
  }, 1500)
}

let scrollThrottleTimer = null

// ── 懒加载：滚动到顶部触发 ──
const handleScroll = () => {
  if (!_isAlive || scrollThrottled) return
  const el = messageListRef.value
  if (!el || isLoadingMore.value || noMoreMessages.value) return
  scrollThrottled = true
  if (el.scrollTop < 100) {
    const prevHeight = el.scrollHeight
    loadEarlierMessages().then(found => {
      if (found) nextTick(() => { el.scrollTop = el.scrollHeight - prevHeight })
    }).catch(() => {})
  }
  scrollThrottleTimer = setTimeout(() => { scrollThrottled = false }, 100)
}

// 持久化面板状态（300ms 防抖，避免高频切换触发同步 IO）
let _uiPersistTimer = null
watch([chatState, isBoosted], () => {
  if (_uiPersistTimer) clearTimeout(_uiPersistTimer)
  _uiPersistTimer = setTimeout(() => {
    try {
      localStorage.setItem('chat-ui-state', JSON.stringify({
        chatState: chatState.value,
        isBoosted: isBoosted.value,
      }))
    } catch (e) { console.warn('ChatWindow:', e) }
    _uiPersistTimer = null
  }, 300)
})

watch(() => props.isActive, (newVal) => {
  if (!newVal) collapse()
})

// --- 组件存活守卫：防止 unmount 后 async 回调继续操作 ref ---
let _isAlive = true
onBeforeUnmount(() => { _isAlive = false })

// --- 生命周期 ---
onMounted(async () => {
  await fetchTodayMessages()
  // 如果组件在 fetchTodayMessages 期间被卸载，放弃后续操作
  if (!_isAlive) return

  const userId = getUserId() || 'unknown'

  // 注册 WebSocket 回调
  disposeCallbacks = setCallbacks({
    onTextMessage: handleTextMessage,
    onAudioChunk: handleAudioChunk,
    onEmotionUpdate: handleEmotionUpdate,
    onSystemMessage: handleSystemMessage,
    onAuthSuccess: (payload) => import.meta.env.DEV && console.log('[WS] 认证成功:', payload),
    onPeekRequest: (payload) => {
      if (import.meta.env.DEV) console.log('[WS] 偷看请求:', payload)
      const peekText = payload?.text || '正在悄悄看着你...'
      addAiMessage(`🔍 ${peekText}`, true)
      scrollToBottom()
    },
    onError: handleError,
    onStatusChange: handleStatusChange,
  })

  connect(userId)

  // Scroll to bottom after messages are loaded and WS is connected
  await nextTick(() => {
    if (_isAlive) scrollToBottom()
  })

  if (!_isAlive) return

  // GSAP 入场动画
  nextTick(() => {
    if (!_isAlive) return
    entryStagger('.message-item', { y: 15, stagger: 0.04, duration: 0.4 })
    timeline().to('.right-action.can-send', {
      scale: 1.05, duration: 1.5, repeat: -1, yoyo: true, ease: 'sine.inOut'
    })
  })
})

onBeforeUnmount(() => {
  if (sendTimeout) clearTimeout(sendTimeout)
  if (streamSaveTimer) { clearTimeout(streamSaveTimer); streamSaveTimer = null }
  if (fileReplyTimer) { clearTimeout(fileReplyTimer); fileReplyTimer = null }
  if (scrollThrottleTimer) { clearTimeout(scrollThrottleTimer); scrollThrottleTimer = null }
  if (_inputDebounceTimer) { clearTimeout(_inputDebounceTimer); _inputDebounceTimer = null }
  if (_uiPersistTimer) { clearTimeout(_uiPersistTimer); _uiPersistTimer = null }
  if (disposeCallbacks) disposeCallbacks()
  if (fileUrlRef.value) { URL.revokeObjectURL(fileUrlRef.value); fileUrlRef.value = '' }
  disconnect()
  stopAudio()
  disposeLive2dChat()
})
</script>

<template>
  <div
      class="chat-window-container"
      :class="{
        'is-hidden': !isActive,
        'is-expanded': chatState === 'expanded',
        'is-boosted': isBoosted
      }"
  >
    <input
        type="file"
        ref="fileInputRef"
        style="display: none"
        accept="image/*"
        @change="handleFileChange"
    />

    <div class="glass-morph-bg" @click.stop></div>

    <transition name="content-fade">
      <div v-show="chatState === 'expanded'" class="chat-panel" @click.stop>
        <div class="terminal-decor">
          <div class="decor-left">
            <span class="decor-dot"></span>
            <span class="decor-line"></span>
            <span class="decor-text">SECURE_LINK // ACTIVE</span>
          </div>
          <div class="decor-actions">
            <button class="decor-btn" @mousedown.prevent.stop="toggleBoost">
              {{ isBoosted ? '收起 ↘' : '展开 ↗' }}
            </button>
            <button class="decor-btn highlight" @mousedown.prevent.stop="openHistory">
              查看全部记录 ≡
            </button>
          </div>
        </div>

        <div class="message-list" ref="messageListRef" @scroll="handleScroll">
          <ChatMessages
            :messages="messages"
            :history-messages="historyMessages"
            :earlier-messages="earlierMessages"
            :is-loading-more="isLoadingMore"
            :no-more-messages="noMoreMessages"
            :is-sending="isSending"
            :current-message="currentMessage"
            :connection-status="connectionStatus"
            :connection-text="connectionText"
            :latest-emotion="latestEmotion"
          />
        </div>
      </div>
    </transition>

    <div class="input-bar" :class="{ 'is-expanded': chatState === 'expanded' }" @click.stop>
      <div class="left-action action-btn" @mousedown.prevent.stop="triggerImageUpload" title="上传图片">
        <span class="icon-star">✦</span>
      </div>

      <div class="input-field">
        <input
            ref="inputRef"
            v-model="inputText"
            :placeholder="connectionStatus === 'connected' ? '在此唤醒思绪...' : '正在建立连接...'"
            @focus="handleFocus"
            @keyup.enter="handleSend"
            @input="handleInput"
            :disabled="isSending"
        />
        <div class="input-focus-line"></div>
      </div>

      <div
          class="right-action action-btn"
          :class="{ 'can-send': inputText.trim() || interactionState === 'responding' }"
          @mousedown.prevent.stop="handleSend"
      >
        <div v-if="interactionState === 'responding'" class="send-loading">
          <div class="loading-ring"></div>
        </div>
        <span v-else class="icon-arrow">→</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-window-container {
  position: fixed;
  left: 50%; bottom: 40px;
  transform: translateX(-50%);
  width: 600px;
  z-index: 1500;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  pointer-events: none;
  transition: all 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.chat-window-container.is-hidden { opacity: 0; transform: translate(-50%, 40px); pointer-events: none; }
.chat-window-container.is-hidden .chat-panel,
.chat-window-container.is-hidden .input-bar,
.chat-window-container.is-hidden .glass-morph-bg { pointer-events: none; }

.glass-morph-bg {
  position: absolute; inset: 0;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px) saturate(120%);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  pointer-events: auto;
  z-index: -1;
  opacity: 1;
  transition: all 0.4s ease;
}

.chat-window-container.is-expanded .glass-morph-bg {
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 15px 50px rgba(0, 0, 0, 0.12);
}

.chat-panel {
  position: relative; height: 0;
  pointer-events: auto; display: flex; flex-direction: column;
  overflow: hidden;
  transition: height 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}
.is-expanded .chat-panel { height: 120px; }
.is-boosted .chat-panel { height: 350px; }

.terminal-decor {
  padding: 15px 25px 0;
  display: flex; justify-content: space-between; align-items: center;
}
.decor-left {
  display: flex; align-items: center; gap: 10px;
  opacity: 0.3; flex: 1;
}
.decor-dot { width: 4px; height: 4px; background: #333; border-radius: 50%; }
.decor-line { width: 40px; height: 1px; background: linear-gradient(90deg, #333, transparent); }
.decor-text { font-size: 9px; letter-spacing: 1.5px; color: #333; }

.decor-actions { display: flex; gap: 12px; }
.decor-btn {
  background: none; border: none; padding: 0;
  font-size: 11px; color: #888; cursor: pointer;
  letter-spacing: 1px; transition: all 0.3s ease;
}
.decor-btn:hover { color: #333; text-shadow: 0 0 5px rgba(0,0,0,0.1); }
.decor-btn.highlight { color: #5ea4ea; }
.decor-btn.highlight:hover { color: #3b82f6; text-shadow: 0 0 8px rgba(59, 130, 246, 0.3); }

.message-list {
  flex: 1; overflow-y: auto; padding: 20px 25px;
  display: flex; flex-direction: column; gap: 20px;
  scrollbar-width: none;
}
.message-list::-webkit-scrollbar { display: none; }

/* ── GPU 分层 ── */
.glass-morph-bg { will-change: transform, opacity; contain: layout style; }

.input-bar {
  height: 65px; display: flex; align-items: center;
  pointer-events: auto; padding: 0 20px; flex-shrink: 0;
  /* 确保输入栏不受容器 is-hidden 的 opacity 影响 */
  opacity: 1; visibility: visible;
}
.input-field { flex: 1; position: relative; margin: 0 15px; height: 100%; display: flex; align-items: center; }
.input-field input {
  width: 100%; background: transparent; border: none; outline: none;
  color: #333; font-size: 15px;
}
.input-field input::placeholder { color: #aaa; }

.input-focus-line {
  position: absolute; bottom: 18px; left: 0; width: 0; height: 1px;
  background: #333; transition: width 0.4s ease;
}
.input-field input:focus ~ .input-focus-line { width: 100%; }

.action-btn {
  width: 40px; height: 40px; border-radius: 50%;
  display: flex; justify-content: center; align-items: center;
  color: #666; cursor: pointer; transition: 0.3s;
}
.action-btn:hover { background: rgba(0,0,0,0.05); color: #000; }

.right-action { opacity: 0; transform: scale(0.8); pointer-events: none; }
.right-action.can-send { opacity: 1; transform: scale(1); pointer-events: auto; }

.loading-ring {
  width: 16px; height: 16px; border: 2px solid rgba(0,0,0,0.1);
  border-top-color: #333; border-radius: 50%;
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.content-fade-enter-active, .content-fade-leave-active { transition: all 0.3s ease; }
.content-fade-enter-from, .content-fade-leave-to { opacity: 0; transform: translateY(5px); }
</style>