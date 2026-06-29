export interface AuthTokens {
  accessToken: string
  refreshToken: string
}

export interface SendCodeRequest {
  email: string
}

export interface LoginRequest {
  email: string
  code: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userId: number
  email: string
  requiresProfileComplete?: boolean
  isNewUser?: boolean
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface RefreshTokenResponse {
  accessToken: string
  refreshToken: string
}

export interface CompleteProfileRequest {
  username: string
  gender: string
  aiType: number
  hobbies: string[]
  birthday?: string
  avatarUrl?: string
}
