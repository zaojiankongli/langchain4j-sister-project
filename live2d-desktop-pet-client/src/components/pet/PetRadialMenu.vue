<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

interface MenuItem {
  id: string
  label: string
  iconSvg: string
  side: 'left' | 'right'
  offsetY: number
}

defineProps<{
  visible: boolean
  activeItem?: string | null
}>()

const emit = defineEmits<{
  menuItemClick: [id: string]
}>()

const menuItems: MenuItem[] = [
  {
    id: 'chat',
    label: '聊天',
    iconSvg: '<svg width="18" height="18" viewBox="0 0 16 16" fill="none"><path d="M2 3a1 1 0 011-1h10a1 1 0 011 1v7a1 1 0 01-1 1H8l-3 2.5V11H3a1 1 0 01-1-1V3z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>',
    side: 'left',
    offsetY: -116,
  },
  {
    id: 'settings',
    label: '设置',
    iconSvg: '<svg width="18" height="18" viewBox="0 0 16 16" fill="none"><path d="M8 2l.6 1.2a4.8 4.8 0 001.4 1.4L11 5l-1 .4a4.8 4.8 0 00-1.4 1.4L8 8l-.6-1.2A4.8 4.8 0 006 5.4L5 5l1-.4A4.8 4.8 0 007.4 3.2L8 2z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/><circle cx="8" cy="8" r="2" stroke="currentColor" stroke-width="1.3"/></svg>',
    side: 'left',
    offsetY: 116,
  },
  {
    id: 'today',
    label: '今日',
    iconSvg: '<svg width="18" height="18" viewBox="0 0 16 16" fill="none"><rect x="2" y="2.5" width="12" height="11" rx="1.5" stroke="currentColor" stroke-width="1.3"/><path d="M5 1.5V4M11 1.5V4M2 5.5H14" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/><path d="M5 8h2M5 11h6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/></svg>',
    side: 'left',
    offsetY: 0,
  },
  {
    id: 'music',
    label: '音乐',
    iconSvg: '<svg width="18" height="18" viewBox="0 0 16 16" fill="none"><path d="M6 12V3l7-2v9" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/><circle cx="4.5" cy="12" r="2.5" stroke="currentColor" stroke-width="1.3"/><circle cx="13.5" cy="10" r="2.5" stroke="currentColor" stroke-width="1.3"/></svg>',
    side: 'right',
    offsetY: -162,
  },
  {
    id: 'screenshot',
    label: '截图',
    iconSvg: '<svg width="18" height="18" viewBox="0 0 16 16" fill="none"><rect x="2" y="4" width="12" height="9" rx="1.5" stroke="currentColor" stroke-width="1.3"/><circle cx="8" cy="8.5" r="2.5" stroke="currentColor" stroke-width="1.3"/><path d="M5.5 4L6.5 2.5h3L10.5 4" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/></svg>',
    side: 'right',
    offsetY: -54,
  },
  {
    id: 'gif-record',
    label: '录制',
    iconSvg: '<svg width="18" height="18" viewBox="0 0 16 16" fill="none"><rect x="2" y="3.5" width="9" height="9" rx="1.5" stroke="currentColor" stroke-width="1.3"/><path d="M11 6.5l3-2v7l-3-2" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round"/><circle cx="6.5" cy="8" r="1" fill="currentColor"/></svg>',
    side: 'right',
    offsetY: 54,
  },
  {
    id: 'debug',
    label: '调试',
    iconSvg: '<svg width="18" height="18" viewBox="0 0 16 16" fill="none"><path d="M6 2.5h4M5 5h6M8 5v2.5M4.5 7.5l-1.5-1M11.5 7.5l1.5-1M5 12.5h6a1.5 1.5 0 001.5-1.5V8A1.5 1.5 0 0011 6.5H5A1.5 1.5 0 003.5 8v3A1.5 1.5 0 005 12.5Z" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/><circle cx="6.5" cy="9.5" r="0.7" fill="currentColor"/><circle cx="9.5" cy="9.5" r="0.7" fill="currentColor"/></svg>',
    side: 'right',
    offsetY: 162,
  },
]

const animated = ref(false)

onMounted(() => {
  requestAnimationFrame(() => {
    animated.value = true
  })
})

interface ItemStyle extends Record<string, string> {
  transform: string
  transitionDelay: string
}

const itemStyles = computed<ItemStyle[]>(() => {
  return menuItems.map((item, i) => {
    const y = item.offsetY
    const curve = Math.abs(y) / 162
    const xBase = 168 - curve * 42
    const x = item.side === 'left' ? -xBase : xBase

    if (animated.value) {
      return {
        transform: `translate(calc(-50% + ${x}px), calc(-50% + ${y}px))`,
        transitionDelay: `${i * 50}ms`,
      }
    }
    return {
      transform: 'translate(-50%, -50%) scale(0.5)',
      transitionDelay: '0ms',
    }
  })
})

function handleClick(id: string): void {
  emit('menuItemClick', id)
}
</script>

<template>
  <Transition name="radial-fade">
    <div v-if="visible" class="radial-menu" aria-label="Radial menu">
      <div class="radial-inner">
        <button
          v-for="(item, i) in menuItems"
          :key="item.id"
          class="radial-item"
          :class="{
            'radial-item--visible': animated,
            'radial-item--active': activeItem === item.id,
            'radial-item--left': item.side === 'left',
            'radial-item--right': item.side === 'right',
          }"
          :style="itemStyles[i]"
          type="button"
          :aria-label="item.label"
          :title="item.label"
          @click="handleClick(item.id)"
        >
          <span class="radial-icon" v-html="item.iconSvg" />
          <span class="radial-label">{{ item.label }}</span>
        </button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.radial-menu {
  position: absolute;
  inset: 0;
  z-index: 8;
  pointer-events: none;
}

.radial-inner {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 0;
  height: 0;
  pointer-events: none;
}

.radial-item {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: rgba(17, 16, 23, 0.28);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  color: rgba(255, 246, 232, 0.82);
  cursor: pointer;
  pointer-events: auto;
  opacity: 0;
  transition:
    transform 400ms cubic-bezier(0.34, 1.56, 0.64, 1),
    opacity 350ms ease,
    background var(--duration-fast) ease,
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
}

.radial-item--visible {
  opacity: 1;
}

.radial-item:hover {
  color: var(--color-heading);
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(242, 179, 95, 0.4);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22);
}

.radial-item--active {
  color: var(--color-heading);
  background: rgba(242, 179, 95, 0.22);
  border-color: rgba(242, 179, 95, 0.55);
  box-shadow: 0 8px 22px rgba(242, 179, 95, 0.18);
}

.radial-item--active:hover {
  color: var(--color-heading);
  background: rgba(242, 179, 95, 0.28);
}

.radial-item:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

.radial-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
}

.radial-label {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0;
  pointer-events: none;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(17, 16, 23, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 0.62rem;
  line-height: 1.1;
  white-space: nowrap;
  color: var(--color-heading);
  transition: opacity var(--duration-fast) ease;
}

.radial-item--left .radial-label {
  right: calc(100% + 10px);
}

.radial-item--right .radial-label {
  left: calc(100% + 10px);
}

.radial-item:hover .radial-label,
.radial-item--active .radial-label {
  opacity: 1;
}

/* Entry transition */
.radial-fade-enter-active {
  transition: opacity 200ms ease;
}

.radial-fade-leave-active {
  transition: opacity 150ms ease;
}

.radial-fade-enter-from,
.radial-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .radial-item {
    transition: none;
  }

  .radial-fade-enter-active,
  .radial-fade-leave-active {
    transition: none;
  }

  .radial-fade-enter-from,
  .radial-fade-leave-to {
    opacity: 1;
  }
}
</style>
