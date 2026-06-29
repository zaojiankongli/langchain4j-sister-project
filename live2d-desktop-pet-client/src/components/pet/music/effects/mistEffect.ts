import type { EffectFrameContext } from './shared'
import { randomBetween } from './shared'

interface MistBlob {
  x: number
  y: number
  radiusX: number
  radiusY: number
  speedX: number
  speedY: number
  alpha: number
  phase: number
}

export interface MistEffectState {
  blobs: MistBlob[]
}

export function createMistEffectState(width: number, height: number): MistEffectState {
  return {
    blobs: Array.from({ length: 6 }, () => ({
      x: randomBetween(width * 0.1, width * 0.9),
      y: randomBetween(height * 0.22, height * 0.82),
      radiusX: randomBetween(width * 0.12, width * 0.26),
      radiusY: randomBetween(height * 0.08, height * 0.16),
      speedX: randomBetween(-6, 6),
      speedY: randomBetween(-2, 2),
      alpha: randomBetween(0.05, 0.11),
      phase: randomBetween(0, Math.PI * 2),
    })),
  }
}

export function drawMistEffect(context: CanvasRenderingContext2D, state: MistEffectState, frame: EffectFrameContext): void {
  context.save()
  context.globalCompositeOperation = 'screen'

  for (const blob of state.blobs) {
    blob.x += (blob.speedX + Math.sin(frame.time * 0.00026 + blob.phase) * 2.5) * frame.deltaSeconds
    blob.y += (blob.speedY + Math.cos(frame.time * 0.00018 + blob.phase) * 1.5) * frame.deltaSeconds

    if (blob.x < -blob.radiusX) blob.x = frame.width + blob.radiusX
    if (blob.x > frame.width + blob.radiusX) blob.x = -blob.radiusX
    if (blob.y < frame.height * 0.12) blob.y = frame.height * 0.82
    if (blob.y > frame.height * 0.9) blob.y = frame.height * 0.24

    const mist = context.createRadialGradient(blob.x, blob.y, 0, blob.x, blob.y, blob.radiusX)
    mist.addColorStop(0, `rgba(222, 236, 248, ${blob.alpha * frame.intensity})`)
    mist.addColorStop(0.6, `rgba(182, 201, 218, ${blob.alpha * 0.55 * frame.intensity})`)
    mist.addColorStop(1, 'rgba(0, 0, 0, 0)')
    context.fillStyle = mist
    context.beginPath()
    context.ellipse(blob.x, blob.y, blob.radiusX, blob.radiusY, 0, 0, Math.PI * 2)
    context.fill()
  }

  const haze = context.createLinearGradient(0, frame.height * 0.14, 0, frame.height)
  haze.addColorStop(0, 'rgba(0, 0, 0, 0)')
  haze.addColorStop(0.45, `rgba(216, 228, 239, ${0.06 * frame.intensity})`)
  haze.addColorStop(1, `rgba(174, 196, 214, ${0.1 * frame.intensity})`)
  context.fillStyle = haze
  context.fillRect(0, 0, frame.width, frame.height)
  context.restore()
}
