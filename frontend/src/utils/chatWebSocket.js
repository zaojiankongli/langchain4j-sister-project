import { ref } from 'vue'
import Stomp from 'stompjs'
import SockJS from 'sockjs-client'
import { STORAGE_KEYS } from '@/config/storage'
import { WS } from '@/config/api'

// STOMP 状态（模块级单例）
const stompClient = ref(null)
const isConnected = ref(false)
const isConnecting = ref(false)
let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 5
let isManualDisconnect = false


// 消息回调（由 ChatWindow 设置，绑定实例防止覆盖）
let callbacks = {
  onTextMessage: null,
  onAudioChunk: null,
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

  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
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
    console.warn('WebSocket closed:', event)
    isConnected.value = false
    isConnecting.value = false
    // 通知状态变化
    callbacks.onStatusChange?.('disconnected')
  }

  // 创建 STOMP 客户端
  stompClient.value = Stomp.over(socket)
  
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
    reconnectAttempts = 0

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
      localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
      localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
      localStorage.removeItem(STORAGE_KEYS.USER)
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
 */
function handleMessage(message) {
  if (message.type === 'TEXT') {
    callbacks.onTextMessage?.(message)
  } else if (message.type === 'AUDIO') {
    const binary = atob(message.payload?.audioData || '')
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i)
    }
    callbacks.onAudioChunk?.(bytes.buffer)
  } else if (message.type === 'ERROR') {
    callbacks.onError?.(message.payload)
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
  if (isManualDisconnect) {
    return
  }

  if (reconnectTimer) clearTimeout(reconnectTimer)

  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    callbacks.onError?.('连接失败，请刷新页面重试')
    return
  }

  const delay = 1000 * Math.pow(2, reconnectAttempts)
  reconnectAttempts++

  reconnectTimer = setTimeout(() => {
    connect(userId)
  }, delay)
}

/**
 * 发送聊天消息
 * @param {string} text - 用户输入
 * @param {boolean} enableAudio - 是否启用语音
 */
export function sendChat(text, enableAudio = true) {
  if (!stompClient.value || !isConnected.value) {
    return false
  }

  stompClient.value.send(
    WS.SEND_CHAT,  // 后端 @MessageMapping("/chat")
    {},
    JSON.stringify({ text, enableAudio })
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
  reconnectAttempts = 0
  isConnecting.value = false
}

/**
 * 设置消息回调（绑定实例，防止多组件覆盖）
 * 使用实例校验：只有当前设置回调的组件未被卸载时，回调才有效
 */
const callbackInstanceId = { current: null }

export function setCallbacks(newCallbacks) {
  const instanceId = Symbol('ws-instance')
  callbackInstanceId.current = instanceId
  callbacks = { ...newCallbacks, _instanceId: instanceId }

  // 返回取消函数，组件卸载时调用
  return function dispose() {
    if (callbackInstanceId.current === instanceId) {
      callbacks = { onTextMessage: null, onAudioChunk: null, onError: null, onStatusChange: null, _instanceId: null }
      callbackInstanceId.current = null
    }
  }
}
