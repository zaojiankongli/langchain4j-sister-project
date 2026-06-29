<script setup lang="ts">
import type { PetRuntimeState } from './petDebugPanelTypes'

defineProps<{
  petRuntimeState: PetRuntimeState
  moodLabel: string
  lastSemanticEvent: string
  socketStatus: string
}>()
</script>

<template>
  <section class="debug-card" aria-labelledby="semantics-title">
    <div class="card-heading">
      <div>
        <p class="eyebrow">Semantics</p>
        <h2 id="semantics-title">Pet state trace</h2>
      </div>
    </div>
    <dl class="trace-list">
      <div>
        <dt>Runtime</dt>
        <dd>{{ petRuntimeState }}</dd>
      </div>
      <div>
        <dt>Mood</dt>
        <dd>{{ moodLabel }}</dd>
      </div>
      <div>
        <dt>Recent semantic event</dt>
        <dd>{{ lastSemanticEvent }}</dd>
      </div>
      <div>
        <dt>Connection</dt>
        <dd>{{ socketStatus }}</dd>
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
