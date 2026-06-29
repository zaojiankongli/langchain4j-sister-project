import { shallowRef } from 'vue'
import { get } from '../utils/apiClient'
import { usePetConfigState } from './usePetConfigState'
import type { ChatMessage, MessagesResponse } from '../types/message'

export interface MessageGroup {
  date: string
  label: string
  items: ChatMessage[]
}

function toLocalDateStr(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function groupMessagesByDate(msgList: ChatMessage[]): MessageGroup[] {
  const groups: MessageGroup[] = []
  const today = new Date()
  const todayStr = toLocalDateStr(today)
  const yesterdayStr = toLocalDateStr(new Date(today.getTime() - 86400000))

  for (const msg of msgList) {
    const dateStr = toLocalDateStr(new Date(msg.createdAt))
    const last = groups[groups.length - 1]
    if (last && last.date === dateStr) {
      last.items.push(msg)
      continue
    }

    let label: string
    if (dateStr === todayStr) label = 'Today'
    else if (dateStr === yesterdayStr) label = 'Yesterday'
    else label = new Date(msg.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    groups.push({ date: dateStr, label, items: [msg] })
  }

  return groups
}

export function useChatMessageState() {
  const { userId } = usePetConfigState()

  const messages = shallowRef<ChatMessage[]>([])
  const streamingMessage = shallowRef('')
  const isStreaming = shallowRef(false)
  const isLoadingHistory = shallowRef(false)
  const hasMoreHistory = shallowRef(true)
  const unreadCount = shallowRef(0)
  const currentPage = shallowRef(0)

  let appendStreamCallback: ((content: string) => void) | null = null
  let streamCompleteCallback: (() => void) | null = null
  let onUserMessageCommitted: ((message: ChatMessage) => void) | null = null
  let onAssistantMessageCommitted: ((message: ChatMessage) => void) | null = null

  const pageSize = 20
  const maxMessages = 200
  const streamChunks: string[] = []

  function trimMessages(arr: ChatMessage[]): ChatMessage[] {
    return arr.length > maxMessages ? arr.slice(-maxMessages) : arr
  }

  function setStreamCallbacks(onAppend: (content: string) => void, onComplete: () => void): void {
    appendStreamCallback = onAppend
    streamCompleteCallback = onComplete
  }

  function setMessageEventHooks(options: {
    onUserMessageCommitted?: (message: ChatMessage) => void
    onAssistantMessageCommitted?: (message: ChatMessage) => void
  }): void {
    onUserMessageCommitted = options.onUserMessageCommitted ?? null
    onAssistantMessageCommitted = options.onAssistantMessageCommitted ?? null
  }

  function appendStreamContent(content: string): void {
    streamChunks.push(content)
    if (streamChunks.length >= 16 || content.length > 64) {
      streamingMessage.value += streamChunks.join('')
      streamChunks.length = 0
    }
    isStreaming.value = true
    appendStreamCallback?.(content)
  }

  function markStreamComplete(): void {
    if (streamChunks.length > 0) {
      streamingMessage.value += streamChunks.join('')
      streamChunks.length = 0
    }

    const content = streamingMessage.value
    streamingMessage.value = ''
    isStreaming.value = false

    if (content) {
      const message: ChatMessage = {
        id: Date.now(),
        userId: userId.value ?? 0,
        role: 'assistant',
        content,
        createdAt: new Date().toISOString(),
      }
      messages.value = trimMessages([...messages.value, message])
      unreadCount.value += 1
      onAssistantMessageCommitted?.(message)
    }

    streamCompleteCallback?.()
  }

  function addOptimisticUserMessage(options: { text: string; imageUrl?: string; isVoice?: boolean }): ChatMessage {
    const message: ChatMessage = {
      id: Date.now(),
      userId: userId.value ?? 0,
      role: 'user',
      content: options.text.trim(),
      createdAt: new Date().toISOString(),
      deliveryState: 'sending',
      ...(options.imageUrl ? { imageUrl: options.imageUrl } : {}),
      ...(options.isVoice ? { isVoice: true } : {}),
    }
    messages.value = trimMessages([...messages.value, message])
    return message
  }

  function updateMessageImageUrl(messageId: number, imageUrl: string): void {
    messages.value = messages.value.map((message) =>
      message.id === messageId ? { ...message, imageUrl } : message,
    )
  }

  function updateMessageDeliveryState(messageId: number, deliveryState: 'sending' | 'sent'): void {
    messages.value = messages.value.map((message) =>
      message.id === messageId ? { ...message, deliveryState } : message,
    )
  }

  function removeMessageById(messageId: number): void {
    messages.value = messages.value.filter((message) => message.id !== messageId)
  }

  function notifyUserMessageCommitted(message: ChatMessage): void {
    onUserMessageCommitted?.(message)
  }

  async function loadHistory(): Promise<void> {
    if (!userId.value) return
    isLoadingHistory.value = true

    try {
      const response = await get<MessagesResponse>(`/api/messages/${userId.value}`)
      messages.value = trimMessages(response.content ?? [])
      hasMoreHistory.value = !!(response.totalPages && response.totalPages > 1)
      currentPage.value = 0
    } catch (error) {
      console.error('Failed to load message history:', error)
    } finally {
      isLoadingHistory.value = false
    }
  }

  async function loadMore(): Promise<void> {
    if (!userId.value || !hasMoreHistory.value || isLoadingHistory.value) return
    isLoadingHistory.value = true

    try {
      const nextPage = currentPage.value + 1
      const response = await get<MessagesResponse>(`/api/messages/${userId.value}`, {
        page: String(nextPage),
        size: String(pageSize),
      })
      if (response.content && response.content.length > 0) {
        messages.value = trimMessages([...messages.value, ...response.content])
      }
      hasMoreHistory.value = !!(response.totalPages && response.totalPages > nextPage)
      currentPage.value = nextPage
    } catch (error) {
      console.error('Failed to load more messages:', error)
    } finally {
      isLoadingHistory.value = false
    }
  }

  function clearMessages(): void {
    messages.value = []
    streamingMessage.value = ''
    isStreaming.value = false
    streamChunks.length = 0
  }

  function clearStream(): void {
    streamingMessage.value = ''
    isStreaming.value = false
    streamChunks.length = 0
  }

  function resetUnreadCount(): void {
    unreadCount.value = 0
  }

  return {
    messages,
    streamingMessage,
    isStreaming,
    isLoadingHistory,
    hasMoreHistory,
    unreadCount,
    appendStreamContent,
    markStreamComplete,
    addOptimisticUserMessage,
    updateMessageImageUrl,
    updateMessageDeliveryState,
    removeMessageById,
    notifyUserMessageCommitted,
    loadHistory,
    loadMore,
    clearMessages,
    clearStream,
    resetUnreadCount,
    setStreamCallbacks,
    setMessageEventHooks,
  }
}
