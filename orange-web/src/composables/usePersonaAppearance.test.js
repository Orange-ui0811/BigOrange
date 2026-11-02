import { defineComponent, reactive } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { usePersonaAppearance } from './usePersonaAppearance'

const updateSettings = vi.hoisted(() => vi.fn())
vi.mock('../api/personaWorkshop', () => ({ updatePersonaSettings: updateSettings }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() } }))

describe('usePersonaAppearance', () => {
  afterEach(() => { vi.useRealTimers(); updateSettings.mockReset() })

  it('debounces appearance saves and clears avatar when showcase is disabled', async () => {
    vi.useFakeTimers()
    updateSettings.mockResolvedValue({ code: 1 })
    let feature
    const snapshot = reactive({ settings: { showcaseEnabled: false, avatarEnabled: true,
      bodyColor: '#F37021', faceStyle: 'FRIENDLY', leafStyle: 'CLASSIC', surfaceStyle: 'NATURAL', faceConfig: {} } })
    const wrapper = mount(defineComponent({ setup() { feature = usePersonaAppearance(snapshot); return () => null } }))
    feature.setAppearance('bodyColor', '#0EA5E9')
    expect(feature.appearanceSaveState.value).toBe('等待保存…')
    await vi.advanceTimersByTimeAsync(260)
    expect(updateSettings).toHaveBeenCalledWith(expect.objectContaining({ bodyColor: '#0EA5E9' }))
    await feature.saveSettings()
    expect(snapshot.settings.avatarEnabled).toBe(false)
    wrapper.unmount()
  })

  it('reports autosave failures and updates face configuration locally', async () => {
    vi.useFakeTimers()
    updateSettings.mockRejectedValue(new Error('offline'))
    let feature
    const snapshot = reactive({ settings: { showcaseEnabled: true, avatarEnabled: true,
      faceConfig: {}, bodyColor: '', faceStyle: '', leafStyle: '', surfaceStyle: '' } })
    const wrapper = mount(defineComponent({ setup() { feature = usePersonaAppearance(snapshot); return () => null } }))
    feature.updateFaceConfig({ eyeSize: 1.2 })
    expect(feature.faceConfig.value.eyeSize).toBe(1.2)
    expect(feature.appearance.value.bodyColor).toBe('#F37021')
    feature.scheduleAppearanceSave()
    await vi.advanceTimersByTimeAsync(260)
    expect(feature.appearanceSaveState.value).toBe('保存失败')
    wrapper.unmount()
  })
})
