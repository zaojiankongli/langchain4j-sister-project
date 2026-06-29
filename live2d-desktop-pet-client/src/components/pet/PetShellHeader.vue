<script setup lang="ts">
import { ref } from 'vue'
import { getCurrentWindow, type Window } from '@tauri-apps/api/window'
import PetStatusStrip, { type StateBadge } from './PetStatusStrip.vue'

defineProps<{
  debugPanelOpen: boolean
  stateBadges: StateBadge[]
  reconnectHint?: string
  isAuthenticated: boolean
  connectionQuality?: 'good' | 'fair' | 'poor'
  isAudioPlaying?: boolean
  mailUnreadCount?: number
}>()

const emit = defineEmits<{
  toggleDebugPanel: []
  prewarmDebugPanel: []
  toggleChatHistory: []
  toggleMoodHistory: []
  toggleSettings: []
  toggleMemory: []
  toggleMailbox: []
  toggleProfile: []
  openAuth: []
  logout: []
}>()

// ── Tauri window integration (cached, graceful fallback) ──

const alwaysOnTop = ref(false)
const isTauri = ref(false)

// Cache the window module at script level
let _tauriWin: Window | null = null

async function getTauriWindow() {
  if (_tauriWin) return _tauriWin
  try {
    _tauriWin = getCurrentWindow()
    isTauri.value = true
    alwaysOnTop.value = await _tauriWin.isAlwaysOnTop()
    return _tauriWin
  } catch {
    isTauri.value = false
    return null
  }
}

// Initialize eagerly — result cached for subsequent calls
getTauriWindow()

async function toggleAlwaysOnTop() {
  const win = await getTauriWindow()
  if (!win) return
  try {
    const next = !alwaysOnTop.value
    await win.setAlwaysOnTop(next)
    alwaysOnTop.value = next
  } catch { /* silent */ }
}

async function minimizeToTray() {
  const win = await getTauriWindow()
  if (!win) return
  try {
    await win.minimize()
  } catch { /* silent */ }
}
</script>

<template>
  <header v-if="debugPanelOpen" class="shell-header" aria-labelledby="probe-title" data-tauri-drag-region>
    <div class="shell-title-block">
      <p class="eyebrow">Desktop pet shell</p>
      <h1 id="probe-title">Live2D companion</h1>
      <p class="summary">Keep the character front and center. Open the panel only when you need controls.</p>
      <div class="actions hero-actions" aria-label="Shell actions">
        <button class="action" type="button" @click="emit('toggleDebugPanel')">Hide debug panel</button>
      </div>
    </div>

    <PetStatusStrip v-if="stateBadges.length" :badges="stateBadges" :connection-quality="connectionQuality" :is-audio-playing="isAudioPlaying" />
  </header>

  <div v-else class="pet-first-bar" :class="{ 'pet-first-bar--solo': !stateBadges.length }" data-tauri-drag-region>
    <div class="pet-first-actions">
      <button
        class="action pet-first-toggle"
        type="button"
        @mouseenter="emit('prewarmDebugPanel')"
        @focus="emit('prewarmDebugPanel')"
        @click="emit('toggleDebugPanel')"
      >
        {{ reconnectHint || 'Controls' }}
      </button>
      <button
        v-if="isAuthenticated"
        class="action pet-first-icon-btn"
        type="button"
        title="个人资料"
        aria-label="个人资料"
        @click="emit('toggleProfile')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <circle cx="8" cy="5" r="3" stroke="currentColor" stroke-width="1.3"/>
          <path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
        </svg>
      </button>
      <button
        v-if="!isAuthenticated"
        class="action pet-first-icon-btn"
        type="button"
        title="登录"
        aria-label="登录"
        @click="emit('openAuth')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M8 8a3 3 0 100-6 3 3 0 000 6zm5 6v-1a4 4 0 00-4-4H7a4 4 0 00-4 4v1" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <button
        v-if="isAuthenticated"
        class="action pet-first-icon-btn"
        type="button"
        title="退出登录"
        aria-label="退出登录"
        @click="emit('logout')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M6 2H3a1 1 0 00-1 1v10a1 1 0 001 1h3m4-3l3-3-3-3m3 3H6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
      <button
        class="action pet-first-icon-btn"
        type="button"
        title="聊天记录 (Ctrl+H)"
        aria-label="聊天记录"
        @click="emit('toggleChatHistory')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M2 3a1 1 0 011-1h10a1 1 0 011 1v7a1 1 0 01-1 1H8l-3 2.5V11H3a1 1 0 01-1-1V3z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
        </svg>
      </button>
      <button
        class="action pet-first-icon-btn"
        type="button"
        title="心情记录 (Ctrl+M)"
        aria-label="心情记录"
        @click="emit('toggleMoodHistory')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.3"/>
          <path d="M5.5 9.5s1 1.5 2.5 1.5 2.5-1.5 2.5-1.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          <circle cx="6" cy="7" r="0.8" fill="currentColor"/>
          <circle cx="10" cy="7" r="0.8" fill="currentColor"/>
        </svg>
      </button>
      <button
        class="action pet-first-icon-btn"
        type="button"
        title="设置"
        aria-label="设置"
        @click="emit('toggleSettings')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M8 2l.6 1.2a4.8 4.8 0 001.4 1.4L11 5l-1 .4a4.8 4.8 0 00-1.4 1.4L8 8l-.6-1.2A4.8 4.8 0 006 5.4L5 5l1-.4A4.8 4.8 0 007.4 3.2L8 2z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
          <circle cx="8" cy="8" r="2" stroke="currentColor" stroke-width="1.3"/>
        </svg>
      </button>
      <button
        class="action pet-first-icon-btn"
        type="button"
        title="记忆"
        aria-label="记忆"
        @click="emit('toggleMemory')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M3 2.5h10a.5.5 0 01.5.5v10a.5.5 0 01-.5.5H3a.5.5 0 01-.5-.5V3a.5.5 0 01.5-.5z" stroke="currentColor" stroke-width="1.3"/>
          <path d="M5.5 5h5M5.5 8h5M5.5 11h3" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
        </svg>
      </button>
      <button
        class="action pet-first-icon-btn mailbox-btn"
        type="button"
        title="信箱 (Ctrl+I)"
        aria-label="信箱"
        @click="emit('toggleMailbox')"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="1.5" y="3.5" width="13" height="9" rx="1.5" stroke="currentColor" stroke-width="1.3"/>
          <path d="M1.5 5l6.5 4 6.5-4" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/>
        </svg>
        <span v-if="mailUnreadCount && mailUnreadCount > 0" class="mailbox-badge">{{ mailUnreadCount > 9 ? '9+' : mailUnreadCount }}</span>
      </button>

      <!-- Desktop: always-on-top toggle -->
      <button
        class="action pet-first-icon-btn"
        type="button"
        :title="alwaysOnTop ? 'Disable always-on-top' : 'Enable always-on-top'"
        :aria-label="alwaysOnTop ? 'Disable always-on-top' : 'Enable always-on-top'"
        :class="{ 'pet-first-icon-btn--active': alwaysOnTop }"
        @click="toggleAlwaysOnTop"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2" y="6" width="12" height="8" rx="1" stroke="currentColor" stroke-width="1.3" fill="none"/>
          <path d="M5 6V4a3 3 0 016 0v2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          <circle cx="8" cy="10" r="1" fill="currentColor"/>
        </svg>
      </button>

      <!-- Desktop: minimize to tray -->
      <button
        class="action pet-first-icon-btn"
        type="button"
        title="Minimize to tray"
        aria-label="Minimize to tray"
        @click="minimizeToTray"
      >
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <rect x="2" y="2" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.3" fill="none"/>
          <path d="M5 11h6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          <path d="M8 8V5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>
          <path d="M6.5 6.5L8 5l1.5 1.5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </div>
    <PetStatusStrip
      v-if="stateBadges.length"
      :badges="stateBadges"
      :connection-quality="connectionQuality"
      :is-audio-playing="isAudioPlaying"
      compact
    />
  </div>
</template>

<style scoped>
.shell-header {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(var(--size-shell-state-min), 1.1fr);
  gap: var(--space-6);
  max-width: var(--size-page-max);
  margin: 0 auto var(--space-6);
  align-items: end;
}

.shell-title-block {
  display: grid;
  gap: var(--space-4);
}

.pet-first-bar {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  align-items: center;
  max-width: 46rem;
  margin: 0 auto var(--space-4);
}

.pet-first-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.pet-first-bar--solo {
  justify-content: flex-start;
  max-width: 46rem;
}

.hero-actions,
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.action {
  border: var(--border-width) solid var(--color-accent);
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-4);
  color: var(--color-action-text);
  background: var(--color-accent);
  font: inherit;
  cursor: pointer;
  transition:
    transform var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}

.action:hover {
  transform: translateY(var(--motion-lift));
  box-shadow: var(--shadow-action);
}

.action:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

.eyebrow {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-caption);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
  color: var(--color-accent);
}

h1,
.summary {
  margin: 0;
}

h1 {
  max-width: var(--measure-heading-debug);
  font-family: var(--font-display);
  font-size: var(--font-size-hero);
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
  color: var(--color-heading);
}

.summary {
  max-width: var(--measure-body);
  font-size: var(--font-size-body);
}

.pet-first-toggle {
  white-space: nowrap;
  padding: var(--space-1) var(--space-2);
  color: var(--color-heading);
  background: var(--color-surface-subtle);
  border-color: var(--color-border-strong);
  box-shadow: none;
  font-size: var(--font-size-small);
}

.pet-first-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  color: var(--color-text-muted);
  background: transparent;
  border-color: transparent;
  border-radius: var(--radius-sm);
  box-shadow: none;
}

.pet-first-icon-btn:hover {
  color: var(--color-heading);
  background: var(--color-surface-subtle);
  box-shadow: none;
  transform: none;
}

.pet-first-icon-btn--active {
  color: var(--color-accent) !important;
  background: var(--color-accent-soft) !important;
}

.mailbox-btn {
  position: relative;
}

.mailbox-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  font-size: 9px;
  font-weight: 700;
  line-height: 14px;
  text-align: center;
  color: #fff;
  background: #ef4444;
  border-radius: 7px;
  pointer-events: none;
}

.pet-first-toggle:hover {
  box-shadow: none;
}

@media (prefers-reduced-motion: reduce) {
  .action {
    transition: none;
  }

  .action:hover {
    transform: none;
    box-shadow: none;
  }
}

@media (max-width: 900px) {
  .shell-header {
    grid-template-columns: 1fr;
  }
}
</style>
