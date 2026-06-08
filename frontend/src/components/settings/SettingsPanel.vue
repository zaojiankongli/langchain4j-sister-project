<template>
  <div class="settings-container">
    <div class="settings-header">
      <span class="header-icon">⚙</span>
      <h3 class="header-title">灵魂调谐</h3>
    </div>

    <div v-if="loading" class="section-loading">
      <div class="loading-spinner"></div>
      <p class="loading-text">加载调谐参数中...</p>
    </div>
    <div v-else-if="loadError" class="section-error">
      <p>{{ loadError }}</p>
      <button class="retry-btn" @click="initLoad">重试</button>
    </div>

    <template v-else>
      <SettingsPersonality :form="form" :presets="presets" @select-preset="applyPreset" />

      <!-- ============ 2. 情绪引擎 ============ -->
      <section class="settings-section">
        <div class="section-title-row">
          <span class="section-icon">◈</span>
          <span class="section-label">情绪引擎 // EMOTION ENGINE</span>
        </div>
        <div class="section-body">
          <div class="param-card">
            <div class="param-header">
              <span class="param-label">敏感度</span>
              <span class="param-value">{{ form.sensitivity.toFixed(2) }}</span>
            </div>
            <p class="param-desc">对外界刺激的反应强度</p>
            <div class="slider-wrap">
              <input v-model.number="form.sensitivity" type="range" min="0" max="1" step="0.05" class="cyber-slider" />
              <div class="slider-fill" :style="{ width: form.sensitivity * 100 + '%' }"></div>
            </div>
          </div>
          <div class="param-card">
            <div class="param-header">
              <span class="param-label">衰减率</span>
              <span class="param-value">{{ form.decayRate.toFixed(2) }}</span>
            </div>
            <p class="param-desc">情绪自然回落的速率</p>
            <div class="slider-wrap">
              <input v-model.number="form.decayRate" type="range" min="0" max="1" step="0.05" class="cyber-slider" />
              <div class="slider-fill" :style="{ width: form.decayRate * 100 + '%' }"></div>
            </div>
          </div>
          <div class="param-card">
            <div class="param-header">
              <span class="param-label">回归率</span>
              <span class="param-value">{{ form.regressionRate.toFixed(2) }}</span>
            </div>
            <p class="param-desc">情绪向基准回归的速度</p>
            <div class="slider-wrap">
              <input v-model.number="form.regressionRate" type="range" min="0" max="1" step="0.05" class="cyber-slider" />
              <div class="slider-fill" :style="{ width: form.regressionRate * 100 + '%' }"></div>
            </div>
          </div>
        </div>
      </section>

      <!-- ============ 3. 语音设置 ============ -->
      <section class="settings-section">
        <div class="section-title-row">
          <span class="section-icon">◈</span>
          <span class="section-label">语音 // TTS</span>
        </div>
        <div class="section-body">
          <div class="param-card row-card">
            <div class="param-header" style="margin-bottom: 0">
              <span class="param-label">语音播报</span>
              <p class="param-desc" style="margin: 0">AI 回复时将文字转为语音</p>
            </div>
            <div
              class="cyber-switch"
              :class="{ on: form.ttsEnabled }"
              @click="form.ttsEnabled = !form.ttsEnabled"
            >
              <div class="switch-handle"></div>
            </div>
          </div>
          <div class="param-card" :class="{ disabled: !form.ttsEnabled }">
            <div class="param-header">
              <span class="param-label">音量</span>
              <span class="param-value">{{ Math.round(form.ttsVolume * 100) }}%</span>
            </div>
            <div class="slider-wrap">
              <input v-model.number="form.ttsVolume" type="range" min="0" max="1" step="0.05" class="cyber-slider" :disabled="!form.ttsEnabled" />
              <div class="slider-fill" :style="{ width: form.ttsVolume * 100 + '%' }"></div>
            </div>
          </div>
          <div class="param-card" :class="{ disabled: !form.ttsEnabled }">
            <div class="param-header">
              <span class="param-label">语速</span>
              <span class="param-value">{{ form.ttsSpeed.toFixed(1) }}x</span>
            </div>
            <div class="slider-wrap">
              <input v-model.number="form.ttsSpeed" type="range" min="0.5" max="2.0" step="0.1" class="cyber-slider" :disabled="!form.ttsEnabled" />
              <div class="slider-fill" :style="{ width: ttsSpeedPercent + '%' }"></div>
            </div>
          </div>
        </div>
      </section>

      <!-- ============ 4. 主动推送 ============ -->
      <section class="settings-section">
        <div class="section-title-row">
          <span class="section-icon">◈</span>
          <span class="section-label">主动推送 // PROACTIVE</span>
        </div>
        <div class="section-body">
          <div class="param-card row-card">
            <div class="param-header" style="margin-bottom: 0">
              <span class="param-label">主动消息</span>
              <p class="param-desc" style="margin: 0">AI 会在空闲时主动发起对话</p>
            </div>
            <div
              class="cyber-switch"
              :class="{ on: form.proactiveEnabled }"
              @click="form.proactiveEnabled = !form.proactiveEnabled"
            >
              <div class="switch-handle"></div>
            </div>
          </div>
          <div class="param-card" :class="{ disabled: !form.proactiveEnabled }">
            <div class="param-header">
              <span class="param-label">推送间隔</span>
              <span class="param-value">{{ form.proactiveIntervalMin }} 分钟</span>
            </div>
            <div class="slider-wrap">
              <input
                v-model.number="form.proactiveIntervalMin"
                type="range" min="5" max="120" step="5"
                class="cyber-slider"
                :disabled="!form.proactiveEnabled"
              />
              <div class="slider-fill" :style="{ width: proactiveIntervalPercent + '%' }"></div>
            </div>
          </div>
        </div>
      </section>

      <!-- ============ 5. 主题背景 ============ -->
      <section class="settings-section">
        <div class="section-title-row">
          <span class="section-icon">◈</span>
          <span class="section-label">界面主题 // THEME</span>
        </div>
        <div class="section-body">
          <div class="theme-grid">
            <div
              v-for="theme in themes"
              :key="theme.id"
              class="theme-card"
              :class="{ active: form.themeId === theme.id }"
              @click="form.themeId = theme.id"
            >
              <div class="theme-preview">
                <img :src="theme.src" :alt="theme.name" loading="lazy" />
              </div>
              <div class="theme-name">{{ theme.name }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 保存按钮 -->
      <div class="settings-footer">
        <button class="btn-reset" @click="handleReset" :disabled="saving">重置</button>
        <button class="save-btn" :class="{ saving: saving }" :disabled="saving" @click="handleSave">
          {{ saving ? '写入中...' : '写入核心配置' }}
        </button>
        <span v-if="saved" class="save-feedback">✓ 已保存</span>
        <span v-if="saveError" class="save-feedback error">{{ saveError }}</span>
      </div>
    </template>
  </div>
</template>

<script setup>
defineOptions({ name: 'SettingsPanel' })
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import SettingsPersonality from './SettingsPersonality.vue'

const settingsStore = useSettingsStore()

const themes = [
  { id: 'default', name: '默认', src: new URL('@/assets/bk1.webp', import.meta.url).href },
  { id: 'bk2', name: '背景2', src: new URL('@/assets/bk2.webp', import.meta.url).href },
  { id: 'bk3', name: '背景3', src: new URL('@/assets/bk3.webp', import.meta.url).href },
  { id: 'bk4', name: '背景4', src: new URL('@/assets/bk4.webp', import.meta.url).href },
  { id: 'bk5', name: '背景5', src: new URL('@/assets/bk5.webp', import.meta.url).href },
]

const form = ref({
  // Personality (read-only display)
  personalityPreset: 'gentleAndShy',
  openness: 0.0,
  conscientiousness: 0.0,
  extraversion: 0.0,
  agreeableness: 0.0,
  neuroticism: 0.0,
  // Emotion engine
  sensitivity: 0.5,
  decayRate: 0.1,
  regressionRate: 0.05,
  // TTS
  ttsEnabled: true,
  ttsVolume: 1.0,
  ttsSpeed: 1.0,
  // Proactive
  proactiveEnabled: true,
  proactiveIntervalMin: 30,
  // Theme
  themeId: 'default',
})

const loading = computed(() => settingsStore.loading)
const loadError = ref('')
const saving = ref(false)
const saveError = ref('')
const saved = ref(false)
let _isAlive = true
onBeforeUnmount(() => { _isAlive = false })

// ── 预设列表 ──
const presets = computed(() => settingsStore.presets)

// 计算属性：语速百分比
const ttsSpeedPercent = computed(() => {
   return ((form.value.ttsSpeed - 0.5) / 1.5) * 100
})

// 计算属性：推送间隔百分比
const proactiveIntervalPercent = computed(() => {
   return (form.value.proactiveIntervalMin / 120) * 100
})

// ── 从后端加载设置 ──
// 始终响应 store 变更（支持外部更新、跨标签同步），用深比较避免用户编辑被覆盖
let _lastAppliedJson = ''
watch(() => settingsStore.settings, (newSettings) => {
  if (!newSettings) return
  const incoming = JSON.stringify(newSettings)
  // 仅当 store 数据真正变化时才覆盖表单（避免覆盖用户正在编辑的内容）
  if (incoming !== _lastAppliedJson) {
    _lastAppliedJson = incoming
    form.value = { ...form.value, ...newSettings }
  }
}, { immediate: true })

const initLoad = async () => {
  loadError.value = ''
  try {
    await Promise.all([
      settingsStore.fetchSettings(),
      settingsStore.fetchPresets(),
    ])
    if (!_isAlive) return
  } catch (e) {
    if (!_isAlive) return
    loadError.value = e?.message || '加载设置失败'
  }
}

const _savedTimer = ref(null)

const handleSave = async () => {
  saving.value = true
  saveError.value = ''
  saved.value = false
  try {
    const success = await settingsStore.saveSettings({ ...form.value })
    if (!_isAlive) return
    if (success) {
      saved.value = true
      _lastAppliedJson = JSON.stringify({ ...form.value })
      if (_savedTimer.value) clearTimeout(_savedTimer.value)
      _savedTimer.value = setTimeout(() => { saved.value = false; _savedTimer.value = null }, 3000)
    } else {
      saveError.value = settingsStore.error || '保存失败'
    }
  } catch (e) {
    if (!_isAlive) return
    saveError.value = e?.message || '保存失败'
  } finally {
    if (_isAlive) saving.value = false
  }
}

onBeforeUnmount(() => {
  if (_savedTimer.value) clearTimeout(_savedTimer.value)
})

const handleReset = () => {
  saveError.value = ''
  saved.value = false
  if (settingsStore.settings) form.value = { ...form.value, ...settingsStore.settings }
}

const applyPreset = (updates) => {
  form.value = { ...form.value, ...updates }
}

onMounted(() => { initLoad() })
</script>

<style scoped>
.settings-container {
  padding: 40px;
  color: white;
  height: 100%;
  overflow-y: auto;
  animation: container-fade 0.8s ease-out forwards;
}
.settings-container::-webkit-scrollbar { width: 0; }

.settings-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 40px;
  opacity: 0.8;
}
.header-icon { font-size: 22px; }
.header-title { font-size: 18px; letter-spacing: 2px; font-weight: 300; }

.settings-section { margin-bottom: 40px; }
.section-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.section-icon { font-size: 12px; opacity: 0.4; }
.section-label { font-size: 13px; letter-spacing: 2px; opacity: 0.6; font-weight: 300; }
.section-body { display: flex; flex-direction: column; gap: 16px; }

.section-loading,
.section-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: rgba(255, 255, 255, 0.5);
  gap: 12px;
}
.loading-spinner {
  width: 24px; height: 24px;
  border: 2px solid rgba(255,255,255,0.1);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
.loading-text { font-size: 12px; opacity: 0.5; margin-top: 8px; }
.retry-btn {
  padding: 6px 20px;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.7);
  border-radius: 20px;
  cursor: pointer;
  font-size: 12px;
  transition: background-color 0.3s ease, color 0.3s ease;
}
.retry-btn:hover { background: rgba(255,255,255,0.15); color: #fff; }

.param-card {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  padding: 20px;
  transition: background-color 0.3s ease, opacity 0.3s ease;
}
.param-card:hover { background: rgba(255,255,255,0.06); }
.param-card.disabled { opacity: 0.35; pointer-events: none; }
.param-card.row-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.param-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.row-card .param-header { margin-bottom: 0; }
.param-label { font-size: 15px; font-weight: 400; }
.param-value {
  font-family: 'Courier New', monospace;
  color: #5eead4;
  font-size: 13px;
}
.param-desc {
  font-size: 12px;
  opacity: 0.45;
  margin-top: 0;
  margin-bottom: 12px;
  line-height: 1.5;
}

/* ── 滑块 ── */
.slider-wrap {
  position: relative;
  width: 100%;
  height: 2px;
}
.cyber-slider {
  position: absolute;
  width: 100%;
  height: 100%;
  appearance: none;
  background: transparent;
  outline: none;
  z-index: 2;
  margin: 0;
  cursor: pointer;
}
.cyber-slider::-webkit-slider-thumb {
  appearance: none;
  width: 4px;
  height: 4px;
  background: white;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 0 8px rgba(255,255,255,0.6);
  transition: transform 0.2s ease;
}
.cyber-slider::-webkit-slider-thumb:hover { transform: scale(2); }
.cyber-slider::-moz-range-thumb {
  width: 4px; height: 4px;
  background: white;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 0 8px rgba(255,255,255,0.6);
}
.cyber-slider:disabled { cursor: not-allowed; }
.cyber-slider:disabled::-webkit-slider-thumb {
  background: rgba(255,255,255,0.3);
  box-shadow: none;
  cursor: not-allowed;
}
.slider-fill {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: linear-gradient(90deg, rgba(255,255,255,0.05), rgba(255,255,255,0.7));
  border-radius: 1px;
  pointer-events: none;
  transition: width 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  box-shadow: 0 0 6px rgba(255,255,255,0.15);
}

/* ── 开关 ── */
.cyber-switch {
  width: 42px;
  height: 22px;
  flex-shrink: 0;
  background: rgba(255,255,255,0.1);
  border-radius: 20px;
  position: relative;
  cursor: pointer;
  transition: background 0.3s ease;
}
.switch-handle {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  background: white;
  border-radius: 50%;
  transition: transform 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28), background 0.3s ease;
  box-shadow: 0 1px 4px rgba(0,0,0,0.2);
}
.cyber-switch.on { background: rgba(94, 234, 212, 0.35); }
.cyber-switch.on .switch-handle {
  transform: translateX(20px);
  background: #5eead4;
}

/* ── 主题网格 ── */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 16px;
}
.theme-card {
  cursor: pointer;
  text-align: center;
}
.theme-preview {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  overflow: hidden;
  border: 2px solid transparent;
  transition: border-color 0.3s, box-shadow 0.3s;
}
.theme-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.theme-card.active .theme-preview {
  border-color: white;
  box-shadow: 0 0 15px rgba(255,255,255,0.15);
}
.theme-name {
  font-size: 12px;
  margin-top: 8px;
  opacity: 0.6;
  transition: opacity 0.3s;
}
.theme-card.active .theme-name { opacity: 1; }

/* ── 底部 ── */
.settings-footer {
  margin-top: 50px;
  padding-bottom: 100px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.btn-reset, .save-btn {
  padding: 12px 30px;
  border-radius: 4px;
  letter-spacing: 2px;
  cursor: pointer;
  transition: background-color 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), border-color 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), color 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  font-size: 13px;
  border: none;
}
.btn-reset {
  background: transparent;
  border: 1px solid rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.6);
}
.btn-reset:hover { background: rgba(255,255,255,0.08); color: rgba(255,255,255,0.8); }
.save-btn {
  background: transparent;
  border: 1px solid rgba(255,255,255,0.3);
  color: white;
}
.save-btn:hover { background: white; color: black; }
.save-btn.saving { opacity: 0.5; pointer-events: none; }
.save-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.save-feedback { font-size: 13px; color: #5eead4; animation: fade-in 0.3s ease; }
.save-feedback.error { color: #fb7185; }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes container-fade { from { opacity: 0; } to { opacity: 1; } }
@keyframes fade-in { from { opacity: 0; transform: translateX(-10px); } to { opacity: 1; transform: translateX(0); } }
</style>
