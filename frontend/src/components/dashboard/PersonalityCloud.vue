<template>
  <div class="personality-settings-container">
    <div class="settings-header">
      <span class="header-icon">✦</span>
      <h3 class="header-title">维度调整 // DIMENSION TUNING</h3>
    </div>

    <div class="settings-grid">
      <section class="parameter-card">
        <div class="card-glow"></div>
        <div class="card-content">
          <div class="param-info">
            <span class="param-label">感知灵敏度</span>
            <span class="param-value">{{ settings.sensitivity }}%</span>
          </div>
          <div class="slider-wrapper">
            <input
                type="range"
                v-model="settings.sensitivity"
                class="cyber-slider"
            >
            <div class="slider-track-glow" :style="{ width: settings.sensitivity + '%' }"></div>
          </div>
          <p class="param-desc">决定 AI 对用户情绪波动的捕捉精度。</p>
        </div>
      </section>

      <section class="parameter-card">
        <div class="card-content">
          <div class="param-info">
            <span class="param-label">记忆处理模式</span>
          </div>
          <div class="toggle-group">
            <div
                v-for="mode in ['即时', '深层', '永恒']"
                :key="mode"
                class="toggle-item"
                :class="{ active: settings.memoryMode === mode }"
                @click="settings.memoryMode = mode"
            >
              {{ mode }}
            </div>
          </div>
          <p class="param-desc">调整对话历史在长期性格塑造中的影响力。</p>
        </div>
      </section>

      <section class="parameter-card">
        <div class="card-content">
          <div class="param-info">
            <span class="param-label">环境光同步</span>
            <div class="cyber-switch"
                 :class="{ on: settings.lightSync }"
                 @click="settings.lightSync = !settings.lightSync">
              <div class="switch-handle"></div>
            </div>
          </div>
          <p class="param-desc">允许 AI 根据现实世界的昼夜更替调整其内在色调。</p>
        </div>
      </section>
    </div>

    <div class="settings-footer">
      <button class="save-btn" @click="handleSave">写入灵魂核心</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { STORAGE_KEYS } from '@/config/storage'

const STORAGE_KEY = STORAGE_KEYS.PERSONALITY_SETTINGS

const settings = ref({
  sensitivity: 75,
  memoryMode: '深层',
  lightSync: true
})

onMounted(() => {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) {
    try {
      settings.value = { ...settings.value, ...JSON.parse(saved) }
    } catch (e) {
      // ignore
    }
  }
})

const handleSave = () => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings.value))
  // 参数已同步
}
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
  transition: all 0.4s ease;
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
  transform: translateX(5px);
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
  cursor: pointer; transition: all 0.3s;
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
  border-radius: 50%; transition: 0.3s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}
.cyber-switch.on { background: rgba(94, 234, 212, 0.4); }
.cyber-switch.on .switch-handle { left: 22px; background: #5eead4; }
.settings-footer { margin-top: 50px; padding-bottom: 100px; }
.save-btn {
  background: transparent; border: 1px solid rgba(255,255,255,0.3);
  color: white; padding: 12px 30px; border-radius: 4px;
  letter-spacing: 2px; cursor: pointer; transition: 0.3s;
}
.save-btn:hover { background: white; color: black; }
</style>