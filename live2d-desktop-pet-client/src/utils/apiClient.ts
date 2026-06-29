import type { AuthTokens, RefreshTokenResponse } from '../types/auth'

// Shared storage keys — must match usePetConfigState.ts
export const STORAGE_KEYS = {
  accessToken: 'desktop-pet.auth.access-token',
  refreshToken: 'desktop-pet.auth.refresh-token',
  stompToken: 'desktop-pet.debug.stomp-token',
} as const

let accessToken: string | null = localStorage.getItem(STORAGE_KEYS.accessToken)
let refreshToken: string | null = localStorage.getItem(STORAGE_KEYS.refreshToken)

let isRefreshing = false
let refreshPromise: Promise<boolean> | null = null

// External listener for token refresh — usePetConfigState registers here
let onTokenRefreshed: ((access: string, refresh: string) => void) | null = null
export function onTokensChanged(cb: (access: string, refresh: string) => void): void {
  onTokenRefreshed = cb
}

export function setTokens(token: string, refresh: string): void {
  accessToken = token
  refreshToken = refresh
  localStorage.setItem(STORAGE_KEYS.accessToken, token)
  localStorage.setItem(STORAGE_KEYS.refreshToken, refresh)
  // Keep STOMP token in sync with the API access token
  localStorage.setItem(STORAGE_KEYS.stompToken, token)
  // Notify usePetConfigState so reactive refs stay current
  onTokenRefreshed?.(token, refresh)
}

export function clearTokens(): void {
  accessToken = null
  refreshToken = null
  localStorage.removeItem(STORAGE_KEYS.accessToken)
  localStorage.removeItem(STORAGE_KEYS.refreshToken)
}

export function getAccessToken(): string | null {
  return accessToken
}

export function getTokens(): AuthTokens | null {
  if (!accessToken || !refreshToken) return null
  return { accessToken, refreshToken }
}

export class ApiError extends Error {
  status: number
  code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface RequestOptions {
  method: string
  headers: Record<string, string>
  body?: string
}

async function attemptTokenRefresh(): Promise<boolean> {
  if (!refreshToken) return false

  // If a refresh is already in flight, join that one instead of starting a new one
  if (isRefreshing) {
    return refreshPromise ?? Promise.resolve(false)
  }

  isRefreshing = true
  const prom = (async () => {
    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })

      if (!response.ok) {
        clearTokens()
        return false
      }

      const data: RefreshTokenResponse = await response.json()
      setTokens(data.accessToken, data.refreshToken)
      return true
    } catch {
      clearTokens()
      return false
    } finally {
      isRefreshing = false
      refreshPromise = null
    }
  })()

  // Set the promise before releasing to other callers (atomic guard)
  refreshPromise = prom
  return prom
}

async function request<T>(url: string, options: RequestOptions, isRetry = false): Promise<T> {
  const headers: Record<string, string> = { ...options.headers }

  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }

  const fetchOptions: RequestInit = {
    method: options.method,
    headers,
  }
  if (options.body !== undefined) {
    fetchOptions.body = options.body
  }

  const response = await fetch(url, fetchOptions)

  if (response.status === 401 && !isRetry) {
    const refreshed = await attemptTokenRefresh()
    if (refreshed) {
      return request<T>(url, options, true)
    }
    clearTokens()
  }

  let body: unknown
  try {
    body = await response.json()
  } catch {
    body = null
  }

  if (!response.ok) {
    const errorBody = body as Record<string, unknown> | null
    throw new ApiError(
      response.status,
      typeof errorBody?.message === 'string' ? errorBody.message : response.statusText,
      typeof errorBody?.code === 'string' ? errorBody.code : undefined,
    )
  }

  return body as T
}

export async function get<T>(url: string, params?: Record<string, string>): Promise<T> {
  let finalUrl = url
  if (params) {
    const searchParams = new URLSearchParams(params)
    finalUrl = `${url}?${searchParams.toString()}`
  }
  return request<T>(finalUrl, { method: 'GET', headers: {} })
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const options: RequestOptions = {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  }
  if (body !== undefined) {
    options.body = JSON.stringify(body)
  }
  return request<T>(url, options)
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const options: RequestOptions = {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
  }
  if (body !== undefined) {
    options.body = JSON.stringify(body)
  }
  return request<T>(url, options)
}

/**
 * Upload a file as multipart/form-data. Used for PEEK screenshot callback.
 * Does NOT set Content-Type header — browser sets it with the boundary.
 */
export async function uploadMultipart<T>(url: string, formData: FormData, isRetry = false): Promise<T> {
  const headers: Record<string, string> = {}
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }

  const response = await fetch(url, { method: 'POST', headers, body: formData })

  if (response.status === 401 && !isRetry) {
    const refreshed = await attemptTokenRefresh()
    if (refreshed) {
      return uploadMultipart<T>(url, formData, true)
    }
    clearTokens()
  }

  let body: unknown
  try { body = await response.json() } catch { body = null }

  if (!response.ok) {
    const errorBody = body as Record<string, unknown> | null
    throw new ApiError(
      response.status,
      typeof errorBody?.message === 'string' ? errorBody.message : response.statusText,
      typeof errorBody?.code === 'string' ? errorBody.code : undefined,
    )
  }

  return body as T
}
