<script setup lang="ts">
import { useDesktopTelemetryDiagnostics } from '../../composables/useDesktopTelemetryDiagnostics'

const { groups, summary, recentEvents, refresh, clear, exportJson } = useDesktopTelemetryDiagnostics()
</script>

<template>
  <section class="debug-card" aria-labelledby="telemetry-title">
    <div class="card-heading">
      <div>
        <p class="eyebrow">Telemetry</p>
        <h2 id="telemetry-title">Desktop diagnostics</h2>
      </div>
      <div class="actions">
        <button class="small-action" type="button" @click="refresh">Refresh</button>
        <button class="small-action" type="button" @click="exportJson">Export JSON</button>
        <button class="small-action small-action--danger" type="button" @click="clear">Clear</button>
      </div>
    </div>

    <dl class="summary-grid">
      <div v-for="item in summary" :key="item.label" class="summary-item">
        <dt>{{ item.label }}</dt>
        <dd>{{ item.value }}</dd>
      </div>
    </dl>

    <div class="section-block">
      <h3>Event groups</h3>
      <div v-if="groups.length" class="group-list">
        <span v-for="group in groups" :key="group.type" class="group-chip">{{ group.type }} · {{ group.count }}</span>
      </div>
      <p v-else class="empty-copy">No telemetry recorded yet.</p>
    </div>

    <div class="section-block">
      <h3>Recent events</h3>
      <ol v-if="recentEvents.length" class="event-list">
        <li v-for="event in recentEvents" :key="event.id" class="event-row">
          <span class="event-type">{{ event.time }} · {{ event.type }}</span>
          <span class="event-detail">{{ event.detail }}</span>
        </li>
      </ol>
      <p v-else class="empty-copy">Connect STOMP or trigger socket actions to collect telemetry.</p>
    </div>
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
h3,
.summary-grid,
.summary-grid dd,
.summary-grid dt,
.event-list,
.empty-copy {
  margin: 0;
}

h2 {
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  line-height: var(--line-height-tight);
  color: var(--color-heading);
}

h3 {
  color: var(--color-heading);
  font-size: var(--font-size-small);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: flex-end;
}

.small-action {
  padding: var(--space-2) var(--space-3);
  border: var(--border-width) solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  background: var(--color-surface-subtle);
  color: var(--color-heading);
  font-size: var(--font-size-small);
  cursor: pointer;
}

.small-action:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.small-action:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

.small-action--danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.summary-item {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
}

.summary-item dt {
  color: var(--color-text-muted);
  font-size: var(--font-size-caption);
}

.summary-item dd {
  margin-top: var(--space-1);
  color: var(--color-heading);
  font-family: var(--font-mono);
  font-size: var(--font-size-code);
  overflow-wrap: anywhere;
}

.section-block {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.group-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.group-chip {
  padding: var(--space-1) var(--space-3);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-surface-subtle);
  color: var(--color-text);
  font-family: var(--font-mono);
  font-size: var(--font-size-code);
}

.event-list {
  display: grid;
  gap: var(--space-3);
  padding: 0;
  list-style: none;
}

.event-row {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-log-bg);
}

.event-type {
  color: var(--color-heading);
  font-family: var(--font-mono);
  font-size: var(--font-size-code);
}

.event-detail,
.empty-copy {
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
  overflow-wrap: anywhere;
}

@media (max-width: 560px) {
  .card-heading {
    display: grid;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
