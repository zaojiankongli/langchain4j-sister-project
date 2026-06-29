#!/usr/bin/env node

const DEFAULT_BACKEND_URL = 'http://localhost:8080'
const DEFAULT_FRONTEND_URL = 'http://localhost:5173'

const backendUrl = normalizeBaseUrl(process.env.SMOKE_BACKEND_URL || DEFAULT_BACKEND_URL)
const frontendUrl = normalizeBaseUrl(process.env.SMOKE_FRONTEND_URL || DEFAULT_FRONTEND_URL)
const authToken = process.env.SMOKE_AUTH_TOKEN || ''
const userId = process.env.SMOKE_USER_ID || ''
const checkWebSocket = process.env.SMOKE_CHECK_WS === 'true'
const checkAdminMetrics = process.env.SMOKE_CHECK_ADMIN_METRICS === 'true'
const allowOffline = process.env.SMOKE_ALLOW_OFFLINE === 'true'

const results = []

function normalizeBaseUrl(value) {
  return value.replace(/\/+$/, '')
}

function pushResult(status, name, detail = '') {
  results.push({ status, name, detail })
  const marker = status === 'pass' ? 'PASS' : status === 'skip' ? 'SKIP' : 'FAIL'
  console.log(`[${marker}] ${name}${detail ? ` - ${detail}` : ''}`)
}

async function fetchText(url, options = {}) {
  const response = await fetch(url, {
    redirect: 'manual',
    signal: AbortSignal.timeout(Number(process.env.SMOKE_TIMEOUT_MS || 5000)),
    ...options,
  })
  const text = await response.text().catch(() => '')
  return { response, text }
}

async function check(name, fn) {
  try {
    await fn()
  } catch (error) {
    if (allowOffline && isConnectionFailure(error)) {
      pushResult('skip', name, `offline: ${error?.message || String(error)}`)
      return
    }
    pushResult('fail', name, error?.message || String(error))
  }
}

function isConnectionFailure(error) {
  return error?.message === 'fetch failed' || error?.name === 'TimeoutError' || error?.cause?.code === 'ECONNREFUSED'
}

async function checkBackendHealth() {
  const { response, text } = await fetchText(`${backendUrl}/actuator/health`)
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 120)}`)
  }
  if (!text.includes('UP')) {
    throw new Error('health response does not include UP')
  }
  pushResult('pass', 'backend health', `${backendUrl}/actuator/health`)
}

async function checkPrometheus() {
  const { response, text } = await fetchText(`${backendUrl}/actuator/prometheus`)
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 120)}`)
  }
  if (!text.includes('# HELP')) {
    throw new Error('prometheus response does not look like metrics text')
  }
  const hasEndpointMetrics = text.includes('app_endpoint_duration') || text.includes('app_endpoint_total')
  pushResult(hasEndpointMetrics ? 'pass' : 'skip', 'backend endpoint metrics', hasEndpointMetrics ? 'app.endpoint metrics present' : 'no app.endpoint samples yet')
}

async function checkFrontendHtml() {
  const { response, text } = await fetchText(frontendUrl)
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 120)}`)
  }
  if (!text.includes('<div id="app"') && !text.includes('/assets/')) {
    throw new Error('frontend response does not look like the Vue app shell')
  }
  pushResult('pass', 'frontend app shell', frontendUrl)
}

async function checkProtectedApi() {
  if (!authToken || !userId) {
    pushResult('skip', 'authenticated API', 'set SMOKE_AUTH_TOKEN and SMOKE_USER_ID to enable')
    return
  }

  const { response, text } = await fetchText(`${backendUrl}/api/messages/${encodeURIComponent(userId)}/latest?limit=1`, {
    headers: { Authorization: `Bearer ${authToken}` },
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 160)}`)
  }
  if (!text.includes('code')) {
    throw new Error('authenticated API response does not include Result code')
  }
  pushResult('pass', 'authenticated API', '/api/messages/{userId}/latest')
}

async function checkStructuredAdminMetrics() {
  if (!checkAdminMetrics) {
    pushResult('skip', 'structured admin metrics', 'set SMOKE_CHECK_ADMIN_METRICS=true to enable')
    return
  }
  if (!authToken) {
    pushResult('skip', 'structured admin metrics', 'set SMOKE_AUTH_TOKEN with an admin access token')
    return
  }

  const { response, text } = await fetchText(`${backendUrl}/api/admin/performance/endpoints`, {
    headers: { Authorization: `Bearer ${authToken}` },
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 160)}`)
  }

  const payload = JSON.parse(text)
  if (payload.code !== 200) {
    throw new Error(`Result ${payload.code}: ${payload.message || 'unknown error'}`)
  }
  if (!Array.isArray(payload.data?.rows)) {
    throw new Error('structured metrics response does not include data.rows')
  }
  if (!payload.data?.summary?.status) {
    throw new Error('structured metrics response does not include data.summary.status')
  }
  if (!Array.isArray(payload.data?.summary?.recommendations)) {
    throw new Error('structured metrics response does not include summary recommendations')
  }

  const historyResult = await fetchText(`${backendUrl}/api/admin/performance/history`, {
    headers: { Authorization: `Bearer ${authToken}` },
  })
  if (!historyResult.response.ok) {
    throw new Error(`history HTTP ${historyResult.response.status}: ${historyResult.text.slice(0, 160)}`)
  }

  const historyPayload = JSON.parse(historyResult.text)
  if (historyPayload.code !== 200) {
    throw new Error(`History Result ${historyPayload.code}: ${historyPayload.message || 'unknown error'}`)
  }
  if (!historyPayload.data?.trend?.direction || !Array.isArray(historyPayload.data?.samples)) {
    throw new Error('structured metrics history does not include trend.direction and samples')
  }

  pushResult('pass', 'structured admin metrics', `${payload.data.summary.status}; ${payload.data.rows.length} rows; ${historyPayload.data.sampleCount} history samples`)
}

async function checkSockJsInfo() {
  if (!checkWebSocket) {
    pushResult('skip', 'websocket info', 'set SMOKE_CHECK_WS=true to enable')
    return
  }

  const { response, text } = await fetchText(`${backendUrl}/ws/chat/info?t=${Date.now()}`)
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${text.slice(0, 160)}`)
  }
  if (!text.includes('websocket')) {
    throw new Error('SockJS info response does not mention websocket')
  }
  pushResult('pass', 'websocket info', '/ws/chat/info')
}

await check('backend health', checkBackendHealth)
await check('backend prometheus', checkPrometheus)
await check('frontend app shell', checkFrontendHtml)
await check('authenticated API', checkProtectedApi)
await check('structured admin metrics', checkStructuredAdminMetrics)
await check('websocket info', checkSockJsInfo)

const failed = results.filter((result) => result.status === 'fail')
const passed = results.filter((result) => result.status === 'pass')
const skipped = results.filter((result) => result.status === 'skip')

console.log(`\nSmoke summary: ${passed.length} passed, ${skipped.length} skipped, ${failed.length} failed`)

if (failed.length > 0) {
  process.exitCode = 1
}
