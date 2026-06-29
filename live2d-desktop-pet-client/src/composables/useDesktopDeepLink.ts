import { listen, type UnlistenFn } from '@tauri-apps/api/event'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { watch, type Ref } from 'vue'

const DEEP_LINK_EVENT = 'desktop-deep-link'
const DESKTOP_PROTOCOL = 'live2d-pet:'

type DesktopDeepLinkPayload =
  | string
  | {
    url?: string
    urls?: string[]
  }

interface UseDesktopDeepLinkOptions {
  enabled: Ref<boolean>
  onOpen?: (url: URL) => void | Promise<void>
}

function extractUrls(payload: DesktopDeepLinkPayload): string[] {
  if (typeof payload === 'string') {
    return [payload]
  }

  if (typeof payload.url === 'string' && payload.url.length > 0) {
    return [payload.url]
  }

  if (Array.isArray(payload.urls)) {
    return payload.urls.filter((url): url is string => typeof url === 'string' && url.length > 0)
  }

  return []
}

function resolveDeepLinkIntent(url: URL): 'open' | null {
  if (url.protocol !== DESKTOP_PROTOCOL) {
    return null
  }

  const hostAction = url.hostname.trim().toLowerCase()
  const pathAction = url.pathname.replace(/^\/+/, '').trim().toLowerCase()
  const queryAction = url.searchParams.get('action')?.trim().toLowerCase()

  if (hostAction === 'open' || pathAction === 'open' || queryAction === 'open') {
    return 'open'
  }

  return null
}

async function restoreAndFocusMainWindow(): Promise<void> {
  const appWindow = getCurrentWindow()

  await appWindow.show()

  if (await appWindow.isMinimized()) {
    await appWindow.unminimize()
  }

  await appWindow.setFocus()
}

export function useDesktopDeepLink(options: UseDesktopDeepLinkOptions): void {
  const { enabled, onOpen } = options

  let stopListening: UnlistenFn | null = null
  let registrationToken = 0

  async function unregisterListener(): Promise<void> {
    stopListening?.()
    stopListening = null
  }

  async function registerListener(): Promise<void> {
    const token = ++registrationToken
    const unlisten = await listen<DesktopDeepLinkPayload>(DEEP_LINK_EVENT, async (event) => {
      const urls = extractUrls(event.payload)

      for (const rawUrl of urls) {
        let parsedUrl: URL
        try {
          parsedUrl = new URL(rawUrl)
        } catch {
          continue
        }

        if (resolveDeepLinkIntent(parsedUrl) !== 'open') {
          continue
        }

        await restoreAndFocusMainWindow()
        await onOpen?.(parsedUrl)
      }
    })

    if (token !== registrationToken) {
      unlisten()
      return
    }

    stopListening = unlisten
  }

  watch(enabled, async (isEnabled) => {
    await unregisterListener()

    if (!isEnabled) {
      return
    }

    await registerListener()
  }, { immediate: true })
}
