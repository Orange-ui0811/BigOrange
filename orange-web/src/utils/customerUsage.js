export const parseCustomerJson = value => {
  if (value && typeof value === 'object') return value
  try { return JSON.parse(value || '{}') } catch { return {} }
}
export function customerDuration(durationMs, started, finished) {
  if (durationMs !== null && durationMs !== undefined && durationMs !== '' && Number.isFinite(Number(durationMs)) && Number(durationMs) >= 0) return (Number(durationMs) / 1000).toFixed(1)
  // Old minute-only timestamps cannot represent request latency.
  if (!/\d{2}:\d{2}:\d{2}/.test(started || '') || !/\d{2}:\d{2}:\d{2}/.test(finished || '')) return ''
  const n = new Date(finished).getTime() - new Date(started).getTime()
  return Number.isFinite(n) && n >= 0 ? (n / 1000).toFixed(1) : ''
}
export function customerUsage(value) {
  const metrics = parseCustomerJson(value)
  if (metrics.tokenUsage) {
    const usage = { ...metrics.tokenUsage, costUsd: metrics.costUsd }
    for (const field of ['inputTokens', 'outputTokens', 'totalTokens', 'calls']) usage[field] = Number(usage[field] || 0)
    return usage
  }
  const calls = metrics.modelCalls || []
  const sum = key => calls.reduce((n, c) => n + Number(c.usage?.[key] || 0), 0)
  return { inputTokens: sum('prompt_tokens'), outputTokens: sum('completion_tokens'), totalTokens: sum('total_tokens'), calls: calls.length,
    complete: calls.every(c => typeof c.usage?.total_tokens === 'number'), callsWithoutUsage: calls.filter(c => !c.usage?.total_tokens && c.usage?.total_tokens !== 0).length, costUsd: metrics.costUsd }
}
