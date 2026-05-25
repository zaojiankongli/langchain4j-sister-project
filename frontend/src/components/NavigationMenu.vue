<template>
  <transition-group name="menu-pop">
    <div v-if="visible" key="radial-menu" class="radial-menu-container">
      <div
        v-for="(item, index) in navItems"
        :key="item.path"
        class="menu-bubble"
        :class="{ 'active-bubble': activeTab === item.path }"
        :style="getBubbleStyle(index)"
        @click.stop="$emit('navigate', item)"
      >
        <div class="bubble-inner">
          <span class="bubble-text">{{ item.shortName }}</span>
        </div>
      </div>
    </div>
  </transition-group>
</template>

<script setup>
const props = defineProps({
  visible: { type: Boolean, default: false },
  activeTab: { type: String, default: null },
})

defineEmits(['navigate'])

const navItems = [
  { name: '你的样子', shortName: '状态', path: 'user' },
  { name: '与我的回忆', shortName: '回忆', path: 'memory' },
  { name: '灵魂的颜色', shortName: '设置', path: 'emotion' },
  { name: '成长轨迹', shortName: '轨迹', path: 'relation' },
  { name: '为你推荐', shortName: '推荐', path: 'action' },
]

const getBubbleStyle = (index) => {
  const total = navItems.length
  const angle = 135 + (index * (225 - 135) / (total - 1))
  const radian = (angle * Math.PI) / 180
  const radius = 240
  return {
    transform: `translate(${Math.cos(radian) * radius}px, ${Math.sin(radian) * radius}px)`,
    transitionDelay: `${index * 50}ms`
  }
}
</script>

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
  transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
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

/* Animations */
.menu-pop-enter-active {
  transition: all 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.menu-pop-enter-from {
  opacity: 0;
  transform: scale(0.5);
}
</style>
