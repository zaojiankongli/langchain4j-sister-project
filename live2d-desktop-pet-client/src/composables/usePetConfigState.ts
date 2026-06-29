import { shallowRef, watch } from 'vue'
import { setTokens as apiSetTokens, clearTokens as apiClearTokens, onTokensChanged, STORAGE_KEYS } from '../utils/apiClient'

const DEBUG_PANEL_STORAGE_KEY = 'desktop-pet.debug.panel-open'
const USER_ID_KEY = 'desktop-pet.auth.user-id'
const USER_EMAIL_KEY = 'desktop-pet.auth.email'

// Module-level singleton state — all callers share the same refs
const authToken = shallowRef('')
const debugPanelOpen = shallowRef(false)
const accessToken = shallowRef('')
const refreshToken = shallowRef('')
const userId = shallowRef<number | null>(null)
const userEmail = shallowRef('')
let initialized = false

function ensureInitialized(): void {
  if (initialized || typeof window === 'undefined') return
  initialized = true
  authToken.value = window.localStorage.getItem(STORAGE_KEYS.stompToken) ?? ''
  debugPanelOpen.value = window.localStorage.getItem(DEBUG_PANEL_STORAGE_KEY) === 'true'
  accessToken.value = window.localStorage.getItem(STORAGE_KEYS.accessToken) ?? ''
  refreshToken.value = window.localStorage.getItem(STORAGE_KEYS.refreshToken) ?? ''
  const storedId = window.localStorage.getItem(USER_ID_KEY)
  userId.value = storedId ? Number(storedId) : null
  userEmail.value = window.localStorage.getItem(USER_EMAIL_KEY) ?? ''

  // Register callback so apiClient token refreshes update our reactive refs
  onTokensChanged((access, refresh) => {
    authToken.value = access
    accessToken.value = access
    refreshToken.value = refresh
  })

  watch(authToken, (value) => {
    if (typeof window === 'undefined') return
    if (!value) {
      window.localStorage.removeItem(STORAGE_KEYS.stompToken)
      return
    }
    window.localStorage.setItem(STORAGE_KEYS.stompToken, value)
  })

  watch(debugPanelOpen, (value) => {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(DEBUG_PANEL_STORAGE_KEY, String(value))
  })

  watch(accessToken, (value) => {
    if (typeof window === 'undefined') return
    if (!value) {
      window.localStorage.removeItem(STORAGE_KEYS.accessToken)
      return
    }
    window.localStorage.setItem(STORAGE_KEYS.accessToken, value)
  })

  watch(refreshToken, (value) => {
    if (typeof window === 'undefined') return
    if (!value) {
      window.localStorage.removeItem(STORAGE_KEYS.refreshToken)
      return
    }
    window.localStorage.setItem(STORAGE_KEYS.refreshToken, value)
  })

  watch(userId, (value) => {
    if (typeof window === 'undefined') return
    if (value === null) {
      window.localStorage.removeItem(USER_ID_KEY)
      return
    }
    window.localStorage.setItem(USER_ID_KEY, String(value))
  })

  watch(userEmail, (value) => {
    if (typeof window === 'undefined') return
    if (!value) {
      window.localStorage.removeItem(USER_EMAIL_KEY)
      return
    }
    window.localStorage.setItem(USER_EMAIL_KEY, value)
  })
}

export function usePetConfigState() {
  ensureInitialized()

  function toggleDebugPanel() {
    debugPanelOpen.value = !debugPanelOpen.value
  }

  function setAuthTokens(access: string, refresh: string, id: number, email: string): void {
    accessToken.value = access
    refreshToken.value = refresh
    userId.value = id
    userEmail.value = email
    authToken.value = access // also set STOMP token so connectSocket() works after login
    apiSetTokens(access, refresh)
  }

  function clearAuthTokens(): void {
    accessToken.value = ''
    refreshToken.value = ''
    userId.value = null
    userEmail.value = ''
    authToken.value = ''
    apiClearTokens()
  }

  return {
    authToken,
    debugPanelOpen,
    accessToken,
    refreshToken,
    userId,
    userEmail,
    toggleDebugPanel,
    setAuthTokens,
    clearAuthTokens,
  }
}
