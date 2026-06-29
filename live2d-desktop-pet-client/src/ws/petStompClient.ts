import { shallowRef } from 'vue'
import { Stomp, type CompatClient, type IMessage } from '@stomp/stompjs'
import { recordTelemetry } from '../utils/telemetry'

// Minimal interface matching the StompSubscription returned by client.subscribe().
// @stomp/stompjs does not export Subscription directly in v7.x.
interface StompSubHandle {
  id: string
  unsubscribe: () => void
}
import SockJS from 'sockjs-client/dist/sockjs.min.js'

export type PetSocketStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'

export interface PetSocketTextEvent {
  type: 'TEXT'
  payload?: {
    content?: string
    isComplete?: boolean
  }
}

export interface PetSocketEmotionEvent {
  type: 'EMOTION_UPDATE'
  payload?: {
    moodLabel?: string
    moodDescription?: string
    pleasure?: number
    arousal?: number
    dominance?: number
  }
}

export interface PetSocketMotionEvent {
  type: 'PET_MOTION'
  payload?: {
    motion?: string
    priority?: string
  }
}

export interface PetSocketExpressionEvent {
  type: 'PET_EXPRESSION'
  payload?: {
    expression?: string
    intensity?: number
    durationMs?: number
  }
}

export interface PetSocketSystemEvent {
  type: 'SYSTEM' | 'ERROR' | 'PONG'
  payload?: Record<string, unknown>
}

export interface PetSocketPeekEvent {
  type: 'PEEK_REQUEST'
  payload?: {
    peekId?: string
  }
}

export interface PetSocketAudioEvent {
  type: 'AUDIO'
  payload?: {
    audioData?: string
    sampleRate?: number
    format?: string
  }
}

export type PetSocketInboundEvent =
  | PetSocketTextEvent
  | PetSocketEmotionEvent
  | PetSocketMotionEvent
  | PetSocketExpressionEvent
  | PetSocketSystemEvent
  | PetSocketPeekEvent
  | PetSocketAudioEvent

export interface PetSocketCallbacks {
  onStatusChange?: (status: PetSocketStatus) => void
  onReconnectMetaChange?: (meta: { attempt: number; maxAttempts: number; nextDelayMs: number | null }) => void
  onMessage?: (event: PetSocketInboundEvent) => void
  onError?: (message: string) => void
}

const WS_CHAT = '/ws/chat'
const WS_SEND_CHAT = '/app/chat'
const WS_SEND_REALTIME_START = '/app/pet/realtime/start'
const WS_SEND_REALTIME_AUDIO = '/app/pet/realtime/audio'
const WS_SEND_REALTIME_STOP = '/app/pet/realtime/stop'
const WS_SEND_PING = '/app/ping'
const MAX_RECONNECT_ATTEMPTS = 5

const clientRef = shallowRef<CompatClient | null>(null)
const reconnectAttempts = shallowRef(0)

let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let pingInterval: ReturnType<typeof setInterval> | null = null
let manualDisconnect = false
let isReconnecting = false
let callbacks: PetSocketCallbacks = {}
let getToken: () => string = () => ''
let connectStartedAt = 0
// Track active STOMP subscriptions so they can be explicitly unsubscribed
// on disconnect/reconnect, preventing stale handlers and server-side leaks.
const activeSubscriptions: StompSubHandle[] = []

function emitStatus(status: PetSocketStatus) {
  callbacks.onStatusChange?.(status)
  recordTelemetry('socket.status', { status })
}

function emitReconnectMeta(nextDelayMs: number | null) {
  callbacks.onReconnectMetaChange?.({
    attempt: reconnectAttempts.value,
    maxAttempts: MAX_RECONNECT_ATTEMPTS,
    nextDelayMs,
  })
  recordTelemetry('socket.reconnect_meta', {
    attempt: reconnectAttempts.value,
    maxAttempts: MAX_RECONNECT_ATTEMPTS,
    nextDelayMs,
  })
}

function emitError(error: unknown) {
  const message = error instanceof Error ? error.message : String(error)
  callbacks.onError?.(message)
  recordTelemetry('socket.error', { message })
}

/** Unsubscribe all tracked STOMP subscriptions and clear the list. */
function unsubscribeAll(): void {
  for (const sub of activeSubscriptions) {
    try { sub.unsubscribe() } catch { /* sub may already be dead */ }
  }
  activeSubscriptions.length = 0
}

function scheduleReconnect() {
  if (isReconnecting || manualDisconnect || reconnectAttempts.value >= MAX_RECONNECT_ATTEMPTS) {
    return
  }

  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }

  if (pingInterval) {
    clearInterval(pingInterval)
    pingInterval = null
  }

  isReconnecting = true
  const baseDelay = 1000 * 2 ** reconnectAttempts.value
  const jitter = baseDelay * (0.75 + Math.random() * 0.5)
  const delay = Math.round(jitter)
  reconnectAttempts.value += 1
  emitReconnectMeta(delay)
  reconnectTimer = setTimeout(() => {
    isReconnecting = false
    connectPetSocket(getToken, callbacks)
  }, delay)
}

function isPetSocketInboundEvent(value: unknown): value is PetSocketInboundEvent {
  if (!value || typeof value !== 'object' || !('type' in value)) {
    return false
  }

  const eventType = (value as { type: unknown }).type
  return (
    eventType === 'TEXT' ||
    eventType === 'EMOTION_UPDATE' ||
    eventType === 'PET_MOTION' ||
    eventType === 'PET_EXPRESSION' ||
    eventType === 'SYSTEM' ||
    eventType === 'ERROR' ||
    eventType === 'PONG' ||
    eventType === 'PEEK_REQUEST' ||
    eventType === 'AUDIO'
  )
}

function handleInbound(messageBody: string) {
  try {
    const event: unknown = JSON.parse(messageBody)
    if (!isPetSocketInboundEvent(event)) {
      emitError('Received unexpected pet socket message shape')
      return
    }

    callbacks.onMessage?.(event)
  } catch (error) {
    emitError(error)
  }
}

export function connectPetSocket(token: string | (() => string), nextCallbacks: PetSocketCallbacks) {
  // Normalize token to a getter so reconnects always use the freshest value
  getToken = typeof token === 'function' ? token : () => token
  callbacks = nextCallbacks
  manualDisconnect = false

  const currentToken = getToken()
  if (!currentToken) {
    recordTelemetry('socket.connect_rejected', { reason: 'missing_token' })
    emitError('Missing Bearer token for STOMP connection')
    emitStatus('error')
    return
  }

    if (clientRef.value) {
      unsubscribeAll()
      clientRef.value.disconnect()
      clientRef.value = null
    }

  emitStatus('connecting')
  connectStartedAt = Date.now()
  recordTelemetry('socket.connect_start', { path: WS_CHAT })

  const socket = new SockJS(WS_CHAT)
  socket.onclose = () => {
    if (manualDisconnect) {
      recordTelemetry('socket.closed', { manual: true })
      emitStatus('disconnected')
      return
    }

    recordTelemetry('socket.closed', { manual: false })
    emitStatus('disconnected')
    scheduleReconnect()
  }

  const client = Stomp.over(socket)
  client.heartbeat.outgoing = 10000
  client.heartbeat.incoming = 10000
  client.debug = () => {}

  client.connect(
    { Authorization: `Bearer ${currentToken}` },
    () => {
      clientRef.value = client
      reconnectAttempts.value = 0
      isReconnecting = false
      emitReconnectMeta(null)
      recordTelemetry('socket.connected', {
        durationMs: connectStartedAt ? Date.now() - connectStartedAt : 0,
        subscriptions: 2,
      })

      activeSubscriptions.push(
        client.subscribe('/user/queue/chat', (frame: IMessage) => {
          handleInbound(frame.body)
        }),
      )
      activeSubscriptions.push(
        client.subscribe('/user/queue/control', (frame: IMessage) => {
          handleInbound(frame.body)
        }),
      )

      if (pingInterval) clearInterval(pingInterval)
      pingInterval = setInterval(() => {
        client.send(WS_SEND_PING, {}, '{}')
      }, 10000)

      emitStatus('connected')
    },
    (error: string | unknown) => {
      emitError(error)
      emitStatus('error')
      scheduleReconnect()
    },
  )
}

export function disconnectPetSocket() {
  manualDisconnect = true
  isReconnecting = false
  recordTelemetry('socket.disconnect_request')
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (pingInterval) {
    clearInterval(pingInterval)
    pingInterval = null
  }

  reconnectAttempts.value = 0
  emitReconnectMeta(null)

  unsubscribeAll()
  clientRef.value?.disconnect()
  clientRef.value = null
  emitStatus('disconnected')
}

export function sendPetChat(text: string, enableAudio: boolean, imageUrl?: string) {
  if (!clientRef.value) {
    return false
  }

  clientRef.value.send(WS_SEND_CHAT, {}, JSON.stringify({ text, enableAudio, imageUrl }))
  return true
}

export interface PetRealtimeStartOptions {
  voice?: string
  threshold?: number
  silenceDurationMs?: number
  instructions?: string
}

export function startPetRealtime(options: PetRealtimeStartOptions = {}): boolean {
  if (!clientRef.value) {
    return false
  }

  clientRef.value.send(WS_SEND_REALTIME_START, {}, JSON.stringify(options))
  return true
}

export function sendPetRealtimeAudio(audioBase64: string): boolean {
  if (!clientRef.value) {
    return false
  }

  clientRef.value.send(WS_SEND_REALTIME_AUDIO, {}, JSON.stringify({
    audioBase64,
    timestamp: Date.now(),
  }))
  return true
}

export function stopPetRealtime(): boolean {
  if (!clientRef.value) {
    return false
  }

  clientRef.value.send(WS_SEND_REALTIME_STOP, {}, '{}')
  return true
}
