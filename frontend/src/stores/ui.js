import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 全局 UI 状态 Store
 *
 * 集中管理 Dashboard 布局状态、全局加载指示器、
 * 通知消息等跨组件共享的 UI 状态。
 * 替代 Dashboard.vue 中 16+ 个分散的 ref。
 */
export const useUiStore = defineStore('ui', () => {
  // ── 面板导航状态 ──
  const activeLayer = ref('')
  const activeTab = ref('user')
  const isSidePanelOpen = ref(true)
  const isHistoryOpen = ref(false)

  // ── 全局加载 ──
  const globalLoading = ref(false)
  const loadingMessage = ref('')

  // ── 通知系统 ──
  const toasts = ref([])
  let toastId = 0

  // ── 面板可见性 ──
  const currentView = computed(() => {
    if (!activeLayer.value) return null
    return activeLayer.value
  })

  // ── 导航动作 ──
  function navigateTo(layer, tab) {
    activeLayer.value = layer
    if (tab) activeTab.value = tab
    isSidePanelOpen.value = true
  }

  function toggleSidePanel() {
    isSidePanelOpen.value = !isSidePanelOpen.value
  }

  function toggleHistory() {
    isHistoryOpen.value = !isHistoryOpen.value
  }

  function closeAllPanels() {
    activeLayer.value = ''
    activeTab.value = 'user'
    isSidePanelOpen.value = false
    isHistoryOpen.value = false
  }

  // ── 全局加载 ──
  function showLoading(msg) {
    globalLoading.value = true
    loadingMessage.value = msg || ''
  }

  function hideLoading() {
    globalLoading.value = false
    loadingMessage.value = ''
  }

  // ── 通知 ──
  function addToast(message, type = 'info', duration = 3000) {
    const id = ++toastId
    toasts.value.push({ id, message, type })
    if (duration > 0) {
      setTimeout(() => removeToast(id), duration)
    }
    return id
  }

  function removeToast(id) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }

  function success(msg) { return addToast(msg, 'success') }
  function error(msg) { return addToast(msg, 'error', 5000) }
  function info(msg) { return addToast(msg, 'info') }

  return {
    activeLayer,
    activeTab,
    isSidePanelOpen,
    isHistoryOpen,
    globalLoading,
    loadingMessage,
    toasts,
    currentView,
    navigateTo,
    toggleSidePanel,
    toggleHistory,
    closeAllPanels,
    showLoading,
    hideLoading,
    addToast,
    removeToast,
    success,
    error,
    info,
  }
})
