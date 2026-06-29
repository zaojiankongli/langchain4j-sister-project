const TOTAL_SAMPLE_NAMES = new Set([
  'app_endpoint_total',
  'app_endpoint_total_total',
])

const DURATION_SUM_SAMPLE_NAMES = new Set([
  'app_endpoint_duration_seconds_sum',
  'app_endpoint_duration_sum',
])

const DURATION_COUNT_SAMPLE_NAMES = new Set([
  'app_endpoint_duration_seconds_count',
  'app_endpoint_duration_count',
])

function addToMap(map, key, value) {
  map.set(key, (map.get(key) || 0) + value)
}

export function buildEndpointRows(text) {
  const samples = parsePrometheusSamples(text)
  const totals = new Map()
  const durationSums = new Map()
  const durationCounts = new Map()

  samples.forEach((sample) => {
    const key = endpointKey(sample.labels)
    if (!key) return

    if (TOTAL_SAMPLE_NAMES.has(sample.name)) {
      addToMap(totals, key, sample.value)
      return
    }

    if (DURATION_SUM_SAMPLE_NAMES.has(sample.name)) {
      addToMap(durationSums, key, sample.value)
      return
    }

    if (DURATION_COUNT_SAMPLE_NAMES.has(sample.name)) {
      addToMap(durationCounts, key, sample.value)
    }
  })

  const keys = new Set([
    ...totals.keys(),
    ...durationCounts.keys(),
    ...durationSums.keys(),
  ])

  return [...keys].map((key) => {
    const [client, endpoint, outcome] = key.split('|')
    const count = durationCounts.get(key) || 0
    const total = totals.get(key) || count
    const sumSeconds = durationSums.get(key) || 0

    return {
      key,
      client,
      endpoint,
      outcome,
      total,
      avgMs: count ? Math.round((sumSeconds / count) * 1000) : 0,
    }
  })
}

export function parsePrometheusSamples(text) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map(parsePrometheusLine)
    .filter(Boolean)
    .filter((sample) => sample.name.startsWith('app_endpoint_'))
}

function parsePrometheusLine(line) {
  const match = line.match(/^([^\s{]+)(?:\{([^}]*)\})?\s+(-?(?:\d+\.?\d*|\.\d+)(?:e[+-]?\d+)?)$/i)
  if (!match) return null

  const value = Number(match[3])
  if (!Number.isFinite(value)) return null

  return {
    name: match[1],
    labels: parseLabels(match[2] || ''),
    value,
  }
}

function parseLabels(rawLabels) {
  const labels = {}
  const matcher = /([a-zA-Z_][\w]*)="((?:\\.|[^"])*)"/g
  let match = matcher.exec(rawLabels)

  while (match) {
    labels[match[1]] = match[2]
      .replace(/\\"/g, '"')
      .replace(/\\n/g, '\n')
      .replace(/\\\\/g, '\\')
    match = matcher.exec(rawLabels)
  }

  return labels
}

function endpointKey(labels) {
  if (!labels.client || !labels.endpoint || !labels.outcome) {
    return ''
  }
  return `${labels.client}|${labels.endpoint}|${labels.outcome}`
}
