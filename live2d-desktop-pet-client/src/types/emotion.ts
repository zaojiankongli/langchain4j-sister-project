export interface EmotionState {
  moodLabel: string
  moodDescription?: string
  pleasure: number
  arousal: number
  dominance: number
}

export interface EmotionMood {
  moodLabel: string
  moodDescription?: string
}

export interface EmotionHistoryEntry {
  id: number
  userId: number
  pleasure: number
  arousal: number
  dominance: number
  moodLabel: string
  moodDescription?: string
  recordedAt: string
}

export interface EmotionEvolution {
  events: Array<{
    timestamp: string
    description: string
    deltaP: number
    deltaA: number
    deltaD: number
  }>
}

export interface EmotionHistoryResponse {
  content: EmotionHistoryEntry[]
  totalPages?: number
  totalElements?: number
}

export type MoodLabel = 'happy' | 'sad' | 'thinking' | 'surprised' | 'annoyed' | 'neutral' | 'loving'
