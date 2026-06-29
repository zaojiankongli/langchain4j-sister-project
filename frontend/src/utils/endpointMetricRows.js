const WARNING_AVG_MS = 1500
const CRITICAL_AVG_MS = 5000

export function normalizeEndpointRows(payload) {
  const rows = Array.isArray(payload?.rows) ? payload.rows : []

  return rows
    .map((row) => {
      const avgMs = Number(row.avgMs || 0)
      const outcome = String(row.outcome || 'unknown')
      const level = row.level || levelFor(outcome, avgMs)

      return {
        key: String(row.key || `${row.client}|${row.endpoint}|${outcome}`),
        client: String(row.client || '--'),
        endpoint: String(row.endpoint || '--'),
        outcome,
        total: Number(row.total || 0),
        avgMs,
        level,
        insight: row.insight || insightFor(outcome, avgMs),
      }
    })
    .filter((row) => Number.isFinite(row.total) && Number.isFinite(row.avgMs))
}

export function normalizeEndpointSummary(payload, rows) {
  if (payload?.summary) {
    return {
      status: payload.summary.status || 'healthy',
      totalRequests: Number(payload.summary.totalRequests || 0),
      endpointCount: Number(payload.summary.endpointCount || 0),
      rowCount: Number(payload.summary.rowCount || rows.length),
      slowRows: Number(payload.summary.slowRows || 0),
      errorRows: Number(payload.summary.errorRows || 0),
      criticalRows: Number(payload.summary.criticalRows || 0),
      recommendations: Array.isArray(payload.summary.recommendations)
        ? payload.summary.recommendations
        : [],
    }
  }

  return buildEndpointSummary(rows)
}

export function buildEndpointSummary(rows) {
  const endpoints = new Set(rows.map((row) => `${row.client}|${row.endpoint}`))
  const slowRows = rows.filter((row) => row.avgMs >= WARNING_AVG_MS).length
  const errorRows = rows.filter((row) => isErrorOutcome(row.outcome)).length
  const criticalRows = rows.filter((row) => row.level === 'critical').length
  const warningRows = rows.filter((row) => row.level === 'warning').length
  const status = criticalRows > 0 ? 'critical' : warningRows > 0 ? 'warning' : 'healthy'
  const recommendations = []

  if (criticalRows > 0) {
    recommendations.push('优先排查 critical 接口：存在异常、服务端错误或超过 5 秒的平均耗时。')
  }
  if (slowRows > 0) {
    recommendations.push('关注慢接口：平均耗时超过 1.5 秒的 endpoint 可能需要缓存、异步化或索引优化。')
  }
  if (errorRows > 0) {
    recommendations.push('关注错误 outcome：错误或限流增多时应结合日志与前端 telemetry 定位。')
  }
  if (!recommendations.length) {
    recommendations.push('当前 endpoint 指标未发现明显异常，继续观察趋势即可。')
  }

  return {
    status,
    totalRequests: rows.reduce((sum, row) => sum + row.total, 0),
    endpointCount: endpoints.size,
    rowCount: rows.length,
    slowRows,
    errorRows,
    criticalRows,
    recommendations,
  }
}

function levelFor(outcome, avgMs) {
  if (outcome === 'exception' || outcome === 'server_error' || avgMs >= CRITICAL_AVG_MS) {
    return 'critical'
  }
  if (isErrorOutcome(outcome) || outcome === 'unauthorized' || avgMs >= WARNING_AVG_MS) {
    return 'warning'
  }
  return 'healthy'
}

function insightFor(outcome, avgMs) {
  if (outcome === 'exception') return '接口出现异常，需要结合服务端日志排查。'
  if (outcome === 'server_error') return '接口返回服务端错误，优先排查依赖服务和业务异常。'
  if (avgMs >= CRITICAL_AVG_MS) return '平均耗时超过 5 秒，优先检查慢查询、外部 API 或大模型调用。'
  if (avgMs >= WARNING_AVG_MS) return '平均耗时超过 1.5 秒，建议观察是否需要缓存或异步化。'
  if (outcome === 'rate_limited') return '接口触发限流，建议确认是否符合预期使用模式。'
  if (outcome === 'client_error' || outcome === 'unauthorized') return '存在客户端或鉴权失败结果，可结合前端 telemetry 核对链路。'
  return '指标健康，继续观察趋势。'
}

function isErrorOutcome(outcome) {
  return ['exception', 'server_error', 'client_error', 'rate_limited'].includes(outcome)
}
