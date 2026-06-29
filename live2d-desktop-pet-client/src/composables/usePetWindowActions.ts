import { invoke } from '@tauri-apps/api/core'
import { getCurrentWindow, PhysicalPosition, PhysicalSize } from '@tauri-apps/api/window'
import { WebviewWindow } from '@tauri-apps/api/webviewWindow'
import type { ShallowRef } from 'vue'
import type { Live2DLogEntry } from './usePetDebugLog'

type WindowMode = 'pet' | 'debug'

const WINDOW_MODE_SIZES: Record<WindowMode, { width: number; height: number }> = {
  pet: { width: 420, height: 700 },
  debug: { width: 800, height: 600 },
}

interface UsePetWindowActionsOptions {
  activeModelPath: ShallowRef<string>
  lastSocketError: ShallowRef<string>
  appendLog: (entry: Omit<Live2DLogEntry, 'time' | 'renderer'>) => void
}

export function usePetWindowActions(options: UsePetWindowActionsOptions) {
  const { activeModelPath, lastSocketError, appendLog } = options

  async function requestClickThrough(enabled: boolean = true): Promise<boolean> {
    try {
      const result = await invoke<boolean>('set_click_through', { enabled })
      appendLog({
        event: 'window:click-through',
        modelPath: activeModelPath.value,
        message: `click-through ${result ? 'enabled' : 'disabled'}`,
      })
      return result
    } catch (error) {
      lastSocketError.value = error instanceof Error ? error.message : String(error)
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: error instanceof Error ? error.message : String(error),
      })
      return false
    }
  }

  async function startWindowDrag(): Promise<boolean> {
    try {
      await getCurrentWindow().startDragging()
      appendLog({
        event: 'window:drag-start',
        modelPath: activeModelPath.value,
        message: 'window drag started',
      })
      return true
    } catch (error) {
      lastSocketError.value = error instanceof Error ? error.message : String(error)
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: error instanceof Error ? error.message : String(error),
      })
      return false
    }
  }

  async function setWindowMode(mode: WindowMode): Promise<boolean> {
    try {
      const appWindow = getCurrentWindow()
      const currentSize = await appWindow.innerSize()
      const currentPosition = await appWindow.outerPosition()
      const nextSize = WINDOW_MODE_SIZES[mode]

      const centerX = currentPosition.x + currentSize.width / 2
      const centerY = currentPosition.y + currentSize.height / 2

      await appWindow.setSize(new PhysicalSize(nextSize.width, nextSize.height))
      await appWindow.setPosition(new PhysicalPosition(
        Math.round(centerX - nextSize.width / 2),
        Math.round(centerY - nextSize.height / 2),
      ))

      appendLog({
        event: 'window:mode-change',
        modelPath: activeModelPath.value,
        message: `window mode -> ${mode} ${nextSize.width}x${nextSize.height}`,
      })
      return true
    } catch (error) {
      lastSocketError.value = error instanceof Error ? error.message : String(error)
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: error instanceof Error ? error.message : String(error),
      })
      return false
    }
  }

  async function openMusicWindow(): Promise<boolean> {
    try {
      const existing = await WebviewWindow.getByLabel('music')
      if (existing) {
        await existing.show()
        await existing.setFocus()
      } else {
        const musicUrl = new URL(window.location.href)
        musicUrl.search = '?window=music'
        musicUrl.hash = ''
        const musicWindow = new WebviewWindow('music', {
          title: '音乐',
          url: musicUrl.toString(),
          width: 1280,
          height: 720,
          transparent: false,
          decorations: false,
          resizable: false,
          maximizable: false,
          minimizable: false,
          center: true,
        })
        await musicWindow.once('tauri://error', async (error) => {
          const message = typeof error === 'string' ? error : JSON.stringify(error)
          lastSocketError.value = message
          appendLog({
            event: 'socket:error',
            modelPath: activeModelPath.value,
            message,
          })
        })
        await musicWindow.once('tauri://created', async () => {
          await musicWindow.setFocus()
        })
      }

      appendLog({
        event: 'window:music-open',
        modelPath: activeModelPath.value,
        message: 'music window opened',
      })
      return true
    } catch (error) {
      lastSocketError.value = error instanceof Error ? error.message : String(error)
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: error instanceof Error ? error.message : String(error),
      })
      return false
    }
  }

  async function openSettingsWindow(): Promise<boolean> {
    try {
      const existing = await WebviewWindow.getByLabel('settings')
      if (existing) {
        await existing.show()
        await existing.setFocus()
      } else {
        const settingsUrl = new URL(window.location.href)
        settingsUrl.search = '?window=settings'
        settingsUrl.hash = ''
        const settingsWindow = new WebviewWindow('settings', {
          title: '设置',
          url: settingsUrl.toString(),
          width: 800,
          height: 600,
          transparent: false,
          decorations: false,
          resizable: false,
          maximizable: false,
          minimizable: false,
          center: true,
        })
        await settingsWindow.once('tauri://error', async (error) => {
          const message = typeof error === 'string' ? error : JSON.stringify(error)
          lastSocketError.value = message
          appendLog({
            event: 'socket:error',
            modelPath: activeModelPath.value,
            message,
          })
        })
        await settingsWindow.once('tauri://created', async () => {
          await settingsWindow.setFocus()
        })
      }

      appendLog({
        event: 'window:settings-open',
        modelPath: activeModelPath.value,
        message: 'settings window opened',
      })
      return true
    } catch (error) {
      lastSocketError.value = error instanceof Error ? error.message : String(error)
      appendLog({
        event: 'socket:error',
        modelPath: activeModelPath.value,
        message: error instanceof Error ? error.message : String(error),
      })
      return false
    }
  }

  return {
    requestClickThrough,
    startWindowDrag,
    setWindowMode,
    openMusicWindow,
    openSettingsWindow,
  }
}
