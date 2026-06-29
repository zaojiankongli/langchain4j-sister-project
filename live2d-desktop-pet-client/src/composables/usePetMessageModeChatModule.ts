import type { ChatSendPayload, useChatMessages } from './useChatMessages'

interface UsePetMessageModeChatModuleOptions {
  chatMessages: ReturnType<typeof useChatMessages>
  sendChatMessage: (text: string, imageUrl?: string) => boolean
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
}

export function usePetMessageModeChatModule(options: UsePetMessageModeChatModuleOptions) {
  const {
    chatMessages,
    sendChatMessage,
    showToast,
  } = options

  chatMessages.setSendChatFunction(sendChatMessage)
  chatMessages.setSendVoiceFunction((blob) => {
    console.log('[usePetMessageModeChatModule] Voice message captured:', blob)
    showToast('语音功能开发中，敬请期待', 'info')
  })

  function sendUiChat(text: string, imageUrl?: string): void {
    void chatMessages.sendMessage({ text, imageUrl })
  }

  function sendPetInput(payload: ChatSendPayload): void {
    void chatMessages.sendMessage(payload)
  }

  return {
    sendUiChat,
    sendPetInput,
  }
}
