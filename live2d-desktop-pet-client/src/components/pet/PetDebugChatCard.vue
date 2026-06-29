<script setup lang="ts">
import { nextTick, useTemplateRef } from 'vue'
const props = defineProps<{
  chatInput: string
  streamPreviewLabel: string
  canSend: boolean
}>()

const emit = defineEmits<{
  'update:chatInput': [value: string]
  sendChat: []
  clearPreview: []
}>()

const chatInputRef = useTemplateRef<HTMLInputElement>('chatInputField')

function updateChatInput(event: Event) {
  emit('update:chatInput', (event.target as HTMLInputElement).value)
}

function onChatKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || !props.canSend) {
    return
  }

  event.preventDefault()
  emit('sendChat')
}

async function focusChatInput() {
  await nextTick()
  chatInputRef.value?.focus()
}

defineExpose({
  focusChatInput,
})
</script>

<template>
  <section class="debug-card" aria-labelledby="chat-title">
    <div class="card-heading">
      <div>
        <p class="eyebrow">Conversation</p>
        <h2 id="chat-title">Chat and streamed reply</h2>
      </div>
    </div>
    <label class="field">
      <span>Chat input</span>
      <input
        ref="chatInputField"
        :value="chatInput"
        type="text"
        placeholder="Say something to the pet"
        @input="updateChatInput"
        @keydown="onChatKeydown"
      />
    </label>
    <div class="actions" aria-label="Chat actions">
      <button class="action" type="button" :disabled="!canSend" @click="emit('sendChat')">Send chat</button>
      <button class="action action-secondary" type="button" @click="emit('clearPreview')">Clear preview</button>
    </div>
    <div class="stream-preview" aria-live="polite">
      <span class="preview-label">Stream preview</span>
      <p>{{ streamPreviewLabel }}</p>
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
.stream-preview p {
  margin: 0;
}

h2 {
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  line-height: var(--line-height-tight);
  color: var(--color-heading);
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

.field input {
  width: 100%;
  padding: var(--space-3);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text);
  background: var(--color-field-bg);
  font: inherit;
  box-sizing: border-box;
}

.field input:focus {
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

.stream-preview {
  margin: var(--space-3) 0 0;
  min-height: var(--size-stream-min-height);
  padding: var(--space-3);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-heading);
  background: var(--color-surface-subtle);
}

.preview-label {
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}
</style>
