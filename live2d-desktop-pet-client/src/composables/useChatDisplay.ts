import { computed, type ShallowRef } from 'vue'
import type { PetRuntimeState } from './usePetRuntimeState'
import type { useChatMessages } from './useChatMessages'

interface UseChatDisplayOptions {
  chatMessages: ReturnType<typeof useChatMessages>
  petRuntimeState: ShallowRef<PetRuntimeState>
}

/**
 * Unified display layer for chat content.
 * Replaces the legacy streamText dual-write pattern with a single source
 * of truth derived from chatMessages.
 */
export function useChatDisplay(options: UseChatDisplayOptions) {
  const { chatMessages, petRuntimeState } = options

  /**
   * The content currently shown in the bubble zone / companion overlay.
   * During streaming: returns the in-progress streaming message.
   * When idle: returns the last assistant message content (or empty).
   */
  const latestBubbleContent = computed(() => {
    if (chatMessages.isStreaming.value && chatMessages.streamingMessage.value) {
      return chatMessages.streamingMessage.value
    }

    // Walk backwards to find the most recent assistant message
    const msgs = chatMessages.messages.value
    for (let i = msgs.length - 1; i >= 0; i--) {
      if (msgs[i].role === 'assistant') return msgs[i].content
    }
    return ''
  })

  /**
   * Whether to show a blinking cursor after the bubble text.
   * Active while the pet is speaking (streaming) or the runtime
   * state indicates ongoing speech.
   */
  const showCursor = computed(() => {
    return petRuntimeState.value === 'speaking' || chatMessages.isStreaming.value
  })

  return {
    latestBubbleContent,
    showCursor,
  }
}
