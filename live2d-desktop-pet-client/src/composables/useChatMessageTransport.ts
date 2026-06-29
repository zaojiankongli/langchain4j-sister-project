import { shallowRef } from 'vue'
import { uploadMultipart } from '../utils/apiClient'
import type { ApiResult } from '../types/api'
import type { ChatMessage } from '../types/message'

export interface ChatSendPayload {
  text: string
  imageUrl?: string
  imageFile?: File
}

interface UseChatMessageTransportOptions {
  clearStream: () => void
  addOptimisticUserMessage: (options: { text: string; imageUrl?: string; isVoice?: boolean }) => ChatMessage
  updateMessageImageUrl: (messageId: number, imageUrl: string) => void
  updateMessageDeliveryState: (messageId: number, deliveryState: 'sending' | 'sent') => void
  removeMessageById: (messageId: number) => void
  notifyUserMessageCommitted: (message: ChatMessage) => void
}

export function useChatMessageTransport(options: UseChatMessageTransportOptions) {
  const {
    clearStream,
    addOptimisticUserMessage,
    updateMessageImageUrl,
    updateMessageDeliveryState,
    removeMessageById,
    notifyUserMessageCommitted,
  } = options

  let sendChatFn: ((text: string, imageUrl?: string) => boolean) | null = null
  let sendVoiceMessageFn: ((blob: Blob) => void) | null = null
  const isSending = shallowRef(false)
  const sendError = shallowRef('')

  function setSendChatFunction(fn: (text: string, imageUrl?: string) => boolean): void {
    sendChatFn = fn
  }

  function setSendVoiceFunction(fn: (blob: Blob) => void): void {
    sendVoiceMessageFn = fn
  }

  function clearSendError(): void {
    sendError.value = ''
  }

  async function resolveImageUrl(imageUrl?: string, imageFile?: File): Promise<string | undefined> {
    if (!imageUrl && !imageFile) {
      return undefined
    }
    if (imageFile) {
      const extension = imageFile.type.split('/')[1] || 'png'
      const formData = new FormData()
      formData.append('file', imageFile, imageFile.name || `chat-image.${extension}`)
      const res = await uploadMultipart<ApiResult<{ url: string }>>('/api/oss/upload/message-image', formData)
      if (res.code !== 200 || !res.data?.url) {
        throw new Error(res.message || 'Image upload failed')
      }
      return res.data.url
    }
    return imageUrl
  }

  async function sendMessage(payload: ChatSendPayload): Promise<boolean> {
    const text = payload.text
    const imageUrl = payload.imageUrl
    const imageFile = payload.imageFile
    if (!text.trim() && !imageUrl && !imageFile) return false
    if (isSending.value) return false

    isSending.value = true
    sendError.value = ''
    clearStream()

    const userMsg = addOptimisticUserMessage({
      text,
      imageUrl,
    })

    let sent = false
    try {
      const uploadedImageUrl = await resolveImageUrl(imageUrl, imageFile)
      sent = sendChatFn?.(text.trim(), uploadedImageUrl) ?? false
      if (!sent) {
        sendError.value = '消息发送失败，请检查连接状态后重试'
      }

      if (sent && uploadedImageUrl && uploadedImageUrl !== imageUrl) {
        updateMessageImageUrl(userMsg.id, uploadedImageUrl)
      }
      if (sent) {
        updateMessageDeliveryState(userMsg.id, 'sent')
      }
      if (imageUrl?.startsWith('blob:')) {
        URL.revokeObjectURL(imageUrl)
      }
    } catch (error) {
      console.error('Failed to upload chat image:', error)
      sendError.value = error instanceof Error ? error.message : '图片发送失败'
      if (imageUrl?.startsWith('blob:')) {
        URL.revokeObjectURL(imageUrl)
      }
      sent = false
    } finally {
      isSending.value = false
    }

    if (!sent) {
      removeMessageById(userMsg.id)
    }

    if (sent) {
      notifyUserMessageCommitted(userMsg)
    }

    return sent
  }

  function sendVoiceMessage(audioBlob: Blob, transcript?: string): boolean {
    if (!audioBlob || audioBlob.size === 0) return false

    addOptimisticUserMessage({
      text: transcript?.trim() || '[Voice message]',
      isVoice: true,
    })

    if (sendVoiceMessageFn) {
      sendVoiceMessageFn(audioBlob)
    } else {
      console.log('[useChatMessageTransport] Voice message captured (backend not yet implemented):', audioBlob)
    }

    return true
  }

  return {
    isSending,
    sendError,
    clearSendError,
    sendMessage,
    sendVoiceMessage,
    setSendChatFunction,
    setSendVoiceFunction,
  }
}
