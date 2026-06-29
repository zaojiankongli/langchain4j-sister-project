import { readonly, shallowRef } from 'vue'
import { get, post } from '../utils/apiClient'
import type { ApiResult } from '../types/api'

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export interface Recommendation {
  id: string
  resourceType: 'document' | 'video' | 'article' | string
  title: string
  url: string
  imageUrl?: string
  description: string
  source: string
  relevanceScore: number
  recommendationDate: string
  isClicked: boolean
  createdAt: string
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

const RESOURCE_EMOJI: Record<string, string> = {
  document: '📄',
  video: '🎬',
  article: '📰',
}

export function getResourceEmoji(type: string): string {
  return RESOURCE_EMOJI[type] ?? '📌'
}

/* ------------------------------------------------------------------ */
/*  Composable                                                         */
/* ------------------------------------------------------------------ */

const recommendations = shallowRef<Recommendation[]>([])
const isLoading = shallowRef(false)
const loadError = shallowRef('')

async function fetchRecommendations(): Promise<void> {
  isLoading.value = true
  loadError.value = ''
  try {
    const res = await get<ApiResult<Recommendation[]>>('/api/ai/recom')
    if (res.code === 200 && Array.isArray(res.data)) {
      recommendations.value = res.data
    } else {
      loadError.value = res.message || '加载推荐失败'
      recommendations.value = []
    }
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : String(e)
    recommendations.value = []
  } finally {
    isLoading.value = false
  }
}

async function markClicked(id: string): Promise<void> {
  try {
    await post<ApiResult<null>>(`/api/ai/recom/click?id=${encodeURIComponent(id)}`)
    recommendations.value = recommendations.value.map((r) =>
      r.id === id ? { ...r, isClicked: true } : r,
    )
  } catch {
    /* non-critical */
  }
}

export function useRecommendations() {
  return {
    recommendations: readonly(recommendations),
    isLoading: readonly(isLoading),
    loadError: readonly(loadError),
    fetchRecommendations,
    markClicked,
  }
}
