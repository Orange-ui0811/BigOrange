import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePersonaEquipment } from './usePersonaEquipment'

const api = vi.hoisted(() => ({ equipment: vi.fn(), blueprint: vi.fn() }))
vi.mock('../api/personaWorkshop', () => ({
  updatePersonaEquipment: api.equipment,
  updatePersonaAccessoryBlueprint: api.blueprint,
}))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))

describe('usePersonaEquipment', () => {
  const item = () => ({ slotCode: 'GOAL_CROWN', dimension: 'GOAL', itemCode: 'COMPASS_CROWN',
    variantCode: 'CLASSIC', colorHex: '#F37021', equipped: true, transform: {}, name: '方向罗盘' })
  beforeEach(() => { api.equipment.mockReset(); api.blueprint.mockReset() })

  it('saves variants and blueprints through the existing API contract', async () => {
    const value = item()
    const reload = vi.fn()
    const feature = usePersonaEquipment({ snapshot: { equipment: [value] }, reload })
    api.equipment.mockResolvedValue({ code: 1 })
    api.blueprint.mockResolvedValue({ code: 1 })
    await feature.setEquipmentVariant(value, 'ORBIT')
    expect(api.equipment).toHaveBeenCalledWith('GOAL_CROWN', expect.objectContaining({ variantCode: 'ORBIT' }))
    await feature.saveAccessoryBlueprint(value, { name: '我的罗盘', parts: [] })
    expect(api.blueprint).toHaveBeenCalledWith('GOAL_CROWN', { name: '我的罗盘', parts: [] })
    expect(reload).not.toHaveBeenCalled()
    feature.updateEquipmentTransform(value, { scale: 1.2 })
    feature.updateAccessoryBlueprint(value, { name: '本地预览' })
    expect(value.transform).toEqual({ scale: 1.2 })
    expect(value.blueprint).toEqual({ name: '本地预览' })
    value.equipped = false
    await feature.saveEquipment(value)
    expect(feature.equipmentFor('MISSING')).toBeUndefined()
  })

  it('reloads authoritative state after a failed save', async () => {
    const reload = vi.fn().mockResolvedValue(undefined)
    const feature = usePersonaEquipment({ snapshot: { equipment: [item()] }, reload })
    api.equipment.mockRejectedValue(new Error('offline'))
    await expect(feature.saveEquipment(item())).rejects.toThrow('offline')
    expect(reload).toHaveBeenCalledOnce()
    api.blueprint.mockRejectedValue(new Error('bad blueprint'))
    await expect(feature.saveAccessoryBlueprint(item(), { parts: [] })).rejects.toThrow('bad blueprint')
    expect(reload).toHaveBeenCalledTimes(2)
  })
})
