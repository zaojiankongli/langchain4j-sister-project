import { computed, readonly, shallowRef } from 'vue'
import { useClientSettings, type NotificationCategory, type NotificationPrefs } from './useClientSettings'

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export type { NotificationCategory, NotificationPrefs } from './useClientSettings'

type NotificationPrefsPatch = Partial<Omit<NotificationPrefs, 'categories'>> & {
  categories?: Partial<Record<NotificationCategory, boolean>>
}

/* ------------------------------------------------------------------ */
/*  Tauri / Web notification bridge                                    */
/* ------------------------------------------------------------------ */

let _permissionGranted = false
let _permissionChecked = false

async function ensurePermission(): Promise<boolean> {
  if (_permissionChecked) return _permissionGranted

  try {
    // Try Tauri native first
    const { isPermissionGranted, requestPermission } = await import(
      '@tauri-apps/plugin-notification'
    )
    _permissionGranted = await isPermissionGranted()
    if (!_permissionGranted) {
      const result = await requestPermission()
      _permissionGranted = result === 'granted'
    }
  } catch {
    // Fallback: Web Notification API (works in dev server & browsers)
    if (typeof Notification !== 'undefined') {
      if (Notification.permission === 'granted') {
        _permissionGranted = true
      } else if (Notification.permission !== 'denied') {
        const result = await Notification.requestPermission()
        _permissionGranted = result === 'granted'
      }
    }
  }

  _permissionChecked = true
  return _permissionGranted
}

async function fireNative(title: string, body?: string): Promise<void> {
  try {
    const { sendNotification } = await import('@tauri-apps/plugin-notification')
    sendNotification({ title, body })
    return
  } catch {
    // Fallback to Web API
  }

  if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
    new Notification(title, { body }) // eslint-disable-line no-new
  }
}

/* ------------------------------------------------------------------ */
/*  Category label helpers (for settings UI)                           */
/* ------------------------------------------------------------------ */

export const CATEGORY_LABELS: Record<NotificationCategory, string> = {
  mail: '新信件',
  mood: '心情变化',
  disconnect: '连接断开',
  message: 'AI 回复',
}

/* ------------------------------------------------------------------ */
/*  Composable (singleton)                                             */
/* ------------------------------------------------------------------ */

const permissionGranted = shallowRef(false)

/** Minimum interval (ms) between notifications of the same category */
const COOLDOWN_MS = 10_000
const lastSentAt = new Map<NotificationCategory, number>()

function canSend(category: NotificationCategory, prefs: NotificationPrefs): boolean {
  if (!prefs.enabled) return false
  if (!prefs.categories[category]) return false
  const last = lastSentAt.get(category) ?? 0
  return Date.now() - last > COOLDOWN_MS
}

async function requestNotificationPermission(): Promise<boolean> {
  const ok = await ensurePermission()
  permissionGranted.value = ok
  return ok
}

// Check permission eagerly on module load (non-blocking)
void ensurePermission().then((ok) => {
  permissionGranted.value = ok
})

export function usePetNotifications() {
  const { clientSettings, updateClientSettings } = useClientSettings()
  const prefs = computed(() => clientSettings.value.notifications)

  async function notify(
    category: NotificationCategory,
    title: string,
    body?: string,
  ): Promise<void> {
    if (!canSend(category, prefs.value)) return
    const ok = await ensurePermission()
    if (!ok) return

    lastSentAt.set(category, Date.now())
    await fireNative(title, body)
  }

  function updatePrefs(partial: NotificationPrefsPatch): void {
    updateClientSettings({ notifications: partial })
  }

  function toggleCategory(category: NotificationCategory): void {
    updatePrefs({
      categories: {
        [category]: !prefs.value.categories[category],
      },
    })
  }

  return {
    prefs: readonly(prefs),
    permissionGranted: readonly(permissionGranted),
    notify,
    updatePrefs,
    toggleCategory,
    requestNotificationPermission,
  }
}
