import { ref, onMounted, onBeforeUnmount } from 'vue'

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
  let ticking = false

  const updateMouse = (e) => {
    // Extract coordinates immediately before the event object is reused
    const clientX = e.clientX
    const clientY = e.clientY
    if (ticking) return
    ticking = true
    requestAnimationFrame(() => {
      mouseX.value = clientX
      mouseY.value = clientY
      ticking = false
    })
  }

  onMounted(() => {
    window.addEventListener('mousemove', updateMouse, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('mousemove', updateMouse)
  })

  return {
    mouseX,
    mouseY
  }
}
