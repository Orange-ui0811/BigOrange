import { beforeEach, describe, expect, it, vi } from 'vitest'
import { pinia } from '../stores'
import { usePersonaStore } from '../stores/personaStore'
import { usePersonaWorkshop } from './usePersonaWorkshop'

const api = vi.hoisted(() => ({
  get: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), accept: vi.fn(), reject: vi.fn(),
}))
const ui = vi.hoisted(() => ({ confirm: vi.fn(), success: vi.fn() }))
vi.mock('../api/personaWorkshop', () => ({
  getPersonaWorkshop: api.get, createPersonaEntry: api.create, updatePersonaEntry: api.update,
  deletePersonaEntry: api.remove, acceptPersonaMaterial: api.accept, rejectPersonaMaterial: api.reject,
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: ui.success }, ElMessageBox: { confirm: ui.confirm },
}))

const response = () => ({ code: 1, data: {
  dimensions: [{ code: 'GOAL', entries: [] }], equipment: [], recommendations: [],
  materials: [{ id: 9, suggestedContent: '候选成长素材' }], materialCount: 1,
  settings: { showcaseEnabled: false }, completion: { percent: 0 },
} })

describe('usePersonaWorkshop', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    Object.values(ui).forEach(mock => mock.mockReset())
    usePersonaStore(pinia).reset()
    api.get.mockResolvedValue(response())
    api.create.mockResolvedValue({ code: 1 })
    api.update.mockResolvedValue({ code: 1 })
    api.remove.mockResolvedValue({ code: 1 })
    api.accept.mockResolvedValue({ code: 1 })
    api.reject.mockResolvedValue({ code: 1 })
  })

  it('loads drafts and serializes duplicate entry submissions', async () => {
    const afterLoad = vi.fn()
    const feature = usePersonaWorkshop({ afterLoad })
    await feature.load()
    expect(feature.materialDrafts[9]).toBe('候选成长素材')
    expect(afterLoad).toHaveBeenCalledOnce()
    feature.drafts.GOAL = '完成真实项目'
    const pending = feature.addEntry('GOAL')
    await feature.addEntry('GOAL')
    await pending
    expect(api.create).toHaveBeenCalledOnce()
    expect(feature.drafts.GOAL).toBe('')
  })

  it('edits, confirms and rejects user-owned persona content', async () => {
    const feature = usePersonaWorkshop()
    feature.openEdit({ id: 3, dimensionCode: 'SKILL', content: 'Vue' })
    expect(feature.editVisible.value).toBe(true)
    feature.editForm.content = 'Vue 与部署'
    await feature.saveEdit()
    expect(api.update).toHaveBeenCalledWith(3, { dimension: 'SKILL', content: 'Vue 与部署' })
    feature.materialDrafts[9] = '确认后的素材'
    await feature.acceptMaterial({ id: 9 })
    await feature.rejectMaterial({ id: 10 })
    expect(api.accept).toHaveBeenCalledWith(9, '确认后的素材')
    expect(api.reject).toHaveBeenCalledWith(10)
  })

  it('deletes only after confirmation and ignores cancellation', async () => {
    const feature = usePersonaWorkshop()
    ui.confirm.mockResolvedValueOnce(true)
    await feature.removeEntry({ id: 5 })
    expect(api.remove).toHaveBeenCalledWith(5)
    ui.confirm.mockRejectedValueOnce('cancel')
    await expect(feature.removeEntry({ id: 6 })).resolves.toBeUndefined()
    expect(api.remove).toHaveBeenCalledOnce()
  })

  it('releases saving state when a write fails', async () => {
    const feature = usePersonaWorkshop()
    feature.drafts.GOAL = '目标'
    api.create.mockRejectedValueOnce(new Error('offline'))
    await expect(feature.addEntry('GOAL')).rejects.toThrow('offline')
    expect(feature.store.saving).toBe(false)
  })

  it('ignores empty and stale actions while preserving unexpected confirmation failures', async () => {
    const feature = usePersonaWorkshop()
    await feature.addEntry('GOAL')
    expect(api.create).not.toHaveBeenCalled()
    feature.store.saving = true
    feature.editForm.content = '不会提交'
    await feature.saveEdit()
    expect(api.update).not.toHaveBeenCalled()
    feature.store.saving = false
    api.get.mockResolvedValueOnce({ code: 0 })
    await feature.load()
    ui.confirm.mockRejectedValueOnce(new Error('dialog failed'))
    await expect(feature.removeEntry({ id: 7 })).rejects.toThrow('dialog failed')
  })
})
