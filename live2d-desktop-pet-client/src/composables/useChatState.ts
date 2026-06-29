import { computed, shallowRef } from 'vue'

export function useChatState() {
  const chatInput = shallowRef('')
  // deprecated — use chatDisplay.latestBubbleContent instead.
  // No longer actively written to by the event pipeline.
  // Kept as a legacy ref to prevent breakage in debug panel bindings.
  const streamText = shallowRef('')

  // deprecated — use chatDisplay.latestBubbleContent instead.
  const streamPreviewLabel = computed(() => streamText.value || '')

  return {
    chatInput,
    streamText,
    streamPreviewLabel,
  }
}
