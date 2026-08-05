import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { updatePersonaSettings } from '../api/personaWorkshop'

export const usePersonaAppearance = snapshot => {
  const saveState = ref('已自动保存')
  let timer
  const appearance = computed(() => ({
    bodyColor: snapshot.settings.bodyColor || '#F37021',
    faceStyle: snapshot.settings.faceStyle || 'FRIENDLY',
    leafStyle: snapshot.settings.leafStyle || 'CLASSIC',
    surfaceStyle: snapshot.settings.surfaceStyle || 'NATURAL',
    faceConfig: snapshot.settings.faceConfig || {},
  }))
  const faceConfig = computed(() => snapshot.settings.faceConfig || {})
  const payload = () => ({
    showcaseEnabled: snapshot.settings.showcaseEnabled, avatarEnabled: snapshot.settings.avatarEnabled,
    bodyColor: snapshot.settings.bodyColor, faceStyle: snapshot.settings.faceStyle,
    leafStyle: snapshot.settings.leafStyle, surfaceStyle: snapshot.settings.surfaceStyle,
    faceConfig: snapshot.settings.faceConfig,
  })
  const scheduleSave = () => {
    clearTimeout(timer)
    saveState.value = '等待保存…'
    timer = setTimeout(async () => {
      saveState.value = '保存中…'
      try { await updatePersonaSettings(payload()); saveState.value = '已自动保存' }
      catch { saveState.value = '保存失败' }
    }, 260)
  }
  const setAppearance = (key, value) => { snapshot.settings[key] = value; scheduleSave() }
  const updateFaceConfig = value => { snapshot.settings.faceConfig = value }
  const saveSettings = async () => {
    if (!snapshot.settings.showcaseEnabled) snapshot.settings.avatarEnabled = false
    await updatePersonaSettings(payload())
    ElMessage.success('展示设置已保存')
  }
  onBeforeUnmount(() => clearTimeout(timer))
  return { appearance, faceConfig, appearanceSaveState: saveState, setAppearance,
    updateFaceConfig, scheduleAppearanceSave: scheduleSave, saveSettings }
}
