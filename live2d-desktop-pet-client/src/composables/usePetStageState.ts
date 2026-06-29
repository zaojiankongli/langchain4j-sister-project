import { shallowRef } from 'vue'
import type { Live2DRendererHandle } from '../live2d/pixiLive2dRenderer'

export function usePetStageState() {
  const modelPath = '/live2d/lafei/lafei.model3.json'
  const invalidModelPath = '/live2d/sample/missing.model3.json'

  const canvasRef = shallowRef<HTMLCanvasElement | null>(null)
  const status = shallowRef<'idle' | 'loading' | 'loaded' | 'failed'>('idle')
  const activeModelPath = shallowRef(modelPath)
  const rendererHandle = shallowRef<Live2DRendererHandle | null>(null)
  const lastLoadError = shallowRef('')

  return {
    modelPath,
    invalidModelPath,
    canvasRef,
    status,
    activeModelPath,
    rendererHandle,
    lastLoadError,
  }
}
