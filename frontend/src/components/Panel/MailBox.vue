<template>
  <div class="mailbox-wrapper">
    <div class="mail-entry" @click="isOverlayOpen = true">
      <div class="mail-text-brief">
        <span class="label">LATEST //</span>
        <p class="content">{{ latestMessage }}</p>
      </div>
      <div class="mail-icon-btn">
        <span class="icon">✉</span>
        <div class="dot" v-if="hasUnread"></div>
      </div>
    </div>

    <Teleport to="body">
      <transition name="fade-blur">
        <div v-if="isOverlayOpen" class="mail-full-overlay" @click.stop>
          <div class="overlay-header">
            <div class="header-left">
              <h2 class="overlay-title">ARCHIVE // 档案与信件</h2>
              <button
                  v-if="hasUnread"
                  class="batch-read-btn"
                  @click="markAllAsRead"
              >
                全部标记为已读
              </button>
            </div>
            <button class="close-overlay-btn" @click="isOverlayOpen = false">✕</button>
          </div>

          <div class="overlay-body">
            <div class="mail-list">
              <div
                  v-for="mail in mailList"
                  :key="mail.id"
                  class="mail-card"
                  :class="{ 'is-read': mail.is_read }"
                  @click="markAsRead(mail)"
              >
                <div class="mail-card-header">
                  <span class="tag">{{ mail.tag }}</span>
                  <span class="date">{{ mail.date }}</span>
                  <div v-if="!mail.is_read" class="unread-indicator">NEW</div>
                </div>
                <h3 class="mail-subject">{{ mail.subject }}</h3>
                <p class="mail-excerpt">{{ mail.excerpt }}</p>
              </div>

              <div v-if="mailList.length === 0" class="empty-tip">暂无任何信件</div>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/utils/request'
import { useAuthStore } from '@/stores/auth'

// --- 状态定义 ---
const isOverlayOpen = ref(false)
const mailList = ref([])

// --- 计算属性 ---
// 1. 是否有未读消息（决定外层大红点和"一键已读"按钮显示）
const hasUnread = computed(() => {
  return mailList.value.some(mail => !mail.is_read)
})

// 2. 显示最上方的一条消息内容
const latestMessage = computed(() => {
  if (mailList.value.length === 0) return "暂无新邮件"
  return mailList.value[0].subject
})

// --- 方法：对接后端 ---

// 1. 初始化获取邮件列表
const fetchMails = async () => {
  try {
    const userId = useAuthStore().userId
    if (!userId) return
    const res = await request.get(`/mails?userId=${userId}`)
    if (res.code === 200 && Array.isArray(res.data)) {
      mailList.value = res.data
    }
  } catch (error) {
    console.error("邮件加载失败", error)
  }
}
  } catch {
    // 静默失败
  }
}

// 2. 单条标记已读
const markAsRead = async (mail) => {
  if (mail.is_read) return

  try {
    await request.post(API.MAIL_READ(mail.id))
    mail.is_read = true
  } catch {
    // 静默失败
  }
}

// 3. 一键已读
const markAllAsRead = async () => {
  try {
    await request.post(API.MAIL_READ_ALL)
    mailList.value.forEach(mail => {
      mail.is_read = true
    })
  } catch {
    // 静默失败
  }
}

onMounted(() => {
  fetchMails()
})
</script>

<style scoped>
/* --- 基础布局保持不变 --- */
.mailbox-wrapper {
  position: absolute;
  top: 50px;
  right: 60px;
  z-index: 60;
}

.mail-entry {
  display: flex;
  align-items: center;
  gap: 15px;
  cursor: pointer;
}

.mail-text-brief {
  text-align: right;
  color: #000;
  text-shadow: 0 2px 4px rgba(255, 255, 255, 0.5);
}

.mail-text-brief .label {
  font-size: 10px;
  font-weight: 900;
  opacity: 0.4;
  display: block;
}

.mail-text-brief .content {
  font-size: 14px;
  font-weight: 600;
  margin: 4px 0 0;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mail-icon-btn {
  position: relative;
  width: 48px;
  height: 48px;
  background: #000;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  transition: transform 0.3s;
}

.mail-entry:hover .mail-icon-btn {
  transform: scale(1.1) rotate(-10deg);
}

/* 入口红点动画 */
.dot {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  background: #ff4d4f;
  border: 2px solid #fff;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(1); box-shadow: 0 0 0 0 rgba(255, 77, 79, 0.7); }
  70% { transform: scale(1.2); box-shadow: 0 0 0 10px rgba(255, 77, 79, 0); }
  100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(255, 77, 79, 0); }
}

/* --- 遮罩层与列表 --- */
.mail-full-overlay {
  position: fixed;
  inset: 0;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(30px);
  z-index: 2000;
  padding: 80px 120px;
  display: flex;
  flex-direction: column;
}

.overlay-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 50px;
  border-bottom: 2px solid #000;
  padding-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 20px;
}

.overlay-title {
  font-size: 32px;
  font-weight: 900;
  color: #000;
}

/* 一键已读按钮样式 */
.batch-read-btn {
  background: transparent;
  border: 1px solid #000;
  color: #000;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  font-weight: bold;
  transition: all 0.2s;
}

.batch-read-btn:hover {
  background: #000;
  color: #fff;
}

.close-overlay-btn {
  background: #000;
  border: none;
  color: #fff;
  width: 40px;
  height: 40px;
  font-size: 20px;
  cursor: pointer;
  border-radius: 50%;
}

.mail-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 30px;
}

/* 邮件卡片状态 */
.mail-card {
  padding: 30px;
  border: 1px solid rgba(0,0,0,0.1);
  background: #fff;
  transition: all 0.3s;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.mail-card:hover {
  box-shadow: 0 20px 40px rgba(0,0,0,0.05);
  transform: translateY(-5px);
}

/* 已读邮件样式变淡 */
.mail-card.is-read {
  opacity: 0.6;
  background: rgba(0,0,0,0.02);
}

.mail-card .tag {
  font-size: 12px;
  background: #000;
  color: #fff;
  padding: 2px 8px;
}

.unread-indicator {
  font-size: 10px;
  color: #ff4d4f;
  font-weight: 900;
  margin-left: 10px;
}

.mail-subject {
  margin: 15px 0;
  font-size: 18px;
  font-weight: bold;
}

.empty-tip {
  grid-column: 1/-1;
  text-align: center;
  padding: 100px;
  opacity: 0.3;
  font-size: 20px;
}

/* 动画过渡 */
.fade-blur-enter-active, .fade-blur-leave-active {
  transition: all 0.5s cubic-bezier(0.16, 1, 0.3, 1);
}
.fade-blur-enter-from, .fade-blur-leave-to {
  opacity: 0;
  filter: blur(20px);
  transform: scale(1.02);
}
</style>