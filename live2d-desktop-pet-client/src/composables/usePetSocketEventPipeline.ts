import type { NotificationCategory } from './usePetNotifications'
import type { PetSocketInboundEvent } from '../ws/petStompClient'

interface PetSocketEventPipelineOptions {
  baseHandleSocketEvent: (event: PetSocketInboundEvent) => void
  chatMessages: {
    appendStreamContent: (content: string) => void
    markStreamComplete: () => void
  }
  audioPlayer: {
    enqueueAudio: (audioData: string) => void
    stop: () => void
  }
  peekHandler: {
    handlePeekRequest: (peekId: string) => void
  }
  showToast: (message: string, type: 'success' | 'error' | 'info') => void
  notify: (category: NotificationCategory, title: string, body?: string) => Promise<void>
}

export function usePetSocketEventPipeline(options: PetSocketEventPipelineOptions) {
  const {
    baseHandleSocketEvent,
    chatMessages,
    audioPlayer,
    peekHandler,
    showToast,
    notify,
  } = options

  function handleSocketEvent(event: PetSocketInboundEvent): void {
    baseHandleSocketEvent(event)

    if (event.type === 'TEXT') {
      const content = event.payload?.content ?? ''
      const isComplete = event.payload?.isComplete === true

      if (content) {
        chatMessages.appendStreamContent(content)
      }

      if (isComplete) {
        chatMessages.markStreamComplete()
        if (content) {
          const preview = content.length > 60 ? content.slice(0, 60) + '…' : content
          void notify('message', '新回复', preview)
        }
      }
    }

    if (event.type === 'AUDIO') {
      const data = event.payload?.audioData
      if (data) {
        audioPlayer.enqueueAudio(data)
      }
    }

    if (event.type === 'PEEK_REQUEST') {
      const peekId = event.payload?.peekId ?? ''
      peekHandler.handlePeekRequest(peekId)
    }

    if (event.type === 'PET_MOTION' && event.payload?.motion === 'listening') {
      audioPlayer.stop()
    }

    if (event.type === 'SYSTEM') {
      showToast((event.payload?.content as string) ?? '系统消息', 'info')
    }
  }

  return {
    handleSocketEvent,
  }
}
