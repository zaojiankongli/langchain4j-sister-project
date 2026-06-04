<template>
  <div class="settings-section">
    <div class="section-title-row">
      <span class="section-icon">◈</span>
      <span class="section-label">人格设定 // PERSONALITY</span>
    </div>
    <div class="section-body">
      <!-- 预设选择卡片 -->
      <div class="preset-grid">
        <div
          v-for="p in presets"
          :key="p.id"
          class="preset-card"
          :class="{ active: form.personalityPreset === p.id }"
          @click="selectPreset(p)"
        >
          <div class="preset-name">{{ p.name }}</div>
          <div class="preset-ocean">
            <div
              v-for="trait in oceanKeys"
              :key="trait.key"
              class="preset-bar"
              :style="{ width: calcPct(p[trait.key]) + '%', background: OCEAN_COLORS[trait.key] }"
            ></div>
          </div>
        </div>
      </div>

      <!-- OCEAN 详细展示 -->
      <div class="param-card">
        <div class="param-header">
          <span class="param-label">当前人格特质</span>
          <span class="preset-tag">{{ presetName }}</span>
        </div>
        <div class="ocean-chart">
          <div v-for="trait in oceanTraitsComputed" :key="trait.key" class="ocean-row">
            <span class="ocean-label">{{ trait.label }}</span>
            <div class="ocean-bar-bg">
              <div class="ocean-bar-fill" :style="{ width: trait.percent + '%', background: trait.color }"></div>
            </div>
            <span class="ocean-value">{{ trait.display }}</span>
          </div>
        </div>
      </div>

      <!-- 自定义 OCEAN 微调滑块 -->
      <div v-if="form.personalityPreset === 'custom'" class="param-card">
        <div class="param-header">
          <span class="param-label">自定义微调</span>
        </div>
        <div class="ocean-sliders">
          <div v-for="trait in oceanKeys" :key="trait.key" class="ocean-slider-row">
            <span class="ocean-label">{{ trait.label }}</span>
            <input v-model.number="form[trait.key]" type="range" min="-1" max="1" step="0.05" class="cyber-slider ocean-slider" />
            <span class="ocean-value">{{ form[trait.key].toFixed(2) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  form: { type: Object, required: true },
  presets: { type: Array, default: () => [] },
})

const OCEAN_COLORS = {
  openness: '#5eead4',
  conscientiousness: '#a78bfa',
  extraversion: '#fbbf24',
  agreeableness: '#f472b6',
  neuroticism: '#fb7185',
}

const oceanKeys = [
  { key: 'openness', label: '开放性' },
  { key: 'conscientiousness', label: '尽责性' },
  { key: 'extraversion', label: '外向性' },
  { key: 'agreeableness', label: '宜人性' },
  { key: 'neuroticism', label: '神经质' },
]

const presetNameMap = {
  gentleAndShy: '温柔害羞',
  tsundere: '傲娇',
  lively: '活泼',
  coolAndDistant: '高冷',
  intellectual: '知性',
  custom: '自定义',
}

const presetName = computed(() => presetNameMap[props.form.personalityPreset] || props.form.personalityPreset)

let _prevOceanSignature = ''
let _cachedOceanTraits = []
const oceanTraitsComputed = computed(() => {
  // 基于实际值计算签名，值未变时返回稳定引用
  const sig = oceanKeys.map(t => `${t.key}:${props.form[t.key] ?? 0}`).join('|')
  if (sig === _prevOceanSignature) return _cachedOceanTraits
  _prevOceanSignature = sig
  _cachedOceanTraits = oceanKeys.map(trait => {
    const val = props.form[trait.key] ?? 0
    const percent = Math.round(((val + 1) / 2) * 100)
    const sign = val >= 0 ? '+' : ''
    return {
      key: trait.key,
      label: trait.label,
      color: OCEAN_COLORS[trait.key],
      percent,
      display: `${sign}${val.toFixed(2)}`,
    }
  })
  return _cachedOceanTraits
})

function selectPreset(preset) {
  props.form.personalityPreset = preset.id
  if (preset.id !== 'custom') {
    for (const t of oceanKeys) {
      props.form[t.key] = preset[t.key]
    }
  }
}

function calcPct(value) {
  return ((value + 1) / 2) * 100
}
</script>

<style scoped>
.settings-section { margin-bottom: 40px; }
.section-title-row { display: flex; align-items: center; gap: 10px; margin-bottom: 20px; }
.section-icon { font-size: 12px; opacity: 0.4; }
.section-label { font-size: 13px; letter-spacing: 2px; opacity: 0.6; font-weight: 300; }
.section-body { display: flex; flex-direction: column; gap: 16px; }
.preset-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 12px; }
.preset-card {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px; padding: 14px 12px; cursor: pointer; transition: all 0.3s ease; text-align: center;
}
.preset-card:hover { background: rgba(255,255,255,0.08); border-color: rgba(255,255,255,0.15); }
.preset-card.active { border-color: #5eead4; background: rgba(94, 234, 212, 0.1); box-shadow: 0 0 12px rgba(94, 234, 212, 0.12); }
.preset-name { font-size: 13px; margin-bottom: 10px; opacity: 0.7; letter-spacing: 1px; transition: opacity 0.3s; }
.preset-card.active .preset-name { opacity: 1; color: #5eead4; }
.preset-ocean { display: flex; flex-direction: column; gap: 3px; align-items: stretch; }
.preset-bar { height: 3px; border-radius: 2px; opacity: 0.5; transition: opacity 0.3s; }
.preset-card.active .preset-bar { opacity: 0.85; }
.preset-tag { font-size: 12px; padding: 3px 12px; border-radius: 20px; background: rgba(94, 234, 212, 0.15); border: 1px solid rgba(94, 234, 212, 0.3); color: #5eead4; letter-spacing: 1px; }
.param-card { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.06); border-radius: 10px; padding: 20px; transition: all 0.3s ease; }
.param-card:hover { background: rgba(255,255,255,0.06); }
.param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.param-label { font-size: 15px; font-weight: 400; }
.ocean-chart { margin-top: 16px; display: flex; flex-direction: column; gap: 10px; }
.ocean-row { display: flex; align-items: center; gap: 12px; }
.ocean-label { width: 56px; font-size: 12px; opacity: 0.6; flex-shrink: 0; }
.ocean-bar-bg { flex: 1; height: 6px; background: rgba(255,255,255,0.06); border-radius: 3px; overflow: hidden; }
.ocean-bar-fill { height: 100%; border-radius: 3px; transition: width 0.5s cubic-bezier(0.22, 1, 0.36, 1); }
.ocean-value { width: 56px; font-size: 11px; font-family: 'Courier New', monospace; color: rgba(255,255,255,0.5); text-align: right; flex-shrink: 0; }
.ocean-sliders { margin-top: 12px; display: flex; flex-direction: column; gap: 16px; }
.ocean-slider-row { display: flex; align-items: center; gap: 12px; }
.ocean-slider-row .ocean-label { width: 56px; font-size: 12px; opacity: 0.6; flex-shrink: 0; }
.ocean-slider-row .ocean-value { width: 48px; font-size: 11px; font-family: 'Courier New', monospace; color: #5eead4; text-align: right; flex-shrink: 0; }
.ocean-slider { position: relative !important; height: 4px !important; appearance: none !important; background: rgba(255,255,255,0.1) !important; outline: none !important; cursor: pointer !important; border-radius: 2px !important; }
.ocean-slider::-webkit-slider-thumb { appearance: none; width: 12px; height: 12px; background: white; border-radius: 50%; cursor: pointer; box-shadow: 0 0 8px rgba(255,255,255,0.4); transition: transform 0.2s ease; }
.ocean-slider::-webkit-slider-thumb:hover { transform: scale(1.3); }
</style>
