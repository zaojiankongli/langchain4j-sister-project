<script setup lang="ts">
import { onUnmounted, shallowRef } from 'vue'

const props = defineProps<{
  progress: number
}>()

const emit = defineEmits<{
  seek: [progress: number]
}>()

const isDragging = shallowRef(false)
const suppressClick = shallowRef(false)
let barRef: HTMLButtonElement | null = null

function calcProgress(event: MouseEvent, element: HTMLElement): number {
  const rect = element.getBoundingClientRect()
  return Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width))
}

function handleClick(event: MouseEvent): void {
  if (suppressClick.value) {
    suppressClick.value = false
    return
  }
  if (isDragging.value) return
  const el = event.currentTarget as HTMLButtonElement | null
  if (!el) return
  emit('seek', calcProgress(event, el))
}

function handleMouseDown(event: MouseEvent): void {
  if (event.button !== 0) return // 仅左键
  event.preventDefault()
  isDragging.value = true
  barRef = event.currentTarget as HTMLButtonElement
  // 拖拽开始时立即 seek 到鼠标位置
  emit('seek', calcProgress(event, barRef))
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseup', handleMouseUp, { once: true })
}

function handleMouseMove(event: MouseEvent): void {
  if (!isDragging.value || !barRef) return
  emit('seek', calcProgress(event, barRef))
}

function handleMouseUp(): void {
  suppressClick.value = true
  isDragging.value = false
  barRef = null
  window.removeEventListener('mousemove', handleMouseMove)
}

function handleKeydown(event: KeyboardEvent): void {
  const step = event.shiftKey ? 0.1 : 0.05

  switch (event.key) {
    case 'ArrowLeft':
    case 'ArrowDown':
      event.preventDefault()
      emit('seek', Math.max(0, props.progress - step))
      break
    case 'ArrowRight':
    case 'ArrowUp':
      event.preventDefault()
      emit('seek', Math.min(1, props.progress + step))
      break
    case 'Home':
      event.preventDefault()
      emit('seek', 0)
      break
    case 'End':
      event.preventDefault()
      emit('seek', 1)
      break
  }
}

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove)
})
</script>

<template>
  <button
    class="progress"
    :class="{ 'progress--dragging': isDragging }"
    type="button"
    role="slider"
    aria-label="调整播放进度"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-valuenow="Math.round(Math.min(1, Math.max(0, props.progress)) * 100)"
    @click="handleClick"
    @mousedown="handleMouseDown"
    @keydown="handleKeydown"
  >
    <span class="progress-track" />
    <span class="progress-fill" :style="{ transform: `scaleX(${Math.min(1, Math.max(0, props.progress))})` }" />
    <span class="progress-thumb" :style="{ left: `${Math.min(100, Math.max(0, props.progress * 100))}%` }" />
  </button>
</template>

<style scoped>
.progress {
  position: relative;
  width: 100%;
  height: 0.5rem;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.progress-track,
.progress-fill {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 2px;
  border-radius: 2px;
  transform: translateY(-50%);
}

.progress-track {
  background: rgba(255, 255, 255, 0.1);
}

.progress-fill {
  right: auto;
  width: 100%;
  transform-origin: left center;
  background: linear-gradient(90deg, rgba(134, 198, 255, 0.9), rgba(255, 184, 126, 0.9));
}

.progress-thumb {
  position: absolute;
  top: 50%;
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 0.15rem 0.5rem rgba(0, 0, 0, 0.3);
  transform: translate(-50%, -50%);
  opacity: 0;
  transition: opacity 120ms ease;
}

.progress:hover .progress-thumb {
  opacity: 1;
}

.progress--dragging .progress-thumb {
  opacity: 1;
  transform: translate(-50%, -50%) scale(1.3);
}
</style>
