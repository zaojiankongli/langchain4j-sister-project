import { onBeforeUnmount } from 'vue'
import gsap from 'gsap'

/**
 * GSAP 动画编排 composable
 *
 * - 自动清理：组件卸载时自动 kill 所有动画
 * - 提供常用动画工厂：入场、弹跳、呼吸、波纹
 *
 * @returns {{
 *   gsap: gsap (原始 GSAP 对象)
 *   timeline: () => gsap.core.Timeline
 *   entryStagger: (selector: string, options?: object) => gsap.core.Timeline
 *   rippleEffect: (el: HTMLElement) => void
 * }}
 */
export function useGsapAnimation() {
  const ctx = gsap.context(() => {})

  onBeforeUnmount(() => { ctx.revert() })

  function timeline(config) {
    const tl = gsap.timeline(config)
    ctx.add(tl)
    return tl
  }

  /**
   * 入场交错动画
   * @param {string|Element|Array} selector  CSS 选择器或元素
   * @param {{ stagger?: number, y?: number, duration?: number, ease?: string, delay?: number }} opts
   */
  function entryStagger(selector, opts = {}) {
    const {
      from = { opacity: 0, y: 20, scale: 0.97 },
      to = { opacity: 1, y: 0, scale: 1 },
      stagger = 0.06,
      ease = 'back.out(1.7)',
      delay = 0,
      duration = 0.5
    } = opts

    const tl = gsap.timeline({ delay })
    tl.fromTo(selector, from, { ...to, duration, stagger, ease })
    ctx.add(tl)
    return tl
  }

  /**
   * 波纹点击效果
   */
  function rippleEffect(el) {
    if (!el || !(el instanceof Element)) return () => {}
    const ripples = new Set()
    function handler(e) {
      const rect = el.getBoundingClientRect()
      const size = Math.max(rect.width, rect.height)
      const x = (e.clientX || e.touches?.[0]?.clientX || rect.left + rect.width / 2) - rect.left - size / 2
      const y = (e.clientY || e.touches?.[0]?.clientY || rect.top + rect.height / 2) - rect.top - size / 2

      const ripple = document.createElement('span')
      ripple.style.cssText = `
        position: absolute; pointer-events: none; border-radius: 50%;
        width: ${size}px; height: ${size}px; left: ${x}px; top: ${y}px;
        background: rgba(255,255,255,0.25); transform: scale(0);
      `
      // 仅在元素为 static 定位时才设为 relative，避免覆盖已有布局
      if (getComputedStyle(el).position === 'static') {
        el.style.position = 'relative'
      }
      el.style.overflow = 'hidden'
      el.appendChild(ripple)
      ripples.add(ripple)

      gsap.to(ripple, {
        scale: 2, opacity: 0, duration: 0.6, ease: 'power2.out',
        onComplete: () => { ripple.remove(); ripples.delete(ripple) }
      })
    }
    el.addEventListener('mousedown', handler)
    ctx.add(() => () => {
      el.removeEventListener('mousedown', handler)
      // 清理所有残留的 ripple DOM 节点（防止 ctx.revert() 杀死 tween 后 onComplete 不触发）
      ripples.forEach(r => r.remove())
      ripples.clear()
    })
    return () => {
      el.removeEventListener('mousedown', handler)
      ripples.forEach(r => r.remove())
      ripples.clear()
    }
  }

  return {
    gsap,
    timeline,
    entryStagger,
    rippleEffect
  }
}
