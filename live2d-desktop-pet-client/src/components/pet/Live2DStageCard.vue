<script setup lang="ts">
import { computed, onMounted, onUnmounted, shallowRef, useTemplateRef } from 'vue'
import type { InteractionParticle } from '../../composables/usePetInteraction'

type ModelLoadStatus = 'idle' | 'loading' | 'loaded' | 'failed'

const props = defineProps<{
  petFirst: boolean
  status: ModelLoadStatus
  statusLabel: string
  rendererName: string
  modelPath: string
  loadError?: string
  runtimeState?: string
  moodLabel?: string
  chatLayoutState?: 'connected' | 'disconnected'
  reconnectHint?: string
  streamText?: string
  /** Content from useChatDisplay — replaces legacy streamText for companion bubble. */
  bubbleContent?: string
  /** Whether to show a blinking cursor (during streaming/speaking). */
  showCursor?: boolean
  streamPreviewLabel?: string
  particles?: InteractionParticle[]
}>()

const emit = defineEmits<{
  canvasReady: [canvas: HTMLCanvasElement | null]
  loadSample: []
  testFailure: []
  checkClickThrough: []
  canvasTap: [x: number, y: number]
  startDrag: []
}>()

const canvasRef = useTemplateRef<HTMLCanvasElement>('live2dCanvas')
const frameRef = useTemplateRef<HTMLElement>('frame')
const pointerDown = shallowRef<{ x: number; y: number } | null>(null)
const dragTriggered = shallowRef(false)

const DRAG_THRESHOLD_PX = 8
const DRAG_THRESHOLD_SQUARED = DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX

function onPointerDown(e: PointerEvent) {
  if (!props.petFirst || props.status !== 'loaded') return
  pointerDown.value = { x: e.clientX, y: e.clientY }
  dragTriggered.value = false
}

function onPointerMove(e: PointerEvent) {
  if (!pointerDown.value || dragTriggered.value || !props.petFirst || props.status !== 'loaded') return
  const dx = e.clientX - pointerDown.value.x
  const dy = e.clientY - pointerDown.value.y
  if (dx * dx + dy * dy >= DRAG_THRESHOLD_SQUARED) {
    dragTriggered.value = true
    emit('startDrag')
  }
}

function resetPointerState() {
  pointerDown.value = null
  dragTriggered.value = false
}

function onFrameClick(e: MouseEvent) {
  if (props.petFirst) {
    if (dragTriggered.value) {
      resetPointerState()
      return
    }
    const frame = frameRef.value
    if (!frame) return
    const rect = frame.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    emit('canvasTap', x, y)
    resetPointerState()
  }
}

const companionStateLabel = computed(() => {
  if (props.runtimeState === 'thinking') return '思考中…'
  if (props.runtimeState === 'speaking') return '正在回应'
  if (props.runtimeState === 'settling') return '整理心情'
  if (props.runtimeState === 'listening') return '听你说话'
  if (props.runtimeState === 'error') return '需要看看'
  return ''
})

const companionMoodLabel = computed(() => {
  const mood = props.moodLabel || 'neutral'
  return mood === 'neutral' ? '' : mood
})

const companionBubble = computed(() => {
  return props.bubbleContent?.trim() || props.streamText?.trim() || ''
})

const companionHint = computed(() => props.reconnectHint || '')
const loadFailureHint = computed(() => props.loadError?.trim() || '')
const showLoadFailure = computed(() => props.status === 'failed' && loadFailureHint.value.length > 0)

onMounted(() => {
  emit('canvasReady', canvasRef.value)
})

onUnmounted(() => {
  emit('canvasReady', null)
})
</script>

<template>
  <article class="stage-card" :class="{ 'stage-card--pet-first': props.petFirst }" aria-labelledby="stage-title">
    <div class="card-heading" :class="{ 'card-heading--pet-first': props.petFirst }">
      <div>
        <p v-if="!props.petFirst" class="eyebrow">Stage</p>
        <h2 v-if="!props.petFirst" id="stage-title" class="card-title">Live2D view</h2>
      </div>
      <span v-if="!props.petFirst || props.status === 'loading' || props.status === 'failed'" class="status-pill" :data-state="props.status">
        {{ props.statusLabel }}
      </span>
    </div>
    <div
      ref="frame"
      class="canvas-frame"
      :class="{ 'canvas-frame--interactive': props.petFirst && props.status === 'loaded' }"
      :data-state="props.petFirst ? (props.runtimeState || 'idle') : undefined"
      @click="props.petFirst ? onFrameClick($event) : undefined"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="resetPointerState"
      @pointercancel="resetPointerState"
      @pointerleave="resetPointerState"
    >
      <canvas ref="live2dCanvas" class="live2d-canvas" aria-label="Live2D model canvas"></canvas>
      <div
        v-if="props.petFirst"
        class="companion-overlay"
        :class="`companion-overlay--${props.chatLayoutState ?? 'connected'}`"
        aria-live="polite"
      >
        <div v-if="companionStateLabel || companionMoodLabel || companionHint || showLoadFailure" class="companion-info-stack">
          <span v-if="companionStateLabel" class="companion-chip companion-chip--state">{{ companionStateLabel }}</span>
          <span v-if="companionMoodLabel" class="companion-chip companion-chip--mood">{{ companionMoodLabel }}</span>
          <span v-if="companionHint" class="companion-chip companion-chip--hint">{{ companionHint }}</span>
          <span v-if="showLoadFailure" class="companion-chip companion-chip--error">{{ loadFailureHint }}</span>
        </div>
        <p v-if="companionBubble" class="companion-bubble">
          <span class="companion-bubble-sparkle" aria-hidden="true">✦</span>
          <span class="companion-bubble-text">{{ companionBubble }}</span>
          <span v-if="showCursor" class="companion-cursor" aria-hidden="true">|</span>
        </p>
      </div>
      <!-- Interaction particles -->
      <TransitionGroup name="particle-float">
        <span
          v-for="p in particles"
          :key="p.id"
          class="interaction-particle"
          :style="{ left: `${p.x}px`, top: `${p.y}px` }"
          aria-hidden="true"
        >{{ p.emoji }}</span>
      </TransitionGroup>
    </div>
    <div v-if="!props.petFirst" class="stage-meta">
      <span>Renderer: {{ props.rendererName }}</span>
      <span>Model: {{ props.modelPath }}</span>
    </div>
    <div v-if="!props.petFirst" class="actions" aria-label="Live2D load actions">
      <button class="action" type="button" @click="emit('loadSample')">Load sample</button>
      <button class="action action-secondary" type="button" @click="emit('testFailure')">Test failure</button>
      <button class="action action-secondary" type="button" @click="emit('checkClickThrough')">
        Check click-through
      </button>
    </div>
  </article>
</template>

<style scoped>
.stage-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-6);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}

.stage-card:has(.companion-overlay) {
  border-color: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0.9rem 2.4rem rgba(0, 0, 0, 0.22);
}

.stage-card--pet-first {
  cursor: pointer;
  padding: 0;
  border-color: transparent;
  background: transparent;
  box-shadow: none;
}

.card-heading {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  align-items: start;
}

.card-heading--pet-first {
  align-items: center;
}

.eyebrow {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-caption);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
  color: var(--color-accent);
}

.card-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  line-height: var(--line-height-tight);
  color: var(--color-heading);
}

.status-pill {
  white-space: nowrap;
  border: var(--border-width) solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  padding: var(--space-1) var(--space-3);
  color: var(--color-text-muted);
  background: var(--color-surface-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-size-code);
}

.status-pill[data-state='loaded'] {
  border-color: var(--color-success);
  color: var(--color-success);
}

.status-pill[data-state='failed'] {
  border-color: var(--color-danger);
  color: var(--color-danger);
}

.status-pill[data-state='loading'] {
  border-color: var(--color-warning);
  color: var(--color-warning);
}

.canvas-frame {
  position: relative;
  min-height: var(--size-canvas-height);
  overflow: hidden;
  border-radius: var(--radius-md);
  background:
    linear-gradient(var(--grid-line) var(--border-width), transparent var(--border-width)),
    linear-gradient(90deg, var(--grid-line) var(--border-width), transparent var(--border-width)),
    var(--color-canvas-bg);
  background-size: var(--space-5) var(--space-5);
}

.canvas-frame::after {
  content: '';
  position: absolute;
  inset: var(--space-3);
  border-radius: 999px;
  opacity: 0;
  pointer-events: none;
  transform: translateZ(0);
}

.stage-card--pet-first .canvas-frame {
  background: transparent;
  border: 0;
  box-shadow: none;
}

.canvas-frame--interactive {
  cursor: pointer;
}

.canvas-frame--interactive:hover {
  cursor: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24'%3E%3Ctext y='18' font-size='18'%3E👆%3C/text%3E%3C/svg%3E") 12 2, pointer;
}

/* ── Runtime state glow animations (pet-first mode) ── */

.canvas-frame[data-state='speaking'] {
  box-shadow: 0 0 20px rgba(168, 85, 247, 0.1);
}

.canvas-frame[data-state='listening'] {
  box-shadow: 0 0 20px rgba(59, 130, 246, 0.1);
}

.canvas-frame[data-state='thinking'] {
  box-shadow: 0 0 18px rgba(251, 191, 36, 0.12);
}

.canvas-frame[data-state='speaking']::after {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.2), transparent 62%);
  animation: runtime-glow 1200ms ease-in-out infinite;
}

.canvas-frame[data-state='listening']::after {
  background: radial-gradient(circle, rgba(59, 130, 246, 0.2), transparent 62%);
  animation: runtime-glow 1500ms ease-in-out infinite;
}

@keyframes runtime-glow {
  0%, 100% { opacity: 0.45; }
  50% { opacity: 1; }
}

/* ── Interaction particles ── */

.interaction-particle {
  position: absolute;
  font-size: 1.5rem;
  line-height: 1;
  pointer-events: none;
  user-select: none;
  z-index: 20;
  transform: translate(-50%, -50%);
}

.particle-float-enter-active {
  transition:
    transform 800ms ease-out,
    opacity 800ms ease-out;
}

.particle-float-leave-active {
  transition: none;
}

.particle-float-enter-from {
  transform: translate(-50%, -50%) scale(0.3);
  opacity: 1;
}

.particle-float-enter-to {
  transform: translate(-50%, calc(-50% - 60px)) scale(1.2);
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .particle-float-enter-active {
    transition: opacity 400ms ease;
  }

  .particle-float-enter-from {
    transform: translate(-50%, -50%);
    opacity: 1;
  }

  .particle-float-enter-to {
    transform: translate(-50%, -50%);
    opacity: 0;
  }

  .canvas-frame[data-state='speaking']::after,
  .canvas-frame[data-state='listening']::after {
    animation: none;
  }

  .companion-cursor {
    animation: none;
  }
}

.live2d-canvas {
  display: block;
  width: 100%;
  height: var(--size-canvas-height);
}

.companion-overlay {
  position: absolute;
  left: var(--space-3);
  right: var(--space-3);
  bottom: 4.35rem;
  display: grid;
  gap: var(--space-2);
  justify-items: start;
  pointer-events: none;
}

.companion-overlay--connected {
  bottom: 4.35rem;
}

.companion-overlay--disconnected {
  bottom: 4.35rem;
}

.companion-info-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: 86%;
}

.companion-overlay--disconnected .companion-info-stack {
  max-width: 100%;
}

.companion-chip {
  padding: 3px 10px;
  border: 1px solid rgba(255, 196, 214, 0.38);
  border-radius: 999px;
  color: #7d5964;
  background: linear-gradient(135deg, rgba(255, 252, 246, 0.86), rgba(255, 225, 237, 0.74));
  font-size: 11px;
  line-height: 1.35;
  letter-spacing: 0.02em;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  box-shadow: 0 4px 16px rgba(255, 159, 189, 0.14);
}

.companion-chip--hint {
  color: rgba(125, 89, 100, 0.72);
  background: rgba(255, 250, 245, 0.68);
}

.companion-chip--error {
  color: #991b1b;
  border-color: rgba(248, 113, 113, 0.32);
  background: rgba(254, 226, 226, 0.92);
}

.companion-bubble {
  position: relative;
  max-width: min(23rem, 72%);
  max-height: 8.8rem;
  overflow-y: auto;
  margin: 0;
  padding: 11px 17px 11px 28px;
  border: 1px solid rgba(255, 185, 208, 0.36);
  border-radius: 20px 20px 20px 6px;
  color: #5b454d;
  background:
    radial-gradient(circle at 12% 18%, rgba(255, 255, 255, 0.95), transparent 32%),
    linear-gradient(135deg, rgba(255, 253, 248, 0.94), rgba(255, 227, 239, 0.86));
  font-size: 13px;
  line-height: 1.55;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow:
    0 10px 26px rgba(255, 143, 184, 0.16),
    0 2px 8px rgba(84, 48, 63, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.companion-overlay--disconnected .companion-bubble {
  max-width: min(23rem, 72%);
  max-height: 8.8rem;
  padding: 11px 17px 11px 28px;
  border-radius: 20px 20px 20px 6px;
  font-size: 13px;
  line-height: 1.55;
}

.companion-bubble::after {
  content: '';
  position: absolute;
  left: 14px;
  bottom: -7px;
  width: 14px;
  height: 14px;
  border-right: 1px solid rgba(255, 185, 208, 0.32);
  border-bottom: 1px solid rgba(255, 185, 208, 0.32);
  background: rgba(255, 236, 244, 0.9);
  transform: rotate(45deg);
}

.companion-overlay--disconnected .companion-bubble::after {
  left: 12px;
}

.companion-bubble-sparkle {
  position: absolute;
  left: 11px;
  top: 10px;
  color: #ff8fb3;
  font-size: 10px;
}

.companion-bubble-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.companion-cursor {
  display: inline-block;
  margin-left: 1px;
  font-weight: 300;
  color: #ff8fb3;
  animation: cursor-blink 800ms step-end infinite;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.stage-meta {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  color: var(--color-text-muted);
  background: var(--color-surface-subtle);
  font-size: var(--font-size-small);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.actions--quiet {
  opacity: 0.82;
}

.action {
  border: var(--border-width) solid var(--color-accent);
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-4);
  color: var(--color-action-text);
  background: var(--color-accent);
  font: inherit;
  cursor: pointer;
  transition:
    transform var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}

.action:hover {
  transform: translateY(var(--motion-lift));
  box-shadow: var(--shadow-action);
}

.action:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

.action-secondary {
  color: var(--color-accent);
  background: transparent;
}
</style>
