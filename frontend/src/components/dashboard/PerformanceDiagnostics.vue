<script setup>
defineOptions({ name: 'PerformanceDiagnostics' })

import { computed, onMounted, ref, shallowRef } from 'vue'
import request from '@/utils/request'
import { clearMetricsSnapshot, getMetricsSnapshot } from '@/utils/metrics'
import { normalizeEndpointRows, normalizeEndpointSummary } from '@/utils/endpointMetricRows'
import { buildEndpointRows } from '@/utils/prometheusEndpointMetrics'

const localEvents = ref([])
const backendRows = ref([])
const backendSummary = ref(null)
const backendHistory = ref(null)
const backendLoading = shallowRef(false)
const backendError = shallowRef('')
const backendUpdatedAt = shallowRef('')
const backendSource = shallowRef('structured')
const exportStatus = shallowRef('')

const structuredMetricsUrl = '/admin/performance/endpoints'
const structuredHistoryUrl = '/admin/performance/history'
const prometheusUrl = '/actuator/prometheus'

const requestEvents = computed(() => localEvents.value.filter((event) => event.type === 'request'))
const authEvents = computed(() => localEvents.value.filter((event) => event.type === 'auth'))
const bootstrapEvents = computed(() => localEvents.value.filter((event) => event.type === 'bootstrap'))
const failedRequests = computed(() => requestEvents.value.filter((event) => event.success === false))

const requestDurations = computed(() => requestEvents.value
  .map((event) => Number(event.durationMs || 0))
  .filter((duration) => Number.isFinite(duration) && duration >= 0))

const averageRequestMs = computed(() => {
  if (!requestDurations.value.length) return 0
  return Math.round(requestDurations.value.reduce((sum, duration) => sum + duration, 0) / requestDurations.value.length)
})

const p95RequestMs = computed(() => {
  if (!requestDurations.value.length) return 0
  const sorted = [...requestDurations.value].sort((a, b) => a - b)
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * 0.95) - 1)
  return Math.round(sorted[index])
})

const recentEvents = computed(() => [...localEvents.value]
  .sort((a, b) => b.timestamp - a.timestamp)
  .slice(0, 12))

const slowestRequests = computed(() => [...requestEvents.value]
  .sort((a, b) => Number(b.durationMs || 0) - Number(a.durationMs || 0))
  .slice(0, 8))

const endpointRows = computed(() => [...backendRows.value]
  .sort((a, b) => severityRank(b.level) - severityRank(a.level) || b.total - a.total)
  .slice(0, 12))

const recentHistorySamples = computed(() => [...(backendHistory.value?.samples || [])].slice(-8).reverse())

const localCards = computed(() => [
  { label: 'Requests', value: requestEvents.value.length, hint: `${failedRequests.value.length} failed` },
  { label: 'Avg latency', value: `${averageRequestMs.value}ms`, hint: `p95 ${p95RequestMs.value}ms` },
  { label: 'Auth events', value: authEvents.value.length, hint: 'token / refresh flow' },
  { label: 'Bootstrap', value: bootstrapEvents.value.length, hint: 'route and app events' },
])

const backendCards = computed(() => {
  const summary = backendSummary.value
  if (!summary) return []

  return [
    { label: 'Endpoint rows', value: summary.rowCount, hint: `${summary.endpointCount} unique endpoints` },
    { label: 'Total calls', value: summary.totalRequests, hint: 'from backend metrics' },
    { label: 'Slow rows', value: summary.slowRows, hint: 'avg >= 1500ms' },
    { label: 'Error rows', value: summary.errorRows, hint: `${summary.criticalRows} critical` },
  ]
})

const trendCards = computed(() => {
  const history = backendHistory.value
  if (!history?.trend) return []

  return [
    { label: 'Trend', value: history.trend.direction, hint: `${history.sampleCount}/${history.capacity} samples` },
    { label: 'Calls delta', value: signed(history.trend.totalRequestDelta), hint: 'first -> latest' },
    { label: 'Slow delta', value: signed(history.trend.slowRowDelta), hint: 'slow rows' },
    { label: 'Error delta', value: signed(history.trend.errorRowDelta), hint: 'error rows' },
  ]
})

const healthLabel = computed(() => {
  const status = backendSummary.value?.status || 'healthy'
  if (status === 'critical') return 'Needs priority'
  if (status === 'warning') return 'Watch closely'
  return 'Healthy'
})

const exportPayload = computed(() => ({
  source: 'web-dashboard',
  exportedAt: new Date().toISOString(),
  summary: {
    events: localEvents.value.length,
    requests: requestEvents.value.length,
    failedRequests: failedRequests.value.length,
    averageRequestMs: averageRequestMs.value,
    p95RequestMs: p95RequestMs.value,
    authEvents: authEvents.value.length,
    bootstrapEvents: bootstrapEvents.value.length,
    backend: backendSummary.value,
    backendHistory: backendHistory.value,
  },
  events: localEvents.value,
}))

const backendSourceLabel = computed(() => (
  backendSource.value === 'structured'
    ? '/api/admin/performance/endpoints'
    : prometheusUrl
))

function refreshLocalEvents() {
  localEvents.value = getMetricsSnapshot()
}

function clearLocalEvents() {
  clearMetricsSnapshot()
  refreshLocalEvents()
}

function exportLocalEvents() {
  refreshLocalEvents()
  const json = JSON.stringify(exportPayload.value, null, 2)
  const blob = new Blob([json], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  link.href = url
  link.download = `sister-web-telemetry-${stamp}.json`
  link.click()
  URL.revokeObjectURL(url)
  exportStatus.value = `Exported ${localEvents.value.length} events`
}

async function loadStructuredEndpointRows() {
  const response = await request.get(structuredMetricsUrl)
  if (response?.code !== 200) {
    throw new Error(response?.message || 'Structured metrics are unavailable')
  }
  const rows = normalizeEndpointRows(response.data)
  if (!rows.length) {
    throw new Error('Structured metrics did not return app.endpoint data')
  }
  backendSource.value = 'structured'
  backendRows.value = rows
  backendSummary.value = normalizeEndpointSummary(response.data, rows)
  backendUpdatedAt.value = response.data?.updatedAt
    ? new Date(response.data.updatedAt).toLocaleTimeString()
    : new Date().toLocaleTimeString()
  await loadStructuredHistory()
}

async function loadStructuredHistory() {
  const response = await request.get(structuredHistoryUrl)
  if (response?.code === 200) {
    backendHistory.value = response.data
  }
}

async function loadPrometheusEndpointRows() {
  const response = await fetch(prometheusUrl, {
    headers: { Accept: 'text/plain' },
  })
  if (!response.ok) {
    throw new Error(`Prometheus responded ${response.status}`)
  }
  const text = await response.text()
  const rows = normalizeEndpointRows({ rows: buildEndpointRows(text) })
  backendSource.value = 'prometheus'
  backendRows.value = rows
  backendSummary.value = normalizeEndpointSummary(null, rows)
  backendHistory.value = null
  backendUpdatedAt.value = new Date().toLocaleTimeString()
}

async function refreshBackendMetrics() {
  backendLoading.value = true
  backendError.value = ''

  try {
    await loadStructuredEndpointRows()
  } catch (structuredError) {
    try {
      await loadPrometheusEndpointRows()
      backendError.value = structuredError?.message
        ? `Structured endpoint unavailable; using Prometheus fallback: ${structuredError.message}`
        : ''
    } catch (prometheusError) {
      backendRows.value = []
      backendSummary.value = null
      backendHistory.value = null
      backendError.value = prometheusError?.message || structuredError?.message || 'Cannot read backend metrics'
    }
  } finally {
    backendLoading.value = false
  }
}

function formatTime(timestamp) {
  if (!timestamp) return '--'
  return new Date(timestamp).toLocaleTimeString()
}

function eventTitle(event) {
  if (event.type === 'request') return `${event.method || 'GET'} ${event.url || ''}`
  if (event.type === 'auth') return `auth:${event.action || 'event'}`
  if (event.type === 'bootstrap') return `bootstrap:${event.stage || 'event'}`
  return event.type
}

function severityRank(level) {
  if (level === 'critical') return 3
  if (level === 'warning') return 2
  return 1
}

function signed(value) {
  const number = Number(value || 0)
  return number > 0 ? `+${number}` : String(number)
}

onMounted(() => {
  refreshLocalEvents()
  refreshBackendMetrics()
})
</script>

<template>
  <div class="diagnostics-container">
    <section class="diagnostics-hero">
      <div>
        <span class="section-kicker">OBSERVE //</span>
        <h3 class="diagnostics-title">Performance Diagnostics</h3>
        <p class="diagnostics-copy">Local events plus structured backend metrics. Find the unhealthy edge before guessing.</p>
      </div>
      <div class="diagnostics-actions">
        <button class="ghost-action" @click="refreshLocalEvents">Refresh local</button>
        <button class="ghost-action" @click="exportLocalEvents">Export JSON</button>
        <button class="ghost-action" :disabled="backendLoading" @click="refreshBackendMetrics">
          {{ backendLoading ? 'Loading...' : 'Refresh backend' }}
        </button>
      </div>
      <p v-if="exportStatus" class="export-status">{{ exportStatus }}</p>
    </section>

    <section class="metric-grid">
      <article v-for="card in localCards" :key="card.label" class="metric-card">
        <span class="metric-label">{{ card.label }}</span>
        <strong class="metric-value">{{ card.value }}</strong>
        <span class="metric-hint">{{ card.hint }}</span>
      </article>
    </section>

    <section v-if="backendSummary" class="diagnostics-section health-section" :class="`health-${backendSummary.status}`">
      <div class="section-head">
        <div>
          <span class="section-kicker">HEALTH //</span>
          <h4 class="section-title">Backend endpoint health: {{ healthLabel }}</h4>
        </div>
        <span class="status-pill">{{ backendSummary.status }}</span>
      </div>

      <div class="backend-card-grid">
        <article v-for="card in backendCards" :key="card.label" class="backend-card">
          <span class="metric-label">{{ card.label }}</span>
          <strong class="metric-value">{{ card.value }}</strong>
          <span class="metric-hint">{{ card.hint }}</span>
        </article>
      </div>

      <ul class="recommendation-list">
        <li v-for="recommendation in backendSummary.recommendations" :key="recommendation">
          {{ recommendation }}
        </li>
      </ul>
    </section>

    <section v-if="backendHistory" class="diagnostics-section trend-section" :class="`trend-${backendHistory.trend.direction}`">
      <div class="section-head">
        <div>
          <span class="section-kicker">TREND //</span>
          <h4 class="section-title">Short-term backend trend</h4>
        </div>
        <span class="status-pill">{{ backendHistory.trend.direction }}</span>
      </div>

      <div class="backend-card-grid">
        <article v-for="card in trendCards" :key="card.label" class="backend-card">
          <span class="metric-label">{{ card.label }}</span>
          <strong class="metric-value">{{ card.value }}</strong>
          <span class="metric-hint">{{ card.hint }}</span>
        </article>
      </div>

      <div v-if="recentHistorySamples.length" class="history-strip">
        <div v-for="sample in recentHistorySamples" :key="sample.updatedAt" class="history-chip" :class="`history-${sample.status}`">
          <strong>{{ sample.status }}</strong>
          <span>{{ formatTime(sample.updatedAt) }}</span>
          <code>{{ sample.slowRows }}/{{ sample.errorRows }}/{{ sample.criticalRows }}</code>
        </div>
      </div>
    </section>

    <section class="diagnostics-section">
      <div class="section-head">
        <div>
          <span class="section-kicker">LOCAL //</span>
          <h4 class="section-title">Recent events</h4>
        </div>
        <button class="danger-action" @click="clearLocalEvents">Clear local</button>
      </div>

      <div v-if="recentEvents.length" class="event-list">
        <div v-for="event in recentEvents" :key="event.id || `${event.type}-${event.timestamp}`" class="event-row">
          <div>
            <strong>{{ eventTitle(event) }}</strong>
            <span>{{ formatTime(event.timestamp) }}</span>
          </div>
          <code>{{ event.statusCode || event.status || event.message || event.durationMs || 'ok' }}</code>
        </div>
      </div>
      <div v-else class="empty-state">No local metrics yet. Trigger a request and this panel will populate.</div>
    </section>

    <section class="diagnostics-section">
      <div class="section-head">
        <div>
          <span class="section-kicker">SLOWEST //</span>
          <h4 class="section-title">Slowest requests</h4>
        </div>
      </div>

      <div v-if="slowestRequests.length" class="table-list">
        <div v-for="requestEvent in slowestRequests" :key="`${requestEvent.timestamp}-${requestEvent.url}`" class="table-row">
          <span>{{ requestEvent.method || 'GET' }}</span>
          <strong>{{ requestEvent.url || '--' }}</strong>
          <code>{{ requestEvent.durationMs || 0 }}ms</code>
        </div>
      </div>
      <div v-else class="empty-state">No request timing data yet.</div>
    </section>

    <section class="diagnostics-section">
      <div class="section-head">
        <div>
          <span class="section-kicker">BACKEND //</span>
          <h4 class="section-title">Endpoint metrics</h4>
        </div>
        <span class="backend-link">{{ backendSourceLabel }}</span>
      </div>

      <div v-if="backendError" class="backend-warning">{{ backendError }}</div>
      <div v-if="endpointRows.length" class="table-list">
        <div
          v-for="row in endpointRows"
          :key="row.key"
          class="table-row endpoint-row"
          :class="`endpoint-${row.level}`"
          :title="row.insight"
        >
          <span class="status-pill compact">{{ row.level }}</span>
          <span>{{ row.client }}</span>
          <strong>{{ row.endpoint }}</strong>
          <em>{{ row.outcome }}</em>
          <code>{{ row.total }} / {{ row.avgMs }}ms</code>
        </div>
      </div>
      <div v-else class="empty-state">
        {{ backendLoading ? 'Reading backend metrics...' : 'No app.endpoint metrics returned yet.' }}
      </div>
      <p v-if="backendUpdatedAt" class="updated-at">Updated at {{ backendUpdatedAt }}</p>
    </section>
  </div>
</template>

<style scoped>
.diagnostics-container {
  display: flex;
  flex-direction: column;
  gap: 18px;
  color: rgba(255, 255, 255, 0.9);
}

.diagnostics-hero,
.diagnostics-section,
.metric-card,
.backend-card {
  border: 1px solid rgba(94, 234, 212, 0.16);
  background: rgba(7, 17, 33, 0.44);
  backdrop-filter: blur(14px);
  border-radius: 14px;
  box-shadow: 0 16px 34px rgba(0, 0, 0, 0.18);
}

.diagnostics-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 22px;
}

.section-kicker {
  display: block;
  margin-bottom: 8px;
  color: var(--color-primary);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 2px;
}

.diagnostics-title,
.section-title {
  margin: 0;
  color: #fff;
  letter-spacing: 0;
}

.diagnostics-title {
  font-size: 28px;
  font-weight: 500;
}

.diagnostics-copy {
  max-width: 520px;
  margin-top: 10px;
  color: rgba(255, 255, 255, 0.62);
  line-height: 1.7;
}

.diagnostics-actions,
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ghost-action,
.danger-action {
  min-height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  cursor: pointer;
}

.ghost-action:disabled {
  opacity: 0.5;
  cursor: wait;
}

.danger-action {
  border-color: rgba(239, 68, 68, 0.32);
  color: #fecaca;
}

.metric-grid,
.backend-card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card,
.backend-card {
  padding: 18px;
}

.health-section,
.trend-section {
  position: relative;
  overflow: hidden;
}

.health-section::before,
.trend-section::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  content: '';
}

.health-healthy::before,
.trend-better::before,
.trend-flat::before {
  background: #5eead4;
}

.health-warning::before {
  background: #f59e0b;
}

.health-critical::before,
.trend-worse::before {
  background: #ef4444;
}

.status-pill {
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.status-pill.compact {
  justify-self: start;
  padding: 4px 8px;
}

.recommendation-list,
.history-strip {
  display: flex;
  gap: 8px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.recommendation-list {
  flex-direction: column;
}

.recommendation-list li,
.history-chip {
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.72);
}

.history-strip {
  overflow-x: auto;
}

.history-chip {
  display: grid;
  min-width: 126px;
  gap: 4px;
}

.history-critical {
  border: 1px solid rgba(239, 68, 68, 0.28);
}

.history-warning {
  border: 1px solid rgba(245, 158, 11, 0.22);
}

.metric-label,
.metric-hint,
.event-row span,
.updated-at,
.history-chip span {
  color: rgba(255, 255, 255, 0.56);
  font-size: 12px;
}

.metric-value {
  display: block;
  margin: 8px 0 4px;
  color: #fff;
  font-size: 26px;
  font-weight: 500;
}

.diagnostics-section {
  padding: 18px;
}

.event-list,
.table-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.event-row,
.table-row {
  display: grid;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
}

.event-row {
  grid-template-columns: minmax(0, 1fr) auto;
}

.event-row div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.event-row strong,
.table-row strong {
  overflow: hidden;
  color: #fff;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-row {
  grid-template-columns: 86px minmax(0, 1fr) 96px;
}

.endpoint-row {
  grid-template-columns: 92px 92px minmax(0, 1fr) 112px 112px;
}

.endpoint-warning {
  border: 1px solid rgba(245, 158, 11, 0.18);
}

.endpoint-critical {
  border: 1px solid rgba(239, 68, 68, 0.28);
  background: rgba(127, 29, 29, 0.18);
}

.table-row span,
.endpoint-row em {
  color: rgba(255, 255, 255, 0.58);
  font-style: normal;
}

code,
.backend-link {
  color: var(--color-primary);
  font-family: var(--font-mono);
  font-size: 12px;
}

.empty-state,
.backend-warning {
  margin-top: 14px;
  padding: 16px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.62);
}

.backend-warning {
  border: 1px solid rgba(245, 158, 11, 0.28);
  color: #fde68a;
}

.updated-at {
  margin-top: 12px;
}

.export-status {
  align-self: flex-end;
  margin: -10px 0 0;
  color: rgba(255, 255, 255, 0.56);
  font-family: var(--font-mono);
  font-size: 12px;
}

@media (max-width: 920px) {
  .diagnostics-hero,
  .diagnostics-actions,
  .section-head {
    align-items: stretch;
    flex-direction: column;
  }

  .metric-grid,
  .backend-card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-row,
  .endpoint-row {
    grid-template-columns: 1fr;
  }
}
</style>
