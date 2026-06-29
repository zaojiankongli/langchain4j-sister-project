import { computed, shallowRef } from 'vue'

export interface Live2DLogEntry {
  event:
    | 'live2d:model-loaded'
    | 'live2d:model-load-failed'
    | 'live2d:model-load-scheduled'
    | 'live2d:runtime-probe'
    | 'socket:status'
    | 'socket:event'
    | 'socket:error'
    | 'window:click-through'
    | 'window:click-through-status'
    | 'window:drag-start'
    | 'window:mode-change'
    | 'window:music-open'
    | 'window:settings-open'
  modelPath: string
  renderer: string
  message?: string
  time: string
}

export function usePetDebugLog(rendererName: string) {
  const logEntries = shallowRef<Live2DLogEntry[]>([])
  const recentEvents = computed(() => logEntries.value.slice(0, 6))

  function appendLog(entry: Omit<Live2DLogEntry, 'time' | 'renderer'>) {
    const logEntry: Live2DLogEntry = {
      ...entry,
      renderer: rendererName,
      time: new Date().toISOString(),
    }

    logEntries.value = [logEntry, ...logEntries.value].slice(0, 200)
    console.info(logEntry.event, logEntry)
  }

  return {
    logEntries,
    recentEvents,
    appendLog,
  }
}
