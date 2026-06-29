export interface ChatMessage {
  id: number
  userId: number
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  moodLabel?: string
  imageUrl?: string
  deliveryState?: 'sending' | 'sent'
  /** Whether this message was sent via voice (VAD recording). */
  isVoice?: boolean
}

export interface MessagesResponse {
  content: ChatMessage[]
  totalPages?: number
  totalElements?: number
  page?: number
  size?: number
}

export interface MessagesByDateResponse {
  date: string
  messages: ChatMessage[]
}

export interface SessionPreview {
  date: string
  preview: string
  messageCount: number
}
