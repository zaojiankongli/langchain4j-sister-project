import type { EffectFrameContext } from './shared'
import { randomBetween } from './shared'

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  alpha: number
  phase: number
}

export interface AmbientParticlesState {
  particles: Particle[]
}

const PARTICLE_COUNT = 46

function resetParticle(particle: Particle, width: number, height: number): void {
  particle.x = randomBetween(0, width)
  particle.y = randomBetween(0, height)
  particle.vx = randomBetween(-3, 8)
  particle.vy = randomBetween(-10, -2)
  particle.size = randomBetween(0.7, 2.4)
  particle.alpha = randomBetween(0.12, 0.42)
  particle.phase = randomBetween(0, Math.PI * 2)
}

export function createAmbientParticlesState(width: number, height: number): AmbientParticlesState {
  const particles: Particle[] = []
  for (let index = 0; index < PARTICLE_COUNT; index += 1) {
    const particle: Particle = { x: 0, y: 0, vx: 0, vy: 0, size: 1, alpha: 0.2, phase: 0 }
    resetParticle(particle, width, height)
    particles.push(particle)
  }

  return { particles }
}

export function drawAmbientParticles(
  context: CanvasRenderingContext2D,
  state: AmbientParticlesState,
  frame: EffectFrameContext,
  variant: 'quiet' | 'breath',
): void {
  context.fillStyle = 'rgba(255, 244, 232, 0.72)'
  for (const particle of state.particles) {
    particle.x += particle.vx * frame.deltaSeconds * (frame.playing ? 1.4 : 0.55)
    particle.y += particle.vy * frame.deltaSeconds * (frame.playing ? 1.15 : 0.42)

    if (particle.y < -8 || particle.x > frame.width + 8 || particle.x < -8) {
      resetParticle(particle, frame.width, frame.height)
      particle.y = frame.height + randomBetween(4, 40)
    }

    const alpha = particle.alpha * frame.intensity * (0.72 + Math.sin(frame.time * 0.001 + particle.phase) * 0.28)
    context.globalAlpha = Math.max(0, variant === 'breath' ? alpha * 1.25 : alpha * 0.72)
    context.beginPath()
    context.arc(Math.round(particle.x), Math.round(particle.y), particle.size, 0, Math.PI * 2)
    context.fill()
  }

  context.globalAlpha = 1
}
