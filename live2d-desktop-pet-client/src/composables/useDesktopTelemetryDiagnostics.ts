import { computed, shallowRef } from 'vue'
import { clearTelemetrySnapshot, getTelemetrySnapshot, type TelemetryEvent } from '../utils/telemetry'

export interface TelemetrySummaryItem {
  label: string
  value: string
}

export interface TelemetryEventGroup {
  type: string
  count: number
}

export interface TelemetryTimelineItem {
  id: string
  time: string
  type: string
  detail: string
}

function downloadJson(fileName: string, payload: unknown): void {
  const json = JSON.stringify(payload, null, 2)
  const blob = new Blob([json], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function asString(value: unknown): string | null {
  return typeof value === 'string' && value.trim().length > 0 ? value : null
}

function average(values: number[]): number {
  if (!values.length) return 0
  return Math.round(values.reduce((total, value) => total + value, 0) / values.length)
}

function percentile(values: number[], ratio: number): number {
  if (!values.length) return 0
  const sorted = [...values].sort((left, right) => left - right)
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)
  return Math.round(sorted[index])
}

function formatTime(timestamp: number): string {
  if (!timestamp) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(timestamp))
}

function getDuration(event: TelemetryEvent): number | null {
  return asNumber(event.payload?.durationMs)
}

function describeEvent(event: TelemetryEvent): string {
  const payload = event.payload ?? {}
  const duration = getDuration(event)
  const durationLabel = duration === null ? '' : ` · ${duration}ms`

  if (event.type === 'socket.status') {
    return `status=${asString(payload.status) ?? 'unknown'}`
  }

  if (event.type === 'socket.reconnect_meta') {
    const attempt = asNumber(payload.attempt) ?? 0
    const maxAttempts = asNumber(payload.maxAttempts) ?? 0
    const nextDelayMs = asNumber(payload.nextDelayMs)
    return nextDelayMs === null
      ? `attempt=${attempt}/${maxAttempts} · idle`
      : `attempt=${attempt}/${maxAttempts} · next=${nextDelayMs}ms`
  }

  if (event.type === 'socket.error') {
    return asString(payload.message) ?? 'unknown socket error'
  }

  if (event.type === 'socket.connect_start') {
    return `path=${asString(payload.path) ?? '/ws/chat'}`
  }

  if (event.type === 'socket.connected') {
    const subscriptions = asNumber(payload.subscriptions) ?? 0
    return `connected${durationLabel} · subscriptions=${subscriptions}`
  }

  if (event.type === 'socket.closed') {
    return `manual=${String(Boolean(payload.manual))}`
  }

  if (event.type === 'socket.connect_rejected') {
    return `reason=${asString(payload.reason) ?? 'unknown'}`
  }

  return Object.keys(payload).length ? JSON.stringify(payload) : 'no payload'
}

export function useDesktopTelemetryDiagnostics() {
  const events = shallowRef<TelemetryEvent[]>([])

  function refresh(): void {
    events.value = getTelemetrySnapshot()
  }

  function clear(): void {
    clearTelemetrySnapshot()
    refresh()
  }

  function exportJson(): void {
    refresh()
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
    downloadJson(`sister-desktop-telemetry-${stamp}.json`, {
      source: 'desktop-client',
      exportedAt: new Date().toISOString(),
      summary: summary.value,
      groups: groups.value,
      events: events.value,
    })
  }

  const socketEvents = computed(() => events.value.filter((event) => event.type.startsWith('socket.')))
  const errorEvents = computed(() => socketEvents.value.filter((event) => event.type === 'socket.error'))
  const reconnectEvents = computed(() => socketEvents.value.filter((event) => event.type === 'socket.reconnect_meta'))
  const connectedEvents = computed(() => socketEvents.value.filter((event) => event.type === 'socket.connected'))
  const connectionDurations = computed(() => connectedEvents.value
    .map((event) => getDuration(event))
    .filter((duration): duration is number => duration !== null && duration > 0))

  const latestStatus = computed(() => {
    const latest = [...events.value].reverse().find((event) => event.type === 'socket.status')
    return asString(latest?.payload?.status) ?? 'unknown'
  })

  const groups = computed<TelemetryEventGroup[]>(() => {
    const counts = events.value.reduce<Record<string, number>>((accumulator, event) => {
      accumulator[event.type] = (accumulator[event.type] ?? 0) + 1
      return accumulator
    }, {})

    return Object.entries(counts)
      .map(([type, count]) => ({ type, count }))
      .sort((left, right) => right.count - left.count)
  })

  const summary = computed<TelemetrySummaryItem[]>(() => [
    { label: 'Telemetry events', value: String(events.value.length) },
    { label: 'Socket events', value: String(socketEvents.value.length) },
    { label: 'Errors', value: String(errorEvents.value.length) },
    { label: 'Reconnect notes', value: String(reconnectEvents.value.length) },
    { label: 'Avg connect', value: `${average(connectionDurations.value)}ms` },
    { label: 'P95 connect', value: `${percentile(connectionDurations.value, 0.95)}ms` },
    { label: 'Latest status', value: latestStatus.value },
  ])

  const recentEvents = computed<TelemetryTimelineItem[]>(() => events.value
    .slice(-24)
    .reverse()
    .map((event, index) => ({
      id: `${event.timestamp}-${event.type}-${index}`,
      time: formatTime(event.timestamp),
      type: event.type,
      detail: describeEvent(event),
    })))

  refresh()

  return {
    events,
    groups,
    summary,
    recentEvents,
    refresh,
    clear,
    exportJson,
  }
}
