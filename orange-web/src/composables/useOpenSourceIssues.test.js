import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useOpenSourceIssues } from './useOpenSourceIssues'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
const success = vi.hoisted(() => vi.fn())
vi.mock('../api/request', () => ({ default: api }))
vi.mock('element-plus', () => ({ ElMessage: { success, error: vi.fn() } }))

const deferred = () => {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}
const selection = () => ({
  candidates: ref([]), selectedIds: computed(() => []), hydrate: vi.fn(),
})

describe('useOpenSourceIssues', () => {
  beforeEach(() => Object.values(api).forEach(mock => mock.mockReset()))

  it('loads issues and resets filters', async () => {
    api.get.mockResolvedValue({ code: 1, data: { records: [{ id: 1 }], total: 1 } })
    const selected = selection()
    const feature = useOpenSourceIssues(selected)
    feature.filters.value.keyword = 'Vue'
    await feature.resetFilters()
    expect(feature.issues.value).toEqual([{ id: 1 }])
    expect(feature.total.value).toBe(1)
    expect(feature.filters.value.keyword).toBe('')
    expect(selected.hydrate).toHaveBeenCalledWith([{ id: 1 }])
  })

  it('keeps the newest issue response when requests race', async () => {
    const first = deferred()
    const second = deferred()
    api.get.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const feature = useOpenSourceIssues(selection())
    const oldRequest = feature.loadIssues()
    const newRequest = feature.loadIssues()
    second.resolve({ code: 1, data: { records: [{ id: 2 }], total: 1 } })
    await newRequest
    first.resolve({ code: 1, data: { records: [{ id: 1 }], total: 1 } })
    await oldRequest
    expect(feature.issues.value).toEqual([{ id: 2 }])
  })

  it('loads and verifies a detail while exposing failures', async () => {
    api.get.mockResolvedValueOnce({ code: 0, msg: '详情失败' })
    const feature = useOpenSourceIssues(selection())
    await feature.openIssue(9)
    expect(feature.detailError.value).toBe('详情失败')
    feature.detail.value = { id: 9 }
    api.post.mockResolvedValue({ code: 1, data: { id: 9, verified: true } })
    await feature.verifyCurrent()
    expect(feature.detail.value.verified).toBe(true)
    expect(success).toHaveBeenCalled()
  })
})
