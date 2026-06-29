import type { EffectFrameContext } from './shared'
import { randomBetween } from './shared'

interface AuroraBand {
  baseY: number
  amplitude: number
  wavelength: number
  speed: number
  phase: number
  thickness: number
  alpha: number
  hue: number
}

export interface AuroraEffectState {
  bands: AuroraBand[]
}

export function createAuroraEffectState(height: number): AuroraEffectState {
  const bands: AuroraBand[] = Array.from({ length: 4 }, (_, index) => ({
    baseY: height * (0.18 + index * 0.08),
    amplitude: randomBetween(24, 70),
    wavelength: randomBetween(120, 260),
    speed: randomBetween(0.00028, 0.00072),
    phase: randomBetween(0, Math.PI * 2),
    thickness: randomBetween(90, 180),
    alpha: randomBetween(0.12, 0.24),
    hue: randomBetween(155, 285),
  }))

  return { bands }
}

export function drawAuroraEffect(context: CanvasRenderingContext2D, state: AuroraEffectState, frame: EffectFrameContext): void {
  context.save()
  context.globalCompositeOperation = 'screen'

  for (const [index, band] of state.bands.entries()) {
    context.beginPath()
    const pointCount = Math.max(24, Math.ceil(frame.width / 28))

    for (let pointIndex = 0; pointIndex <= pointCount; pointIndex += 1) {
      const x = (frame.width * pointIndex) / pointCount
      const normalizedX = pointIndex / pointCount
      const wave = Math.sin(frame.time * band.speed + normalizedX * (frame.width / band.wavelength) + band.phase) * band.amplitude
      const secondary = Math.sin(frame.time * band.speed * 1.8 + normalizedX * 7.5 + band.phase * 0.7) * (band.amplitude * 0.42)
      const y = band.baseY + wave + secondary

      if (pointIndex === 0) {
        context.moveTo(x, y)
      } else {
        context.lineTo(x, y)
      }
    }

    context.lineTo(frame.width, frame.height * 0.74)
    context.lineTo(0, frame.height * 0.74)
    context.closePath()

    const gradient = context.createLinearGradient(0, 0, 0, frame.height * 0.8)
    gradient.addColorStop(0, `hsla(${band.hue}, 92%, 72%, ${band.alpha * frame.intensity})`)
    gradient.addColorStop(0.45, `hsla(${band.hue + 24}, 88%, 60%, ${band.alpha * 0.52 * frame.intensity})`)
    gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')
    context.fillStyle = gradient
    context.fill()

    const glow = context.createRadialGradient(frame.width * (0.28 + index * 0.16), band.baseY, 0, frame.width * (0.28 + index * 0.16), band.baseY, band.thickness)
    glow.addColorStop(0, `hsla(${band.hue}, 100%, 78%, ${0.08 * frame.intensity})`)
    glow.addColorStop(1, 'rgba(0, 0, 0, 0)')
    context.fillStyle = glow
    context.fillRect(0, 0, frame.width, frame.height)
  }

  context.restore()
}
