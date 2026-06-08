import { ref } from 'vue'
import request from '@/utils/request'
import { API } from '@/config/api'

/**
 * AI 图片生成 Composable
 *
 * 封装后端 ImageController 的调用流程：
 *   文本内容 → extract-elements → ImageElements → generate → imageUrl
 *
 * 提供 loading / error 状态管理，支持重试。
 *
 * @param {() => boolean} [aliveCheck] - 可选生命周期检测函数，
 *   async 完成后若 aliveCheck() 返回 false 则放弃修改 ref
 */
export function useImageGeneration(aliveCheck) {
  const loading = ref(false)
  const error = ref('')
  const imageUrl = ref('')

  /**
   * 从文本内容生成 AI 图片
   *
   * @param {Object} options
   * @param {string} options.content - 用于提取元素的文本（记忆内容、日记等）
   * @returns {Promise<string|null>} 生成的图片 URL，失败返回 null
   */
  // ── 代际计数器：防止并发调用导致状态错乱 ──
  let _gen = 0

  async function generateFromContent({ content }) {
    if (!content || !content.trim()) {
      error.value = '内容不能为空'
      return null
    }

    const gen = ++_gen
    loading.value = true
    error.value = ''

    try {
      // Step 1: 从内容提取图片元素
      const elemRes = await request.post(API.IMAGE_EXTRACT_ELEMENTS, {
        memoryContent: content
      })
      if (aliveCheck && !aliveCheck()) return null
      if (gen !== _gen) return null  // 被新调用取代，丢弃
      if (elemRes.code !== 200) {
        throw new Error(elemRes.message || '元素提取失败')
      }
      const elements = elemRes.data
      if (!elements) {
        throw new Error('元素提取结果为空')
      }

      // Step 2: 使用元素生成图片
      const genRes = await request.post(API.IMAGE_GENERATE, elements)
      if (aliveCheck && !aliveCheck()) return null
      if (gen !== _gen) return null
      if (genRes.code !== 200) {
        throw new Error(genRes.message || '图片生成失败')
      }
      const url = genRes.data?.imageUrl || genRes.data?.url
      if (!url) {
        throw new Error('图片 URL 为空')
      }

      imageUrl.value = url
      return url
    } catch (e) {
      if (gen === _gen) {
        error.value = e.message || '图片生成失败'
      }
      console.error('AI 图片生成失败:', e)
      return null
    } finally {
      if (gen === _gen) loading.value = false
    }
  }

  /**
   * 重置状态
   */
  function reset() {
    loading.value = false
    error.value = ''
    imageUrl.value = ''
  }

  return {
    loading,
    error,
    imageUrl,
    generateFromContent,
    reset,
  }
}
