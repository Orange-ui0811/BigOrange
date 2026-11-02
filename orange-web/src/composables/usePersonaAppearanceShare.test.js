import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePersonaAppearanceShare } from './usePersonaAppearanceShare'

const api = vi.hoisted(() => ({ exportCode: vi.fn(), current: vi.fn(), preview: vi.fn(), apply: vi.fn() }))
const ui = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), confirm: vi.fn() }))
vi.mock('../api/personaWorkshop', () => ({
  exportPersonaAppearanceCode: api.exportCode,
  getCurrentPersonaAppearanceCode: api.current,
  previewPersonaAppearanceCode: api.preview,
  applyPersonaAppearanceCode: api.apply,
}))
vi.mock('element-plus', () => ({
  ElMessage: { success: ui.success, warning: ui.warning },
  ElMessageBox: { confirm: ui.confirm },
}))

describe('usePersonaAppearanceShare', () => {
  beforeEach(() => {
    Object.values(api).forEach(mock => mock.mockReset())
    Object.values(ui).forEach(mock => mock.mockReset())
    api.exportCode.mockResolvedValue({ data: { code: 'OVC-ABCD-2345' } })
    api.current.mockResolvedValue({ data: null })
    api.preview.mockResolvedValue({ data: {
      code: 'OVC-ABCD-2345', readyToImport: true,
      appearance: { bodyColor: '#F37021' },
      equipment: [{ slotCode: 'LEFT_HAND_WEAPON', equipped: true, available: true }],
    } })
    api.apply.mockResolvedValue({ data: { importedEquipment: 7 } })
    ui.confirm.mockResolvedValue(true)
  })

  it('restores a matching current code and clears it after the visual state changes', async () => {
    api.current.mockResolvedValue({ data: { code: 'OVC-ABCD-2345' } })
    const fingerprint = ref('look-a')
    const feature = usePersonaAppearanceShare({ completion: ref({ percent: 100 }), visualFingerprint: fingerprint })
    await Promise.resolve()
    await Promise.resolve()
    expect(feature.generatedCode.value).toBe('OVC-ABCD-2345')
    fingerprint.value = 'look-b'
    await Promise.resolve()
    expect(feature.generatedCode.value).toBe('')
  })

  it('exports, previews and applies a visual-only code for a complete persona', async () => {
    const afterImport = vi.fn()
    const feature = usePersonaAppearanceShare({ completion: ref({ percent: 100 }), afterImport })
    await feature.generateCode()
    expect(feature.generatedCode.value).toBe('OVC-ABCD-2345')
    feature.importCode.value = 'ovc-abcd-2345'
    await feature.loadPreview()
    expect(api.preview).toHaveBeenCalledWith('OVC-ABCD-2345')
    expect(feature.preview.value.equipment[0].unlocked).toBe(true)
    await feature.applyCode()
    expect(api.apply).toHaveBeenCalledWith('OVC-ABCD-2345')
    expect(afterImport).toHaveBeenCalledOnce()
  })

  it('clears a stale preview when the entered code changes', async () => {
    const feature = usePersonaAppearanceShare({ completion: ref({ percent: 100 }) })
    feature.importCode.value = 'OVC-ABCD-2345'
    await feature.loadPreview()
    expect(feature.preview.value).not.toBeNull()
    feature.importCode.value = 'OVC-WXYZ-6789'
    await Promise.resolve()
    expect(feature.preview.value).toBeNull()
  })

  it('does not export before completion while keeping preview data non-destructive', async () => {
    const feature = usePersonaAppearanceShare({ completion: ref({ percent: 86 }) })
    feature.importCode.value = 'OVC-ABCD-2345'
    await feature.generateCode()
    await feature.loadPreview()
    expect(api.exportCode).not.toHaveBeenCalled()
    expect(api.preview).toHaveBeenCalledOnce()
    expect(feature.isComplete.value).toBe(false)
  })
})
