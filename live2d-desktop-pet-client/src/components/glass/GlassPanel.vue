<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  tag?: keyof HTMLElementTagNameMap
  dismissible?: boolean
  side?: 'left' | 'right'
}>(), {
  tag: 'div',
  dismissible: true,
  side: 'right',
})

const emit = defineEmits<{
  backdropClick: []
}>()

const rootTag = computed(() => props.tag)
</script>

<template>
  <component :is="rootTag" class="glass-panel" :class="`glass-panel--${props.side}`">
    <div
      v-if="props.dismissible"
      class="glass-backdrop"
      aria-hidden="true"
      @click="emit('backdropClick')"
    />
    <div class="glass-tint" aria-hidden="true" />
    <div class="glass-content">
      <slot />
    </div>
  </component>
</template>

<style scoped>
.glass-panel {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  contain: layout style paint;
  background: var(--color-surface-raised);
  box-shadow: var(--shadow-panel);
  box-sizing: border-box;
}

.glass-panel--right {
  border-left: 1px solid var(--color-border);
}

.glass-panel--left {
  border-right: 1px solid var(--color-border);
}

.glass-backdrop,
.glass-tint,
.glass-content {
  position: absolute;
  inset: 0;
}

.glass-backdrop {
  z-index: 0;
  backdrop-filter: blur(var(--glass-blur, 12px));
  -webkit-backdrop-filter: blur(var(--glass-blur, 12px));
  pointer-events: auto;
}

.glass-tint {
  z-index: 0;
  background: rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.glass-content {
  z-index: 1;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
  pointer-events: auto;
}

@media (prefers-reduced-motion: reduce) {
  .glass-backdrop {
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
