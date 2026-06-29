import { onMounted, onUnmounted } from 'vue'

type IdleCallbackHandle = number

interface IdleDeadline {
  didTimeout: boolean
  timeRemaining: () => number
}

type WindowWithIdleCallback = Window & {
  requestIdleCallback?: (callback: (deadline: IdleDeadline) => void, options?: { timeout: number }) => IdleCallbackHandle
  cancelIdleCallback?: (handle: IdleCallbackHandle) => void
}

interface UsePetShellLifecycleOptions {
  loadModel: (path: string) => Promise<void> | void
  modelPath: string
  disconnectSocket: () => void
  disposeRenderer: () => void
  appendLog?: (entry: { event: 'live2d:model-load-scheduled'; modelPath: string; message?: string }) => void
}

export function usePetShellLifecycle(options: UsePetShellLifecycleOptions) {
  const { loadModel, modelPath, disconnectSocket, disposeRenderer, appendLog } = options
  let timeoutHandle: ReturnType<typeof setTimeout> | null = null
  let idleHandle: IdleCallbackHandle | null = null

  onMounted(() => {
    const win = window as WindowWithIdleCallback
    const mountedAt = performance.now()

    const runLoad = () => {
      timeoutHandle = null
      idleHandle = null
      appendLog?.({
        event: 'live2d:model-load-scheduled',
        modelPath,
        message: `strategy=${typeof win.requestIdleCallback === 'function' ? 'idle-callback' : 'timeout'} delayMs=${Math.round(performance.now() - mountedAt)}`,
      })
      void loadModel(modelPath)
    }

    if (typeof win.requestIdleCallback === 'function') {
      idleHandle = win.requestIdleCallback(runLoad, { timeout: 500 })
      return
    }

    timeoutHandle = setTimeout(runLoad, 32)
  })

  onUnmounted(() => {
    const win = window as WindowWithIdleCallback
    if (idleHandle !== null && typeof win.cancelIdleCallback === 'function') {
      win.cancelIdleCallback(idleHandle)
      idleHandle = null
    }
    if (timeoutHandle) {
      clearTimeout(timeoutHandle)
      timeoutHandle = null
    }
    disconnectSocket()
    disposeRenderer()
  })
}
