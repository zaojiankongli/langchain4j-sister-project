import type { EffectFrameContext } from './shared'
import { randomBetween } from './shared'

interface Star {
  x: number
  y: number
  size: number
  opacity: number
  twinkleSpeed: number
  twinklePhase: number
}

interface Meteor {
  x: number
  y: number
  angle: number
  speed: number
  life: number
  maxLife: number
  length: number
}

export interface StarsEffectState {
  stars: Star[]
  meteors: Meteor[]
  meteorCooldown: number
}

export function createStarsEffectState(width: number, height: number): StarsEffectState {
  const starCount = Math.max(90, Math.floor((width * height) / 9000))

  return {
    stars: Array.from({ length: starCount }, () => ({
      x: Math.random() * width,
      y: Math.random() * height * 0.72,
      size: Math.random() * 1.8 + 0.3,
      opacity: Math.random() * 0.8 + 0.2,
      twinkleSpeed: Math.random() * 0.02 + 0.005,
      twinklePhase: Math.random() * Math.PI * 2,
    })),
    meteors: [],
    meteorCooldown: randomBetween(3, 8),
  }
}

function spawnMeteor(state: StarsEffectState, width: number, height: number): void {
  state.meteors.push({
    x: randomBetween(width * 0.1, width * 0.9),
    y: randomBetween(0, height * 0.22),
    angle: randomBetween(Math.PI * 0.18, Math.PI * 0.34),
    speed: randomBetween(380, 720),
    life: 0,
    maxLife: randomBetween(0.55, 1.1),
    length: randomBetween(60, 140),
  })
}

export function drawStarsEffect(context: CanvasRenderingContext2D, state: StarsEffectState, frame: EffectFrameContext): void {
  for (const star of state.stars) {
    const alpha = star.opacity * (0.6 + 0.4 * Math.sin(frame.time * star.twinkleSpeed + star.twinklePhase))
    context.fillStyle = `rgba(230, 238, 255, ${alpha * frame.intensity})`
    context.beginPath()
    context.arc(star.x, star.y, star.size, 0, Math.PI * 2)
    context.fill()
  }

  state.meteorCooldown -= frame.deltaSeconds * (frame.playing ? 1 : 0.5)
  if (state.meteorCooldown <= 0) {
    spawnMeteor(state, frame.width, frame.height)
    state.meteorCooldown = randomBetween(4, 10)
  }

  context.save()
  context.globalCompositeOperation = 'screen'
  for (let index = state.meteors.length - 1; index >= 0; index -= 1) {
    const meteor = state.meteors[index]
    meteor.life += frame.deltaSeconds
    meteor.x += Math.cos(meteor.angle) * meteor.speed * frame.deltaSeconds
    meteor.y += Math.sin(meteor.angle) * meteor.speed * frame.deltaSeconds

    if (meteor.life > meteor.maxLife) {
      state.meteors.splice(index, 1)
      continue
    }

    const alpha = (1 - meteor.life / meteor.maxLife) * 0.85 * frame.intensity
    const endX = meteor.x - Math.cos(meteor.angle) * meteor.length
    const endY = meteor.y - Math.sin(meteor.angle) * meteor.length
    const gradient = context.createLinearGradient(meteor.x, meteor.y, endX, endY)
    gradient.addColorStop(0, `rgba(255, 246, 228, ${alpha})`)
    gradient.addColorStop(0.4, `rgba(201, 225, 255, ${alpha * 0.55})`)
    gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')
    context.strokeStyle = gradient
    context.lineWidth = 1.4
    context.beginPath()
    context.moveTo(meteor.x, meteor.y)
    context.lineTo(endX, endY)
    context.stroke()
  }
  context.restore()
}
