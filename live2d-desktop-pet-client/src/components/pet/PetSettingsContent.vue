<script setup lang="ts">
import { proxyRefs } from 'vue'
import type { PersonalityPreset, UserSettings } from '../../types/settings'
import { CATEGORY_LABELS } from '../../composables/usePetNotifications'
import type { PetSettingsForm } from '../../composables/usePetSettingsForm'
import PetSettingsAudioSection from './settings/PetSettingsAudioSection.vue'
import PetSettingsDisplaySection from './settings/PetSettingsDisplaySection.vue'

const props = defineProps<{
  form: PetSettingsForm
  presets?: PersonalityPreset[]
  isSaving: boolean
  settingsError: string
}>()

const form = proxyRefs(props.form)


const emit = defineEmits<{
  save: [settings: Partial<UserSettings>]
}>()
</script>

<template>
  <div class="settings-content-shell">
    <h2 class="settings-title">宠物设置</h2>
    <p class="settings-subtitle">本地偏好即时生效，服务器设置可用 Ctrl+S 保存</p>

    <div class="settings-content">
      <section class="settings-section">
        <h3 class="settings-section-title">性格画像</h3>

        <div v-if="presets && presets.length > 0" class="presets-row">
          <button
            v-for="preset in presets"
            :key="preset.id"
            type="button"
            class="preset-pill"
            :title="preset.description"
            @click="form.applyPreset(preset)"
          >
            {{ preset.name }}
          </button>
        </div>

        <div v-for="dim in form.oceanDims" :key="dim.key" class="slider-row">
          <label class="slider-label">
            <span class="slider-name">{{ dim.label }}</span>
            <span class="slider-value">{{ form.personality[dim.key].toFixed(2) }}</span>
          </label>
          <input
            v-model.number="form.personality[dim.key]"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="settings-slider"
          />
        </div>
      </section>

      <section class="settings-section">
        <h3 class="settings-section-title">行为阈值</h3>

        <div class="slider-row">
          <label class="slider-label">
            <span class="slider-name">敏感度</span>
            <span class="slider-value">{{ form.sensitivity.toFixed(2) }}</span>
          </label>
          <input
            v-model.number="form.sensitivity"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="settings-slider"
          />
        </div>

        <div class="slider-row">
          <label class="slider-label">
            <span class="slider-name">衰减率</span>
            <span class="slider-value">{{ form.decayRate.toFixed(2) }}</span>
          </label>
          <input
            v-model.number="form.decayRate"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="settings-slider"
          />
        </div>

        <div class="slider-row">
          <label class="slider-label">
            <span class="slider-name">回归率</span>
            <span class="slider-value">{{ form.regressionRate.toFixed(2) }}</span>
          </label>
          <input
            v-model.number="form.regressionRate"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="settings-slider"
          />
        </div>

        <div class="select-row">
          <label class="slider-name" for="theme-select">主题</label>
          <select id="theme-select" v-model.number="form.themeId" class="settings-select">
            <option :value="1">默认</option>
            <option :value="2">暖色</option>
            <option :value="3">冷色</option>
            <option :value="4">深色</option>
            <option :value="5">柔和</option>
          </select>
        </div>
      </section>

      <section class="settings-section">
        <h3 class="settings-section-title">语音播报</h3>

        <div class="toggle-row">
          <span class="slider-name">启用 TTS</span>
          <label class="toggle">
            <input v-model="form.ttsEnabled" type="checkbox" class="toggle-input" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>

        <div class="slider-row">
          <label class="slider-label">
            <span class="slider-name">音量</span>
            <span class="slider-value">{{ form.ttsVolume.toFixed(2) }}</span>
          </label>
          <input
            v-model.number="form.ttsVolume"
            type="range"
            min="0"
            max="1"
            step="0.01"
            class="settings-slider"
          />
        </div>

        <div class="slider-row">
          <label class="slider-label">
            <span class="slider-name">语速</span>
            <span class="slider-value">{{ form.ttsSpeed.toFixed(1) }}</span>
          </label>
          <input
            v-model.number="form.ttsSpeed"
            type="range"
            min="0.5"
            max="2.0"
            step="0.1"
            class="settings-slider"
          />
        </div>
      </section>

      <section class="settings-section">
        <h3 class="settings-section-title">主动陪伴</h3>

        <div class="toggle-row">
          <span class="slider-name">启用主动陪伴</span>
          <label class="toggle">
            <input v-model="form.proactiveEnabled" type="checkbox" class="toggle-input" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>

        <div v-if="form.proactiveEnabled" class="slider-row">
          <label class="slider-label">
            <span class="slider-name">对话间隔 (秒)</span>
            <span class="slider-value">{{ form.formatProactiveInterval(form.proactiveInterval) }}</span>
          </label>
          <input
            v-model.number="form.proactiveInterval"
            type="range"
            min="30"
            max="600"
            step="30"
            class="settings-slider"
          />
        </div>
      </section>

      <section class="settings-section">
        <h3 class="settings-section-title">音乐播放器</h3>

        <div class="path-row">
          <span class="slider-name">本地曲库</span>
          <span class="path-value">{{ form.musicDirectoryLabel }}</span>
        </div>
        <div class="settings-actions-row">
          <button class="settings-btn settings-btn--secondary settings-btn--inline" type="button" @click="form.chooseMusicDirectory">
            选择音乐目录
          </button>
          <button class="settings-btn settings-btn--secondary settings-btn--inline" type="button" @click="form.clearMusicDirectory">
            清空目录
          </button>
        </div>

        <div class="select-row">
          <label class="slider-name" for="music-background-mode">背景样式</label>
          <select
            id="music-background-mode"
            :value="form.musicBackground.mode"
            class="settings-select"
            @change="form.onMusicBackgroundModeChange"
          >
            <option v-for="mode in form.musicBackgroundModes" :key="mode.id" :value="mode.id">
              {{ mode.label }}
            </option>
          </select>
        </div>

        <div class="path-row">
          <span class="slider-name">自定义背景</span>
          <span class="path-value">{{ form.musicBackground.customPath || '未选择' }}</span>
        </div>
        <div class="settings-actions-row">
          <button class="settings-btn settings-btn--secondary settings-btn--inline" type="button" @click="form.chooseMusicBackground">
            选择背景图
          </button>
          <button class="settings-btn settings-btn--secondary settings-btn--inline" type="button" @click="form.clearMusicBackground">
            恢复默认背景
          </button>
        </div>

        <div class="slider-row">
          <label class="slider-label">
            <span class="slider-name">背景遮罩</span>
            <span class="slider-value">{{ form.musicBackground.overlayOpacity.toFixed(2) }}</span>
          </label>
          <input
            :value="form.musicBackground.overlayOpacity"
            type="range"
            min="0.2"
            max="0.8"
            step="0.02"
            class="settings-slider"
            @input="form.onMusicOverlayOpacityInput"
          />
        </div>
      </section>

      <section class="settings-section">
        <h3 class="settings-section-title">通知提醒</h3>

        <div class="toggle-row">
          <span class="slider-name">启用系统通知</span>
          <label class="toggle">
            <input :checked="form.notifPrefs.enabled" type="checkbox" class="toggle-input" @change="form.onNotifMasterToggle" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>

        <div v-if="form.notifPrefs.enabled" class="notif-categories">
          <div v-for="cat in form.notifCategories" :key="cat" class="toggle-row toggle-row--sub">
            <span class="slider-name">{{ CATEGORY_LABELS[cat] }}</span>
            <label class="toggle">
              <input
                :checked="form.notifPrefs.categories[cat]"
                type="checkbox"
                class="toggle-input"
                @change="form.onNotifCategoryToggle(cat)"
              />
              <span class="toggle-track"><span class="toggle-thumb" /></span>
            </label>
          </div>
        </div>

        <button class="settings-btn settings-btn--secondary notif-permission-btn" type="button" @click="form.onGrantPermission">
          启用系统通知
        </button>
      </section>

      <section class="settings-section">
        <h3 class="settings-section-title">离线陪伴</h3>

        <div class="toggle-row">
          <span class="slider-name">显示离线陪伴</span>
          <label class="toggle">
            <input :checked="form.localCompanionSettings.enabled" type="checkbox" class="toggle-input" @change="form.onLocalCompanionEnabledToggle" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>

        <div class="toggle-row">
          <span class="slider-name">自动轮换离线气泡</span>
          <label class="toggle">
            <input
              :checked="form.localCompanionSettings.autoRotateMessages"
              :disabled="!form.localCompanionSettings.enabled"
              type="checkbox"
              class="toggle-input"
              @change="form.onLocalCompanionAutoRotateToggle"
            />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>

        <div v-if="form.localCompanionSettings.enabled && form.localCompanionSettings.autoRotateMessages" class="slider-row">
          <label class="slider-label">
            <span class="slider-name">轮换间隔</span>
            <span class="slider-value">{{ form.localCompanionSettings.messageRotationSeconds }}秒</span>
          </label>
          <input
            :value="form.localCompanionSettings.messageRotationSeconds"
            type="range"
            min="5"
            max="60"
            step="1"
            class="settings-slider"
            @input="form.onLocalCompanionRotationSecondsInput"
          />
        </div>

        <div class="toggle-row">
          <span class="slider-name">离线点击随机动作</span>
          <label class="toggle">
            <input
              :checked="form.localCompanionSettings.tapMotionsEnabled"
              :disabled="!form.localCompanionSettings.enabled"
              type="checkbox"
              class="toggle-input"
              @change="form.onLocalCompanionTapMotionsToggle"
            />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>
      </section>

      <PetSettingsAudioSection
        :audio-enabled="form.audioEnabled"
        :audio-volume="form.audioVolume"
        @toggle-audio-enabled="form.onAudioEnabledToggle"
        @update-audio-volume="form.onAudioVolumeInput"
      />

      <PetSettingsDisplaySection
        :scale="form.petDisplay.scale"
        :opacity="form.petDisplay.opacity"
        @update-scale="form.onPetDisplayScaleInput"
        @update-opacity="form.onPetDisplayOpacityInput"
      />

      <section class="settings-section">
        <h3 class="settings-section-title">窗口交互</h3>

        <div class="toggle-row">
          <span class="slider-name">点击穿透</span>
          <label class="toggle">
            <input :checked="form.windowBehavior.clickThrough" type="checkbox" class="toggle-input" @change="form.onWindowClickThroughToggle" />
            <span class="toggle-track"><span class="toggle-thumb" /></span>
          </label>
        </div>
      </section>
    </div>

    <div class="settings-footer">
      <p v-if="settingsError" class="settings-error" role="alert">
        {{ settingsError }}
      </p>
      <button class="settings-btn settings-btn--save" :disabled="isSaving" type="button" @click="emit('save', form.handleSave())">
        <template v-if="isSaving">
          <span class="settings-btn-spinner" aria-hidden="true" />
          保存中…
        </template>
        <template v-else>保存更改</template>
      </button>
    </div>
  </div>
</template>

<style scoped>
.settings-content-shell {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.settings-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding-right: var(--space-1);
}

.settings-content::-webkit-scrollbar {
  width: 4px;
}

.settings-content::-webkit-scrollbar-track {
  background: transparent;
}

.settings-content::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: var(--radius-pill);
}

.settings-title {
  margin: 0 0 var(--space-1);
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-heading);
  text-align: center;
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
}

.settings-subtitle {
  margin: 0 0 var(--space-5);
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  text-align: center;
}

:deep(.settings-section) {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.025);
}

:deep(.settings-section-title) {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-subtitle);
  font-weight: 500;
  color: var(--color-accent);
  letter-spacing: var(--letter-spacing-tight);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

:deep(.slider-row) {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

:deep(.slider-label),
:deep(.toggle-row),
:deep(.select-row) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(.slider-name) {
  font-size: var(--font-size-body);
  color: var(--color-text);
}

:deep(.slider-value) {
  font-size: var(--font-size-small);
  font-family: var(--font-mono);
  color: var(--color-text-muted);
  min-width: 3ch;
  text-align: right;
}

:deep(.settings-slider) {
  -webkit-appearance: none;
  appearance: none;
  width: 100%;
  height: 4px;
  background: var(--color-surface-subtle);
  border-radius: var(--radius-pill);
  outline: none;
  cursor: pointer;
  transition: background var(--duration-fast) ease;
}

:deep(.settings-slider::-webkit-slider-thumb) {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  background: var(--color-accent);
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid var(--color-action-text);
  transition: transform var(--duration-fast) ease;
}

:deep(.settings-slider::-webkit-slider-thumb:hover) {
  transform: scale(1.2);
}

:deep(.settings-slider::-moz-range-track) {
  height: 4px;
  background: var(--color-surface-subtle);
  border-radius: var(--radius-pill);
  border: none;
}

:deep(.settings-slider::-moz-range-thumb) {
  width: 14px;
  height: 14px;
  background: var(--color-accent);
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid var(--color-action-text);
}

:deep(.settings-slider:focus-visible),
:deep(.preset-pill:focus-visible),
:deep(.settings-select:focus-visible),
:deep(.toggle-input:focus-visible + .toggle-track) {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: 2px;
}

:deep(.presets-row),
:deep(.settings-actions-row) {
  display: flex;
  gap: var(--space-2);
}

:deep(.presets-row) {
  flex-wrap: wrap;
}

:deep(.preset-pill) {
  display: inline-flex;
  align-items: center;
  padding: var(--space-1) var(--space-3);
  font-size: var(--font-size-small);
  font-family: var(--font-body);
  font-weight: 500;
  color: var(--color-action-text);
  background: var(--color-accent);
  border: none;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: opacity var(--duration-fast) ease, transform var(--duration-fast) ease;
  user-select: none;
  white-space: nowrap;
}

:deep(.preset-pill:hover) {
  opacity: 0.85;
}

:deep(.preset-pill:active),
.settings-btn:active:not(:disabled) {
  transform: translateY(1px);
}

:deep(.path-row) {
  display: grid;
  gap: var(--space-1);
}

:deep(.path-value) {
  min-height: 1.5rem;
  padding: var(--space-1) var(--space-2);
  overflow: hidden;
  color: var(--color-text-muted);
  font-family: var(--font-mono);
  font-size: var(--font-size-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

:deep(.settings-select) {
  min-width: 8rem;
  padding: var(--space-1) var(--space-3);
  font-size: var(--font-size-body);
  font-family: var(--font-body);
  color: var(--color-text);
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  outline: none;
  transition: border-color var(--duration-fast) ease;
}

:deep(.settings-select:hover) {
  border-color: var(--color-accent-soft);
}

:deep(.toggle) {
  position: relative;
  display: inline-block;
  cursor: pointer;
}

:deep(.toggle-input) {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
  pointer-events: none;
}

:deep(.toggle-track) {
  display: block;
  width: 2.25rem;
  height: 1.25rem;
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  position: relative;
  transition: background var(--duration-fast) ease, border-color var(--duration-fast) ease;
}

:deep(.toggle-input:checked + .toggle-track) {
  background: var(--color-accent);
  border-color: var(--color-accent);
}

:deep(.toggle-thumb) {
  position: absolute;
  top: 2px;
  left: 2px;
  width: calc(1.25rem - 6px);
  height: calc(1.25rem - 6px);
  background: var(--color-text);
  border-radius: 50%;
  transition: transform var(--duration-fast) ease;
}

:deep(.toggle-input:checked + .toggle-track .toggle-thumb) {
  transform: translateX(calc(100% - 2px));
}

:deep(.notif-categories) {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding-left: var(--space-4);
}

:deep(.toggle-row--sub .slider-name) {
  color: var(--color-text-muted);
}

.settings-footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

.settings-error {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  font-size: var(--font-size-small);
  color: var(--color-danger);
  background: rgba(255, 143, 124, 0.1);
  border-radius: var(--radius-sm);
  text-align: center;
}

.settings-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-size-body);
  font-family: var(--font-body);
  font-weight: 500;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: opacity var(--duration-fast) ease, transform var(--duration-fast) ease;
  user-select: none;
}

.settings-btn--save,
:deep(.preset-pill) {
  color: var(--color-action-text);
  background: var(--color-accent);
}

.settings-btn--save:hover:not(:disabled) {
  opacity: 0.9;
}

:deep(.settings-btn--secondary) {
  color: var(--color-text);
  background: var(--color-surface-subtle);
  border: 1px solid var(--color-border);
}

:deep(.settings-btn--secondary:hover:not(:disabled)) {
  background: rgba(255, 255, 255, 0.06);
}

:deep(.settings-btn--inline) {
  width: auto;
  flex: 1;
}

:deep(.notif-permission-btn) {
  align-self: flex-start;
  width: auto;
}

.settings-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.settings-btn-spinner {
  display: inline-block;
  width: 1em;
  height: 1em;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: settings-spin 0.8s linear infinite;
}

@keyframes settings-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
