<script setup>
import { computed } from 'vue'
import { isDiagnosticsEnabled } from '@/utils/diagnosticsAccess'

const props = defineProps({
  visible: { type: Boolean, default: false },
  activeTab: { type: String, default: null },
})

defineEmits(['navigate'])

const allNavItems = [
  { name: '你的样子', shortName: '状态', path: 'user' },
  { name: '与我的回忆', shortName: '回忆', path: 'memory' },
  { name: '灵魂的颜色', shortName: '情绪', path: 'emotion' },
  { name: '成长轨迹', shortName: '轨迹', path: 'relation' },
  { name: '为你推荐', shortName: '推荐', path: 'action' },
  { name: '灵魂调谐', shortName: '调谐', path: 'settings' },
  { name: '性能诊断', shortName: '诊断', path: 'diagnostics' },
]

const navItems = computed(() => allNavItems.filter((item) => item.path !== 'diagnostics' || isDiagnosticsEnabled()))

const RADIUS = 240
const bubbleStyles = computed(() => navItems.value.map((_, index) => {
  const total = Math.max(navItems.value.length - 1, 1)
  const angle = 135 + (index * (225 - 135) / total)
  const radian = (angle * Math.PI) / 180
  return {
    transform: `translate(${Math.cos(radian) * RADIUS}px, ${Math.sin(radian) * RADIUS}px)`,
    transitionDelay: `${index * 50}ms`,
  }
}))
</script>

<template>
  <transition name="menu-pop">
    <div v-if="visible" class="radial-menu-container">
      <div
        v-for="(item, index) in navItems"
        :key="item.path"
        class="menu-bubble"
        :class="{ 'active-bubble': activeTab === item.path }"
        :style="bubbleStyles[index]"
        @click.stop="$emit('navigate', item)"
      >
        <div class="bubble-inner">
          <span class="bubble-text">{{ item.shortName }}</span>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.radial-menu-container {
  position: absolute;
  top: 40%;
  left: 50%;
  pointer-events: none;
}

.menu-bubble {
  position: absolute;
  width: 64px;
  height: 64px;
  margin-left: -32px;
  margin-top: -32px;
  pointer-events: auto;
  cursor: pointer;
}

.bubble-inner {
  width: 100%;
  height: 100%;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  transition: background-color 0.4s cubic-bezier(0.16, 1, 0.3, 1), border-color 0.4s cubic-bezier(0.16, 1, 0.3, 1), transform 0.4s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.active-bubble .bubble-inner,
.bubble-inner:hover {
  background: rgba(94, 234, 212, 0.25);
  border-color: var(--color-primary);
  transform: scale(1.15);
  box-shadow: 0 0 15px rgba(94, 234, 212, 0.4);
}

.bubble-text {
  font-size: 14px;
  font-weight: 300;
}

.menu-pop-enter-active {
  transition: opacity 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275), transform 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.menu-pop-leave-active {
  transition: opacity 0.3s cubic-bezier(0.6, 0, 0.4, 1), transform 0.3s cubic-bezier(0.6, 0, 0.4, 1);
}

.menu-pop-enter-from {
  opacity: 0;
  transform: scale(0.5);
}

.menu-pop-leave-to {
  opacity: 0;
  transform: scale(0.5);
}
</style>
