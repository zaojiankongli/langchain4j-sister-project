import { shallowRef, onUnmounted } from 'vue'
import { post, ApiError } from '../utils/apiClient'
import { usePetConfigState } from './usePetConfigState'
import type { LoginResponse } from '../types/auth'

export function useAuthFlow() {
  const { setAuthTokens } = usePetConfigState()

  const email = shallowRef('')
  const code = shallowRef('')
  const step = shallowRef<'email' | 'code'>('email')
  const isSendingCode = shallowRef(false)
  const isLoggingIn = shallowRef(false)
  const authError = shallowRef('')
  const countdown = shallowRef(0)
  const isAuthenticated = shallowRef(false)
  const authEmail = shallowRef('')

  let countdownTimer: ReturnType<typeof setInterval> | null = null

  function startCountdown(): void {
    countdown.value = 60
    if (countdownTimer) clearInterval(countdownTimer)
    countdownTimer = setInterval(() => {
      if (countdown.value > 0) {
        countdown.value--
      } else {
        if (countdownTimer) {
          clearInterval(countdownTimer)
          countdownTimer = null
        }
      }
    }, 1000)
  }

  function stopCountdown(): void {
    if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
    countdown.value = 0
  }

  function isValidEmail(val: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val)
  }

  async function sendCode(): Promise<boolean> {
    if (isSendingCode.value) return false
    if (!isValidEmail(email.value)) {
      authError.value = '请输入有效的邮箱地址'
      return false
    }

    isSendingCode.value = true
    authError.value = ''

    try {
      await post('/api/auth/send-code', { email: email.value })
      step.value = 'code'
      startCountdown()
      return true
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        authError.value = err.message
      } else {
        authError.value = '发送验证码失败，请重试'
      }
      return false
    } finally {
      isSendingCode.value = false
    }
  }

  async function login(): Promise<boolean> {
    if (isLoggingIn.value) return false
    if (!/^\d{6}$/.test(code.value)) {
      authError.value = '请输入6位验证码'
      return false
    }

    isLoggingIn.value = true
    authError.value = ''

    try {
      const response = await post<LoginResponse>('/api/auth/login', {
        email: email.value,
        code: code.value,
      })
      setAuthTokens(response.accessToken, response.refreshToken, response.userId, response.email)
      isAuthenticated.value = true
      authEmail.value = response.email
      stopCountdown()
      return true
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        authError.value = err.message
      } else {
        authError.value = '登录失败，请重试'
      }
      return false
    } finally {
      isLoggingIn.value = false
    }
  }

  function resetAuth(): void {
    email.value = ''
    code.value = ''
    step.value = 'email'
    authError.value = ''
    stopCountdown()
  }

  onUnmounted(() => { stopCountdown() })

  return {
    email,
    code,
    step,
    isSendingCode,
    isLoggingIn,
    authError,
    countdown,
    isAuthenticated,
    authEmail,
    sendCode,
    login,
    resetAuth,
  }
}
