import type { ShallowRef } from 'vue'
import type { Live2DRendererHandle } from '../live2d/pixiLive2dRenderer'
import type { Live2DLogEntry } from './usePetDebugLog'
import type { PetRuntimeState } from './usePetRuntimeState'

interface UsePetRendererControllerOptions {
  canvasRef: ShallowRef<HTMLCanvasElement | null>
  status: ShallowRef<'idle' | 'loading' | 'loaded' | 'failed'>
  activeModelPath: ShallowRef<string>
  rendererHandle: ShallowRef<Live2DRendererHandle | null>
  petRuntimeState: ShallowRef<PetRuntimeState>
  lastLoadError: ShallowRef<string>
  appendLog: (entry: Omit<Live2DLogEntry, 'time' | 'renderer'>) => void
}

export function usePetRendererController(options: UsePetRendererControllerOptions) {
  const { canvasRef, status, activeModelPath, rendererHandle, petRuntimeState, lastLoadError, appendLog } = options
  let loadRequestId = 0

  function disposeRenderer(invalidatePendingLoad = true) {
    if (invalidatePendingLoad) {
      loadRequestId += 1
    }
    rendererHandle.value?.dispose()
    rendererHandle.value = null
  }

  function setCanvasRef(canvas: HTMLCanvasElement | null) {
    canvasRef.value = canvas
  }

  function refreshLayout() {
    rendererHandle.value?.refreshLayout()
  }

  async function loadModel(path: string) {
    const canvas = canvasRef.value

    if (!canvas) return

    const startedAt = performance.now()
    const currentRequestId = ++loadRequestId
    disposeRenderer(false)
    status.value = 'loading'
    activeModelPath.value = path
    lastLoadError.value = ''

    try {
      const { mountPixiLive2D } = await import('../live2d/pixiLive2dRenderer')
      const nextRendererHandle = await mountPixiLive2D({ canvas, modelPath: path })

      if (currentRequestId !== loadRequestId) {
        nextRendererHandle.dispose()
        return
      }

      rendererHandle.value = nextRendererHandle
      status.value = 'loaded'
      lastLoadError.value = ''
      if (petRuntimeState.value === 'error') {
        petRuntimeState.value = 'idle'
      }
      appendLog({
        event: 'live2d:model-loaded',
        modelPath: path,
        message: `loadMs=${Math.round(performance.now() - startedAt)} ${nextRendererHandle.getDebugInfo()}`,
      })
    } catch (error) {
      if (currentRequestId !== loadRequestId) {
        return
      }
      status.value = 'failed'
      petRuntimeState.value = 'error'
      lastLoadError.value = error instanceof Error ? error.message : String(error)
      appendLog({
        event: 'live2d:model-load-failed',
        modelPath: path,
        message: `loadMs=${Math.round(performance.now() - startedAt)} ${lastLoadError.value}`,
      })
    }
  }

  return {
    disposeRenderer,
    setCanvasRef,
    loadModel,
    refreshLayout,
  }
}
