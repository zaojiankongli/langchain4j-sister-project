import { onUnmounted } from 'vue'
import type { ShallowRef } from 'vue'
import type { Live2DRendererHandle } from '../live2d/pixiLive2dRenderer'
import type { PetSocketInboundEvent } from '../ws/petStompClient'
import type { Live2DLogEntry } from './usePetDebugLog'
import type { PetRuntimeState } from './usePetRuntimeState'

interface UsePetEventReducerOptions {
  activeModelPath: ShallowRef<string>
  // deprecated: streamText removed — single source of truth is chatMessages
  moodLabel: ShallowRef<string>
  pleasure?: ShallowRef<number>
  arousal?: ShallowRef<number>
  dominance?: ShallowRef<number>
  petRuntimeState: ShallowRef<PetRuntimeState>
  lastSemanticEvent: ShallowRef<string>
  rendererHandle: ShallowRef<Live2DRendererHandle | null>
  appendLog: (entry: Omit<Live2DLogEntry, 'time' | 'renderer'>) => void
}

export function usePetEventReducer(options: UsePetEventReducerOptions) {
  const {
    activeModelPath,
    moodLabel,
    pleasure,
    arousal,
    dominance,
    petRuntimeState,
    lastSemanticEvent,
    rendererHandle,
    appendLog,
  } = options

  let settleTimer: ReturnType<typeof setTimeout> | null = null
  /** Timer that auto-resets expression back to mood-based after durationMs expires. */
  let expressionResetTimer: ReturnType<typeof setTimeout> | null = null

  function clearSettleTimer() {
    if (settleTimer) {
      clearTimeout(settleTimer)
      settleTimer = null
    }
  }

  function clearExpressionResetTimer() {
    if (expressionResetTimer) {
      clearTimeout(expressionResetTimer)
      expressionResetTimer = null
    }
  }

  /**
   * Schedule an auto-reset that returns the Live2D expression to the current
   * mood-based expression after `durationMs` elapses.  Any subsequent
   * PET_EXPRESSION or EMOTION_UPDATE event cancels the pending reset.
   */
  function scheduleExpressionReset(durationMs: number) {
    clearExpressionResetTimer()
    expressionResetTimer = setTimeout(() => {
      expressionResetTimer = null
      // Re-apply the current mood as the "base" expression
      const mood = moodLabel.value.toLowerCase()
      void rendererHandle.value?.setSemanticExpression(mood)
    }, durationMs)
  }

  function handleSocketEvent(event: PetSocketInboundEvent) {
    lastSemanticEvent.value = event.type
    appendLog({
      event: 'socket:event',
      modelPath: activeModelPath.value,
      message: `${event.type} ${JSON.stringify(event.payload ?? {})}`,
    })

    if (event.type === 'TEXT') {
      // Only TEXT events reset the settle timer (they re-arm it)
      clearSettleTimer()
      const content = event.payload?.content ?? ''
      const isComplete = event.payload?.isComplete === true
      // streamText write removed — single source of truth is now chatMessages.appendStreamContent()
      // (driven by usePetSocketEventPipeline). Legacy streamText ref kept for backward compat.

      if (isComplete) {
        petRuntimeState.value = 'settling'
        settleTimer = setTimeout(() => {
          petRuntimeState.value = 'idle'
          settleTimer = null
        }, 900)
      } else if (content) {
        petRuntimeState.value = 'speaking'
      }
    }

    if (event.type === 'EMOTION_UPDATE') {
      moodLabel.value = event.payload?.moodLabel ?? 'neutral'
      // Update PAD values from EMOTION_UPDATE payload (prevents stale display)
      if (pleasure && event.payload?.pleasure !== undefined) pleasure.value = event.payload.pleasure
      if (arousal && event.payload?.arousal !== undefined) arousal.value = event.payload.arousal
      if (dominance && event.payload?.dominance !== undefined) dominance.value = event.payload.dominance
      if (moodLabel.value.toLowerCase() === 'thinking' && petRuntimeState.value !== 'speaking' && petRuntimeState.value !== 'settling') { petRuntimeState.value = 'thinking' }

      // A new mood update should cancel any pending expression auto-reset
      // because the mood itself is the new "base" expression.
      clearExpressionResetTimer()
      void rendererHandle.value?.setSemanticExpression(moodLabel.value.toLowerCase())
    }

    if (event.type === 'PET_EXPRESSION' && event.payload?.expression) {
      lastSemanticEvent.value = `expression:${event.payload.expression}`
      // Cancel any prior auto-reset before applying the new expression
      clearExpressionResetTimer()
      void rendererHandle.value?.setSemanticExpression(event.payload.expression)

      // Schedule auto-reset when the backend provides a durationMs so the
      // Live2D face naturally returns to the mood-based expression afterwards.
      const duration = event.payload.durationMs
      if (duration && duration > 0) {
        scheduleExpressionReset(duration)
      }
    }

    if (event.type === 'PET_MOTION' && event.payload?.motion) {
      const motion = event.payload.motion
      lastSemanticEvent.value = `motion:${motion}`
      // Priority guard: don't downgrade from speaking → thinking.
      // The backend sends "thinking" at chat start and "speaking" when the
      // first LLM token arrives, so thinking should never override speaking.
      if (
        motion === 'thinking'
        && petRuntimeState.value !== 'speaking'
        && petRuntimeState.value !== 'settling'
      ) {
        petRuntimeState.value = 'thinking'
      } else if (motion === 'listening') {
        petRuntimeState.value = 'listening'
      } else if (motion === 'speaking') {
        petRuntimeState.value = 'speaking'
      } else if (motion === 'idle') {
        petRuntimeState.value = 'idle'
      }
      void rendererHandle.value?.playSemanticMotion(motion)
    }

    if (event.type === 'ERROR') {
      petRuntimeState.value = 'error'
    }
  }

  onUnmounted(() => {
    clearSettleTimer()
    clearExpressionResetTimer()
  })

  return {
    handleSocketEvent,
  }
}
