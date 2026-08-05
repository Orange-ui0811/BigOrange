import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applyPersonaAppearanceCode,
  exportPersonaAppearanceCode,
  previewPersonaAppearanceCode,
} from '../api/personaWorkshop'

export const usePersonaAppearanceShare = ({ completion, afterImport }) => {
  const generatedCode = ref('')
  const importCode = ref('')
  const preview = ref(null)
  const exporting = ref(false)
  const previewing = ref(false)
  const importing = ref(false)
  const isComplete = computed(() => Number(completion.value?.percent || 0) === 100)

  const generateCode = async () => {
    if (!isComplete.value || exporting.value) return
    exporting.value = true
    try {
      const response = await exportPersonaAppearanceCode()
      generatedCode.value = response.data?.code || ''
      if (generatedCode.value) ElMessage.success('橙子形象码已生成')
    } finally {
      exporting.value = false
    }
  }

  const copyCode = async () => {
    if (!generatedCode.value) return
    try {
      await navigator.clipboard.writeText(generatedCode.value)
      ElMessage.success('形象码已复制')
    } catch {
      ElMessage.warning('复制失败，请手动选择形象码')
    }
  }

  const loadPreview = async () => {
    const code = importCode.value.trim().toUpperCase()
    if (!code || previewing.value) return
    previewing.value = true
    try {
      const response = await previewPersonaAppearanceCode(code)
      preview.value = response.data || null
      importCode.value = response.data?.code || code
    } finally {
      previewing.value = false
    }
  }

  const applyCode = async () => {
    if (!preview.value?.readyToImport || importing.value) return
    await ElMessageBox.confirm('导入会覆盖当前橙子的外观、穿戴和 DIY 饰品，但不会修改任何画像内容。',
      '确认导入橙子形象', { confirmButtonText: '确认导入', cancelButtonText: '保留当前形象', type: 'warning' })
    importing.value = true
    try {
      await applyPersonaAppearanceCode(preview.value.code)
      ElMessage.success('橙子形象已一键生成')
      preview.value = null
      generatedCode.value = ''
      await afterImport?.()
    } finally {
      importing.value = false
    }
  }

  return { generatedCode, importCode, preview, exporting, previewing, importing,
    isComplete, generateCode, copyCode, loadPreview, applyCode }
}
