<template>
  <div
    class="content-panel history-panel"
    :class="{ 'panel-open': isOpen }"
  >
    <div class="panel-background-container"><div class="panel-glass-bg"></div></div>
    <div class="panel-header">
      <div class="title-area">
        <span class="title-tag">ARCHIVE //</span>
        <h2 class="panel-title">记忆回溯</h2>
      </div>
      <button class="close-btn" @click="$emit('close')">✕</button>
    </div>

    <div class="history-body">
      <!-- 左侧时间线列表 -->
      <div class="history-sidebar">
        <div v-if="historyLoading" class="sidebar-empty">
          <span class="empty-hint-text">加载中...</span>
        </div>
        <div v-else-if="historyError" class="sidebar-empty">
          <span class="error-hint-text">{{ historyError }}</span>
          <button class="retry-btn" @click="fetchSessions">重试</button>
        </div>
        <div v-else-if="sessions.length === 0" class="sidebar-empty">
          <span class="empty-hint-text">暂无历史会话</span>
        </div>
        <template v-else>
          <div
            v-for="session in sessions" :key="session.id"
            class="session-item"
            :class="{ active: selectedSession?.id === session.id }"
            @click="selectSession(session)"
          >
            <div class="session-item-glow"></div>
            <div class="session-time">{{ session.date }}</div>
            <div class="session-meta">
              <span class="mood-tag">{{ session.mood || '日常' }}</span>
            </div>
            <div class="session-preview">
              {{ session.quote || (session.desc ? session.desc.substring(0, 16) + '...' : '') }}
            </div>
          </div>
        </template>
      </div>

      <!-- 右侧详情 -->
      <div class="history-detail">
        <div v-if="messagesLoading" class="history-loading">
          <div class="loading-dots"><span></span><span></span><span></span></div>
          <p>正在追溯记忆片段...</p>
        </div>
        <div v-else-if="messagesError" class="history-error">
          <p class="error-text">⚠ {{ messagesError }}</p>
          <button class="retry-btn" @click="selectSession(selectedSession)">重试</button>
        </div>
        <div v-else-if="selectedSession" class="archive-chat-container">
          <div class="archive-date-divider">
            <span>{{ selectedSession.date }} · {{ selectedSession.mood || '日常' }}</span>
          </div>
          <div
            v-for="msg in sessionMessages"
            :key="msg.id"
            class="archive-message"
            :class="msg.role"
          >
            <div class="archive-bubble-wrapper">
              <span class="archive-time" v-if="msg.role === 'user'">{{ formatArchiveTime(msg.timestamp) }}</span>
              <div class="archive-bubble">
                <img v-if="msg.type === 'image'" :src="getOptimizedImageUrl(msg.content, { width: 480 })" class="archive-image" loading="lazy" decoding="async" @error="handleArchiveImgError" />
                <div v-else class="archive-text" v-text="msg.content"></div>
              </div>
              <span class="archive-time" v-if="msg.role === 'ai'">{{ formatArchiveTime(msg.timestamp) }}</span>
            </div>
          </div>
          <div v-if="sessionMessages.length === 0 && !messagesLoading" class="empty-hint">该天无聊天记录</div>
        </div>
        <div v-else class="empty-hint select-prompt">
          <span class="icon">✦</span>
          <span>选择左侧时间线以回放这段记忆</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import request from '@/utils/request'
import { getUserId } from '@/utils/auth'
import { API } from '@/config/api'
import { useUiStore } from '@/stores/ui'
import { getOptimizedImageUrl } from '@/utils/image'

const props = defineProps({
  isOpen: { type: Boolean, default: false },
})

defineEmits(['close'])

const sessions = ref([])
const selectedSession = ref(null)
const sessionMessages = ref([])
const historyLoading = ref(false)
const historyError = ref('')
const messagesLoading = ref(false)
const messagesError = ref('')
const uiStore = useUiStore()

const handleArchiveImgError = (e) => { e.target.style.display = 'none' }

let _isMounted = true
onBeforeUnmount(() => { _isMounted = false })

async function fetchSessions() {
  historyLoading.value = true
  historyError.value = ''
  try {
    const res = await request.get(API.MEMORY_LIST, { params: { page: 1, size: 50, excludeToday: true } })
    if (!_isMounted) return
    if (Array.isArray(res) && res.length > 0) {
      sessions.value = res
    } else {
      // 无历史会话记录，显示空状态而非假数据
      sessions.value = []
    }
  } catch (e) {
    if (!_isMounted) return
    sessions.value = []
    historyError.value = '获取会话列表失败，请稍后重试'
    console.error('HistoryPanel fetchSessions:', e)
  } finally {
    if (_isMounted) historyLoading.value = false
  }
}

function formatArchiveTime(iso) {
  if (!iso) return ''
  // 如果已经是对 HH:mm 格式，直接返回
  if (/^\d{2}:\d{2}$/.test(iso)) return iso
  const d = new Date(iso)
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}

let _selectGen = 0

async function selectSession(session) {
  if (!session) return  // 空值守卫
  const gen = ++_selectGen
  selectedSession.value = session
  sessionMessages.value = []
  messagesError.value = ''

  messagesLoading.value = true
  const dateStr = session.date.replace(/\./g, '-')
  try {
    const res = await request.get(API.MESSAGES_BY_DATE(getUserId()), { params: { date: dateStr } })
    if (!_isMounted || gen !== _selectGen) return  // 过时响应丢弃
    if (Array.isArray(res) && res.length > 0) {
      sessionMessages.value = res
    } else {
      // 该天确实无聊天记录，显示空状态
      sessionMessages.value = []
    }
  } catch (e) {
    if (!_isMounted || gen !== _selectGen) return
    sessionMessages.value = []
    messagesError.value = '获取消息失败，请重试'
    console.error('HistoryPanel selectSession:', e)
  } finally {
    if (_isMounted && gen === _selectGen) messagesLoading.value = false
  }
}

watch(() => props.isOpen, (open) => {
  if (open && !historyLoading.value) fetchSessions()
})
</script>

<style scoped>
.content-panel {
  background: rgba(10, 15, 30, 0.4);
  backdrop-filter: blur(12px);
  position: fixed; top: 0; width: 50%; height: 100%;
  z-index: 100; transition: transform 0.8s cubic-bezier(0.16, 1, 0.3, 1);
  pointer-events: auto;
}
.history-panel { left: 0; transform: translateX(-100%); }
.history-panel.panel-open { transform: translateX(0); }

.panel-glass-bg {
  position: absolute; inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,0.03) 0%, transparent 100%);
  border-right: 1px solid rgba(255,255,255,0.05);
}

.panel-header {
  position: relative;
  padding: 60px 40px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-title { color: white; font-size: 24px; font-weight: 200; letter-spacing: 2px; }
.title-tag { font-size: 10px; color: #5eead4; letter-spacing: 2px; font-family: monospace; }
.close-btn {
  background: none; border: none;
  color: white; font-size: 20px; cursor: pointer; opacity: 0.4;
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.close-btn:hover { opacity: 1; transform: rotate(90deg); }

/* ── 主体布局 ── */
.history-body {
  position: relative; display: flex;
  height: calc(100% - 130px);
  padding: 0;
  box-sizing: border-box;
}

/* ── 左侧时间线 ── */
.history-sidebar {
  width: 240px; height: 100%;
  border-right: 1px solid rgba(255,255,255,0.04);
  overflow-y: auto;
  padding: 0 12px 60px 40px;
  box-sizing: border-box;
  scrollbar-width: none;
}
.history-sidebar::-webkit-scrollbar { display: none; }

.session-item {
  position: relative;
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  border-radius: 8px;
  background: rgba(255,255,255,0.01);
  border: 1px solid rgba(255,255,255,0.03);
  transition: background-color 0.4s cubic-bezier(0.16, 1, 0.3, 1), border-color 0.4s cubic-bezier(0.16, 1, 0.3, 1);
  overflow: hidden;
}
.session-item-glow {
  position: absolute; left: 0; top: 0; width: 3px; height: 100%;
  background: #5eead4; opacity: 0;
  transform: scaleY(0.3); transition: opacity 0.3s ease, transform 0.3s ease;
}
.session-item:hover { background: rgba(255,255,255,0.03); border-color: rgba(255,255,255,0.08); }
.session-item.active {
  background: rgba(94,234,212,0.04);
  border-color: rgba(94,234,212,0.2);
}
.session-item.active .session-item-glow { opacity: 1; transform: scaleY(1); }

.session-time {
  font-size: 11px; color: rgba(255,255,255,0.4);
  font-family: monospace; margin-bottom: 6px;
}
.session-meta { margin-bottom: 8px; }
.mood-tag {
  font-size: 10px; color: #5eead4;
  background: rgba(94,234,212,0.08);
  padding: 2px 6px; border-radius: 3px;
}
.session-preview {
  font-size: 12px; color: rgba(255,255,255,0.6);
  line-height: 1.5;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ── 右侧对话详情 ── */
.history-detail {
  flex: 1; display: flex; flex-direction: column;
  height: 100%; overflow-y: auto;
  padding: 0 40px 0 32px;
  box-sizing: border-box;
}
.history-detail::-webkit-scrollbar { width: 4px; }
.history-detail::-webkit-scrollbar-track { background: transparent; }
.history-detail::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.05); border-radius: 2px; }
.history-detail::-webkit-scrollbar-thumb:hover { background: rgba(94,234,212,0.2); }

.archive-chat-container {
  display: flex; flex-direction: column; gap: 20px;
  padding-top: 4px;
  padding-bottom: 80px;
}
.archive-date-divider { text-align: center; margin: 10px 0; }
.archive-date-divider span {
  font-size: 10px; color: rgba(255,255,255,0.3);
  font-family: monospace; letter-spacing: 1px;
}

.archive-message { display: flex; width: 100%; }
.archive-message.user { justify-content: flex-end; }
.archive-message.ai { justify-content: flex-start; }

.archive-bubble-wrapper {
  display: flex; align-items: flex-end; gap: 8px; max-width: 85%;
}
.archive-time {
  font-size: 10px; color: rgba(255,255,255,0.25);
  font-family: monospace; padding-bottom: 4px;
}

.archive-bubble {
  padding: 12px 16px; border-radius: 12px;
  font-size: 13.5px; line-height: 1.6; font-weight: 300; letter-spacing: 0.5px;
}
.archive-message.user .archive-bubble {
  background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.9);
  border: 1px solid rgba(255,255,255,0.08);
  border-bottom-right-radius: 2px;
}
.archive-message.ai .archive-bubble {
  background: linear-gradient(135deg, rgba(94,234,212,0.08) 0%, rgba(94,234,212,0.02) 100%);
  color: #e2fdf8;
  border: 1px solid rgba(94,234,212,0.15);
  border-bottom-left-radius: 2px;
  box-shadow: 0 4px 12px rgba(94,234,212,0.02);
}
.archive-text { white-space: pre-wrap; word-break: break-word; }

.archive-image {
  max-width: 240px; max-height: 180px;
  object-fit: cover; border-radius: 6px;
  display: block; border: 1px solid rgba(255,255,255,0.1);
}

/* ── 加载 / 错误 / 空状态 ── */
.sidebar-empty {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; padding: 40px 12px; gap: 12px;
}
.empty-hint-text { font-size: 12px; color: rgba(255,255,255,0.25); }
.error-hint-text { font-size: 12px; color: rgba(248,113,113,0.7); text-align: center; }
.sidebar-empty .retry-btn {
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.7); padding: 4px 16px; border-radius: 16px;
  cursor: pointer; font-size: 11px; transition: background-color 0.3s ease;
}
.sidebar-empty .retry-btn:hover { background: rgba(255,255,255,0.15); color: #fff; }

.history-loading, .history-error {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  color: rgba(255,255,255,0.5); font-size: 14px; gap: 12px;
}
.history-loading .loading-dots { display: flex; gap: 6px; }
.history-loading .loading-dots span {
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(255,255,255,0.3);
  animation: dot-pulse 1.4s ease-in-out infinite;
}
.history-loading .loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.history-loading .loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-pulse {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}
.history-error .error-text { font-size: 13px; color: rgba(248,113,113,0.8); text-align: center; }
.history-error .retry-btn {
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.7); padding: 6px 20px; border-radius: 20px;
  cursor: pointer; font-size: 12px; transition: background-color 0.3s ease, color 0.3s ease;
}
.history-error .retry-btn:hover { background: rgba(255,255,255,0.15); color: #fff; }

.empty-hint {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.25); font-size: 13px;
}
.select-prompt {
  flex-direction: column;
  gap: 8px;
}
.select-prompt .icon {
  color: #5eead4; font-size: 16px;
  animation: pulseGlow 2s ease-in-out infinite;
}
@keyframes pulseGlow {
  0%, 100% { opacity: 0.4; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1.1); text-shadow: 0 0 8px #5eead4; }
}

/* GPU 分层：仅对整体面板启用，避免 v-for 中子项创建过多独立层 */
.history-panel { will-change: transform, opacity; }

/* ── 响应式布局 ── */

/* 平板 (≤ 1024px) */
@media (max-width: 1024px) {
  .content-panel { width: 65%; }
}

/* 手机 (≤ 768px) */
@media (max-width: 768px) {
  .content-panel { width: 100%; }
  .panel-header { padding: 40px 20px 16px; }
  .panel-title { font-size: 20px; }
  .history-body { flex-direction: column; height: calc(100% - 100px); }
  .history-sidebar {
    width: 100%; height: 160px;
    border-right: none; border-bottom: 1px solid rgba(255,255,255,0.06);
    padding: 0 16px 12px;
    display: flex; gap: 10px; overflow-x: auto; overflow-y: hidden;
    flex-shrink: 0;
  }
  .session-item { min-width: 160px; margin-bottom: 0; padding: 12px; }
  .history-detail { padding: 16px; }
}

/* 小屏手机 (≤ 480px) */
@media (max-width: 480px) {
  .panel-header { padding: 32px 16px 12px; }
  .panel-title { font-size: 18px; }
  .history-sidebar { height: 130px; padding: 0 12px 10px; }
  .session-item { min-width: 140px; padding: 10px; }
  .history-detail { padding: 12px; }
}
</style>
