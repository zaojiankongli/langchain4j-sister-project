import { shallowRef } from 'vue'
import { get } from '../utils/apiClient'
import { usePetConfigState } from './usePetConfigState'

export interface MemoryEntry {
  id: number
  date: string
  title?: string
  content?: string
  contentPreview?: string
  moodLabel?: string
  imageUrl?: string
}

export interface MemoryListResponse {
  content: MemoryEntry[]
  totalPages?: number
  totalElements?: number
  page?: number
  size?: number
}

export interface SemanticSearchResult {
  userId: string
  query: string
  results: string[]
  count: number
}

export type MemoryDateFilter = '' | '最近' | '更早' | string

export function usePetMemory() {
  const { userId } = usePetConfigState()

  const memoryEntries = shallowRef<MemoryEntry[]>([])
  const isLoadingMemory = shallowRef(false)
  const isLoadingMore = shallowRef(false)
  const memoryError = shallowRef('')
  const currentPage = shallowRef(0)
  const totalPages = shallowRef(0)
  const hasMoreMemory = shallowRef(false)
  const pageSize = 10

  // Search state
  const searchResults = shallowRef<string[]>([])
  const isSearching = shallowRef(false)
  const searchQuery = shallowRef('')
  const activeFilter = shallowRef<MemoryDateFilter>('')

  async function loadMemory(filter?: MemoryDateFilter): Promise<void> {
    if (!userId.value) return
    currentPage.value = 0
    isLoadingMemory.value = true
    memoryError.value = ''
    if (filter !== undefined) activeFilter.value = filter
    try {
      const params: Record<string, string> = {
        page: '1',
        size: String(pageSize),
      }
      if (activeFilter.value) {
        params.filter = activeFilter.value
      }
      const response = await get<MemoryListResponse>(
        `/api/ai/memory/list`,
        params,
      )
      memoryEntries.value = response.content ?? []
      totalPages.value = response.totalPages ?? 0
      hasMoreMemory.value = (response.totalPages ?? 0) > 1
    } catch (error) {
      console.error('Failed to load memory entries:', error)
      memoryError.value = 'Failed to load memories'
    } finally {
      isLoadingMemory.value = false
    }
  }

  async function loadMoreMemory(): Promise<void> {
    if (!userId.value || !hasMoreMemory.value || isLoadingMore.value) return
    const nextPage = currentPage.value + 1
    isLoadingMore.value = true
    try {
      const params: Record<string, string> = {
        page: String(nextPage),
        size: String(pageSize),
      }
      if (activeFilter.value) {
        params.filter = activeFilter.value
      }
      const response = await get<MemoryListResponse>(
        `/api/ai/memory/list`,
        params,
      )
      const newEntries = response.content ?? []
      memoryEntries.value = [...memoryEntries.value, ...newEntries]
      currentPage.value = nextPage
      hasMoreMemory.value = nextPage + 1 < (response.totalPages ?? 0)
    } catch (error) {
      console.error('Failed to load more memory entries:', error)
      memoryError.value = 'Failed to load more memories'
    } finally {
      isLoadingMore.value = false
    }
  }

  /**
   * Semantic search against the Milvus vector store.
   * Uses hybrid search (dense embedding + BM25 full-text, RRF fusion).
   */
  async function searchMemories(query: string, limit = 5): Promise<void> {
    if (!query.trim()) {
      searchResults.value = []
      searchQuery.value = ''
      return
    }
    isSearching.value = true
    searchQuery.value = query
    try {
      const res = await get<{ code: number; data: SemanticSearchResult }>(
        '/api/memory/search',
        { query: query.trim(), limit: String(Math.min(limit, 20)) },
      )
      if (res.code === 200 && Array.isArray(res.data?.results)) {
        searchResults.value = res.data.results
      } else {
        searchResults.value = []
      }
    } catch (error) {
      console.error('Memory search failed:', error)
      searchResults.value = []
    } finally {
      isSearching.value = false
    }
  }

  /**
   * Search memories within a date range.
   * startDate/endDate format: "2026年3月" or "2026-03-01"
   */
  async function searchMemoriesByDate(
    query: string,
    startDate: string,
    endDate: string,
    limit = 5,
  ): Promise<void> {
    if (!query.trim()) {
      searchResults.value = []
      searchQuery.value = ''
      return
    }
    isSearching.value = true
    searchQuery.value = query
    try {
      const res = await get<{ code: number; data: SemanticSearchResult }>(
        '/api/memory/search/by-date',
        {
          query: query.trim(),
          startDate,
          endDate,
          limit: String(Math.min(limit, 20)),
        },
      )
      if (res.code === 200 && Array.isArray(res.data?.results)) {
        searchResults.value = res.data.results
      } else {
        searchResults.value = []
      }
    } catch (error) {
      console.error('Memory date search failed:', error)
      searchResults.value = []
    } finally {
      isSearching.value = false
    }
  }

  function clearSearch(): void {
    searchResults.value = []
    searchQuery.value = ''
  }

  return {
    memoryEntries,
    isLoadingMemory,
    isLoadingMore,
    memoryError,
    hasMoreMemory,
    activeFilter,
    searchResults,
    isSearching,
    searchQuery,
    loadMemory,
    loadMoreMemory,
    searchMemories,
    searchMemoriesByDate,
    clearSearch,
  }
}
