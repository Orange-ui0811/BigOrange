import { reactive, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTrendHistory } from './useTrendHistory'

const api = vi.hoisted(() => ({ get: vi.fn(), delete: vi.fn() }))
const ui = vi.hoisted(() => ({ error: vi.fn(), confirm: vi.fn() }))
vi.mock('../api/request', () => ({ default: api }))
vi.mock('element-plus', () => ({ ElMessage: { error: ui.error }, ElMessageBox: { confirm: ui.confirm } }))

describe('useTrendHistory', () => {
  beforeEach(() => {
    sessionStorage.clear()
    Object.values(api).forEach(mock => mock.mockReset())
    Object.values(ui).forEach(mock => mock.mockReset())
  })

  it('loads, restores and deletes server-backed records', async () => {
    sessionStorage.setItem('appToken', 'token')
    api.get.mockResolvedValue({ code: 1, data: [{ id: 1, createTime: 'now' }] })
    api.delete.mockResolvedValue({ code: 1 })
    ui.confirm.mockResolvedValue(true)
    const profile = reactive({})
    const result = ref(null)
    const currentStep = ref(0)
    const feature = useTrendHistory({ profile, result, currentStep })
    await feature.loadHistoryFromDB()
    expect(feature.history.value[0].time).toBe('now')
    feature.loadHistory({ result: { analysis: {} }, topics: ['Agent'], languages: ['Java'], useRag: false })
    expect(result.value).toEqual({ analysis: {} })
    expect(profile.topics).toEqual(['Agent'])
    expect(currentStep.value).toBe(3)
    await feature.delHistory(0)
    await feature.clearHistory()
    expect(api.delete).toHaveBeenCalledWith('/app/trend/history/clear')
    expect(feature.history.value).toEqual([])
  })

  it('does not request private history without a token and rejects corrupted data', async () => {
    const feature = useTrendHistory({ profile: reactive({}), result: ref(null), currentStep: ref(0) })
    await feature.loadHistoryFromDB()
    expect(api.get).not.toHaveBeenCalled()
    feature.loadHistory({ corrupted: true })
    expect(ui.error).toHaveBeenCalled()
  })
})
