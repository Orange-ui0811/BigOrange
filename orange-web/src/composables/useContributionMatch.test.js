import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useContributionMatch } from './useContributionMatch'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
const quota = vi.hoisted(() => vi.fn())
const ui = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn() }))
vi.mock('../api/request', () => ({ default: api }))
vi.mock('../utils/quotaConfirm', () => ({ confirmQuotaUsage: quota }))
vi.mock('element-plus', () => ({ ElMessage: ui }))

const selection = () => {
  const candidates = ref([])
  return {
    candidates,
    selectedIds: computed(() => candidates.value.map(item => item.id)),
    includes: id => candidates.value.some(item => item.id === id),
    toggle: issue => candidates.value.push(issue),
    maxCandidates: 20,
  }
}

describe('useContributionMatch', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    Object.values(ui).forEach(mock => mock.mockReset())
    quota.mockReset().mockResolvedValue(true)
  })

  it('loads pricing and matches selected issues', async () => {
    api.get.mockResolvedValue({ code: 1, data: { matchCost: 2, jobMatchCost: 3, guideCost: 4 } })
    api.post.mockResolvedValue({ code: 1, data: { matches: [{ issue: { id: 7 } }], recommendedIssueId: 7 } })
    const selected = selection()
    const feature = useContributionMatch(selected)
    await feature.loadConfig()
    feature.matchForm.value.profile = '我会 Vue，每周可以投入六小时'
    await feature.runMatch()
    expect(feature.pricingReady.value).toBe(true)
    expect(feature.recommendedIssueId.value).toBe(7)
    expect(selected.candidates.value).toEqual([{ id: 7 }])
  })

  it('validates profile and reports provider failure', async () => {
    const feature = useContributionMatch(selection())
    await feature.runMatch()
    expect(ui.warning).toHaveBeenCalled()
    feature.config.value = { matchCost: 2, jobMatchCost: 3, guideCost: 4 }
    feature.matchForm.value.profile = '这是足够长度的用户能力和时间条件'
    api.post.mockRejectedValue({ response: { data: { msg: '模型繁忙' } } })
    await feature.runMatch()
    expect(feature.matchError.value).toBe('模型繁忙')
  })

  it('matches jobs and suppresses duplicate submissions', async () => {
    const pending = new Promise(() => {})
    api.post.mockReturnValue(pending)
    const feature = useContributionMatch(selection())
    feature.config.value.jobMatchCost = 3
    const firstRequest = feature.runJobMatchFor({ id: 8 })
    await vi.waitFor(() => expect(feature.jobMatching.value).toBe(true))
    await feature.runJobMatchFor({ id: 8 })
    expect(api.post).toHaveBeenCalledTimes(1)
    expect(firstRequest).toBeInstanceOf(Promise)
  })
})
