<template>
  <div class="cyber-layout" @click="handleGlobalClick">
    <StatusPanel :style="statusPanelStyle"
                 class="dynamic-status-panel"/>

    <MailBox :style="mailBoxStyle" />
    <div class="fullscreen-bg-container">
      <div class="parallax-layer" :style="parallaxStyle">
        <img :src="themeBgSrc" class="base-bg-img breath-effect" draggable="false" loading="lazy" decoding="async" fetchpriority="low">
      </div>
      <div class="ambient-overlay" :style="ambientStyle"></div>
    </div>

    <div class="character-stage" :style="charParallaxStyle">


      <div class="live2d-box" @click.stop="openNav">
        <div ref="live2dInnerRef" class="live2d-container"></div>
        <div v-if="live2dLoadStatus === 'loading'" class="live2d-loading-overlay">
          <div class="live2d-loading-spinner"></div>
        </div>
        <div v-else-if="live2dLoadStatus === 'idle' && live2dLoadMode === 'manual'" class="live2d-deferred-overlay">
          <button class="live2d-load-btn" type="button" @click.stop="requestLive2DLoad">加载 Live2D</button>
          <span>{{ live2dLoadHint }}</span>
        </div>
        <div v-else-if="live2dLoadStatus === 'fail'" class="live2d-deferred-overlay">
          <button class="live2d-load-btn" type="button" @click.stop="requestLive2DLoad">重新加载</button>
          <span>Live2D 加载失败，可稍后重试。</span>
        </div>
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
      <div ref="panelBodyRef" class="panel-body">
          <div class="global-module-wrapper">
            <keep-alive include="UserProfile,MemoryFragment,EmotionPulse,ActionCenter,SettingsPanel,PerformanceDiagnostics">
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
  import { ref, computed, watch, onMounted, onBeforeUnmount, provide, defineAsyncComponent, nextTick } from 'vue'
import { useGsapAnimation } from '@/composables/useGsapAnimation'
import { useMouseParallax } from '@/composables/useMouseParallax'
import { useRouter } from 'vue-router'
import { disconnect, disposeAppLevel } from '@/utils/chatWebSocket'
import live2dModels from '@/config/live2d-models.json'
import request from '@/utils/request'
import { API } from '@/config/api'
import { useSettingsStore } from '@/stores/settings'
import { useUiStore } from '@/stores/ui'
import { useAuthStore } from '@/stores/auth'
import { useUserStore } from '@/stores/user'
import { OML2D_KEY } from '@/symbols'
import { isDiagnosticsEnabled } from '@/utils/diagnosticsAccess'
// ── ChatWindow 同步导入（输入框必须在首屏就绪） ──
import ChatWindow from "@/components/chat/ChatWindow.vue"
// ── 其余首屏不用的组件延迟加载 ──
const NavigationMenu = defineAsyncComponent(() => import("@/components/NavigationMenu.vue"))
const HistoryPanel = defineAsyncComponent(() => import("@/components/HistoryPanel.vue"))
const UserProfile = defineAsyncComponent(() => import("@/components/dashboard/UserProfile.vue"))
const MemoryFragment = defineAsyncComponent(() => import('@/components/dashboard/MemoryFragment.vue'))
const EmotionPulse = defineAsyncComponent(() => import("@/components/dashboard/EmotionPulse.vue"))
const ActionCenter = defineAsyncComponent(() => import('@/components/dashboard/ActionCenter.vue'))
const SettingsPanel = defineAsyncComponent(() => import('@/components/settings/SettingsPanel.vue'))
const PerformanceDiagnostics = defineAsyncComponent(() => import('@/components/dashboard/PerformanceDiagnostics.vue'))
const MailBox = defineAsyncComponent(() => import("@/components/Panel/MailBox.vue"))
const StatusPanel = defineAsyncComponent(() => import("@/components/Panel/StatusPanel.vue"))
const diagnosticsEnabled = isDiagnosticsEnabled()

const backgroundMap = {
  default: new URL('../assets/bk1.webp', import.meta.url).href,
  bk2: new URL('../assets/bk2.webp', import.meta.url).href,
  bk3: new URL('../assets/bk3.webp', import.meta.url).href,
  bk4: new URL('../assets/bk4.webp', import.meta.url).href,
  bk5: new URL('../assets/bk5.webp', import.meta.url).href,
}

// 模块级常量：避免 computed 每次求值都创建新对象
const viewMap = { 'user': UserProfile, 'memory': MemoryFragment, 'emotion': EmotionPulse, 'relation': EmotionPulse, 'action': ActionCenter, 'settings': SettingsPanel, 'diagnostics': PerformanceDiagnostics }
// 模型配置缓存：initLive2D 重试时复用，避免重复 map
// 缓存源为 live2d-models.json 静态 import，运行时不可变，因此不会过期
let modelsCache = null

// 首次 Live2D 空闲调度超时（ms），超过则降级为 setTimeout 兜底
const LIVE2D_IDLE_TIMEOUT = 5000
// Live2D 模型加载超时（ms），超时做重试
const LIVE2D_LOAD_TIMEOUT = 15000

const { gsap, timeline, entryStagger, rippleEffect } = useGsapAnimation()
const { mouseX, mouseY } = useMouseParallax()

const router = useRouter()
let live2dInitTimer = null
let live2dIdleCbId = null
let gsapEnterTimer = null
let _isAlive = true              // 组件存活标记，阻止卸载后的异步回调
let _live2dGen = 0               // 生成计数器，每次 destroy 自增，使旧 onLoad 回调失效
const activeLayer = ref('idle')
const activeTab = ref(null)
const chatWindowRef = ref(null)
const live2dInnerRef = ref(null)
const entered = ref(false)

// ── 主题背景（从用户设置读取） ──
const settingsStore = useSettingsStore()
const uiStore = useUiStore()
const themeBgSrc = computed(() => {
  const themeId = settingsStore.settings?.themeId || 'default'
  return backgroundMap[themeId] || backgroundMap.default
})

// ── 事件穿透防护 ──
// 记录面板打开的时间戳，handleGlobalClick 检测到短时间内（<300ms）的 click 视为穿透忽略
const lastPanelActionTime = ref(0)

// --- Live2D 相关（使用 oml2d 内置功能，去掉自定义 motionManager）---
const oml2dInstance = ref(null)
const live2dLoadStatus = ref('idle') // idle | loading | success | fail
const live2dLoadMode = ref('auto') // auto | manual
let _initLive2Ding = false // 防并发

provide(OML2D_KEY, oml2dInstance)

let _savedOnCopy = undefined  // 保存 oml2d 初始化前的 document.oncopy

const live2dLoadHint = computed(() => (
  live2dLoadMode.value === 'manual'
    ? '当前环境优先省流，点击后再下载 Live2D 资源。'
    : 'Live2D 将在浏览器空闲时自动加载。'
))

const shouldDeferLive2D = () => {
  if (typeof window === 'undefined') return false
  const isSmallScreen = window.matchMedia?.('(max-width: 768px)').matches
  const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection
  const saveData = Boolean(connection?.saveData)
  const effectiveType = connection?.effectiveType || ''
  const slowConnection = /(^|-)2g$|slow-2g/.test(effectiveType)
  return Boolean(isSmallScreen || prefersReducedMotion || saveData || slowConnection)
}

const cancelScheduledLive2DInit = () => {
  if (live2dIdleCbId !== null && 'cancelIdleCallback' in window) {
    cancelIdleCallback(live2dIdleCbId)
    live2dIdleCbId = null
  }
  if (live2dInitTimer) {
    clearTimeout(live2dInitTimer)
    live2dInitTimer = null
  }
}

const scheduleLive2DInit = () => {
  cancelScheduledLive2DInit()
  if (shouldDeferLive2D()) {
    live2dLoadMode.value = 'manual'
    return
  }

  live2dLoadMode.value = 'auto'
  if ('requestIdleCallback' in window) {
    live2dIdleCbId = requestIdleCallback(() => { live2dIdleCbId = null; initLive2D() }, { timeout: LIVE2D_IDLE_TIMEOUT })
    return
  }
  live2dInitTimer = setTimeout(initLive2D, 500)
}

const requestLive2DLoad = () => {
  live2dLoadMode.value = 'auto'
  cancelScheduledLive2DInit()
  initLive2D()
}

/** 销毁 oml2d 实例，清理所有资源 */
const destroyOml2d = () => {
  _live2dGen++ // 自增使所有待定 onLoad/loadTimer 回调失效
  const inst = oml2dInstance.value
  if (!inst) return
  try {
    inst.stopTipsIdle()
    inst.clearTips()
    inst.stageSlideOut()
  } catch (e) {
    console.warn('Dashboard destroyOml2d:', e)
  }
  // 恢复 document.oncopy 为 oml2d 初始化前的值（而非强制置 null）
  if (_savedOnCopy !== undefined) {
    window.document.oncopy = _savedOnCopy
    _savedOnCopy = undefined
  }
  if (live2dInnerRef.value) live2dInnerRef.value.innerHTML = ''
  oml2dInstance.value = null
  live2dLoadStatus.value = 'idle'
}

/** 加载模型重试 */
const _live2dRetries = ref(0)
const MAX_LIVE2D_RETRIES = 2
const retryLive2D = () => {
  if (!_isAlive) return
  if (_live2dRetries.value >= MAX_LIVE2D_RETRIES) {
    live2dLoadStatus.value = 'fail'
    return
  }
  _live2dRetries.value++
  console.warn(`Dashboard Live2D 模型加载失败，第 ${_live2dRetries.value} 次重试...`)
  live2dLoadStatus.value = 'loading'
  initLive2D()
}

const initLive2D = async () => {
  if (!_isAlive || !live2dInnerRef.value || _initLive2Ding) return
  _initLive2Ding = true
  live2dLoadStatus.value = 'loading'

  // 捕获当前 generation：所有异步回调都要与此比对，防止跨实例污染
  const gen = ++_live2dGen

  // 动态导入 oh-my-live2d（~976KB 延后加载，不阻塞首屏）
  let loadOml2d
  try {
    const mod = await import('oh-my-live2d')
    loadOml2d = mod.loadOml2d
  } catch (e) {
    console.error('Dashboard Live2D 模块加载失败:', e)
    live2dLoadStatus.value = 'fail'
    _initLive2Ding = false
    return
  }
  // await 后再次校验组件存活性
  if (!_isAlive || !live2dInnerRef.value || gen !== _live2dGen) {
    _initLive2Ding = false
    return
  }

  // 预计算模型配置（每次重试共享同一份）
  const modelCfgs = modelsCache || (() => {
    modelsCache = live2dModels.models.map(m => ({
      name: m.name,
      path: m.path,
      scale: 0.12,
      motionPreloadStrategy: 'IDLE',
    }))
    return modelsCache
  })()

  // 保存 oml2d 初始化前的 oncopy，销毁时恢复
  _savedOnCopy = window.document.oncopy

  const inst = loadOml2d({
    parentElement: live2dInnerRef.value,
    models: modelCfgs,
    primaryColor: '#5eead4',
    dockedPosition: 'left',
    sayHello: false,
    transitionTime: 800,
    initialStatus: 'sleep',
    // ── 状态栏（毛玻璃风格）──
    statusBar: {
      disable: false,
      loadingMessage: '同步记忆回路…',
      loadSuccessMessage: '记忆同步完成',
      loadFailMessage: '连接中断，点击重试',
      reloadMessage: '重新连接',
      restMessage: '知微正在休息',
      switchingMessage: '切换形态中',
      errorColor: '#e74c3c',
      transitionTime: 600,
      style: {
        background: 'rgba(0,0,0,0.6)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        border: '1px solid rgba(94,234,212,0.15)',
        borderRadius: '8px',
        color: '#fff',
        fontSize: '12px',
        padding: '6px 14px',
        letterSpacing: '1px',
        fontFamily: '"SF Mono", monospace',
      },
    },
    // ── 菜单（函数形式，未来可扩展多模型不同配置）──
    menus: {
      items: (defaultItems) => [
        // 保留除 About 外的所有默认菜单项
        ...defaultItems.filter(item => item.id !== 'About'),
        // 自定义：切换提示框常驻/自动模式
        {
          id: 'ToggleTips',
          title: '切换提示模式',
          icon: '💬',
          onClick: (oml2d) => {
            // 通过 tipsMessage 发送一条确认
            oml2d.tipsMessage('提示模式已切换 ♡', 2000, 5)
          }
        }
      ],
      style: {
        bottom: '120px',
        right: '10px',
        gap: '10px',
        display: 'flex',
        flexDirection: 'column',
      },
      itemStyle: {
        width: '44px',
        height: '44px',
        background: '#fffbf7',
        backdropFilter: 'blur(12px)',
        WebkitBackdropFilter: 'blur(12px)',
        border: '1.5px solid rgba(244,114,182,0.15)',
        borderRadius: '16px',
        color: '#4a4a5a',
        fontSize: '18px',
        fontFamily: '"PingFang SC","Microsoft YaHei","Hiragino Sans",sans-serif',
        boxShadow: '0 4px 16px rgba(244,114,182,0.08), 0 0 0 1px rgba(244,114,182,0.05)',
        transition: 'all 0.25s ease',
        cursor: 'pointer',
      },
    },
    // ── 提示框（函数形式，按时间段微调消息）──
    tips: (currentModel, modelIndex) => ({
      messageLine: 2,
      style: {
        maxWidth: '280px',
        whiteSpace: 'nowrap',
        overflow: 'visible',
        // 注意：实际视觉样式由 main.css 的 !important 接管，此处仅作 fallback
        background: 'rgba(0,0,0,0.5)',
        backdropFilter: 'blur(6px)',
        WebkitBackdropFilter: 'blur(6px)',
        border: '1px solid rgba(94,234,212,0.1)',
        borderRadius: '10px',
        padding: '10px 18px',
        color: '#fff',
        fontSize: '13px',
        letterSpacing: '0.5px',
        boxShadow: '0 4px 20px rgba(0,0,0,0.2)',
      },
      welcomeTips: {
        duration: 5000,
        priority: 3,
        message: {
          daybreak: '破晓了，记忆回路正在苏醒……',
          morning: `「${currentModel.name || '看板娘'}」已就位，今天的记录会写入哪段记忆呢？`,
          noon: '正午了，该补充数据能量了。',
          afternoon: '午后容易犯困，要重置一下注意力吗？',
          dusk: '黄昏时分，今天的记忆正在归档。',
          night: '晚上好，要翻阅今天的记忆碎片吗？',
          lateNight: '已经很晚了，记忆也需要休眠整理。',
          weeHours: '深夜信号…你还不休息吗？'
        }
      },
      idleTips: {
        interval: 30000,
        duration: 4000,
        priority: 1,
        wordTheDay(wordTheDayData) {
          return `${wordTheDayData.hitokoto}    ——${wordTheDayData.from}`
        },
      },
      copyTips: {
        duration: 3000,
        priority: 2,
        message: [
          '正在复制这段记忆…',
          '需要帮你归档这段内容吗？',
          '数据已复制到剪贴板。',
          '这段内容值得记住呢 ♡'
        ]
      }
    })
  })

  // await loadOml2d 返回后再次校验
  if (!_isAlive || !live2dInnerRef.value || gen !== _live2dGen) {
    _initLive2Ding = false
    return
  }

  oml2dInstance.value = inst

  // ── 模型加载超时监控（闭包捕获 gen，与当前实例绑定）──
  let loadTimer = null
  const currentGen = gen  // 固定闭包中的 gen 值

  const onLoadCb = (status) => {
    // 核心生命周期守卫：generation 不匹配说明实例已废弃
    if (currentGen !== _live2dGen) return

    switch (status) {
      case 'loading':
        loadTimer = setTimeout(() => {
          if (currentGen !== _live2dGen || !_isAlive) return
          console.warn('Dashboard Live2D 模型加载超时，触发重试')
          try { destroyOml2d() } catch (e) { console.warn('destroyOml2d during timeout:', e) }
          _initLive2Ding = false
          retryLive2D()
        }, LIVE2D_LOAD_TIMEOUT)
        break

      case 'success':
        if (loadTimer) { clearTimeout(loadTimer); loadTimer = null }
        _initLive2Ding = false
        live2dLoadStatus.value = 'success'
        if (currentGen === _live2dGen && oml2dInstance.value) {
          oml2dInstance.value.stageSlideIn()
        }
        break

      case 'fail':
        if (loadTimer) { clearTimeout(loadTimer); loadTimer = null }
        _initLive2Ding = false
        if (currentGen === _live2dGen) retryLive2D()
        break
    }
  }

  inst.onLoad(onLoadCb)
}

// 退出登录
const handleLogout = async () => {
  const authStore = useAuthStore()
  const userStore = useUserStore()
  try {
    // 用 authStore.refreshToken 而非直接读 localStorage，保持状态一致
    await request.post(API.AUTH_LOGOUT, { refreshToken: authStore.refreshToken })
  } catch {
    // 即使后端 logout 失败，前端仍需清理本地状态（通知用户已退出，但未通知服务端）
    uiStore.error('已退出登录（服务端通知失败）')
  }
  try { disconnect() } catch (e) { console.warn('WebSocket disconnect:', e) }
  destroyOml2d()
  userStore.clearProfile()
  settingsStore.resetSettings()
  uiStore.clearAllToasts()
  uiStore.closeAllPanels()
  // 清理 Pinia authStore（自动同步 localStorage），防止同一会话重新登录后 token 引用过期
  authStore.clearAuth()
  router.push({ name: 'Login' }).catch(() => {})
}

// navItems moved to NavigationMenu.vue

// --- 缓存签名工具：为每个 computed 创建独立的缓存实例，避免交叉缓存碰撞 ---
function createStyleCache() {
  let sig = ''
  let val = null
  return (s, factory) => {
    if (s === sig) return val
    sig = s
    val = factory()
    return val
  }
}
const statusPanelStyleCache = createStyleCache()
const mailBoxStyleCache = createStyleCache()
const charParallaxStyleCache = createStyleCache()
const parallaxStyleCache = createStyleCache()
const ambientStyleCache = createStyleCache()

// 时间组件逻辑
const statusPanelStyle = computed(() => {
  const sig = `${activeLayer.value}|${activeTab.value}`
  return statusPanelStyleCache(sig, () => {
    const isHistoryOpen = activeLayer.value === 'history'
    const isRightNavOpen = activeLayer.value === 'nav' && activeTab.value !== null
    if (isHistoryOpen) return { opacity: 0, pointerEvents: 'none', transform: 'translateX(-20px)', transition: 'all 0.6s cubic-bezier(0.16, 1, 0.3, 1)' }
    if (isRightNavOpen) return { opacity: 1, transform: 'scale(0.65) translate(-20%, -20%)', transformOrigin: 'top left', transition: 'all 0.8s cubic-bezier(0.16, 1, 0.3, 1)' }
    return { opacity: 1, transform: 'scale(1)', transition: 'all 0.8s cubic-bezier(0.16, 1, 0.3, 1)' }
  })
})

// 邮箱组件
const mailBoxStyle = computed(() => {
  const sig = `${activeLayer.value}|${activeTab.value}`
  return mailBoxStyleCache(sig, () => {
    const isAnyPanelOpen = activeLayer.value === 'history' || (activeLayer.value === 'nav' && activeTab.value !== null) || activeLayer.value === 'chat'
    return { opacity: isAnyPanelOpen ? 0 : 1, pointerEvents: isAnyPanelOpen ? 'none' : 'auto', transform: isAnyPanelOpen ? 'translateX(20px)' : 'translateX(0)', transition: 'all 0.6s cubic-bezier(0.16, 1, 0.3, 1)' }
  })
})

// 交互逻辑
const openNav = (e) => {
  lastPanelActionTime.value = Date.now()
  if(activeLayer.value === 'nav'){
    activeLayer.value = 'idle'
  }else {
    activeLayer.value = 'nav'
  }
  activeTab.value = null
  chatWindowRef.value?.collapse()
}

const handleNavClick = (item) => {
  if (item.path === 'diagnostics' && !diagnosticsEnabled) return
  activeTab.value = item.path
}

const panelBodyRef = ref(null)

// 切换 tab 时重置面板滚动位置
watch(activeTab, () => {
  nextTick(() => {
    if (panelBodyRef.value) panelBodyRef.value.scrollTop = 0
  })
})

const openHistory = () => {
  lastPanelActionTime.value = Date.now()
  activeLayer.value = 'history'
  activeTab.value = null
  chatWindowRef.value?.collapse()
}

const handleGlobalClick = () => {
  // 面板打开后 300ms 内的 click 视为事件穿透，忽略
  if (Date.now() - lastPanelActionTime.value < 300) return

  activeLayer.value = 'idle'
  activeTab.value = null
  chatWindowRef.value?.collapse()
}
// 核心：人物舞台样式计算（mouse 值量化为 4px 网格，减少微小编移触发的无意义重算）
const charParallaxStyle = computed(() => {
  const mx = Math.round(mouseX.value / 4) * 4
  const my = Math.round(mouseY.value / 4) * 4
  const sig = `${activeLayer.value}|${activeTab.value}|${mx}|${my}`
  return charParallaxStyleCache(sig, () => {
    const isPanelOpen = (activeLayer.value === 'nav' && activeTab.value !== null) || activeLayer.value === 'history'
    const scale = isPanelOpen ? 0.78 : 1
    const opacity = isPanelOpen ? 0.7 : 1
    let xOffset = 0
    if (activeLayer.value === 'nav' && activeTab.value !== null) xOffset = -18
    if (activeLayer.value === 'history') xOffset = 18
    const px = (mx / window.innerWidth - 0.5) * 12
    const py = (my / window.innerHeight - 0.5) * 8
    return { transform: `translate(calc(-50% + ${xOffset}vw + ${px}px), calc(-50% + ${py}px)) scale(${scale})`, opacity, transition: 'all 0.8s cubic-bezier(0.16, 1, 0.3, 1)' }
  })
})

const parallaxStyle = computed(() => {
  const mx = Math.round(mouseX.value / 4) * 4
  const sig = `${activeLayer.value}|${activeTab.value}|${mx}`
  return parallaxStyleCache(sig, () => {
    const isPanelOpen = (activeLayer.value === 'nav' && activeTab.value !== null) || activeLayer.value === 'history'
    const blurValue = isPanelOpen ? 12 : 0
    const px = (mx / window.innerWidth - 0.5) * 30
    return { transform: `translate(calc(-50% + ${px}px), -50%)`, filter: `blur(${blurValue}px)`, transition: 'filter 0.6s ease' }
  })
})

const effectiveViewMap = computed(() => {
  if (diagnosticsEnabled) return viewMap
  const { diagnostics: _diagnostics, ...rest } = viewMap
  return rest
})
const currentView = computed(() => effectiveViewMap.value[activeTab.value] || null)

const tabNames = { 'user': '你的样子', 'memory': '与我的回忆', 'emotion': '灵魂的颜色', 'relation': '成长轨迹', 'action': '为你推荐', 'settings': '灵魂调谐', 'diagnostics': '性能诊断' }
const activeTabName = computed(() => tabNames[activeTab.value] || '')

const ambientHour = ref(new Date().getHours())
let ambientTimer = null

const ambientStyle = computed(() => {
  const sig = `${ambientHour.value}`
  return ambientStyleCache(sig, () => {
    const h = ambientHour.value
    let color = 'transparent'
    if (h >= 19 || h < 6) color = 'rgba(20, 30, 80, 0.2)'
    return { backgroundColor: color }
  })
})

onMounted(() => {
  // 页面挂载后获取用户设置（不在模块级执行，避免副作用时序问题）
  if (!settingsStore.settings) settingsStore.fetchSettings()

  // Live2D 延后到空闲时初始化，避免阻塞首屏渲染
  // 小屏/省流/慢网环境改为点击后再下载 Live2D 大 chunk。
  scheduleLive2DInit()

  // 周期性更新 ambientHour，使 ambientStyle 在小时边界生效
  ambientTimer = setInterval(() => { ambientHour.value = new Date().getHours() }, 60000)

  // GSAP 入场动画 — 使用 timeline 序列
  gsapEnterTimer = setTimeout(() => {
    entered.value = true
    nextTick(() => {
      const tl = timeline()
      tl.from('.dynamic-status-panel', { opacity: 0, y: 25, duration: 0.6, ease: 'back.out(1.7)' })
        .from('.mailbox-wrapper', { opacity: 0, y: 25, duration: 0.6, ease: 'back.out(1.7)' }, '-=0.4')
        .from('.character-stage', { opacity: 0, y: 25, duration: 0.6, ease: 'back.out(1.7)' }, '-=0.4')
        .from('.global-chat-area', { opacity: 0, y: 25, duration: 0.6, ease: 'back.out(1.7)' }, '-=0.4')

      document.querySelectorAll('.logout-btn, .close-btn').forEach(el => rippleEffect(el))
    })
  }, 100)
})
onBeforeUnmount(() => {
  _isAlive = false  // 组件卸载标记，必须在 destroyOml2d 之前设置
  destroyOml2d()
  disposeAppLevel()  // 清除 WebSocket 残留定时器，阻止后续重连
  cancelScheduledLive2DInit()
  if (gsapEnterTimer) { clearTimeout(gsapEnterTimer); gsapEnterTimer = null }
  if (ambientTimer) { clearInterval(ambientTimer); ambientTimer = null }
})
</script>

<style scoped>
/* ── GPU 分层（只对实际动画元素启用 will-change，静态面板用 contain 隔离） ── */
.character-stage,
.content-panel,
.chat-window-container { will-change: transform, opacity; contain: layout style; }

.dynamic-status-panel,
.mailbox-wrapper,
.global-chat-area,
.parallax-layer,
.ambient-overlay { contain: layout style; }

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
.live2d-loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.live2d-deferred-overlay {
  position: absolute;
  left: 50%;
  bottom: 12%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: min(260px, 80vw);
  padding: 14px 16px;
  border: 1px solid rgba(94, 234, 212, 0.2);
  border-radius: 14px;
  background: rgba(7, 17, 33, 0.58);
  backdrop-filter: blur(12px);
  box-shadow: 0 14px 28px rgba(0, 0, 0, 0.24);
  color: rgba(255, 255, 255, 0.68);
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  transform: translateX(-50%);
  pointer-events: auto;
}

.live2d-load-btn {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid rgba(94, 234, 212, 0.36);
  border-radius: 999px;
  background: rgba(94, 234, 212, 0.12);
  color: #b9fff4;
  cursor: pointer;
}

.live2d-load-btn:hover {
  border-color: rgba(94, 234, 212, 0.68);
  background: rgba(94, 234, 212, 0.2);
}

.live2d-loading-spinner {
  width: 32px;
  height: 32px;
  border: 2px solid rgba(255,255,255,0.1);
  border-top-color: #5eead4;
  border-radius: 50%;
  animation: live2d-spin 0.8s linear infinite;
}
@keyframes live2d-spin {
  to { transform: rotate(360deg); }
}

/* --- 面板通用样式 --- */
.content-panel {
  background: rgba(0,0,0,0.3); /* 调暗背景更沉浸 */
  backdrop-filter: blur(6px);
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
  cursor: pointer; transition: background-color 0.3s ease, border-color 0.3s ease, color 0.3s ease;
}
.logout-btn:hover {
  background: rgba(216, 74, 98, 0.2);
  border-color: rgba(216, 74, 98, 0.5); color: #d84a62;
}
.close-btn { background: none; border: none; color: white; font-size: 20px; cursor: pointer; opacity: 0.5; transition: opacity 0.3s ease, transform 0.3s ease; }
.close-btn:hover { opacity: 1; transform: rotate(90deg); }

.panel-body {
  height: calc(100% - 120px); overflow-y: auto;
  position: relative; z-index: 1;
}

/* 动画 */
.bubble-fade-enter-active, .bubble-fade-leave-active { transition: opacity 0.5s ease, transform 0.5s ease; }
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

/* ── 响应式布局 ── */

/* 平板 (≤ 1024px)：内容面板占 65%，Live2D 缩小 */
@media (max-width: 1024px) {
  .content-panel { width: 65%; }
  .live2d-box { width: 450px; }
  .live2d-container { width: 500px; height: 500px; }
}

/* 手机 (≤ 768px)：内容面板全屏，Live2D 进一步缩小 */
@media (max-width: 768px) {
  .content-panel {
    width: 100%; height: 100%;
    border-radius: 0;
  }
  .panel-header { padding: 40px 20px 16px; }
  .panel-title { font-size: 20px; }
  .live2d-box { width: 320px; }
  .live2d-container { width: 380px; height: 380px; }
  .panel-body { height: calc(100% - 90px); }
}

/* 小屏手机 (≤ 480px) */
@media (max-width: 480px) {
  .panel-header { padding: 32px 16px 12px; }
  .panel-title { font-size: 18px; }
  .logout-btn { font-size: 11px; padding: 5px 12px; }
  .live2d-box { width: 260px; }
  .live2d-container { width: 300px; height: 300px; }
}
</style>
