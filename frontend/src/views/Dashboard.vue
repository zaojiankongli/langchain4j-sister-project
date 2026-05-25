<template>
  <div class="cyber-layout" @click="handleGlobalClick">
    <StatusPanel :style="statusPanelStyle"
                 class="dynamic-status-panel"/>

    <MailBox :style="mailBoxStyle" />
    <div class="fullscreen-bg-container">
      <div class="parallax-layer" :style="parallaxStyle">
        <img src="../assets/背景2.png" class="base-bg-img breath-effect" draggable="false">
      </div>
      <div class="ambient-overlay" :style="ambientStyle"></div>
    </div>

    <div class="character-stage" :style="charParallaxStyle">


      <div class="live2d-box" @click.stop="openNav">
        <div ref="live2dInnerRef" class="live2d-container"></div>
      </div>

      <NavigationMenu
          :visible="activeLayer === 'nav'"
          :activeTab="activeTab"
          @navigate="handleNavClick"
      />
    </div>

    <div
        class="content-panel nav-panel"
        :class="{ 'panel-open': activeLayer === 'nav' && activeTab !== null }"
        @click.stop
    >
      <div class="panel-background-container"><div class="panel-glass-bg"></div></div>
      <div class="panel-header">
        <div class="title-area">
          <span class="title-tag">SPACE //</span>
          <h2 class="panel-title">{{ activeTabName }}</h2>
        </div>
        <div class="header-actions">
          <button class="logout-btn" @click="handleLogout">退出登录</button>
          <button class="close-btn" @click="activeTab = null">✕</button>
        </div>
      </div>
      <div class="panel-body">
          <div class="global-module-wrapper">
            <keep-alive include="UserProfile,MemoryFragment,PersonalityCloud,EmotionPulse,ActionCenter">
              <component :is="currentView" />
            </keep-alive>
          </div>
      </div>
    </div>

    <HistoryPanel
        :isOpen="activeLayer === 'history'"
        @close="handleGlobalClick"
    />

    <ChatWindow
        ref="chatWindowRef"
        class="global-chat-area"
        :isActive="activeLayer === 'idle' || activeLayer === 'chat'"
        @open-history="openHistory"
        @click.stop
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, provide } from 'vue'
import { useRouter } from 'vue-router'
import { clearToken } from '@/utils/auth'
import { disconnect } from '@/utils/chatWebSocket'
import { loadOml2d } from 'oh-my-live2d'
import live2dModels from '@/config/live2d-models.json'
import { Live2DMotionManager } from '@/utils/live2dMotionManager'
import request from '@/utils/request'
import ChatWindow from "@/components/chat/ChatWindow.vue"
import NavigationMenu from "@/components/NavigationMenu.vue"
import HistoryPanel from "@/components/HistoryPanel.vue"

import UserProfile from "@/components/dashboard/UserProfile.vue"
import MemoryFragment from '@/components/dashboard/MemoryFragment.vue'
import PersonalityCloud from '@/components/dashboard/PersonalityCloud.vue'
import EmotionPulse from "@/components/dashboard/EmotionPulse.vue"
import ActionCenter from '@/components/dashboard/ActionCenter.vue'
import MailBox from "@/components/Panel/MailBox.vue"
import StatusPanel from "@/components/Panel/StatusPanel.vue"

const router = useRouter()
const activeLayer = ref('idle')
const activeTab = ref(null)
const chatWindowRef = ref(null)
const live2dInnerRef = ref(null)
const mouseX = ref(window.innerWidth / 2)
const mouseY = ref(window.innerHeight / 2)


// 新增：事件锁，防止 mousedown 导致底层的 click 穿透触发关闭
const isTransitionLocked = ref(false)

// --- Live2D 相关 ---
const oml2dInstance = ref(null)
let motionManager = null

// 在 setup 顶层 provide（保证子组件 inject 可获取响应式 ref）
provide('oml2d', oml2dInstance)

const initLive2D = () => {
  if (!live2dInnerRef.value) return
  const models = live2dModels.models.map(model => ({
    name: model.name,
    path: model.path,
    scale: 0.12
  }))

  oml2dInstance.value = loadOml2d({
    parentElement: live2dInnerRef.value,
    models: models,
    draggable: true,
    touchable: true
  })

  if (!oml2dInstance.value) return

  motionManager = new Live2DMotionManager(oml2dInstance.value)

  oml2dInstance.value.onLoad((status) => {
    if (status === 'success') {
      motionManager?.startIdle()
    }
  })
}

// 退出登录
const handleLogout = () => {
  request.post('/auth/logout').catch(() => {})
  disconnect()
  motionManager?.destroy()
  oml2dInstance.value?.destroy()
  if (live2dInnerRef.value) {
    live2dInnerRef.value.innerHTML = ''
  }
  oml2dInstance.value = null
  clearToken()
  router.push('/login')
}

// navItems moved to NavigationMenu.vue

// 时间组件逻辑
const statusPanelStyle = computed(() => {
  const isHistoryOpen = activeLayer.value === 'history';
  const isRightNavOpen = activeLayer.value === 'nav' && activeTab.value !== null;

  if (isHistoryOpen) {
    return {
      opacity: 0,
      pointerEvents: 'none',
      transform: 'translateX(-20px)',
      transition: 'all 0.6s cubic-bezier(0.16, 1, 0.3, 1)'
    };
  }

  if (isRightNavOpen) {
    return {
      opacity: 1,
      transform: 'scale(0.65) translate(-20%, -20%)',
      transformOrigin: 'top left',
      transition: 'all 0.8s cubic-bezier(0.16, 1, 0.3, 1)'
    };
  }

  return {
    opacity: 1,
    transform: 'scale(1)',
    transition: 'all 0.8s cubic-bezier(0.16, 1, 0.3, 1)'
  };
});

// 邮箱组件
const mailBoxStyle = computed(() => {
  const isAnyPanelOpen =
      activeLayer.value === 'history' ||
      (activeLayer.value === 'nav' && activeTab.value !== null) ||
      activeLayer.value === 'chat';

  return {
    opacity: isAnyPanelOpen ? 0 : 1,
    pointerEvents: isAnyPanelOpen ? 'none' : 'auto',
    transform: isAnyPanelOpen ? 'translateX(20px)' : 'translateX(0)',
    transition: 'all 0.6s cubic-bezier(0.16, 1, 0.3, 1)'
  };
});

// 交互逻辑
const openNav = () => {
  if(activeLayer.value == 'nav'){
    activeLayer.value = 'idle'
  }else {
    activeLayer.value = 'nav'
  }
  activeTab.value = null
  chatWindowRef.value?.collapse()
}

const handleNavClick = (item) => {
  activeTab.value = item.path
}

const openHistory = () => {
  // 1. 开启锁定状态
  isTransitionLocked.value = true

  activeLayer.value = 'history'
  activeTab.value = null
  chatWindowRef.value?.collapse()

  // 2. 延迟 150 毫秒后解锁，避开孤儿 Click 事件触发的瞬间
  setTimeout(() => {
    isTransitionLocked.value = false
  }, 150)
}

const handleGlobalClick = () => {
  // 3. 如果当前处于锁定状态（刚点开历史记录），则拦截这次误触的点击
  if (isTransitionLocked.value) return

  activeLayer.value = 'idle'
  activeTab.value = null
  chatWindowRef.value?.collapse()
}
// 核心：人物舞台样式计算
const charParallaxStyle = computed(() => {
  const isPanelOpen = (activeLayer.value === 'nav' && activeTab.value !== null) || activeLayer.value === 'history'

  const scale = isPanelOpen ? 0.78 : 1
  const opacity = isPanelOpen ? 0.7 : 1

  let xOffset = 0
  if (activeLayer.value === 'nav' && activeTab.value !== null) xOffset = -18
  if (activeLayer.value === 'history') xOffset = 18

  const mx = (mouseX.value / window.innerWidth - 0.5) * 12
  const my = (mouseY.value / window.innerHeight - 0.5) * 8

  return {
    transform: `translate(calc(-50% + ${xOffset}vw + ${mx}px), calc(-50% + ${my}px)) scale(${scale})`,
    opacity: opacity,
    transition: 'all 0.8s cubic-bezier(0.16, 1, 0.3, 1)'
  }
})

const parallaxStyle = computed(() => {
  const isPanelOpen = (activeLayer.value === 'nav' && activeTab.value !== null) || activeLayer.value === 'history'
  const blurValue = isPanelOpen ? 12 : 0
  const mx = (mouseX.value / window.innerWidth - 0.5) * 30

  return {
    transform: `translate(calc(-50% + ${mx}px), -50%)`,
    filter: `blur(${blurValue}px)`,
    transition: 'filter 0.6s ease'
  }
})

const currentView = computed(() => {
  const maps = {
    'user': UserProfile,
    'memory': MemoryFragment,
    'emotion': PersonalityCloud,
    'relation': EmotionPulse,
    'action': ActionCenter,
  }
  return maps[activeTab.value] || null
})

const tabNames = { 'user': '你的样子', 'memory': '与我的回忆', 'emotion': '灵魂的颜色', 'relation': '成长轨迹', 'action': '为你推荐' }
const activeTabName = computed(() => tabNames[activeTab.value] || '')

const ambientStyle = computed(() => {
  const h = new Date().getHours()
  let color = 'transparent'
  if (h >= 19 || h < 6) color = 'rgba(20, 30, 80, 0.2)'
  return { backgroundColor: color }
})

const updateMouse = (e) => { mouseX.value = e.clientX; mouseY.value = e.clientY }
onMounted(() => {
  window.addEventListener('mousemove', updateMouse, { passive: true })
  initLive2D()
})
onUnmounted(() => {
  window.removeEventListener('mousemove', updateMouse)
  motionManager?.destroy()
  if (live2dInnerRef.value) {
    live2dInnerRef.value.innerHTML = ''
  }
  oml2dInstance.value = null
})
</script>

<style scoped>
.cyber-layout {
  width: 100vw; height: 100vh;
  position: relative; overflow: hidden; background: #000;
}

/* --- 背景与视差 --- */
.fullscreen-bg-container { position: absolute; inset: 0; pointer-events: none; }
.parallax-layer { position: absolute; top: 50%; left: 50%; width: 110%; height: 110%; }
.base-bg-img { width: 100%; height: 100%; object-fit: cover; }
.ambient-overlay { position: absolute; inset: 0; transition: background 2s ease; }

/* --- 人物舞台布局 --- */
.character-stage {
  position: absolute; top: 50%; left: 50%;
  z-index: 10;
  pointer-events: none;
}

/* --- 点击交互能力 --- */
.live2d-box,
.menu-bubble {
  pointer-events: auto;
}
.live2d-box {
  width: 600px; height: 85vh;
  display: flex; align-items: flex-end; justify-content: center;
  cursor: pointer;
}
.live2d-container {
  position: absolute;
  bottom: 5%;
  left: 50%;
  transform: translateX(-50%);
  width: 650px;
  height: 650px;
}

/* --- 面板通用样式 --- */
.content-panel {
  background: rgba(0,0,0,0.3); /* 调暗背景更沉浸 */
  backdrop-filter: blur(10px);
  position: fixed; top: 0; width: 50%; height: 100%;
  z-index: 100; transition: transform 0.8s cubic-bezier(0.16, 1, 0.3, 1);
  pointer-events: auto;
}
.nav-panel { right: 0; transform: translateX(100%); }
.nav-panel.panel-open { transform: translateX(0); }
.history-panel { left: 0; transform: translateX(-100%); }
.history-panel.panel-open { transform: translateX(0); }

.panel-glass-bg {
  position: absolute; inset: 0;
  background: rgba(255, 255, 255, 0.02);
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}
.panel-header { position: relative; padding: 60px 40px 20px; display: flex; justify-content: space-between; align-items: center; }
.panel-title { color: white; font-size: 24px; font-weight: 200; letter-spacing: 1px; }
.title-tag { font-size: 10px; color: #5eead4; letter-spacing: 2px; }

.header-actions { display: flex; align-items: center; gap: 12px; }
.logout-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px; padding: 6px 16px; border-radius: 6px;
  cursor: pointer; transition: all 0.3s;
}
.logout-btn:hover {
  background: rgba(216, 74, 98, 0.2);
  border-color: rgba(216, 74, 98, 0.5); color: #d84a62;
}
.close-btn { background: none; border: none; color: white; font-size: 20px; cursor: pointer; opacity: 0.5; transition: 0.3s; }
.close-btn:hover { opacity: 1; transform: rotate(90deg); }

.panel-body {
  height: calc(100% - 120px); overflow-y: auto;
  position: relative; z-index: 1;
}

/* 动画 */
.bubble-fade-enter-active, .bubble-fade-leave-active { transition: all 0.5s ease; }
.bubble-fade-enter-from, .bubble-fade-leave-to { opacity: 0; transform: translateY(10px); }

/* view-dissolve transition for panel content switching */
.view-dissolve-enter-active,
.view-dissolve-leave-active {
  transition: opacity 0.3s ease;
}
.view-dissolve-enter-from,
.view-dissolve-leave-to {
  opacity: 0;
}
</style>