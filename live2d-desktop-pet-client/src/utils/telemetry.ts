export interface TelemetryEvent {
  type: string
  timestamp: number
  payload?: Record<string, unknown>
}

const MAX_TELEMETRY_EVENTS = 160
const STORAGE_KEY = 'sister.desktop.telemetry'

function readEvents(): TelemetryEvent[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const events = JSON.parse(raw)
    return Array.isArray(events) ? events : []
  } catch {
    return []
  }
}

function writeEvents(events: TelemetryEvent[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(events.slice(-MAX_TELEMETRY_EVENTS)))
  } catch {
    // Telemetry should never affect the desktop pet runtime.
  }
}

export function recordTelemetry(type: string, payload: Record<string, unknown> = {}): void {
  const events = readEvents()
  events.push({
    type,
    timestamp: Date.now(),
    payload,
  })
  writeEvents(events)
}

export function getTelemetrySnapshot(): TelemetryEvent[] {
  return readEvents()
}

export function clearTelemetrySnapshot(): void {
  writeEvents([])
}
