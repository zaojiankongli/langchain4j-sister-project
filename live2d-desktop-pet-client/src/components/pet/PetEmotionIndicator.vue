<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import { getMoodEmoji, getMoodColor } from '../../utils/moodEmoji'
import { normalizeBar } from '../../utils/formatBar'

const props = withDefaults(defineProps<{
  moodLabel: string
  moodDescription?: string
  pleasure?: number
  arousal?: number
  dominance?: number
  compact?: boolean
  isPeeking?: boolean
  peekStatus?: string
}>(), {
  moodDescription: '',
  pleasure: 0,
  arousal: 0.5,
  dominance: 0,
  compact: false,
  isPeeking: false,
  peekStatus: 'idle',
})

const pulseAnimating = ref(false)
let pulseTimer: ReturnType<typeof setTimeout> | null = null

watch(() => props.moodLabel, () => {
  pulseAnimating.value = true
  if (pulseTimer) clearTimeout(pulseTimer)
  pulseTimer = setTimeout(() => {
    pulseAnimating.value = false
    pulseTimer = null
  }, 600)
})

onUnmounted(() => {
  if (pulseTimer) clearTimeout(pulseTimer)
})

const ariaLabel = computed(() => {
  const mood = props.moodLabel || 'unknown'
  const p = props.pleasure?.toFixed(2) ?? '0'
  const a = props.arousal?.toFixed(2) ?? '0.5'
  const d = props.dominance?.toFixed(2) ?? '0'
  const desc = props.moodDescription ? `, ${props.moodDescription}` : ''
  return `Emotion: ${mood}${desc}, PAD values: Pleasure ${p}, Arousal ${a}, Dominance ${d}`
})

const currentEmoji = computed(() => {
  return getMoodEmoji(props.moodLabel)
})

const currentColor = computed(() => {
  return getMoodColor(props.moodLabel)
})

const barWidths = computed(() => ({
  pleasure: normalizeBar(props.pleasure, -1, 1),
  arousal: normalizeBar(props.arousal, 0, 1),
  dominance: normalizeBar(props.dominance, -1, 1),
}))
</script>

<template>
  <div
    v-if="moodLabel"
    class="emotion-indicator"
    :class="{
      'emotion-indicator--compact': compact,
      'emotion-indicator--peeking': isPeeking,
      'emotion-indicator--pulse': pulseAnimating,
    }"
    :aria-label="ariaLabel"
    :style="{ '--mood-color': currentColor }"
  >
    <Transition name="peek-fade">
      <div v-if="isPeeking" class="emotion-peek">
        <span class="emotion-peek-emoji">👀</span>
        <span class="emotion-peek-text">
          <template v-if="peekStatus === 'capturing'">正在截屏...</template>
          <template v-else-if="peekStatus === 'uploading'">正在分析...</template>
          <template v-else-if="peekStatus === 'done'">分析完成~</template>
          <template v-else-if="peekStatus === 'error'">出了点问题</template>
          <template v-else>悄悄看着你</template>
        </span>
      </div>
    </Transition>

    <div class="emotion-main">
      <span class="emotion-emoji" aria-hidden="true">{{ currentEmoji }}</span>
      <div class="emotion-body">
        <span class="emotion-label">{{ moodLabel }}</span>
        <span v-if="!compact && moodDescription" class="emotion-description">{{ moodDescription }}</span>
      </div>
    </div>

    <div v-if="!compact" class="emotion-pad">
      <div class="pad-bar">
        <span class="pad-bar-label">P</span>
        <div class="pad-bar-track">
          <div class="pad-bar-fill pad-bar-fill--pleasure" :style="{ width: barWidths.pleasure }"></div>
        </div>
      </div>
      <div class="pad-bar">
        <span class="pad-bar-label">A</span>
        <div class="pad-bar-track">
          <div class="pad-bar-fill pad-bar-fill--arousal" :style="{ width: barWidths.arousal }"></div>
        </div>
      </div>
      <div class="pad-bar">
        <span class="pad-bar-label">D</span>
        <div class="pad-bar-track">
          <div class="pad-bar-fill pad-bar-fill--dominance" :style="{ width: barWidths.dominance }"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.emotion-indicator {
  display: inline-flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: var(--border-width) solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: rgba(16, 15, 20, 0.78);
  backdrop-filter: blur(6px);
  pointer-events: none;
  position: relative;
  overflow: hidden;
  transition:
    border-color var(--duration-fast, 160ms) ease,
    background var(--duration-fast, 160ms) ease;
}

.emotion-indicator--compact {
  flex-direction: row;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-pill);
  background: rgba(16, 15, 20, 0.72);
}

.emotion-indicator::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  opacity: 0.08;
  background: var(--mood-color);
  transition: background var(--duration-fast, 160ms) ease;
}

/* Peeking flash */
.emotion-peek {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  border-radius: inherit;
  background: rgba(16, 15, 20, 0.92);
  backdrop-filter: blur(4px);
  z-index: 1;
  white-space: nowrap;
}

.emotion-peek-emoji {
  font-size: var(--font-size-subtitle, 1.08rem);
  line-height: 1;
}

.emotion-peek-text {
  font-size: var(--font-size-small);
  color: var(--color-heading);
}

.peek-fade-enter-active {
  animation: peek-pulse 1s ease-out;
}

.peek-fade-leave-active {
  transition: opacity 0.3s ease;
}

.peek-fade-leave-to {
  opacity: 0;
}

@keyframes peek-pulse {
  0% {
    opacity: 0;
    transform: scale(0.92);
  }
  20% {
    opacity: 1;
    transform: scale(1.04);
  }
  40% {
    transform: scale(1);
  }
  80% {
    opacity: 1;
  }
  100% {
    opacity: 0.85;
  }
}

/* Pulse animation on emotion change */
.emotion-indicator--pulse {
  animation: emotion-pulse 0.6s ease;
}

@keyframes emotion-pulse {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--mood-color) 40%, transparent);
    transform: scale(1);
  }
  50% {
    box-shadow: 0 0 12px 4px color-mix(in srgb, var(--mood-color) 25%, transparent);
    transform: scale(1.04);
  }
  100% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--mood-color) 0%, transparent);
    transform: scale(1);
  }
}

/* Main row: emoji + text */
.emotion-main {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  position: relative;
  z-index: 0;
}

.emotion-emoji {
  font-size: var(--font-size-title, 1.35rem);
  line-height: 1;
  flex-shrink: 0;
}

.emotion-body {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
}

.emotion-label {
  font-size: var(--font-size-small);
  font-weight: 500;
  color: var(--color-heading);
  text-transform: capitalize;
  line-height: 1.3;
}

.emotion-description {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 16rem;
}

/* PAD bars */
.emotion-pad {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  position: relative;
  z-index: 0;
}

.pad-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.pad-bar-label {
  width: 1rem;
  font-family: var(--font-mono);
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  flex-shrink: 0;
  text-align: right;
}

.pad-bar-track {
  flex: 1;
  height: 4px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.pad-bar-fill {
  height: 100%;
  border-radius: var(--radius-sm);
  transition: width var(--duration-fast, 160ms) ease;
}

.pad-bar-fill--pleasure {
  background: #7edfa0;
}

.pad-bar-fill--arousal {
  background: #ffd166;
}

.pad-bar-fill--dominance {
  background: #8fd7ff;
}

/* Compact override: hide PAD bars in compact mode */
.emotion-indicator--compact .emotion-pad {
  display: none;
}

/* Empty state */
.emotion-indicator:empty {
  display: none;
}

@media (prefers-reduced-motion: reduce) {
  .emotion-indicator--pulse {
    animation: none;
  }

  .pad-bar-fill {
    transition: none;
  }
}
</style>
