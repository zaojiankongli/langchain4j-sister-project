<script setup lang="ts">
import type { PetDebugLogEntry } from './petDebugPanelTypes'

defineProps<{
  logEntries: PetDebugLogEntry[]
  recentEvents: PetDebugLogEntry[]
}>()
</script>

<template>
  <section class="log-panel" aria-labelledby="logs-title">
    <div class="card-heading">
      <div>
        <p class="eyebrow">Evidence</p>
        <h2 id="logs-title">Recent debug events</h2>
      </div>
      <span class="log-count">{{ logEntries.length }} total</span>
    </div>
    <ol class="log-list">
      <li v-for="entry in recentEvents" :key="`${entry.time}-${entry.event}`" class="log-entry">
        <span class="log-event">{{ entry.event }}</span>
        <span class="log-path">{{ entry.modelPath }}</span>
        <span v-if="entry.message" class="log-message">{{ entry.message }}</span>
      </li>
    </ol>
    <p v-if="!recentEvents.length" class="empty-log">Load the model or connect STOMP to collect debug evidence.</p>
  </section>
</template>

<style scoped>
.log-panel {
  padding: var(--space-5);
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

h2,
.empty-log {
  margin: 0;
}

h2 {
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  line-height: var(--line-height-tight);
  color: var(--color-heading);
}

.log-count,
.empty-log {
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}

.log-list {
  display: grid;
  gap: var(--space-3);
  margin: var(--space-4) 0 0;
  padding: 0;
  list-style: none;
}

.log-entry {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-log-bg);
}

.log-event {
  font-family: var(--font-mono);
  font-size: var(--font-size-code);
  color: var(--color-heading);
}

.log-path,
.log-message {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
}

.empty-log {
  margin-top: var(--space-4);
}
</style>
