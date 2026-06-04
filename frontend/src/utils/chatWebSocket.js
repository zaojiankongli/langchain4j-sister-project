import { ref } from 'vue'
import Stomp from 'stompjs'
import SockJS from 'sockjs-client'
import { STORAGE_KEYS } from '@/config/storage'
import { WS } from '@/config/api'
import { safeGet, safeRemove } from '@/utils/storage'
import { base64UrlDecode } from './request'

// STOMP 状态（模块级单例）
const stompClient = ref(null)
const isConnected = ref(false)
const isConnecting = ref(false)
const reconnectAttempts = ref(0)
const MAX_RECONNECT_ATTEMPTS = 5
let isManualDisconnect = false
let _appDisposed = false // 应用级销毁标记，防止 reconnectTimer 在 app 卸载后继续



// 消息回调（由 ChatWindow 设置，绑定实例防止覆盖）
let callbacks = {
  onTextMessage: null,
  onAudioChunk: null,
  onEmotionUpdate: null,
  onSystemMessage: null,
  onAuthSuccess: null,
  onPeekRequest: null,
  onError: null,
  onStatusChange: null
}

/**
 * 连接 STOMP WebSocket
 */
export function connect(userId) {
  isManualDisconnect = false

  if (stompClient.value && isConnected.value) {
    return
  }

  if (isConnecting.value) {
    return
  }
  isConnecting.value = true

  const token = safeGet(STORAGE_KEYS.ACCESS_TOKEN)
  if (!token) {
    callbacks.onError?.('请先登录')
    callbacks.onStatusChange?.('disconnected')
    isConnecting.value = false
    return
  }

  // 通过 Vite 代理连接 WebSocket（注意：/ws/chat 在 vite.config.js 中代理到 ws://localhost:8080/ws/chat）
  // SockJS 会在 /ws/chat 建立原生 WebSocket
  const socket = new SockJS(WS.CHAT)
  
  // 添加底层 WebSocket 关闭处理
  socket.onclose = (event) => {
    isConnected.value = false
    isConnecting.value = false
    // 手动断开时不触发额外通知，避免与 disconnect() 的清理竞争
    if (isManualDisconnect) return
    callbacks.onStatusChange?.('disconnected')
  }

  // 创建 STOMP 客户端
  stompClient.value = Stomp.over(socket)
  
  // 配置 STOMP 心跳：10s 发送心跳，10s 期望接收心跳
  stompClient.value.heartbeat.outgoing = 10000
  stompClient.value.heartbeat.incoming = 10000

  // 关闭 STOMP 调试日志（生产环境）
  stompClient.value.debug = function(str) {
  }

  // 连接并认证
  stompClient.value.connect(
    {
      Authorization: `Bearer ${token}`
    },
    onConnect,
    onError2
  )

  function onConnect(frame) {
    isConnecting.value = false
    isConnected.value = true
    reconnectAttempts.value = 0

    // 订阅用户私有队列
    stompClient.value.subscribe(
      '/user/queue/chat',
      (message) => {
        handleMessage(JSON.parse(message.body))
      }
    )

      stompClient.value.subscribe(
      '/user/queue/control',
      (message) => {
        handleControlMessage(JSON.parse(message.body))
      }
    )

    callbacks.onStatusChange?.('connected')
  }

  function onError2(error) {
    isConnecting.value = false
    isConnected.value = false
    callbacks.onError?.(error)
    callbacks.onStatusChange?.('error')

    // 认证失败不重连
    if (error && error.toString().includes('Access token')) {
      safeRemove(STORAGE_KEYS.ACCESS_TOKEN)
      safeRemove(STORAGE_KEYS.REFRESH_TOKEN)
      safeRemove(STORAGE_KEYS.USER)
      return
    }

    // 尝试重连
    if (!isManualDisconnect) {
      scheduleReconnect(userId)
    }
  }
}

/**
 * 处理聊天消息（来自 /user/{userId}/queue/chat）
 * 消息类型：TEXT / AUDIO / SYSTEM（主动唤醒）/ EMOTION_UPDATE / ERROR
 */

function handleMessage(message) {
  if (message.type === 'TEXT') {
    callbacks.onTextMessage?.(message)
  } else if (message.type === 'AUDIO') {
    const binary = base64UrlDecode(message.payload?.audioData || '')
    const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
    callbacks.onAudioChunk?.(bytes.buffer)
  } else if (message.type === 'SYSTEM') {
    // 如果是 authSuccess payload（有 success 字段），分发给专门的处理器
    if (message.payload?.success === true && callbacks.onAuthSuccess) {
      callbacks.onAuthSuccess?.(message.payload)
    } else {
      // 主动唤醒/系统通知（WakeUpScheduler 推送）
      callbacks.onSystemMessage?.(message)
    }
  } else if (message.type === 'EMOTION_UPDATE') {
    callbacks.onEmotionUpdate?.(message.payload)
  } else if (message.type === 'ERROR') {
    callbacks.onError?.(message.payload)
  } else if (message.type === 'PEEK_REQUEST') {
    callbacks.onPeekRequest?.(message.payload)
  }
}

/**
 * 处理控制消息（来自 /user/{userId}/queue/control）
 */
function handleControlMessage(message) {
    switch (message.type) {
    case 'SYSTEM':
      break
    case 'PONG':
      // 心跳响应，忽略
      break
    case 'ERROR':
      callbacks.onError?.(message.payload)
      break
    default:
  }
}

/**
 * 重连逻辑（指数退避）
 */
let reconnectTimer = null

function scheduleReconnect(userId) {
  if (isManualDisconnect || _appDisposed) {
    return
  }

  if (reconnectTimer) clearTimeout(reconnectTimer)

  if (reconnectAttempts.value >= MAX_RECONNECT_ATTEMPTS) {
    callbacks.onError?.('连接失败，请刷新页面重试')
    return
  }

  const delay = 1000 * Math.pow(2, reconnectAttempts.value)
  reconnectAttempts.value++

  reconnectTimer = setTimeout(() => {
    connect(userId)
  }, delay)
}

/**
 * 发送聊天消息
 * @param {string} text - 用户输入
 * @param {boolean} enableAudio - 是否启用语音
 * @param {string} [imageUrl] - 图片 URL（可选）
 */
export function sendChat(text, enableAudio = true, imageUrl) {
  if (!stompClient.value || !isConnected.value) {
    return false
  }

  const payload = { text, enableAudio }
  if (imageUrl) payload.imageUrl = imageUrl

  stompClient.value.send(
    WS.SEND_CHAT,  // 后端 @MessageMapping("/chat")
    {},
    JSON.stringify(payload)
  )
  return true
}

/**
 * 断开连接
 */
export function disconnect() {
  isManualDisconnect = true
  if (reconnectTimer) clearTimeout(reconnectTimer)

  if (stompClient.value) {
    stompClient.value.disconnect()
    stompClient.value = null
  }
  isConnected.value = false
  reconnectAttempts.value = 0
  isConnecting.value = false
}

/**
 * 应用级销毁：清除所有残留定时器（在 app 卸载时调用）
 * 与组件级 disconnect() 不同，此函数确保 reconnectTimer 也被清除
 */
export function disposeAppLevel() {
  _appDisposed = true
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

export { isConnected, isConnecting, reconnectAttempts, MAX_RECONNECT_ATTEMPTS }

/**
 * 设置消息回调（绑定实例，防止多组件覆盖）
 * 使用实例校验：只有当前设置回调的组件未被卸载时，回调才有效
 */
const callbackInstanceId = { current: null }

export function setCallbacks(newCallbacks) {
  const instanceId = Symbol('ws-instance')
  callbackInstanceId.current = instanceId
  callbacks = { ...newCallbacks, _instanceId: instanceId }

  return function dispose() {
    if (callbackInstanceId.current === instanceId) {
      callbacks = {
        onTextMessage: null, onAudioChunk: null, onEmotionUpdate: null,
        onSystemMessage: null, onAuthSuccess: null, onPeekRequest: null,
        onError: null, onStatusChange: null, _instanceId: null
      }
      callbackInstanceId.current = null
    }
  }
}
