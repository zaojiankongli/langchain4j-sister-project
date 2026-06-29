<script setup lang="ts">
import { onMounted, onUnmounted, shallowRef, useTemplateRef, watch } from 'vue'
import type { MusicEffectMode } from './petMusicEffects'
import { createAmbientParticlesState, drawAmbientParticles, type AmbientParticlesState } from './effects/ambientParticles'
import { createAuroraEffectState, drawAuroraEffect, type AuroraEffectState } from './effects/auroraEffect'
import { createFirefliesEffectState, drawFirefliesEffect, type FirefliesEffectState } from './effects/firefliesEffect'
import { createMistEffectState, drawMistEffect, type MistEffectState } from './effects/mistEffect'
import { createRainEffectState, drawRainEffect, type RainEffectState } from './effects/rainEffect'
import { createStarsEffectState, drawStarsEffect, type StarsEffectState } from './effects/starsEffect'
import type { EffectFrameContext } from './effects/shared'

const props = defineProps<{
  effectMode: MusicEffectMode
  playing: boolean
}>()

const canvasRef = useTemplateRef<HTMLCanvasElement>('canvas')
const reducedMotion = shallowRef(false)

let ctx: CanvasRenderingContext2D | null = null
let width = 0
let height = 0
let dpr = 1
let animationFrameId: number | null = null
let lastFrameTime = 0
let resizeObserver: ResizeObserver | null = null

let ambientState: AmbientParticlesState | null = null
let rainState: RainEffectState | null = null
let auroraState: AuroraEffectState | null = null
let firefliesState: FirefliesEffectState | null = null
let mistState: MistEffectState | null = null
let starsState: StarsEffectState | null = null

const FRAME_INTERVAL_MS = 1000 / 45
const MAX_DPR = 2

function resizeCanvas(): void {
  const canvas = canvasRef.value
  if (!canvas) return

  const rect = canvas.getBoundingClientRect()
  width = Math.max(1, Math.floor(rect.width))
  height = Math.max(1, Math.floor(rect.height))
  dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR)

  canvas.width = Math.floor(width * dpr)
  canvas.height = Math.floor(height * dpr)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  ctx = canvas.getContext('2d')
  ctx?.setTransform(dpr, 0, 0, dpr, 0, 0)

  ambientState = createAmbientParticlesState(width, height)
  rainState = createRainEffectState(width, height)
  auroraState = createAuroraEffectState(height)
  firefliesState = createFirefliesEffectState(width, height)
  mistState = createMistEffectState(width, height)
  starsState = createStarsEffectState(width, height)
  drawFrame(0)
}

function clearCanvas(context: CanvasRenderingContext2D): void {
  context.clearRect(0, 0, width, height)
}

function drawSoftBackground(context: CanvasRenderingContext2D, time: number, intensity: number): void {
  const pulse = 0.5 + Math.sin(time * 0.0008) * 0.5
  const gradient = context.createRadialGradient(width * 0.52, height * 0.42, 0, width * 0.52, height * 0.42, Math.max(width, height) * 0.64)
  gradient.addColorStop(0, `rgba(255, 222, 190, ${0.06 * intensity + pulse * 0.025})`)
  gradient.addColorStop(0.48, `rgba(110, 156, 255, ${0.035 * intensity})`)
  gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')
  context.fillStyle = gradient
  context.fillRect(0, 0, width, height)
}

function createFrameContext(timestamp: number, deltaSeconds: number): EffectFrameContext {
  const baseIntensity = props.playing ? 1 : 0.42

  return {
    width,
    height,
    time: timestamp,
    deltaSeconds,
    playing: props.playing,
    intensity: baseIntensity,
  }
}

function drawFrame(timestamp: number): void {
  const context = ctx
  if (!context) return

  const deltaSeconds = lastFrameTime > 0 ? Math.min(0.08, (timestamp - lastFrameTime) / 1000) : 0
  lastFrameTime = timestamp
  const frame = createFrameContext(timestamp, deltaSeconds)

  clearCanvas(context)
  drawSoftBackground(context, timestamp, props.effectMode === 'quiet' ? frame.intensity * 0.5 : frame.intensity)

  switch (props.effectMode) {
    case 'rain':
      if (rainState) {
        drawRainEffect(context, rainState, { ...frame, intensity: Math.max(0.72, frame.intensity) })
      }
      break
    case 'aurora':
      if (auroraState) {
        drawAuroraEffect(context, auroraState, { ...frame, intensity: Math.max(0.7, frame.intensity) })
      }
      break
    case 'fireflies':
      if (firefliesState) {
        drawFirefliesEffect(context, firefliesState, { ...frame, intensity: Math.max(0.72, frame.intensity) })
      }
      break
    case 'mist':
      if (mistState) {
        drawMistEffect(context, mistState, { ...frame, intensity: Math.max(0.68, frame.intensity) })
      }
      break
    case 'stars':
      if (starsState) {
        drawStarsEffect(context, starsState, { ...frame, intensity: Math.max(0.8, frame.intensity) })
      }
      break
    case 'breath':
      if (ambientState) {
        drawAmbientParticles(context, ambientState, frame, 'breath')
      }
      break
    case 'quiet':
    default:
      if (ambientState) {
        drawAmbientParticles(context, ambientState, frame, 'quiet')
      }
      break
  }
}

function animate(timestamp: number): void {
  if (document.visibilityState === 'hidden') {
    animationFrameId = requestAnimationFrame(animate)
    return
  }

  if (timestamp - lastFrameTime >= FRAME_INTERVAL_MS) {
    drawFrame(timestamp)
  }

  animationFrameId = requestAnimationFrame(animate)
}

function startAnimation(): void {
  if (animationFrameId !== null) return
  lastFrameTime = 0
  animationFrameId = requestAnimationFrame(animate)
}

function stopAnimation(): void {
  if (animationFrameId === null) return
  cancelAnimationFrame(animationFrameId)
  animationFrameId = null
}

function handleVisibilityChange(): void {
  if (document.visibilityState === 'visible' && !reducedMotion.value) {
    lastFrameTime = 0
  }
}

onMounted(() => {
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  resizeCanvas()

  const canvas = canvasRef.value
  if (canvas?.parentElement) {
    resizeObserver = new ResizeObserver(() => resizeCanvas())
    resizeObserver.observe(canvas.parentElement)
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)
  if (!reducedMotion.value) {
    startAnimation()
  }
})

onUnmounted(() => {
  stopAnimation()
  resizeObserver?.disconnect()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

watch(() => [props.effectMode, props.playing] as const, () => {
  if (reducedMotion.value) {
    drawFrame(performance.now())
  }
})
</script>

<template>
  <canvas ref="canvas" class="music-canvas-effects" aria-hidden="true" />
</template>

<style scoped>
.music-canvas-effects {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
</style>
