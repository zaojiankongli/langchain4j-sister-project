import { computed, onUnmounted, shallowRef, watch, type ShallowRef } from 'vue'
import type { PetSocketStatus } from '../ws/petStompClient'
import type { LocalCompanionSettings } from './useLocalCompanionSettings'

type ModelLoadStatus = 'idle' | 'loading' | 'loaded' | 'failed'

export interface LocalCompanionReaction {
  motion: string
}

interface UseLocalCompanionModeOptions {
  socketStatus: ShallowRef<PetSocketStatus>
  isReconnecting: ShallowRef<boolean>
  stageStatus: ShallowRef<ModelLoadStatus>
  settings: Readonly<{ value: LocalCompanionSettings }>
  isActive?: Readonly<{ value: boolean }>
}

const LOCAL_MESSAGE_POOLS = {
  idle: [
    '我现在离线，但还在这里。',
    '暂时连不上服务器，我先安静陪你待会儿。',
    '网络回来后，我们继续聊天。',
  ],
  reconnecting: [
    '我正在尝试重新连接…',
    '连接还在恢复中，稍等我一下。',
    '我在找回服务器的声音。',
  ],
  error: [
    '连接暂时出错了，我先陪你待会儿。',
    '服务器好像走神了，我还在。',
    '现在不能聊天，但你点我我会回应。',
  ],
  tap: [
    '我现在离线，但还能陪你待会儿。',
    '网络回来后我们继续聊天。',
    '点我也有反应哦。',
    '服务器暂时不在，我还在。',
  ],
} as const

const LOCAL_MOTION_POOLS = {
  idle: ['touch_head', 'touch_body', 'main_2', 'home'],
  reconnecting: ['idle', 'nod', 'main_1'],
  error: ['touch_special', 'shake_head', 'idle'],
} as const

function pickRandom<T>(items: readonly T[]): T {
  return items[Math.floor(Math.random() * items.length)]
}

export function useLocalCompanionMode(options: UseLocalCompanionModeOptions) {
  const { socketStatus, isReconnecting, stageStatus, settings } = options
  const messageIndex = shallowRef(0)
  const tappedMessageIndex = shallowRef<number | null>(null)
  let idleRotationTimer: ReturnType<typeof setTimeout> | null = null

  const isActive = computed(() => options.isActive?.value ?? true)

  const isLocalCompanionMode = computed(() => {
    return settings.value.enabled
      && stageStatus.value === 'loaded'
      && (socketStatus.value === 'disconnected' || socketStatus.value === 'error' || isReconnecting.value)
  })

  const activeMessagePool = computed<readonly string[]>(() => {
    if (isReconnecting.value) {
      return LOCAL_MESSAGE_POOLS.reconnecting
    }

    if (socketStatus.value === 'error') {
      return LOCAL_MESSAGE_POOLS.error
    }

    return LOCAL_MESSAGE_POOLS.idle
  })

  const defaultLocalCompanionBubble = computed(() => {
    if (!isLocalCompanionMode.value) {
      return ''
    }

    return activeMessagePool.value[messageIndex.value % activeMessagePool.value.length]
  })

  const activeMotionPool = computed<readonly string[]>(() => {
    if (isReconnecting.value) {
      return LOCAL_MOTION_POOLS.reconnecting
    }

    if (socketStatus.value === 'error') {
      return LOCAL_MOTION_POOLS.error
    }

    return LOCAL_MOTION_POOLS.idle
  })

  const localCompanionBubble = computed(() => {
    if (!isLocalCompanionMode.value) {
      return ''
    }

    if (tappedMessageIndex.value !== null) {
      return LOCAL_MESSAGE_POOLS.tap[tappedMessageIndex.value]
    }

    return defaultLocalCompanionBubble.value
  })

  function handleLocalPetTap(): LocalCompanionReaction | null {
    if (!isLocalCompanionMode.value) {
      tappedMessageIndex.value = null
      return null
    }

    tappedMessageIndex.value = tappedMessageIndex.value === null
      ? 0
      : (tappedMessageIndex.value + 1) % LOCAL_MESSAGE_POOLS.tap.length

    if (!settings.value.tapMotionsEnabled) {
      return null
    }

    return {
      motion: pickRandom(activeMotionPool.value),
    }
  }

  function resetLocalState(): void {
    messageIndex.value = 0
    tappedMessageIndex.value = null
  }

  function clearIdleRotationTimer(): void {
    if (idleRotationTimer) {
      clearTimeout(idleRotationTimer)
      idleRotationTimer = null
    }
  }

  function scheduleIdleRotation(): void {
    clearIdleRotationTimer()

    if (!isLocalCompanionMode.value || !isActive.value || !settings.value.autoRotateMessages) {
      return
    }

    idleRotationTimer = setTimeout(() => {
      messageIndex.value = (messageIndex.value + 1) % activeMessagePool.value.length
      tappedMessageIndex.value = null
      scheduleIdleRotation()
    }, settings.value.messageRotationSeconds * 1000)
  }

  watch(isLocalCompanionMode, (enabled) => {
    clearIdleRotationTimer()

    if (!enabled || !isActive.value || !settings.value.autoRotateMessages) {
      resetLocalState()
      return
    }

    scheduleIdleRotation()
  }, { immediate: true })

  watch(settings, () => {
    clearIdleRotationTimer()
    resetLocalState()

    if (!isLocalCompanionMode.value || !isActive.value || !settings.value.autoRotateMessages) {
      return
    }

    scheduleIdleRotation()
  })

  watch(activeMessagePool, () => {
    messageIndex.value = 0
    tappedMessageIndex.value = null
  })

  watch(isActive, (active) => {
    if (!active) {
      clearIdleRotationTimer()
      return
    }

    if (isLocalCompanionMode.value && settings.value.autoRotateMessages) {
      scheduleIdleRotation()
    }
  })

  onUnmounted(() => {
    clearIdleRotationTimer()
  })

  return {
    isLocalCompanionMode,
    localCompanionBubble,
    handleLocalPetTap,
  }
}
