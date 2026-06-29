/**
 * Shared time/date formatting utilities.
 * Single source of truth for all time display formatting across the app.
 */

/** Format ISO timestamp to HH:MM (24h). */
export function formatTime(isoString: string): string {
  try {
    const d = new Date(isoString)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return ''
  }
}

/** Format ISO timestamp to short time using locale (e.g. "02:30 PM"). */
export function formatTimeLocale(isoString: string): string {
  try {
    const d = new Date(isoString)
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ''
  }
}

/** Format ISO timestamp to MM/DD. */
export function formatDate(isoString: string): string {
  const d = new Date(isoString)
  return `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`
}

/** Format ISO timestamp to YYYY年MM月DD日. */
export function formatDateCN(isoString: string): string {
  const d = new Date(isoString)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}\u5E74${m}\u6708${day}\u65E5`
}
