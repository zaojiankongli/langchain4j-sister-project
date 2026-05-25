<template>
  <div
    class="content-panel history-panel"
    :class="{ 'panel-open': isOpen }"
    @click.stop
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
      <div class="history-sidebar">
        <div
          v-for="session in sessions" :key="session.id"
          class="session-item"
          :class="{ active: selectedSession?.id === session.id }"
          @click="selectSession(session)"
        >
          <div class="session-time">{{ session.date }}</div>
          <div class="session-preview">
            {{ session.quote || (session.desc ? session.desc.substring(0, 20) + '...' : '') }}
          </div>
        </div>
      </div>

      <div class="history-detail">
        <div v-if="messagesLoading" class="history-loading">
          <div class="loading-dots"><span></span><span></span><span></span></div>
          <p>加载消息中...</p>
        </div>
        <div v-else-if="messagesError" class="history-error">
          <p class="error-text">⚠ {{ messagesError }}</p>
          <button class="retry-btn" @click="selectSession(selectedSession)">重试</button>
        </div>
        <div v-else-if="selectedSession" class="archive-chat-container">
          <div class="archive-date-divider"><span>{{ selectedSession.date }} · {{ selectedSession.mood }}</span></div>
          <div
            v-for="msg in sessionMessages"
            :key="msg.id"
            class="archive-message"
            :class="msg.role"
          >
            <div class="archive-bubble-wrapper">
              <span class="archive-time" v-if="msg.role === 'user'">{{ msg.timestamp }}</span>
              <div class="archive-bubble">
                <img v-if="msg.type === 'image'" :src="msg.content" class="archive-image" />
                <div v-else class="archive-text">{{ msg.content }}</div>
              </div>
              <span class="archive-time" v-if="msg.role === 'ai'">{{ msg.timestamp }}</span>
            </div>
          </div>
          <div v-if="sessionMessages.length === 0 && !messagesLoading" class="empty-hint">该天无聊天记录</div>
        </div>
        <div v-else class="empty-hint">选择左侧时间线以回放记忆</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import request from '@/utils/request'
import { getUserId } from '@/utils/auth'
import { API } from '@/config/api'

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

async function fetchSessions() {
  historyLoading.value = true
  try {
    const res = await request.get(API.MEMORY_LIST, { params: { page: 1, size: 50, excludeToday: true } })
    if (res.code === 200) sessions.value = res.data
  } finally {
    historyLoading.value = false
  }
}

async function selectSession(session) {
  selectedSession.value = session
  sessionMessages.value = []
  messagesError.value = ''
  messagesLoading.value = true
  const dateStr = session.date.replace(/\./g, '-')
  try {
    const res = await request.get(API.MESSAGES_BY_DATE(getUserId()), { params: { date: dateStr } })
    if (res.code === 200) {
      sessionMessages.value = res.data
    } else {
      messagesError.value = '加载消息失败：数据格式异常'
    }
  } catch (e) {
    messagesError.value = e?.response?.data?.message || e?.message || '加载消息失败，请重试'
    sessionMessages.value = []
  } finally {
    messagesLoading.value = false
  }
}

// 面板打开时自动加载数据
watch(() => props.isOpen, (open) => {
  if (open) fetchSessions()
})
</script>

<style scoped>
.content-panel {
  background: rgba(0,0,0,0.3);
  backdrop-filter: blur(10px);
  position: fixed; top: 0; width: 50%; height: 100%;
  z-index: 100; transition: transform 0.8s cubic-bezier(0.16, 1, 0.3, 1);
  pointer-events: auto;
}
.history-panel { left: 0; transform: translateX(-100%); }
.history-panel.panel-open { transform: translateX(0); }

.panel-glass-bg {
  position: absolute; inset: 0;
  background: rgba(255, 255, 255, 0.02);
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}

.panel-header {
  position: relative;
  padding: 60px 40px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-title { color: white; font-size: 24px; font-weight: 200; letter-spacing: 1px; }
.title-tag { font-size: 10px; color: #5eead4; letter-spacing: 2px; }

.close-btn {
  background: none; border: none;
  color: white; font-size: 20px; cursor: pointer; opacity: 0.5; transition: 0.3s;
}
.close-btn:hover { opacity: 1; transform: rotate(90deg); }

.history-body {
  position: relative; display: flex;
  height: calc(100% - 130px);
  padding: 0 0 0 40px;
  box-sizing: border-box;
}

.history-sidebar {
  width: 260px;
  height: 100%;
  border-right: 1px solid rgba(255,255,255,0.08);
  overflow-y: auto;
  padding-right: 15px;
  padding-bottom: 60px;
  box-sizing: border-box;
  scrollbar-width: thin;
}

.history-sidebar::-webkit-scrollbar { width: 4px; }
.history-sidebar::-webkit-scrollbar-track { background: transparent; }
.history-sidebar::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.08); border-radius: 4px; }
.history-sidebar::-webkit-scrollbar-thumb:hover { background: rgba(94, 234, 212, 0.3); }

.session-item {
  padding: 20px; margin-bottom: 10px;
  cursor: pointer; border-radius: 12px;
  background: rgba(255,255,255,0.02);
  border: 1px solid transparent;
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.session-item:hover { background: rgba(255,255,255,0.05); }

.session-item.active {
  background: rgba(94, 234, 212, 0.08);
  border-color: rgba(94, 234, 212, 0.3);
  box-shadow: 0 4px 20px rgba(0,0,0,0.1);
}

.session-time { font-size: 12px; color: #5eead4; margin-bottom: 8px; font-family: monospace; letter-spacing: 1px; }
.session-preview { font-size: 13px; color: rgba(255,255,255,0.7); line-height: 1.5; }

.history-detail {
  flex: 1; display: flex; flex-direction: column;
  height: 100%; overflow-y: auto;
  padding: 0 20px 0 40px;
  box-sizing: border-box;
  scrollbar-width: thin;
}

.history-detail::-webkit-scrollbar { width: 6px; }
.history-detail::-webkit-scrollbar-track { background: transparent; margin: 20px 0; }
.history-detail::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 4px; }
.history-detail::-webkit-scrollbar-thumb:hover { background: rgba(94, 234, 212, 0.4); }

.empty-hint {
  flex: 1; display: flex; align-items: center; justify-content: center;
  color: rgba(255,255,255,0.3); font-size: 14px; letter-spacing: 1px;
}

.archive-chat-container {
  display: flex; flex-direction: column; gap: 24px;
  padding-top: 20px;
  padding-bottom: 80px;
}

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
.history-error .error-text { font-size: 13px; color: rgba(248, 113, 113, 0.8); text-align: center; }
.history-error .retry-btn {
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.7); padding: 6px 20px; border-radius: 20px;
  cursor: pointer; font-size: 12px; transition: all 0.3s;
}
.history-error .retry-btn:hover { background: rgba(255,255,255,0.15); color: #fff; }

.archive-date-divider { text-align: center; margin-bottom: 10px; }
.archive-date-divider span {
  font-size: 11px; color: rgba(255,255,255,0.4);
  background: rgba(255,255,255,0.05);
  padding: 4px 12px; border-radius: 20px; letter-spacing: 1px;
}

.archive-message { display: flex; width: 100%; }
.archive-message.user { justify-content: flex-end; }
.archive-message.ai { justify-content: flex-start; }

.archive-bubble-wrapper {
  display: flex; align-items: flex-end; gap: 10px; max-width: 80%;
}

.archive-time { font-size: 10px; color: rgba(255,255,255,0.3); font-family: monospace; padding-bottom: 5px; }

.archive-bubble {
  padding: 14px 18px; border-radius: 12px;
  font-size: 14px; line-height: 1.6; font-weight: 300;
  box-shadow: 0 5px 15px rgba(0,0,0,0.15);
}

.archive-message.user .archive-bubble {
  background: rgba(255,255,255,0.1); color: #fff;
  border-bottom-right-radius: 4px;
  border: 1px solid rgba(255,255,255,0.1);
}

.archive-message.ai .archive-bubble {
  background: rgba(94, 234, 212, 0.1); color: #e2fdf8;
  border-bottom-left-radius: 4px;
}

.archive-image {
  max-width: 260px; max-height: 200px;
  object-fit: cover; border-radius: 6px;
  display: block; box-shadow: 0 4px 12px rgba(0,0,0,0.2);
}
</style>
