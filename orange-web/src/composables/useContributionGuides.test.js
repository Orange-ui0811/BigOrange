import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useContributionGuides } from './useContributionGuides'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
const ui = vi.hoisted(() => ({ success: vi.fn(), info: vi.fn(), confirm: vi.fn() }))
vi.mock('../api/request', () => ({ default: api }))
vi.mock('element-plus', () => ({
  ElMessage: { success: ui.success, info: ui.info },
  ElMessageBox: { confirm: ui.confirm },
}))

const createFeature = () => useContributionGuides({
  config: ref({ guideCost: 4 }),
  confirmCharge: vi.fn().mockResolvedValue(true),
  closeDetail: vi.fn(),
  openGuides: vi.fn(),
})

describe('useContributionGuides', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    Object.values(ui).forEach(mock => mock.mockReset())
  })

  it('loads a list and its first guide', async () => {
    api.get.mockResolvedValueOnce({ code: 1, data: [{ id: 1 }] })
      .mockResolvedValueOnce({ code: 1, data: { id: 1, content: '步骤' } })
    const feature = createFeature()
    await feature.loadGuides()
    expect(feature.guides.value).toEqual([{ id: 1 }])
    expect(feature.currentGuide.value.content).toBe('步骤')
  })

  it('generates and then deletes a guide', async () => {
    api.post.mockResolvedValue({ code: 1, data: { guide: { id: 2 } } })
    api.get.mockResolvedValue({ code: 1, data: [] })
    api.delete.mockResolvedValue({ code: 1 })
    ui.confirm.mockResolvedValue(true)
    const feature = createFeature()
    await feature.generateGuideFor({ id: 8 })
    expect(feature.currentGuide.value).toEqual({ id: 2 })
    await feature.deleteGuide({ id: 2, issueTitle: '任务' })
    expect(api.delete).toHaveBeenCalledWith('/app/open-source/guides/2')
  })

  it('exposes list and detail failures', async () => {
    api.get.mockResolvedValueOnce({ code: 0, msg: '列表失败' })
      .mockResolvedValueOnce({ code: 0, msg: '详情失败' })
    const feature = createFeature()
    await feature.loadGuides(false)
    await feature.openGuide(1)
    expect(feature.guidesError.value).toBe('列表失败')
    expect(feature.guideError.value).toBe('详情失败')
  })
})
