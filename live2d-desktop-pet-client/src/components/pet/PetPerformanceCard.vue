<script setup lang="ts">
import { computed } from 'vue'
import type { PetDebugLogEntry } from './petDebugPanelTypes'

const props = defineProps<{
  logEntries: PetDebugLogEntry[]
}>()

const emit = defineEmits<{
  reloadModel: []
}>()

function extractMetric(message: string | undefined, key: string): string {
  if (!message) return '—'
  const match = message.match(new RegExp(`${key}=([^\s]+)`))
  return match?.[1] ?? '—'
}

const latestSchedule = computed(() =>
  props.logEntries.find((entry) => entry.event === 'live2d:model-load-scheduled') ?? null,
)

const latestLoad = computed(() =>
  props.logEntries.find((entry) => entry.event === 'live2d:model-loaded' || entry.event === 'live2d:model-load-failed') ?? null,
)

const latestSuccessfulLoad = computed(() =>
  props.logEntries.find((entry) => entry.event === 'live2d:model-loaded') ?? null,
)

const latestFailedLoad = computed(() =>
  props.logEntries.find((entry) => entry.event === 'live2d:model-load-failed') ?? null,
)

const scheduleStrategy = computed(() => extractMetric(latestSchedule.value?.message, 'strategy'))
const scheduleDelay = computed(() => extractMetric(latestSchedule.value?.message, 'delayMs'))
const loadDuration = computed(() => extractMetric(latestLoad.value?.message, 'loadMs'))
const loadStatus = computed(() => {
  if (!latestLoad.value) return '—'
  return latestLoad.value.event === 'live2d:model-loaded' ? 'loaded' : 'failed'
})
const lastSuccessDuration = computed(() => extractMetric(latestSuccessfulLoad.value?.message, 'loadMs'))
const lastFailureDuration = computed(() => extractMetric(latestFailedLoad.value?.message, 'loadMs'))
const lastFailureReason = computed(() => {
  const message = latestFailedLoad.value?.message
  if (!message) return '—'
  return message.replace(/^loadMs=\S+\s*/, '') || '—'
})
const latestFitInfo = computed(() => {
  const message = latestSuccessfulLoad.value?.message
  if (!message) return '—'
  const fitStart = message.indexOf('fit=')
  return fitStart >= 0 ? message.slice(fitStart) : '—'
})
</script>

<template>
  <section class="debug-card" aria-labelledby="performance-title">
    <div class="card-heading">
      <div>
        <p class="eyebrow">Performance</p>
        <h2 id="performance-title">Live2D load timings</h2>
      </div>
      <button class="reload-btn" type="button" @click="emit('reloadModel')">Reload model</button>
    </div>
    <dl class="trace-list">
      <div>
        <dt>Schedule strategy</dt>
        <dd>{{ scheduleStrategy }}</dd>
      </div>
      <div>
        <dt>Schedule delay</dt>
        <dd>{{ scheduleDelay === '—' ? '—' : `${scheduleDelay} ms` }}</dd>
      </div>
      <div>
        <dt>Load duration</dt>
        <dd>{{ loadDuration === '—' ? '—' : `${loadDuration} ms` }}</dd>
      </div>
      <div>
        <dt>Last load status</dt>
        <dd>{{ loadStatus }}</dd>
      </div>
      <div>
        <dt>Last success</dt>
        <dd>{{ lastSuccessDuration === '—' ? '—' : `${lastSuccessDuration} ms` }}</dd>
      </div>
      <div>
        <dt>Last failure</dt>
        <dd>{{ lastFailureDuration === '—' ? '—' : `${lastFailureDuration} ms` }}</dd>
      </div>
      <div>
        <dt>Failure reason</dt>
        <dd>{{ lastFailureReason }}</dd>
      </div>
      <div>
        <dt>Latest fit info</dt>
        <dd>{{ latestFitInfo }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.debug-card {
  padding: var(--space-6);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}

.card-heading {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  align-items: start;
}

.eyebrow {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-caption);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
  color: var(--color-accent);
}

.reload-btn {
  padding: var(--space-2) var(--space-3);
  border: var(--border-width) solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  background: var(--color-surface-subtle);
  color: var(--color-heading);
  font-size: var(--font-size-small);
  cursor: pointer;
}

.reload-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

h2,
.trace-list,
.trace-list dd,
.trace-list dt {
  margin: 0;
}

h2 {
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  line-height: var(--line-height-tight);
  color: var(--color-heading);
}

.trace-list {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.trace-list div {
  display: grid;
  grid-template-columns: var(--size-trace-label) minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
}

.trace-list dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}

.trace-list dd {
  color: var(--color-heading);
  font-family: var(--font-mono);
  font-size: var(--font-size-code);
  overflow-wrap: anywhere;
}

@media (max-width: 560px) {
  .trace-list div {
    grid-template-columns: 1fr;
  }
}
</style>
