import { shallowRef } from 'vue'
import { get } from '../utils/apiClient'
import { usePetConfigState } from './usePetConfigState'
import type { EmotionHistoryEntry } from '../types/emotion'

export function useMoodHistory() {
  const { userId } = usePetConfigState()

  const historyEntries = shallowRef<EmotionHistoryEntry[]>([])
  const isLoadingHistory = shallowRef(false)
  const historyError = shallowRef('')

  async function loadHistory(): Promise<void> {
    if (!userId.value) return
    isLoadingHistory.value = true
    historyError.value = ''
    try {
      const response = await get<{ content: EmotionHistoryEntry[] }>(
        `/api/emotion/${userId.value}/history`
      )
      historyEntries.value = response.content ?? []
    } catch (error) {
      console.error('Failed to load emotion history:', error)
      historyError.value = '加载情绪历史失败'
    } finally {
      isLoadingHistory.value = false
    }
  }

  async function loadHistoryByDate(date: string): Promise<void> {
    if (!userId.value) return
    isLoadingHistory.value = true
    historyError.value = ''
    try {
      const response = await get<{ content: EmotionHistoryEntry[] }>(
        `/api/emotion/${userId.value}/history`,
        { date }
      )
      historyEntries.value = response.content ?? []
    } catch (error) {
      console.error('Failed to load emotion history by date:', error)
      historyError.value = '加载情绪历史失败'
    } finally {
      isLoadingHistory.value = false
    }
  }

  return {
    historyEntries,
    isLoadingHistory,
    historyError,
    loadHistory,
    loadHistoryByDate,
  }
}
