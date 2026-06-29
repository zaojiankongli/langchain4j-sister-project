import type { EffectFrameContext } from './shared'
import { randomBetween } from './shared'

interface Firefly {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  glowSize: number
  pulseSpeed: number
  pulsePhase: number
  driftPhase: number
  color: string
}

export interface FirefliesEffectState {
  fireflies: Firefly[]
}

const FIREFLY_COUNT = 26
const COLORS = ['rgba(255, 238, 168, 1)', 'rgba(191, 255, 196, 1)', 'rgba(180, 232, 255, 1)']

function createFirefly(width: number, height: number): Firefly {
  return {
    x: randomBetween(0, width),
    y: randomBetween(height * 0.18, height * 0.82),
    vx: randomBetween(-10, 10),
    vy: randomBetween(-6, 6),
    size: randomBetween(1.2, 3.2),
    glowSize: randomBetween(10, 28),
    pulseSpeed: randomBetween(0.8, 2.2),
    pulsePhase: randomBetween(0, Math.PI * 2),
    driftPhase: randomBetween(0, Math.PI * 2),
    color: COLORS[Math.floor(randomBetween(0, COLORS.length))] ?? COLORS[0],
  }
}

export function createFirefliesEffectState(width: number, height: number): FirefliesEffectState {
  return {
    fireflies: Array.from({ length: FIREFLY_COUNT }, () => createFirefly(width, height)),
  }
}

export function drawFirefliesEffect(context: CanvasRenderingContext2D, state: FirefliesEffectState, frame: EffectFrameContext): void {
  context.save()
  context.globalCompositeOperation = 'lighter'

  for (const firefly of state.fireflies) {
    firefly.driftPhase += frame.deltaSeconds * 0.6
    firefly.x += (firefly.vx + Math.sin(frame.time * 0.0007 + firefly.driftPhase) * 9) * frame.deltaSeconds
    firefly.y += (firefly.vy + Math.cos(frame.time * 0.0005 + firefly.driftPhase) * 6) * frame.deltaSeconds

    if (firefly.x < -firefly.glowSize) firefly.x = frame.width + firefly.glowSize
    if (firefly.x > frame.width + firefly.glowSize) firefly.x = -firefly.glowSize
    if (firefly.y < frame.height * 0.12) firefly.y = frame.height * 0.82
    if (firefly.y > frame.height * 0.88) firefly.y = frame.height * 0.18

    const pulse = 0.45 + 0.55 * Math.sin(frame.time * 0.001 * firefly.pulseSpeed + firefly.pulsePhase)
    const halo = context.createRadialGradient(firefly.x, firefly.y, 0, firefly.x, firefly.y, firefly.glowSize)
    halo.addColorStop(0, firefly.color.replace(', 1)', `, ${0.22 * pulse * frame.intensity})`))
    halo.addColorStop(1, 'rgba(0, 0, 0, 0)')
    context.fillStyle = halo
    context.fillRect(firefly.x - firefly.glowSize, firefly.y - firefly.glowSize, firefly.glowSize * 2, firefly.glowSize * 2)

    context.beginPath()
    context.fillStyle = firefly.color.replace(', 1)', `, ${0.72 * pulse * frame.intensity})`)
    context.arc(firefly.x, firefly.y, firefly.size, 0, Math.PI * 2)
    context.fill()
  }

  context.restore()
}
