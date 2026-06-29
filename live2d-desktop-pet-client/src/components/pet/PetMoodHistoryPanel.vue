<script setup lang="ts">
import { computed } from 'vue'
import GlassPanel from '../glass/GlassPanel.vue'
import type { EmotionHistoryEntry } from '../../types/emotion'
import { getMoodEmoji } from '../../utils/moodEmoji'
import { normalizeBar } from '../../utils/formatBar'
import { formatTime, formatDate } from '../../utils/formatTime'

const props = defineProps<{
  entries: EmotionHistoryEntry[]
  isLoading: boolean
  isOpen: boolean
  historyError?: string
}>()

const emit = defineEmits<{
  close: []
  retry: []
}>()

function getEmoji(moodLabel: string): string {
  return getMoodEmoji(moodLabel)
}

const sortedEntries = computed(() => {
  return [...props.entries].sort(
    (a, b) => new Date(b.recordedAt).getTime() - new Date(a.recordedAt).getTime()
  )
})

const todayEntries = computed(() => {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  return props.entries.filter((e) => new Date(e.recordedAt).getTime() >= todayStart)
})

const mostCommonMoodToday = computed(() => {
  if (todayEntries.value.length === 0) return null
  const counts: Record<string, number> = {}
  for (const entry of todayEntries.value) {
    counts[entry.moodLabel] = (counts[entry.moodLabel] || 0) + 1
  }
  let maxCount = 0
  let maxMood = ''
  for (const [mood, count] of Object.entries(counts)) {
    if (count > maxCount) {
      maxCount = count
      maxMood = mood
    }
  }
  return maxMood
})

const last7DaysCount = computed(() => {
  const sevenDaysAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
  return props.entries.filter((e) => new Date(e.recordedAt).getTime() >= sevenDaysAgo).length
})

// Uses formatTime and formatDate from shared utils
</script>

<template>
  <Transition name="panel-slide">
    <GlassPanel
      v-if="isOpen"
      class="mood-panel"
      tag="aside"
      side="left"
      @backdrop-click="emit('close')"
      role="dialog"
      aria-label="心情记录"
      tabindex="-1"
      @keydown.escape="emit('close')"
    >
      <div class="mood-panel-header">
        <h2 class="mood-panel-title">心情记录</h2>
        <button
          class="mood-panel-close"
          type="button"
          aria-label="关闭"
          @click="emit('close')"
        >
          <span aria-hidden="true">✕</span>
        </button>
      </div>

      <div class="mood-panel-body">
        <!-- Loading -->
        <div v-if="isLoading" class="mood-loading">
          加载中...
        </div>

        <template v-else>
          <!-- Error state -->
          <div v-if="historyError" class="mood-error" role="alert">
            <p class="mood-error-text">{{ historyError }}</p>
            <button
              class="mood-retry-btn"
              type="button"
              @click="emit('retry')"
            >
              重试
            </button>
          </div>

          <template v-else>
            <!-- Summary -->
            <div class="mood-summary">
              <div class="summary-row">
                <span class="summary-label">今日心情</span>
                <span
                  v-if="mostCommonMoodToday"
                  class="summary-emoji"
                >{{ getEmoji(mostCommonMoodToday) }}</span>
                <span v-else class="summary-empty">暂无</span>
              </div>
              <div class="summary-row">
                <span class="summary-label">近 7 日记录</span>
                <span class="summary-count">{{ last7DaysCount }} 条</span>
              </div>
            </div>

            <!-- Empty state -->
            <div v-if="entries.length === 0" class="mood-empty">
              <div class="mood-empty-icon" aria-hidden="true">
                <svg width="48" height="48" viewBox="0 0 48 48" fill="none" aria-hidden="true">
                  <circle cx="24" cy="24" r="16" stroke="currentColor" stroke-width="1.5" opacity="0.4" />
                  <path d="M16 28s3 4 8 4 8-4 8-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity="0.4" />
                  <circle cx="18" cy="20" r="2" fill="currentColor" opacity="0.3" />
                  <circle cx="30" cy="20" r="2" fill="currentColor" opacity="0.3" />
                </svg>
              </div>
              <p class="mood-empty-text">No mood data yet</p>
            </div>

            <!-- Timeline -->
            <div v-else class="mood-timeline">
              <div
                v-for="(entry, _index) in sortedEntries"
                :key="entry.id"
                class="timeline-item"
              >
                <div class="timeline-dot"></div>
                <div class="timeline-content">
                  <div class="entry-header">
                    <span class="entry-emoji" aria-hidden="true">{{ getEmoji(entry.moodLabel) }}</span>
                    <span class="entry-label">{{ entry.moodLabel }}</span>
                    <span class="entry-meta">
                      <span class="entry-time">{{ formatTime(entry.recordedAt) }}</span>
                      <span class="entry-date">{{ formatDate(entry.recordedAt) }}</span>
                    </span>
                  </div>

                  <div v-if="entry.moodDescription" class="entry-description">
                    {{ entry.moodDescription }}
                  </div>

                  <div class="entry-pad">
                    <div class="pad-row">
                      <span class="pad-label">P</span>
                      <div class="pad-track">
                        <div
                          class="pad-fill pad-fill--pleasure"
                          :style="{ width: normalizeBar(entry.pleasure, -1, 1) }"
                        ></div>
                      </div>
                    </div>
                    <div class="pad-row">
                      <span class="pad-label">A</span>
                      <div class="pad-track">
                        <div
                          class="pad-fill pad-fill--arousal"
                          :style="{ width: normalizeBar(entry.arousal, 0, 1) }"
                        ></div>
                      </div>
                    </div>
                    <div class="pad-row">
                      <span class="pad-label">D</span>
                      <div class="pad-track">
                        <div
                          class="pad-fill pad-fill--dominance"
                          :style="{ width: normalizeBar(entry.dominance, -1, 1) }"
                        ></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </template>
      </div>
    </GlassPanel>
  </Transition>
</template>

<style scoped>
/* ── Panel container ── */
.mood-panel {
  position: fixed;
  left: 0;
  top: 0;
  width: 320px;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 101;
  overflow: hidden;
}

/* ── Header ── */
.mood-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-4) var(--space-3);
  border-bottom: var(--border-width) solid var(--color-border);
  flex-shrink: 0;
}

.mood-panel-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-subtitle);
  color: var(--color-heading);
  letter-spacing: var(--letter-spacing-tight);
  line-height: 1.3;
}

.mood-panel-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: var(--border-width) solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--font-size-body);
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    background var(--duration-fast) ease,
    border-color var(--duration-fast) ease;
}

.mood-panel-close:hover {
  color: var(--color-heading);
  background: var(--color-surface-subtle);
  border-color: var(--color-border-strong);
}

.mood-panel-close:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

/* ── Scrollable body ── */
.mood-panel-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: var(--space-3) var(--space-4) var(--space-4);
}

/* Thin custom scrollbar */
.mood-panel-body::-webkit-scrollbar {
  width: 4px;
}

.mood-panel-body::-webkit-scrollbar-track {
  background: transparent;
}

.mood-panel-body::-webkit-scrollbar-thumb {
  background: var(--color-border-strong);
  border-radius: 2px;
}

.mood-panel-body::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-muted);
}

/* ── Loading state ── */
.mood-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}

/* ── Summary ── */
.mood-summary {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  margin-bottom: var(--space-4);
  background: var(--color-surface-subtle);
  border-radius: var(--radius-sm);
  border: var(--border-width) solid var(--color-border);
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.summary-label {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
}

.summary-emoji {
  font-size: var(--font-size-subtitle);
  line-height: 1;
}

.summary-empty {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
}

.summary-count {
  font-size: var(--font-size-small);
  color: var(--color-heading);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

/* ── Error state ── */
.mood-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-3);
  text-align: center;
}

.mood-error-text {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-danger);
  line-height: 1.4;
}

.mood-retry-btn {
  padding: var(--space-1) var(--space-4);
  font-size: var(--font-size-small);
  font-family: var(--font-body);
  color: var(--color-action-text);
  background: var(--color-accent);
  border: none;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition:
    opacity var(--duration-fast) ease,
    transform var(--duration-fast) ease;
}

.mood-retry-btn:hover {
  opacity: 0.85;
}

.mood-retry-btn:active {
  transform: translateY(1px);
}

.mood-retry-btn:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

/* ── Empty state ── */
.mood-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  min-height: 160px;
  user-select: none;
}

.mood-empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  opacity: 0.5;
}

.mood-empty-text {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  opacity: 0.75;
}

/* ── Timeline ── */
.mood-timeline {
  position: relative;
}

/* Vertical line */
.mood-timeline::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 6px;
  bottom: 6px;
  width: 2px;
  background: var(--color-border-strong);
  border-radius: 1px;
}

/* ── Timeline item ── */
.timeline-item {
  position: relative;
  padding: 0 0 var(--space-3) 24px;
}

.timeline-item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 24px;
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--color-border);
}

.timeline-item:last-child {
  padding-bottom: 0;
}

/* Timeline dot */
.timeline-dot {
  position: absolute;
  left: 1px;
  top: 6px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--color-accent);
  border: 2px solid var(--color-surface);
  z-index: 1;
  box-sizing: border-box;
}

/* ── Entry content ── */
.timeline-content {
  min-width: 0;
}

.entry-header {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-1);
}

.entry-emoji {
  font-size: var(--font-size-body);
  line-height: 1;
  flex-shrink: 0;
  margin-right: var(--space-1);
}

.entry-label {
  font-size: var(--font-size-small);
  color: var(--color-heading);
  font-weight: 500;
  text-transform: capitalize;
  flex-shrink: 0;
}

.entry-meta {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}

.entry-time {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  font-family: var(--font-mono);
}

.entry-date {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.entry-description {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  line-height: 1.4;
  margin-bottom: var(--space-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── PAD bars ── */
.entry-pad {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.pad-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.pad-label {
  width: 1rem;
  font-family: var(--font-mono);
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  flex-shrink: 0;
  text-align: right;
}

.pad-track {
  flex: 1;
  height: 4px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.pad-fill {
  height: 100%;
  border-radius: var(--radius-sm);
  transition: width var(--duration-fast) ease;
}

.pad-fill--pleasure {
  background: #7edfa0;
}

.pad-fill--arousal {
  background: #ffd166;
}

.pad-fill--dominance {
  background: #8fd7ff;
}

/* ── Slide transition ── */
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: transform 250ms ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  transform: translateX(-100%);
}

/* ── Reduced motion ── */
@media (prefers-reduced-motion: reduce) {
  .panel-slide-enter-active,
  .panel-slide-leave-active {
    transition: none;
  }

  .pad-fill {
    transition: none;
  }
}
</style>
