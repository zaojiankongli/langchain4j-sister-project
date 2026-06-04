<script setup>
defineProps({
  messages: { type: Array, default: () => [] },
  historyMessages: { type: Array, default: () => [] },
  earlierMessages: { type: Array, default: () => [] },
  isLoadingMore: { type: Boolean, default: false },
  noMoreMessages: { type: Boolean, default: false },
  isSending: { type: Boolean, default: false },
  currentMessage: { type: Object, default: null },
  connectionStatus: { type: String, default: 'disconnected' },
  connectionText: { type: String, default: '' },
  latestEmotion: { type: Object, default: null },
})

function moodColor(e) {
  const label = e?.moodLabel || ''
  if (label.includes('害羞')) return '#f472b6'
  if (label.includes('开心')) return '#60a5fa'
  if (label.includes('难过') || label.includes('失落')) return '#a78bfa'
  if (label.includes('紧张')) return '#fbbf24'
  if (label.includes('平静') || label.includes('放松')) return '#34d399'
  return '#5eead4'
}

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

function handleImgError(e) {
  e.target.style.display = 'none'
}

function isNewDateGroup(msg, prevMsg) {
  if (!msg?.timestamp) return false
  if (!prevMsg?.timestamp) return true
  return msg.timestamp.substring(0, 10) !== prevMsg.timestamp.substring(0, 10)
}
</script>

<template>
  <!-- 连接状态 -->
  <div v-if="connectionStatus !== 'connected'" class="connection-hint">
    <span class="conn-dot" :class="connectionStatus"></span>
    <span class="conn-text">{{ connectionText }}</span>
  </div>

  <!-- 懒加载指示器 -->
  <div v-if="isLoadingMore" class="load-more-indicator">
    <div class="typing-dots"><span></span><span></span><span></span></div>
    <span>载入更早记忆...</span>
  </div>
  <div v-else-if="noMoreMessages && earlierMessages.length > 0" class="load-more-indicator end">
    <span>— 已加载全部记忆 —</span>
  </div>

  <!-- 更早消息 -->
  <template v-for="(msg, eIdx) in earlierMessages" :key="'e-' + msg.id">
    <div v-if="isNewDateGroup(msg, eIdx > 0 ? earlierMessages[eIdx - 1] : null)" class="date-divider">
      <span>{{ formatDateLabel(msg.timestamp) }}</span>
    </div>
    <div class="message-item" :class="msg.role" v-memo="[msg.id, msg.content, msg.type, msg.timestamp]">
      <div class="message-content">
        <div class="text-wrapper" :class="{ 'image-wrapper': msg.type === 'image' }">
          <img v-if="msg.type === 'image'" :src="msg.content" class="chat-image" alt="earlier" loading="lazy" @error="handleImgError" />
          <template v-else><span class="msg-text" v-text="msg.content"></span></template>
        </div>
        <span class="msg-time" v-if="msg.timestamp">{{ formatTime(msg.timestamp) }}</span>
      </div>
    </div>
  </template>

  <!-- 今昨分割 -->
  <div v-if="earlierMessages.length > 0 && (historyMessages.length > 0 || messages.length > 0)" class="date-divider today-edge">
    <span>今天</span>
  </div>

  <!-- 今日历史 -->
  <template v-for="msg in historyMessages" :key="'h-' + msg.id">
    <div class="message-item" :class="msg.role" v-memo="[msg.id, msg.content, msg.type, msg.timestamp]">
      <div class="message-content">
        <div class="text-wrapper" :class="{ 'image-wrapper': msg.type === 'image' }">
          <img v-if="msg.type === 'image'" :src="msg.content" class="chat-image" alt="history" loading="lazy" @error="handleImgError" />
          <template v-else><span class="msg-text" v-text="msg.content"></span></template>
        </div>
        <span class="msg-time" v-if="msg.timestamp">{{ formatTime(msg.timestamp) }}</span>
      </div>
    </div>
  </template>

  <!-- 当前会话分割 -->
  <div v-if="historyMessages.length > 0 && messages.length > 0" class="history-divider">
    <span>— 当前会话 —</span>
  </div>

  <!-- 当前消息 -->
  <template v-for="msg in messages" :key="msg.id">
    <div class="message-item" :class="[msg.role, { 'is-temp': msg.isTemp, 'is-error': msg.isError }]" v-memo="[msg.id, msg.content, msg.isTemp, msg.isComplete, msg.isError, msg.type, msg.timestamp]">
      <div class="message-content">
        <div v-if="msg.role === 'ai' && latestEmotion && msg.isComplete && msg === messages[messages.length - 1]" class="mood-badge">
          <span class="mood-dot" :style="{ background: moodColor(latestEmotion) }"></span>
          <span class="mood-label">{{ latestEmotion.moodLabel }}</span>
        </div>
        <div class="text-wrapper" :class="{ 'image-wrapper': msg.type === 'image' }">
          <img v-if="msg.type === 'image'" :src="msg.content" class="chat-image" alt="upload" loading="lazy" @error="handleImgError" />
          <template v-else><span class="msg-text" v-text="msg.content"></span></template>
          <span v-if="msg.isTemp" class="temp-indicator">...</span>
        </div>
        <div v-if="msg.isError" class="error-badge">⚠</div>
        <span class="msg-time" v-if="msg.timestamp">{{ formatTime(msg.timestamp) }}</span>
      </div>
    </div>
  </template>

  <!-- 正在输入 -->
  <div v-if="isSending && !currentMessage" class="message-item ai typing-indicator">
    <div class="typing-dots"><span></span><span></span><span></span></div>
  </div>

  <!-- 空状态 -->
  <div v-if="messages.length === 0 && historyMessages.length === 0 && earlierMessages.length === 0 && connectionStatus === 'connected' && !isLoadingMore" class="empty-state">
    <p>" 在这里，记录你的每一个思绪 "</p>
  </div>
</template>

<style scoped>
.connection-hint { display: flex; align-items: center; gap: 8px; padding: 10px 0; font-size: 12px; color: rgba(0,0,0,0.4); }
.conn-dot { width: 6px; height: 6px; border-radius: 50%; }
.conn-dot.connected { background: #22c55e; box-shadow: 0 0 6px rgba(34,197,94,0.5); }
.conn-dot.connecting { background: #f59e0b; animation: pulse 1s infinite; }
.conn-dot.error, .conn-dot.disconnected { background: #ef4444; }
.msg-text { white-space: pre-wrap; word-break: break-word; }
.msg-time { display: block; font-size: 10px; color: rgba(0,0,0,0.25); margin-top: 4px; font-family: monospace; }
.message-item { display: flex; width: 100%; contain: layout style; }
.message-item.user { justify-content: flex-end; }
.message-item.ai { justify-content: flex-start; }
.message-content { max-width: 85%; position: relative; }
.text-wrapper { font-size: 15px; line-height: 1.7; color: #333; font-weight: 400; }
.user .text-wrapper { text-align: right; }
.ai .text-wrapper { border-left: 1px solid rgba(0,0,0,0.08); padding-left: 14px; }
.text-wrapper.image-wrapper { padding: 0; border: none !important; }
.chat-image { max-width: 220px; max-height: 180px; object-fit: contain; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); display: block; }
.mood-badge { display: inline-flex; align-items: center; gap: 4px; margin-bottom: 4px; padding: 1px 8px 1px 6px; border-radius: 10px; background: rgba(0,0,0,0.03); }
.mood-dot { width: 5px; height: 5px; border-radius: 50%; display: inline-block; }
.mood-label { font-size: 10px; color: #888; letter-spacing: 0.5px; }
.temp-indicator { display: inline; animation: blink 1s step-end infinite; font-size: 15px; color: #999; margin-left: 1px; }
@keyframes blink { 50% { opacity: 0; } }
.error-badge { display: inline-flex; align-items: center; font-size: 11px; color: #ef4444; margin-top: 2px; letter-spacing: 0.5px; }
.message-item.is-error .text-wrapper { border-left-color: #fca5a5; }
.message-item.is-error .msg-text { color: #dc2626; font-size: 13px; }
.date-divider { display: flex; justify-content: center; align-items: center; margin: 20px 0 12px; }
.date-divider span { font-size: 11px; color: rgba(0,0,0,0.3); background: rgba(0,0,0,0.04); padding: 3px 14px; border-radius: 12px; letter-spacing: 1px; }
.date-divider.today-edge span { color: var(--color-primary, #5eead4); background: rgba(94,234,212,0.1); border: 1px solid rgba(94,234,212,0.2); }
.load-more-indicator { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 16px 0; color: rgba(0,0,0,0.35); font-size: 12px; }
.load-more-indicator.end { padding: 6px 0 16px; opacity: 0.5; }
.typing-dots { display: flex; gap: 4px; padding-left: 15px; border-left: 2px solid #ddd; }
.typing-dots span { width: 4px; height: 4px; background: #aaa; border-radius: 50%; animation: bounce 1.4s infinite; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1); } }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
.empty-state p { color: #ccc; font-size: 13px; text-align: center; width: 100%; }
.history-divider { display: flex; justify-content: center; align-items: center; position: relative; margin: 10px 0; }
.history-divider span { font-size: 10px; color: #bbb; letter-spacing: 2px; background: rgba(255,255,255,0.5); padding: 0 10px; position: relative; z-index: 1; }
.history-divider::before { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 1px; background: rgba(0,0,0,0.08); }
</style>
