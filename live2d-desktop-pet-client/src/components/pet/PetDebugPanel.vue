<script setup lang="ts">
import { useTemplateRef } from 'vue'
import type { PetSocketStatus } from '../../ws/petStompClient'
import PetDebugChatCard from './PetDebugChatCard.vue'
import PetDebugConnectionCard from './PetDebugConnectionCard.vue'
import PetDebugEventLog from './PetDebugEventLog.vue'
import PetMemoryGalleryDebugCard from './PetMemoryGalleryDebugCard.vue'
import PetPerformanceCard from './PetPerformanceCard.vue'
import PetSemanticTraceCard from './PetSemanticTraceCard.vue'
import PetTelemetryDiagnosticsCard from './PetTelemetryDiagnosticsCard.vue'
import type { PetDebugLogEntry, PetRuntimeState } from './petDebugPanelTypes'
import { isDiagnosticsEnabled } from '../../utils/diagnosticsAccess'

export type { PetDebugLogEntry, PetRuntimeState }

defineProps<{
  authToken: string
  chatInput: string
  canSend: boolean
  canConnect: boolean
  canDisconnect: boolean
  socketStatus: PetSocketStatus
  socketStatusLabel: string
  socketStatusVisualState: 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'
  lastSocketError: string
  reconnectHint: string
  streamPreviewLabel: string
  petRuntimeState: PetRuntimeState
  moodLabel: string
  lastSemanticEvent: string
  logEntries: PetDebugLogEntry[]
  recentEvents: PetDebugLogEntry[]
}>()

const emit = defineEmits<{
  'update:authToken': [value: string]
  'update:chatInput': [value: string]
  connectSocket: []
  disconnectSocket: []
  sendChat: []
  clearPreview: []
  reloadModel: []
}>()

const connectionCardRef = useTemplateRef<{
  focusTokenInput: () => Promise<void>
}>('connectionCard')

const chatCardRef = useTemplateRef<{
  focusChatInput: () => Promise<void>
}>('chatCard')

const diagnosticsEnabled = isDiagnosticsEnabled()

async function focusPreferredField(preferToken: boolean) {
  if (preferToken) {
    await connectionCardRef.value?.focusTokenInput()
    return
  }

  await chatCardRef.value?.focusChatInput()
}

defineExpose({
  focusPreferredField,
})

</script>

<template>
  <aside class="control-stack" aria-label="Debug controls and status">
    <PetDebugConnectionCard
      ref="connectionCard"
      :auth-token="authToken"
      :can-connect="canConnect"
      :can-disconnect="canDisconnect"
      :socket-status="socketStatus"
      :socket-status-label="socketStatusLabel"
      :socket-status-visual-state="socketStatusVisualState"
      :last-socket-error="lastSocketError"
      :reconnect-hint="reconnectHint"
      @update:auth-token="emit('update:authToken', $event)"
      @connect-socket="emit('connectSocket')"
      @disconnect-socket="emit('disconnectSocket')"
    />

    <PetDebugChatCard
      ref="chatCard"
      :chat-input="chatInput"
      :can-send="canSend"
      :stream-preview-label="streamPreviewLabel"
      @update:chat-input="emit('update:chatInput', $event)"
      @send-chat="emit('sendChat')"
      @clear-preview="emit('clearPreview')"
    />

    <PetSemanticTraceCard
      :pet-runtime-state="petRuntimeState"
      :mood-label="moodLabel"
      :last-semantic-event="lastSemanticEvent"
      :socket-status="socketStatus"
    />

    <PetPerformanceCard :log-entries="logEntries" @reload-model="emit('reloadModel')" />

    <PetTelemetryDiagnosticsCard v-if="diagnosticsEnabled" />

    <PetMemoryGalleryDebugCard v-if="diagnosticsEnabled" />

    <PetDebugEventLog :log-entries="logEntries" :recent-events="recentEvents" />
  </aside>
</template>

<style scoped>
.control-stack {
  display: grid;
  gap: var(--space-4);
}
</style>
