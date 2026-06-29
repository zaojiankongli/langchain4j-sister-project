import { STORAGE_KEYS } from '@/config/storage'
import { safeGetJSON, safeSetJSON } from '@/utils/storage'

const MAX_METRIC_EVENTS = 120

function readEvents() {
  return safeGetJSON(STORAGE_KEYS.METRICS_EVENTS, []) || []
}

function writeEvents(events) {
  safeSetJSON(STORAGE_KEYS.METRICS_EVENTS, events.slice(-MAX_METRIC_EVENTS))
}

export function recordMetric(type, payload = {}) {
  const events = readEvents()
  events.push({
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    type,
    timestamp: Date.now(),
    ...payload,
  })
  writeEvents(events)
}

export function recordRequestMetric(payload = {}) {
  recordMetric('request', payload)
}

export function recordAuthMetric(action, payload = {}) {
  recordMetric('auth', { action, ...payload })
}

export function recordBootstrapMetric(stage, payload = {}) {
  recordMetric('bootstrap', { stage, ...payload })
}

export function getMetricsSnapshot() {
  return readEvents()
}

export function clearMetricsSnapshot() {
  writeEvents([])
}
