<script setup lang="ts">
import { nextTick, useTemplateRef } from 'vue'
import type { PetSocketStatus } from '../../ws/petStompClient'

defineProps<{
  authToken: string
  canConnect: boolean
  canDisconnect: boolean
  socketStatus: PetSocketStatus
  socketStatusLabel: string
  socketStatusVisualState: 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'
  lastSocketError: string
  reconnectHint: string
}>()

const emit = defineEmits<{
  'update:authToken': [value: string]
  connectSocket: []
  disconnectSocket: []
}>()

const tokenInputRef = useTemplateRef<HTMLTextAreaElement>('tokenInput')

function updateAuthToken(event: Event) {
  emit('update:authToken', (event.target as HTMLTextAreaElement).value)
}

async function focusTokenInput() {
  await nextTick()
  tokenInputRef.value?.focus()
}

defineExpose({
  focusTokenInput,
})
</script>

<template>
  <section class="debug-card" aria-labelledby="connection-title">
    <div class="card-heading">
      <div>
        <p class="eyebrow">Connection</p>
        <h2 id="connection-title">STOMP bridge</h2>
      </div>
      <span class="status-pill" :data-state="socketStatusVisualState">{{ socketStatusLabel }}</span>
    </div>
    <label class="field">
      <span>Bearer token</span>
      <textarea
        ref="tokenInput"
        :value="authToken"
        rows="3"
        placeholder="Paste access token for /ws/chat"
        @input="updateAuthToken"
      ></textarea>
    </label>
    <div class="actions" aria-label="Socket actions">
      <button class="action" type="button" :disabled="!canConnect" @click="emit('connectSocket')">Connect STOMP</button>
      <button class="action action-secondary" type="button" :disabled="!canDisconnect" @click="emit('disconnectSocket')">Disconnect</button>
    </div>
    <p v-if="reconnectHint" class="reconnect-hint">{{ reconnectHint }}</p>
    <p v-if="lastSocketError" class="inline-error">{{ lastSocketError }}</p>
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
.inline-error {
  margin: 0;
}

h2 {
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  line-height: var(--line-height-tight);
  color: var(--color-heading);
}

.status-pill {
  white-space: nowrap;
  border: var(--border-width) solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  padding: var(--space-1) var(--space-3);
  color: var(--color-text-muted);
  background: var(--color-surface-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-size-code);
}

.status-pill[data-state='connected'] {
  border-color: var(--color-success);
  color: var(--color-success);
}

.status-pill[data-state='error'] {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.status-pill[data-state='connecting'] {
  border-color: var(--color-warning);
  color: var(--color-warning);
}

.field {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.field span {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
}

.field textarea {
  width: 100%;
  padding: var(--space-3);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text);
  background: var(--color-field-bg);
  font: inherit;
  box-sizing: border-box;
}

.field textarea:focus {
  border-color: var(--color-focus);
  outline: none;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-5);
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

.action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
  box-shadow: none;
  transform: none;
}

.action-secondary {
  color: var(--color-accent);
  background: transparent;
}

.inline-error {
  margin-top: var(--space-3);
  color: var(--color-danger);
  font-size: var(--font-size-small);
}

.reconnect-hint {
  margin: var(--space-3) 0 0;
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}
</style>
