<template>
  <div class="personality-settings-container">
    <div class="settings-header">
      <span class="header-icon">✦</span>
      <h3 class="header-title">维度调整 // DIMENSION TUNING</h3>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="state-container">
      <div class="loading-bar"></div>
      <p class="state-text">正在加载设置数据...</p>
    </div>

    <!-- Error state -->
    <div v-else-if="loadError" class="state-container error">
      <span class="error-icon">⚠</span>
      <p class="error-text">{{ loadError }}</p>
      <button class="retry-btn" @click="settingsStore.init()">重新连接</button>
    </div>

    <!-- Settings grid -->
    <div v-else class="settings-grid">
      <section class="parameter-card">
        <div class="card-glow"></div>
        <div class="card-content">
          <div class="param-info">
            <span class="param-label">感知灵敏度</span>
            <span class="param-value">{{ displaySettings.sensitivity }}</span>
          </div>
          <div class="slider-wrapper">
            <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                v-model.number="displaySettings.sensitivity"
                class="cyber-slider"
            >
            <div class="slider-track-glow" :style="{ width: (displaySettings.sensitivity * 100) + '%' }"></div>
          </div>
          <p class="param-desc">决定 AI 对用户情绪波动的捕捉精度。</p>
        </div>
      </section>

      <section class="parameter-card">
        <div class="card-glow"></div>
        <div class="card-content">
          <div class="param-info">
            <span class="param-label">情绪衰减率</span>
            <span class="param-value">{{ displaySettings.decayRate }}</span>
          </div>
          <div class="slider-wrapper">
            <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                v-model.number="displaySettings.decayRate"
                class="cyber-slider"
            >
            <div class="slider-track-glow" :style="{ width: (displaySettings.decayRate * 100) + '%' }"></div>
          </div>
          <p class="param-desc">情绪强度随时间的自然衰减速度。</p>
        </div>
      </section>

      <section class="parameter-card">
        <div class="card-glow"></div>
        <div class="card-content">
          <div class="param-info">
            <span class="param-label">回归基线速度</span>
            <span class="param-value">{{ displaySettings.regressionRate }}</span>
          </div>
          <div class="slider-wrapper">
            <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                v-model.number="displaySettings.regressionRate"
                class="cyber-slider"
            >
            <div class="slider-track-glow" :style="{ width: (displaySettings.regressionRate * 100) + '%' }"></div>
          </div>
          <p class="param-desc">情绪刺激后的回归常态速度。</p>
        </div>
      </section>
    </div>

    <div class="settings-footer">
      <button class="save-btn" @click="handleSave" :disabled="saving">
        {{ saving ? '同步中...' : '写入灵魂核心' }}
      </button>
      <p v-if="saveMsg" class="save-msg">{{ saveMsg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import { useUiStore } from '@/stores/ui'

const settingsStore = useSettingsStore()
const uiStore = useUiStore()

// Local form for editing
const displaySettings = ref({
  sensitivity: 0.5,
  decayRate: 0.1,
  regressionRate: 0.05,
})

const saving = ref(false)
const saveMsg = ref('')
const loadError = ref('')
const loading = ref(true)
let _isMounted = true
onBeforeUnmount(() => { _isMounted = false })

// Combined watch for settings, loading, and errors
watch([() => settingsStore.settings, () => settingsStore.loading, () => settingsStore.error], ([settings, loadingVal, errorVal]) => {
  if (errorVal) {
    loadError.value = errorVal
    loading.value = false
    return
  }
  if (settings) {
    // 增量更新：不替换整个 displaySettings 对象，只更新具体字段
    // 避免触发 slider-track-glow 等依赖 .value 引用变化的 :style 重渲染
    displaySettings.value.sensitivity = settings.sensitivity ?? 0.5
    displaySettings.value.decayRate = settings.decayRate ?? 0.1
    displaySettings.value.regressionRate = settings.regressionRate ?? 0.05
    loadError.value = ''
  }
  // Only reflect the store's loading state directly — no premature false assignment
  loading.value = loadingVal
}, { immediate: true })

const handleSave = async () => {
  saving.value = true
  saveMsg.value = ''
  try {
    const current = settingsStore.settings || {}
    const merged = { ...current, ...displaySettings.value }
    const success = await settingsStore.saveSettings(merged)
    if (!_isMounted) return
    if (success) {
      uiStore.success('灵魂参数已同步')
    } else {
      saveMsg.value = settingsStore.error || '同步失败'
    }
  } catch (e) {
    if (!_isMounted) return
    saveMsg.value = e?.message || '同步失败'
  } finally {
    if (_isMounted) saving.value = false
  }
}

onMounted(() => {
  settingsStore.init()
})
</script>

<style scoped>
/* --- 以下为新增/修改的动画逻辑，不改变原有布局结构 --- */

/* 1. 给容器增加一个整体的初始淡入，确保加载时不闪烁 */
.personality-settings-container {
  padding: 40px;
  color: white;
  height: 100%;
  overflow-y: auto;
  /* 基础动画 */
  animation: container-fade 0.8s ease-out forwards;
}

/* 2. 定义卡片的错峰进入动画 */
.parameter-card {
  position: relative;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  padding: 25px;
  transition: background-color 0.4s ease, transform 0.4s ease;
  overflow: hidden;

  /* 初始状态：透明且向下偏移 */
  opacity: 0;
  transform: translateY(20px);
  /* 执行动画 */
  animation: card-appear 0.8s cubic-bezier(0.2, 0, 0.2, 1) forwards;
}

/* 错峰延迟：通过 nth-child 控制每个卡片弹出的时间 */
.parameter-card:nth-child(1) { animation-delay: 0.1s; }
.parameter-card:nth-child(2) { animation-delay: 0.3s; }
.parameter-card:nth-child(3) { animation-delay: 0.5s; }

/* 3. 定义关键帧 */
@keyframes container-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes card-appear {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 4. 保持你原有的交互样式不变 */
.parameter-card:hover {
  background: rgba(255, 255, 255, 0.08);
  transform: translateY(0) translateX(5px);
}

.card-glow {
  position: absolute;
  top: 0; left: 0; width: 4px; height: 100%;
  background: linear-gradient(to bottom, transparent, rgba(255,255,255,0.5), transparent);
  opacity: 0;
  transition: opacity 0.4s;
}
.parameter-card:hover .card-glow { opacity: 1; }

/* --- 以下是你原有的其他所有布局样式，完全保留 --- */
.settings-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
  opacity: 0.8;
}
.header-icon { font-size: 20px; color: #fff; text-shadow: 0 0 10px rgba(255,255,255,0.5); }
.header-title { font-size: 18px; opacity: 1; letter-spacing: 2px; font-weight: 300; }
.settings-grid {
  display: flex;
  flex-direction: column;
  gap: 25px;
}
.param-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}
.param-label { font-size: 16px; opacity: 0.9; }
.param-value { font-family: 'Courier New', monospace; color: #5eead4; }
.param-desc { font-size: 12px; opacity: 0.6; margin-top: 15px; line-height: 1.6; }
.slider-wrapper { position: relative; width: 100%; height: 4px; margin: 20px 0; }
.cyber-slider {
  position: absolute; width: 100%; height: 100%;
  appearance: none; background: rgba(255,255,255,0.1);
  outline: none; z-index: 2;
}
.cyber-slider::-webkit-slider-thumb {
  appearance: none; width: 12px; height: 12px;
  background: white; border-radius: 50%; cursor: pointer;
  box-shadow: 0 0 10px rgba(255,255,255,0.8);
}
.slider-track-glow {
  position: absolute; left: 0; top: 0; height: 100%;
  background: white; box-shadow: 0 0 15px rgba(255,255,255,0.3);
}
.toggle-group { display: flex; gap: 10px; }
.toggle-item {
  padding: 6px 15px; border-radius: 4px;
  background: rgba(255,255,255,0.05); font-size: 13px;
  cursor: pointer; transition: background-color 0.3s ease, border-color 0.3s ease;
  border: 1px solid rgba(255,255,255,0.1);
}
.toggle-item.active {
  background: rgba(255,255,255,0.2); border-color: white;
}
.cyber-switch {
  width: 40px; height: 20px;
  background: rgba(255,255,255,0.1);
  border-radius: 20px; position: relative; cursor: pointer;
}
.switch-handle {
  position: absolute; top: 2px; left: 2px;
  width: 16px; height: 16px; background: white;
  border-radius: 50%; transition: left 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28), background-color 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}
.cyber-switch.on { background: rgba(94, 234, 212, 0.4); }
.cyber-switch.on .switch-handle { left: 22px; background: #5eead4; }
.settings-footer { margin-top: 50px; padding-bottom: 100px; }
.save-btn {
  background: transparent; border: 1px solid rgba(255,255,255,0.3);
  color: white; padding: 12px 30px; border-radius: 4px;
  letter-spacing: 2px; cursor: pointer; transition: background-color 0.3s ease, color 0.3s ease, opacity 0.3s ease;
}
.save-btn:hover { background: white; color: black; }
.save-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.save-btn:disabled:hover { background: transparent; color: white; }

.save-msg {
  margin-top: 12px;
  font-size: 12px;
  color: #ff6b6b;
  text-align: center;
}

/* Loading & error states */
.state-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 16px;
  color: rgba(255,255,255,0.6);
  font-size: 13px;
}
.state-container.error { color: #ff6b6b; }
.state-text { margin: 0; }
.error-icon { font-size: 28px; }
.error-text { color: #ff6b6b; margin: 0; }
.loading-bar {
  width: 36px; height: 36px;
  border: 2px solid rgba(255,255,255,0.1);
  border-top-color: #5eead4;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.retry-btn {
  margin-top: 8px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.2);
  color: white;
  padding: 8px 20px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}
.retry-btn:hover {
  background: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.4);
}

@keyframes spin { to { transform: rotate(360deg); } }
</style>
