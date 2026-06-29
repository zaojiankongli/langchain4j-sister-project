export interface OCEANPersonality {
  openness: number
  conscientiousness: number
  extraversion: number
  agreeableness: number
  neuroticism: number
}

export interface PersonalityPreset {
  id: number
  name: string
  description: string
  personality: OCEANPersonality
  sensitivity: number
  decayRate: number
  regressionRate: number
}

export interface TTSSettings {
  enabled: boolean
  volume: number
  speed: number
}

export interface ProactiveSettings {
  enabled: boolean
  interval: number
}

export interface UserSettings {
  userId: number
  personalityPresetId?: number
  personality: OCEANPersonality
  sensitivity: number
  decayRate: number
  regressionRate: number
  tts: TTSSettings
  proactive: ProactiveSettings
  themeId?: number
}
