export interface PetDebugLogEntry {
  event: string
  modelPath: string
  renderer: string
  message?: string
  time: string
}

// Re-export from canonical source instead of duplicating the type
export type { PetRuntimeState } from '../../composables/usePetRuntimeState'
