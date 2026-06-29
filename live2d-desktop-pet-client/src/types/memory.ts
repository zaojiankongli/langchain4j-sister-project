export type MemoryCategory = 'daily' | 'emotion' | 'story' | 'cg'

export type MemoryRarity = 'common' | 'rare' | 'epic'

export type MemoryCondition =
  | { type: 'first_chat' }
  | { type: 'chat_sent_count'; target: number }
  | { type: 'voice_session_count'; target: number }
  | { type: 'music_session_count'; target: number }
  | { type: 'disconnect_companion_count'; target: number }
  | { type: 'mail_read_count'; target: number }
  | { type: 'mood_observed_count'; target: number }

export interface MemoryDefinition {
  id: string
  title: string
  category: MemoryCategory
  rarity: MemoryRarity
  collectionLabel?: string
  coverTheme?: 'sunrise' | 'midnight' | 'letter' | 'rain' | 'dream' | 'song' | 'blush'
  coverEmoji?: string
  matchKeywords?: string[]
  hint: string
  description: string
  detailQuote?: string
  coverAsset?: string
  detailAsset?: string
  unlockConditions: MemoryCondition[]
}

export interface MemoryProgress {
  memoryId: string
  unlocked: boolean
  unlockedAt?: string
  relatedMood?: string
  relatedExcerpt?: string
}

export interface MemoryConditionProgress {
  condition: MemoryCondition
  current: number
  target: number
  fulfilled: boolean
  label: string
}

export interface MemoryGalleryItem {
  definition: MemoryDefinition
  progress: MemoryProgress | null
  unlocked: boolean
  unlockedAt?: string
  relatedMood?: string
  relatedExcerpt?: string
  sourceImageUrl?: string
  sourceMemoryTitle?: string
  sourceMemoryDate?: string
  sourceMemoryContent?: string
  matchedKeywords?: string[]
  primaryConfidence?: number | null
  conditions: MemoryConditionProgress[]
  completionRatio: number
}

export type MemoryGalleryTab = 'records' | 'gallery'

export interface BackendGalleryDefinition {
  galleryKey: string
  title: string
  category: MemoryCategory
  rarity: MemoryRarity
  hint: string
  description: string
  coverTheme?: MemoryDefinition['coverTheme']
  matchKeywords?: string[]
  sortOrder?: number
}

export interface BackendGalleryUnlock {
  galleryKey: string
  unlockedAt: string
  relatedMood?: string
  relatedExcerpt?: string
  sourceMemoryId?: number
  sourceMemoryTitle?: string
  sourceMemoryDate?: string
  sourceImageUrl?: string
}

export interface BackendGalleryOverview {
  definitions: BackendGalleryDefinition[]
  unlocks: BackendGalleryUnlock[]
  counts: Record<string, number>
}

export interface BackendGalleryDetailResponse {
  definition: BackendGalleryDefinition | null
  unlock: BackendGalleryUnlock | null
  unlocked: boolean
  primaryConfidence?: number | null
  matchedKeywords?: string[]
  sourceMemoryContent?: string | null
}
