<script setup>
import { ref, watch, nextTick, onMounted, onBeforeUnmount, inject } from 'vue'
import {
  connect,
  disconnect,
  sendChat,
  setCallbacks
} from '@/utils/chatWebSocket'
import { useStreamingAudioPlayer } from '@/composables/useStreamingAudioPlayer'
import { getUserId } from '@/utils/auth'
import request from '@/utils/request'
import { API } from '@/config/api'

// 从父组件（Dashboard.vue）注入 oml2d 实例（响应式 ref）
const oml2dInstance = inject('oml2d', ref(null))
const getOml2d = () => oml2dInstance.value
// 累积完整回复，流式结束后一次性推送到 live2d 提示框
let pendingLive2dText = ''

const props = defineProps({
  isActive: { type: Boolean, default: true }
})
// 新增 open-history 事件
const emit = defineEmits(['trigger-image-upload', 'open-history'])

const chatState = ref('collapsed')
const interactionState = ref('idle')
const isBoosted = ref(false)
const inputText = ref('')
const messages = ref([])
const messageListRef = ref(null)
const inputRef = ref(null)
const fileInputRef = ref(null)

// 今天的历史消息
const historyMessages = ref([])
const historyLoaded = ref(false)

// WebSocket 连接状态
const connectionStatus = ref('disconnected')
const connectionText = ref('')
const isSending = ref(false)

// 音频播放
const { init: initAudio, appendAudioChunk, stop: stopAudio, stats: audioStats } = useStreamingAudioPlayer()

// 当前正在接收的流式消息
const currentMessage = ref(null)

// --- 外部调用接口 ---
const activateFromBubble = () => {
  chatState.value = 'expanded'
  isBoosted.value = true
  nextTick(() => {
    inputRef.value?.focus()
  })
}

const collapse = () => {
  chatState.value = 'collapsed'
  isBoosted.value = false
}

// 切换聊天窗展开高度
const toggleBoost = () => {
  if (chatState.value === 'collapsed') {
    chatState.value = 'expanded'
  }
  isBoosted.value = !isBoosted.value
}

// 打开全部记录
const openHistory = () => {
  emit('open-history')
}

// 加载今天的历史消息
async function fetchTodayMessages() {
  if (historyLoaded.value) return
  const userId = getUserId()
  if (!userId) return
  try {
    const today = new Date()
    const dateStr = today.getFullYear() + '-' +
      String(today.getMonth() + 1).padStart(2, '0') + '-' +
      String(today.getDate()).padStart(2, '0')
    const res = await request.get(API.MESSAGES_BY_DATE(userId), { params: { date: dateStr } })
    if (res.code === 200 && res.data) {
      historyMessages.value = res.data
    }
  } catch {
    // 静默失败
  } finally {
    historyLoaded.value = true
  }
}

defineExpose({ activateFromBubble, collapse })

// --- WebSocket 回调 ---
setCallbacks({
  onTextMessage: handleTextMessage,
  onAudioChunk: handleAudioChunk,
  onError: handleError,
  onStatusChange: handleStatusChange
})

function handleTextMessage(message) {
  const { content, isComplete } = message.payload
  const oml2d = getOml2d()

  if (!currentMessage.value || isComplete) {
    if (currentMessage.value && isComplete) {
      currentMessage.value.isComplete = true
      currentMessage.value.isTemp = false
      currentMessage.value = null
      isSending.value = false
      interactionState.value = 'idle'
      // 流式结束，将累积的完整回复一次性推送到 live2d 提示框
      if (pendingLive2dText && oml2d) {
        oml2d.tipsMessage(pendingLive2dText, 8000, 4)
      }
      pendingLive2dText = ''
    } else {
      const newMessage = {
        id: message.messageId || Date.now(),
        role: 'ai',
        type: 'text',
        content: content,
        isTemp: !isComplete,
        isComplete: isComplete,
        timestamp: new Date().toISOString()
      }
      messages.value.push(newMessage)
      pendingLive2dText = content
      if (isComplete) {
        // 单块响应（无后续流式块），立即清理状态
        currentMessage.value = null
        isSending.value = false
        interactionState.value = 'idle'
        if (pendingLive2dText && oml2d) {
          oml2d.tipsMessage(pendingLive2dText, 8000, 4)
        }
        pendingLive2dText = ''
      } else {
        currentMessage.value = newMessage
        // 第一个片段，重置累积文本并立即显示
        if (content && oml2d) {
          oml2d.tipsMessage(content, 8000, 4)
        }
      }
    }
  } else {
    currentMessage.value.content += content
    // 流式中间片段，累积但不更新提示框（避免闪烁）
    pendingLive2dText += content
  }

  scrollToBottom()
}

async function handleAudioChunk(arrayBuffer) {
  try {
    if (audioStats.value.chunksReceived === 0) {
      await initAudio()
    }
    await appendAudioChunk(arrayBuffer)
  } catch (e) {
    console.error('音频处理异常', e)
  }
}

function handleError(error) {
  isSending.value = false
  interactionState.value = 'idle'
  // 重置流式消息状态，避免残留的 temp 标记
  currentMessage.value = null
  pendingLive2dText = ''
}

function handleStatusChange(status) {
  connectionStatus.value = status
  if (status === 'connected') {
    connectionText.value = ''
  } else if (status === 'connecting') {
    connectionText.value = '正在连接...'
  } else {
    connectionText.value = '连接已断开'
  }
}

// --- 交互逻辑 ---
const handleFocus = () => {
  chatState.value = 'expanded'
}

const handleInput = () => {
  interactionState.value = inputText.value.trim() ? 'typing' : 'idle'
}

const handleSend = () => {
  const text = inputText.value.trim()
  // 【修复 3】: 移除 connectionStatus 阻断，让代码能往下走到报错气泡逻辑
  if (!text) return

  messages.value.push({
    id: Date.now(),
    role: 'user',
    type: 'text',
    content: text,
    timestamp: new Date().toISOString()
  })

  inputText.value = ''
  interactionState.value = 'responding'
  isSending.value = true
  scrollToBottom()

  // 评估发送状态
  const success = connectionStatus.value === 'connected' ? sendChat(text, true) : false

  if (!success) {
    messages.value.push({
      id: Date.now(),
      role: 'ai',
      type: 'text',
      content: '发送失败：WebSocket 未连接',
      timestamp: new Date().toISOString()
    })
    isSending.value = false
    interactionState.value = 'idle'
    scrollToBottom()
  }
}

const triggerImageUpload = () => {
  chatState.value = 'expanded'
  fileInputRef.value.click()
}

const handleFileChange = (e) => {
  const file = e.target.files[0]
  if (!file) return
  emit('trigger-image-upload', file)

  const fileUrl = URL.createObjectURL(file)

  messages.value.push({
    id: Date.now(),
    role: 'user',
    type: 'image',
    content: fileUrl
  })
  interactionState.value = 'responding'
  scrollToBottom()

  setTimeout(() => {
    messages.value.push({ id: Date.now() + 1, role: 'ai', type: 'text', content: '画面已经刻录，有什么想对我说的吗？' })
    interactionState.value = 'idle'
    scrollToBottom()
  }, 1500)

  e.target.value = ''
}

let scrollToBottomTimer = null
const scrollToBottom = async () => {
  // 防抖：高频调用（如流式消息逐段追加）合并为一次
  if (scrollToBottomTimer) clearTimeout(scrollToBottomTimer)
  scrollToBottomTimer = setTimeout(async () => {
    scrollToBottomTimer = null
    await nextTick()
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  }, 16) // ~1 frame at 60fps
}

watch(() => props.isActive, (newVal) => {
  if (!newVal) collapse()
})

// --- 生命周期 ---
onMounted(() => {
  fetchTodayMessages()
  const userId = getUserId() || 'unknown'
  connect(userId)
})

onBeforeUnmount(() => {
  disconnect()
  stopAudio()
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

    <div class="glass-morph-bg"></div>

    <transition name="content-fade">
      <div v-show="chatState === 'expanded'" class="chat-panel">
        <div class="terminal-decor">
          <div class="decor-left">
            <span class="decor-dot"></span>
            <span class="decor-line"></span>
            <span class="decor-text">SECURE_LINK // ACTIVE</span>
          </div>
          <div class="decor-actions">
            <button class="decor-btn" @mousedown.prevent="toggleBoost">
              {{ isBoosted ? '收起 ↘' : '展开 ↗' }}
            </button>
            <button class="decor-btn highlight" @mousedown.prevent="openHistory">
              查看全部记录 ≡
            </button>
          </div>
        </div>

        <div class="message-list" ref="messageListRef">
          <div v-if="connectionStatus !== 'connected'" class="connection-hint">
            <span class="conn-dot" :class="connectionStatus"></span>
            <span class="conn-text">{{ connectionText }}</span>
          </div>

          <div
              v-for="(msg, i) in historyMessages"
              :key="'h-' + msg.id"
              class="message-item"
              :class="msg.role"
          >
            <div class="message-content">
              <div v-if="msg.role === 'ai'" class="ai-tag">AI //</div>
              <div class="text-wrapper" :class="{ 'image-wrapper': msg.type === 'image' }">
                <img v-if="msg.type === 'image'" :src="msg.content" class="chat-image" alt="history image" />
                <template v-else>
                  {{ msg.content }}
                </template>
              </div>
            </div>
          </div>

          <div v-if="historyMessages.length > 0 && messages.length > 0" class="history-divider">
            <span>— 今日历史 —</span>
          </div>

          <div
              v-for="(msg, i) in messages"
              :key="msg.id || i"
              class="message-item"
              :class="[msg.role, { 'is-temp': msg.isTemp }]"
          >
            <div class="message-content">
              <div v-if="msg.role === 'ai'" class="ai-tag">AI //</div>
              <div class="text-wrapper" :class="{ 'image-wrapper': msg.type === 'image' }">
                <img v-if="msg.type === 'image'" :src="msg.content" class="chat-image" alt="uploaded image" />
                <template v-else>
                  {{ msg.content }}
                </template>
                <span v-if="msg.isTemp" class="temp-indicator">...</span>
              </div>
            </div>
          </div>

          <div v-if="interactionState === 'responding' && (!currentMessage || !currentMessage.isTemp)" class="message-item ai typing-indicator">
            <div class="typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>

          <div v-if="messages.length === 0 && historyMessages.length === 0 && connectionStatus === 'connected'" class="empty-state">
            <p>“ 在这里，记录你的每一个思绪 ”</p>
          </div>
        </div>
      </div>
    </transition>

    <div class="input-bar" :class="{ 'is-expanded': chatState === 'expanded' }">
      <div class="left-action action-btn" @mousedown.prevent="triggerImageUpload" title="上传图片">
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
          @mousedown.prevent="handleSend"
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

.chat-window-container.is-hidden { opacity: 0; transform: translate(-50%, 40px); }

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

.message-item { display: flex; width: 100%; }
.message-item.user { justify-content: flex-end; }
.message-item.ai { justify-content: flex-start; }

.message-content { max-width: 85%; position: relative; }
.ai-tag { font-size: 9px; color: #999; margin-bottom: 4px; letter-spacing: 1px; }

.text-wrapper {
  font-size: 15px; line-height: 1.7; color: #333;
  font-weight: 400;
}
.user .text-wrapper { text-align: right; border-right: 2px solid #333; padding-right: 15px; }
.ai .text-wrapper { border-left: 2px solid #333; padding-left: 15px; }

.text-wrapper.image-wrapper { padding: 0; border: none !important; }
.chat-image {
  max-width: 220px; max-height: 180px; object-fit: contain;
  border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.1);
  display: block;
}

.input-bar {
  height: 65px; display: flex; align-items: center;
  pointer-events: auto; padding: 0 20px; flex-shrink: 0;
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

.typing-dots { display: flex; gap: 4px; padding-left: 15px; border-left: 2px solid #ddd; }
.typing-dots span { width: 4px; height: 4px; background: #aaa; border-radius: 50%; animation: bounce 1.4s infinite; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1); } }

.empty-state p { color: #ccc; font-size: 13px; text-align: center; width: 100%; }

.history-divider {
  display: flex; justify-content: center; align-items: center;
  position: relative; margin: 10px 0;
}
.history-divider span {
  font-size: 10px; color: #bbb; letter-spacing: 2px;
  background: rgba(255,255,255,0.5); padding: 0 10px;
  position: relative; z-index: 1;
}
.history-divider::before {
  content: ''; position: absolute; left: 0; right: 0; top: 50%;
  height: 1px; background: rgba(0,0,0,0.08);
}

.content-fade-enter-active, .content-fade-leave-active { transition: all 0.3s ease; }
.content-fade-enter-from, .content-fade-leave-to { opacity: 0; transform: translateY(5px); }
</style>