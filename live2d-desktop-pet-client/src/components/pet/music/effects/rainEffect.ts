import type { EffectFrameContext } from './shared'
import { randomBetween } from './shared'

interface RainDrop {
  x: number
  y: number
  vx: number
  vy: number
  length: number
  alpha: number
  width: number
  layer: number
  shimmer: number
}

interface RainTrail {
  x: number
  y: number
  length: number
  alpha: number
  decay: number
}

interface Ripple {
  x: number
  y: number
  radius: number
  maxRadius: number
  alpha: number
  decay: number
}

export interface RainEffectState {
  rainDrops: RainDrop[]
  rainTrails: RainTrail[]
  ripples: Ripple[]
}

const RAIN_COUNT = 220
const MAX_TRAILS = 70
const MAX_RIPPLES = 36

function resetRainDrop(drop: RainDrop, width: number, height: number, startAbove = false): void {
  drop.layer = Math.floor(randomBetween(0, 3))
  const layerScale = drop.layer === 0 ? 0.72 : drop.layer === 1 ? 1 : 1.34
  drop.x = randomBetween(-width * 0.18, width * 1.18)
  drop.y = startAbove ? randomBetween(-height * 0.8, -12) : randomBetween(-24, height)
  drop.vx = randomBetween(-64, -24) * layerScale
  drop.vy = randomBetween(260, 620) * layerScale
  drop.length = randomBetween(22, 74) * layerScale
  drop.alpha = randomBetween(0.18, 0.52) * layerScale
  drop.width = drop.layer === 2 ? 1.55 : drop.layer === 1 ? 1.15 : 0.85
  drop.shimmer = randomBetween(0.75, 1.25)
}

export function createRainEffectState(width: number, height: number): RainEffectState {
  const rainDrops: RainDrop[] = []
  for (let index = 0; index < RAIN_COUNT; index += 1) {
    const drop: RainDrop = { x: 0, y: 0, vx: 0, vy: 0, length: 0, alpha: 0, width: 1, layer: 0, shimmer: 1 }
    resetRainDrop(drop, width, height)
    rainDrops.push(drop)
  }

  return {
    rainDrops,
    rainTrails: [],
    ripples: [],
  }
}

function drawRainVeil(context: CanvasRenderingContext2D, frame: EffectFrameContext): void {
  const sweep = (frame.time * 0.018) % Math.max(frame.width, 1)
  const veil = context.createLinearGradient(frame.width * 0.18, 0, frame.width * 0.86, frame.height)
  veil.addColorStop(0, `rgba(168, 197, 224, ${0.02 * frame.intensity})`)
  veil.addColorStop(0.38, `rgba(205, 229, 248, ${0.09 * frame.intensity})`)
  veil.addColorStop(1, `rgba(132, 165, 196, ${0.01 * frame.intensity})`)

  context.fillStyle = veil
  context.fillRect(0, 0, frame.width, frame.height)

  context.save()
  context.globalCompositeOperation = 'lighter'
  context.translate(sweep - frame.width, 0)
  context.rotate(-0.18)
  context.fillStyle = `rgba(210, 232, 255, ${0.035 * frame.intensity})`
  for (let index = 0; index < 4; index += 1) {
    context.fillRect(index * frame.width * 0.48, -frame.height * 0.2, frame.width * 0.08, frame.height * 1.4)
  }
  context.restore()
}

function spawnRainTrail(state: RainEffectState, drop: RainDrop): void {
  if (state.rainTrails.length >= MAX_TRAILS || Math.random() > 0.1) {
    return
  }

  state.rainTrails.push({
    x: drop.x,
    y: drop.y - randomBetween(4, 18),
    length: randomBetween(34, 120),
    alpha: randomBetween(0.08, 0.2),
    decay: randomBetween(0.26, 0.58),
  })
}

function spawnRipple(state: RainEffectState, drop: RainDrop, height: number): void {
  if (state.ripples.length >= MAX_RIPPLES || drop.layer === 0 || Math.random() > 0.28) {
    return
  }

  state.ripples.push({
    x: drop.x,
    y: height - randomBetween(4, 22),
    radius: randomBetween(1.2, 2.4),
    maxRadius: randomBetween(9, 26) * (drop.layer === 2 ? 1.16 : 1),
    alpha: randomBetween(0.12, 0.26),
    decay: randomBetween(0.9, 1.4),
  })
}

function drawRainTrails(context: CanvasRenderingContext2D, state: RainEffectState, frame: EffectFrameContext): void {
  context.save()
  context.lineCap = 'round'
  context.strokeStyle = `rgba(164, 200, 230, ${0.34 * frame.intensity})`

  for (let index = state.rainTrails.length - 1; index >= 0; index -= 1) {
    const trail = state.rainTrails[index]
    trail.alpha -= trail.decay * frame.deltaSeconds
    trail.y += 28 * frame.deltaSeconds

    if (trail.alpha <= 0) {
      state.rainTrails.splice(index, 1)
      continue
    }

    context.globalAlpha = trail.alpha * frame.intensity
    context.lineWidth = 0.7
    context.beginPath()
    context.moveTo(trail.x, trail.y)
    context.lineTo(trail.x - 8, Math.min(frame.height, trail.y + trail.length))
    context.stroke()
  }

  context.restore()
}

function drawRipples(context: CanvasRenderingContext2D, state: RainEffectState, frame: EffectFrameContext): void {
  context.save()
  context.strokeStyle = `rgba(202, 229, 248, ${0.42 * frame.intensity})`

  for (let index = state.ripples.length - 1; index >= 0; index -= 1) {
    const ripple = state.ripples[index]
    ripple.radius += 26 * frame.deltaSeconds
    ripple.alpha -= ripple.decay * frame.deltaSeconds

    if (ripple.radius > ripple.maxRadius || ripple.alpha <= 0) {
      state.ripples.splice(index, 1)
      continue
    }

    context.globalAlpha = ripple.alpha * frame.intensity
    context.lineWidth = 0.8
    context.beginPath()
    context.ellipse(ripple.x, ripple.y, ripple.radius, ripple.radius * 0.22, 0, 0, Math.PI * 2)
    context.stroke()
  }

  context.restore()
}

export function drawRainEffect(context: CanvasRenderingContext2D, state: RainEffectState, frame: EffectFrameContext): void {
  const speedMultiplier = frame.playing ? 1 : 0.64
  const windPhase = Math.sin(frame.time * 0.00026) * 18

  drawRainVeil(context, frame)
  drawRainTrails(context, state, frame)

  context.save()
  context.lineCap = 'round'
  context.globalCompositeOperation = 'lighter'
  for (let layer = 0; layer < 3; layer += 1) {
    context.beginPath()
    const layerAlpha = layer === 0 ? 0.46 : layer === 1 ? 0.72 : 1
    context.strokeStyle = `rgba(195, 224, 246, ${0.5 * layerAlpha * frame.intensity})`
    context.lineWidth = layer === 2 ? 1.45 : layer === 1 ? 1.1 : 0.75

    for (const drop of state.rainDrops) {
      if (drop.layer !== layer) continue
      drop.x += (drop.vx + windPhase) * frame.deltaSeconds * speedMultiplier
      drop.y += drop.vy * frame.deltaSeconds * speedMultiplier

      if (drop.y > frame.height - randomBetween(0, 26)) {
        spawnRainTrail(state, drop)
        spawnRipple(state, drop, frame.height)
      }

      if (drop.y > frame.height + drop.length || drop.x < -frame.width * 0.22) {
        resetRainDrop(drop, frame.width, frame.height, true)
        drop.y = -drop.length
        drop.x = randomBetween(0, frame.width * 1.18)
      }

      const slant = drop.length * (drop.vx / drop.vy)
      const tailAlpha = 0.78 + Math.sin(frame.time * 0.0022 + drop.shimmer) * 0.22
      context.globalAlpha = drop.alpha * tailAlpha
      context.moveTo(drop.x, drop.y)
      context.lineTo(drop.x + slant, drop.y + drop.length)
    }
    context.stroke()
  }
  context.restore()

  drawRipples(context, state, frame)
}
