import { computed, shallowRef, watch, type ShallowRef } from 'vue'
import { get } from '../utils/apiClient'
import type { ApiResult } from '../types/api'
import type {
  BackendGalleryDetailResponse,
  BackendGalleryDefinition,
  BackendGalleryOverview,
  BackendGalleryUnlock,
  MemoryConditionProgress,
  MemoryDefinition,
  MemoryGalleryItem,
} from '../types/memory'

interface UseBackendMemoryGalleryOptions {
  userId: ShallowRef<number | null>
}

function toPlaceholderCondition(unlocked: boolean): MemoryConditionProgress {
  return {
    condition: { type: 'first_chat' },
    current: unlocked ? 1 : 0,
    target: 1,
    fulfilled: unlocked,
    label: unlocked ? '已由后端归档解锁' : '等待后端归档解锁',
  }
}

function toMemoryDefinition(definition: BackendGalleryDefinition): MemoryDefinition {
  return {
    id: definition.galleryKey,
    title: definition.title,
    category: definition.category,
    rarity: definition.rarity,
    hint: definition.hint,
    description: definition.description,
    coverTheme: definition.coverTheme,
    matchKeywords: definition.matchKeywords,
    unlockConditions: [],
  }
}

export function useBackendMemoryGallery(options: UseBackendMemoryGalleryOptions) {
  const { userId } = options
  const definitions = shallowRef<BackendGalleryDefinition[]>([])
  const unlockMap = shallowRef<Record<string, BackendGalleryUnlock>>({})
  const detailMap = shallowRef<Record<string, BackendGalleryDetailResponse>>({})
  const loadingDetailKeys = shallowRef<Record<string, boolean>>({})
  const isLoading = shallowRef(false)
  const loadError = shallowRef('')

  async function loadOverview(): Promise<void> {
    if (!userId.value) {
      definitions.value = []
      unlockMap.value = {}
      detailMap.value = {}
      loadingDetailKeys.value = {}
      loadError.value = ''
      return
    }

    isLoading.value = true
    loadError.value = ''
    try {
      const res = await get<ApiResult<BackendGalleryOverview>>('/api/ai/gallery/overview')
      if (res.code !== 200 || !res.data) {
        definitions.value = []
        unlockMap.value = {}
        detailMap.value = {}
        loadingDetailKeys.value = {}
        loadError.value = res.message || '加载图鉴失败'
        return
      }

      definitions.value = Array.isArray(res.data.definitions) ? res.data.definitions : []
      unlockMap.value = Object.fromEntries(
        (Array.isArray(res.data.unlocks) ? res.data.unlocks : []).map((unlock) => [unlock.galleryKey, unlock]),
      )

      const firstGalleryKey = definitions.value[0]?.galleryKey
      if (firstGalleryKey) {
        void loadDetail(firstGalleryKey)
      }
    } catch (error) {
      definitions.value = []
      unlockMap.value = {}
      detailMap.value = {}
      loadingDetailKeys.value = {}
      loadError.value = error instanceof Error ? error.message : String(error)
    } finally {
      isLoading.value = false
    }
  }

  async function loadDetail(galleryKey: string): Promise<void> {
    if (!galleryKey.trim()) {
      return
    }
    if (detailMap.value[galleryKey] || loadingDetailKeys.value[galleryKey]) {
      return
    }

    try {
      loadingDetailKeys.value = {
        ...loadingDetailKeys.value,
        [galleryKey]: true,
      }
      const res = await get<ApiResult<BackendGalleryDetailResponse>>(`/api/ai/gallery/${encodeURIComponent(galleryKey)}`)
      if (res.code !== 200 || !res.data) {
        return
      }
      detailMap.value = {
        ...detailMap.value,
        [galleryKey]: res.data,
      }
    } catch {
      // Detail is additive enhancement; keep overview usable on failure.
    } finally {
      const next = { ...loadingDetailKeys.value }
      delete next[galleryKey]
      loadingDetailKeys.value = next
    }
  }

  const galleryItems = computed<MemoryGalleryItem[]>(() => {
    return definitions.value.map((definition) => {
      const detail = detailMap.value[definition.galleryKey]
      const resolvedDefinition = detail?.definition ?? definition
      const unlock = detail?.unlock ?? unlockMap.value[definition.galleryKey]
      const unlocked = !!unlock
      return {
        definition: toMemoryDefinition(resolvedDefinition),
        progress: unlock
          ? {
              memoryId: resolvedDefinition.galleryKey,
              unlocked: true,
              unlockedAt: unlock.unlockedAt,
              relatedMood: unlock.relatedMood,
              relatedExcerpt: unlock.relatedExcerpt,
            }
          : null,
        unlocked,
        unlockedAt: unlock?.unlockedAt,
        relatedMood: unlock?.relatedMood,
        relatedExcerpt: unlock?.relatedExcerpt,
        sourceImageUrl: unlock?.sourceImageUrl,
        sourceMemoryTitle: unlock?.sourceMemoryTitle,
        sourceMemoryDate: unlock?.sourceMemoryDate,
        sourceMemoryContent: detail?.sourceMemoryContent ?? undefined,
        matchedKeywords: detail?.matchedKeywords ?? [],
        primaryConfidence: detail?.primaryConfidence ?? null,
        conditions: [toPlaceholderCondition(unlocked)],
        completionRatio: unlocked ? 1 : 0,
      }
    })
  })

  const unlockedCount = computed(() => galleryItems.value.filter((item) => item.unlocked).length)
  const totalCount = computed(() => galleryItems.value.length)

  function resetForUser(): void {
    definitions.value = []
    unlockMap.value = {}
    detailMap.value = {}
    loadingDetailKeys.value = {}
    loadError.value = ''
  }

  watch(userId, () => {
    void loadOverview()
  }, { immediate: true })

  return {
    galleryItems,
    unlockedCount,
    totalCount,
    isLoading,
    loadError,
    loadOverview,
    loadDetail,
    resetForUser,
  }
}
