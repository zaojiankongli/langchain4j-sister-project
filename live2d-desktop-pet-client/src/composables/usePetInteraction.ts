import { onUnmounted, shallowRef, type ShallowRef } from 'vue'
import type { Live2DRendererHandle } from '../live2d/pixiLive2dRenderer'

/* ------------------------------------------------------------------ */
/*  Particle types                                                     */
/* ------------------------------------------------------------------ */

export interface InteractionParticle {
  id: number
  x: number
  y: number
  emoji: string
}

/* ------------------------------------------------------------------ */
/*  Reaction pool                                                      */
/* ------------------------------------------------------------------ */

const TAP_EMOJIS = ['❤️', '✨', '💕', '⭐', '💖']
const DBL_TAP_MOTIONS = ['greet', 'wave', 'nod'] as const

let nextParticleId = 0

function pickRandom<T>(arr: readonly T[]): T {
  return arr[Math.floor(Math.random() * arr.length)]
}

/* ------------------------------------------------------------------ */
/*  Composable                                                         */
/* ------------------------------------------------------------------ */

export function usePetInteraction(rendererHandle: ShallowRef<Live2DRendererHandle | null>) {
  const particles = shallowRef<InteractionParticle[]>([])
  const tapCount = shallowRef(0)

  let dblTapTimer: ReturnType<typeof setTimeout> | null = null
  let lastTapTime = 0

  const pendingTimers = new Set<ReturnType<typeof setTimeout>>()

  function trackedSetTimeout(fn: () => void, ms: number): ReturnType<typeof setTimeout> {
    const id = setTimeout(() => {
      pendingTimers.delete(id)
      fn()
    }, ms)
    pendingTimers.add(id)
    return id
  }

  function clearAllTimers(): void {
    for (const id of pendingTimers) clearTimeout(id)
    pendingTimers.clear()
    if (dblTapTimer) {
      clearTimeout(dblTapTimer)
      dblTapTimer = null
    }
  }

  /**
   * Remove a particle after its animation completes.
   */
  function removeParticle(id: number) {
    particles.value = particles.value.filter((p) => p.id !== id)
  }

  /**
   * Spawn a particle at the given position (relative to the canvas frame).
   */
  function spawnParticle(x: number, y: number, emoji?: string) {
    const id = ++nextParticleId
    const particle: InteractionParticle = {
      id,
      x,
      y,
      emoji: emoji ?? pickRandom(TAP_EMOJIS),
    }
    particles.value = [...particles.value, particle]
    // Auto-remove after animation (800ms)
    trackedSetTimeout(() => removeParticle(id), 800)
  }

  /**
   * Handle a tap/click on the Live2D canvas.
   * Single tap: spawn a particle.
   * Double tap (within 300ms): trigger a random motion on the model.
   */
  function handleCanvasTap(x: number, y: number) {
    const now = Date.now()
    tapCount.value += 1

    if (now - lastTapTime < 300) {
      // Double tap detected → play a random motion
      if (dblTapTimer) {
        clearTimeout(dblTapTimer)
        dblTapTimer = null
      }
      const motion = pickRandom(DBL_TAP_MOTIONS)
      void rendererHandle.value?.playSemanticMotion(motion)
      // Spawn a bigger particle for double tap
      spawnParticle(x, y, '💖')
      lastTapTime = 0
    } else {
      // Potential single tap — wait briefly to see if a double tap follows
      lastTapTime = now
      spawnParticle(x, y)

      dblTapTimer = trackedSetTimeout(() => {
        // Confirmed single tap — play a happy expression briefly
        dblTapTimer = null
        void rendererHandle.value?.setSemanticExpression('happy')
        // Auto-reset expression after 2 seconds
        trackedSetTimeout(() => {
          // Let the mood-based expression take over naturally
          void rendererHandle.value?.setSemanticExpression('neutral')
        }, 2000)
      }, 300)
    }
  }

  onUnmounted(() => {
    clearAllTimers()
    particles.value = []
  })

  return {
    particles,
    tapCount,
    handleCanvasTap,
  }
}
