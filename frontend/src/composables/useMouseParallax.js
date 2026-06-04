import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 鼠标位置追踪 composable
 *
 * 在 mounted 时绑定 mousemove 事件，
 * unmounted 时自动解绑。
 *
 * @returns {{ mouseX: import('vue').Ref<number>, mouseY: import('vue').Ref<number> }}
 */
export function useMouseParallax() {
  const mouseX = ref(window.innerWidth / 2)
  const mouseY = ref(window.innerHeight / 2)
  const ticking = ref(false)

  const updateMouse = (e) => {
    if (ticking.value) return
    ticking.value = true
    requestAnimationFrame(() => {
      mouseX.value = e.clientX
      mouseY.value = e.clientY
      ticking.value = false
    })
  }

  onMounted(() => {
    window.addEventListener('mousemove', updateMouse, { passive: true })
  })

  onUnmounted(() => {
    window.removeEventListener('mousemove', updateMouse)
  })

  return {
    mouseX,
    mouseY
  }
}
