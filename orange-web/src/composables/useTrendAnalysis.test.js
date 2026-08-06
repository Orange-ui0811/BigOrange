import { reactive, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useTrendAnalysis } from './useTrendAnalysis'

const request = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }))
const quota = vi.hoisted(() => vi.fn())
const warning = vi.hoisted(() => vi.fn())
vi.mock('../api/request', () => ({ default: request }))
vi.mock('../utils/quotaConfirm', () => ({ confirmQuotaUsage: quota }))
vi.mock('element-plus', () => ({ ElMessage: { warning } }))

describe('useTrendAnalysis', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    request.post.mockReset(); request.get.mockReset(); quota.mockReset(); warning.mockReset()
    quota.mockResolvedValue(true)
    request.get.mockResolvedValue({ data: { trendV1: 50, trendV2: 50 } })
  })
  afterEach(() => vi.useRealTimers())

  it('validates topics and saves a completed analysis', async () => {
    const profile = reactive({ topics: [], languages: [], useRag: true, usePersona: false })
    const saved = vi.fn()
    const feature = useTrendAnalysis(profile, saved)
    const step = ref(2)
    await feature.startJuicing(step)
    expect(feature.v2Error.value).toContain('技术方向')
    expect(warning).toHaveBeenCalled()
    profile.usePersona = true
    request.post.mockResolvedValue({ code: 1, data: { analysis: {}, historySaved: true } })
    const pending = feature.startJuicing(step)
    await vi.advanceTimersByTimeAsync(2500)
    await pending
    expect(feature.v2Result.value.analysis).toEqual({})
    expect(request.post).toHaveBeenCalledWith('/app/trend/analyze-v2',
      expect.objectContaining({ useRag: true, usePersona: true }), { timeout: 0 })
    expect(saved).toHaveBeenCalledOnce()
    expect(feature.analyzing.value).toBe(false)
  })

  it('asks V1 and refreshes pricing without persisting sensitive results', async () => {
    const feature = useTrendAnalysis(reactive({ topics: [] }))
    feature.aiQuestion.value = '趋势如何'
    request.get.mockResolvedValue({ data: { trendV1: 60, trendV2: 70 } })
    request.post.mockResolvedValue({ code: 1, data: { content: '**增长**' } })
    await feature.askAI()
    expect(feature.aiReply.value).toBe('**增长**')
    expect(feature.aiReplyHtml.value).toContain('<strong>增长</strong>')
    await feature.loadPricing()
    expect(feature.trendV1Cost.value).toBe(60)
    expect(feature.trendV2Cost.value).toBe(70)
    feature.dispose()
  })

  it('does not start an analysis when the current pricing cannot be loaded', async () => {
    const profile = reactive({ topics: ['AI智能体'], languages: [], useRag: true, usePersona: false })
    const feature = useTrendAnalysis(profile)
    request.get.mockRejectedValue(new Error('offline'))

    await feature.startJuicing(ref(3))

    expect(request.post).not.toHaveBeenCalled()
    expect(quota).not.toHaveBeenCalled()
    expect(feature.v2Error.value).toContain('最新配额定价')
  })
})
