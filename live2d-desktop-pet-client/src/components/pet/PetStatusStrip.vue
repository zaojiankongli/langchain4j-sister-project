<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { formatTime as formatTimeUtil } from '../../utils/formatTime'
import { usePerformanceQuality } from '../../composables/usePerformanceQuality'

export interface StateBadge {
  label: string
  value: string
  tone: 'neutral' | 'active' | 'success' | 'warning' | 'danger'
}

const props = defineProps<{
  badges: StateBadge[]
  compact?: boolean
  connectionQuality?: 'good' | 'fair' | 'poor'
  isAudioPlaying?: boolean
}>()

const now = ref(new Date())
let timer: ReturnType<typeof setTimeout> | null = null
const performanceQuality = usePerformanceQuality()

function startClock(): void {
  if (timer) return
  now.value = new Date()
  const scheduleNextTick = (): void => {
    const delay = 60000 - (Date.now() % 60000)
    timer = setTimeout(() => {
      now.value = new Date()
      timer = null
      if (performanceQuality.isPageVisible.value) {
        scheduleNextTick()
      }
    }, delay)
  }

  scheduleNextTick()
}

function stopClock(): void {
  if (!timer) return
  clearTimeout(timer)
  timer = null
}

onMounted(() => {
  watch(
    () => performanceQuality.isPageVisible.value,
    (visible) => {
      if (visible) {
        startClock()
        return
      }

      stopClock()
    },
    { immediate: true },
  )
})

onUnmounted(() => {
  stopClock()
})

function formatTime(date: Date): string {
  return formatTimeUtil(date.toISOString())
}

const connectionQualityTone = computed<'success' | 'warning' | 'danger'>(() => {
  if (props.connectionQuality === 'good') return 'success'
  if (props.connectionQuality === 'fair') return 'warning'
  return 'danger'
})

const connectionQualityLabel = computed(() => {
  if (props.connectionQuality === 'good') return '\u25CF Good'
  if (props.connectionQuality === 'fair') return '\u25CF Fair'
  return '\u25CF Poor'
})
</script>

<template>
  <dl class="state-strip" :class="{ 'state-strip--compact': compact }" aria-label="Pet runtime state">
    <div class="state-strip-grid" :class="{ 'state-strip-grid--compact': compact }">
      <div
        v-for="badge in badges"
        :key="badge.label"
        class="state-badge"
        :class="{ 'state-badge--compact': compact }"
        :data-tone="badge.tone"
      >
        <dt>{{ badge.label }}</dt>
        <dd>{{ badge.value }}</dd>
      </div>

      <!-- Clock -->
      <div
        class="state-badge"
        :class="{ 'state-badge--compact': compact }"
        data-tone="neutral"
      >
        <dt>Time</dt>
        <dd class="state-clock">{{ formatTime(now) }}</dd>
      </div>

      <!-- Connection quality -->
      <div
        v-if="connectionQuality"
        class="state-badge state-badge--connection"
        :class="{ 'state-badge--compact': compact }"
        :data-tone="connectionQualityTone"
      >
        <dt>Link</dt>
        <dd>{{ connectionQualityLabel }}</dd>
      </div>

      <!-- Audio playback indicator -->
      <div
        v-if="isAudioPlaying"
        class="state-badge state-badge--audio"
        :class="{ 'state-badge--compact': compact }"
        data-tone="active"
      >
        <dt>Audio</dt>
        <dd class="state-audio">
          <span class="audio-dot" aria-hidden="true" />
          <span>Playing</span>
        </dd>
      </div>
    </div>
  </dl>
</template>

<style scoped>
.state-strip {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.state-strip-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.state-strip-grid--compact {
  gap: var(--space-2);
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.state-badge {
  padding: var(--space-3) var(--space-4);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
}

.state-badge--compact {
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
}

.state-badge dt {
  margin: 0;
  font-size: var(--font-size-caption);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.state-badge--compact dt {
  font-size: 0.7rem;
}

.state-badge dd {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-subtitle);
  color: var(--color-heading);
}

.state-badge--compact dd {
  margin-top: 0;
  font-size: 0.82rem;
}

/* Tone variants */
.state-badge[data-tone='success'] {
  border-color: color-mix(in srgb, var(--color-success) 65%, var(--color-border));
}

.state-badge[data-tone='warning'] {
  border-color: color-mix(in srgb, var(--color-warning) 65%, var(--color-border));
}

.state-badge[data-tone='danger'] {
  border-color: color-mix(in srgb, var(--color-danger) 65%, var(--color-border));
}

.state-badge[data-tone='active'] {
  border-color: color-mix(in srgb, var(--color-accent) 65%, var(--color-border));
}

/* Clock */
.state-clock {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

/* Connection quality */
.state-badge--connection dd {
  font-family: var(--font-mono);
  font-size: var(--font-size-small);
}

/* Audio indicator */
.state-audio {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.audio-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent);
  animation: audio-pulse 1.2s ease-in-out infinite;
}

@keyframes audio-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(0.75);
  }
}

@media (prefers-reduced-motion: reduce) {
  .audio-dot {
    animation: none;
  }
}
</style>
