import { nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePersonaStore } from '../stores/personaStore'
import { pinia } from '../stores'
import {
  acceptPersonaMaterial, createPersonaEntry, deletePersonaEntry,
  rejectPersonaMaterial, updatePersonaEntry,
} from '../api/personaWorkshop'

export const usePersonaWorkshop = ({ afterLoad } = {}) => {
  const store = usePersonaStore(pinia)
  const snapshot = store.data
  const editVisible = ref(false)
  const editForm = reactive({ id: null, dimension: 'GOAL', content: '' })
  const drafts = reactive({ GOAL: '', INTEREST: '', SKILL: '', RESOURCE: '', GROWTH: '' })
  const materialDrafts = reactive({})

  const load = async () => {
    const response = await store.load()
    if (response?.code === 1) {
      snapshot.materials?.forEach(item => { materialDrafts[item.id] = item.suggestedContent })
      await nextTick()
      afterLoad?.()
    }
    return response
  }

  const addEntry = async dimension => {
    const content = drafts[dimension]?.trim()
    if (!content || store.saving) return
    store.saving = true
    try {
      await createPersonaEntry({ dimension, content })
      drafts[dimension] = ''
      ElMessage.success('已加入画像，对应装备现在可以解锁')
      await load()
    } finally { store.saving = false }
  }

  const openEdit = entry => {
    Object.assign(editForm, { id: entry.id, dimension: entry.dimensionCode, content: entry.content })
    editVisible.value = true
  }
  const saveEdit = async () => {
    if (store.saving) return
    store.saving = true
    try {
      await updatePersonaEntry(editForm.id, { dimension: editForm.dimension, content: editForm.content.trim() })
      editVisible.value = false
      ElMessage.success('画像内容已更新')
      await load()
    } finally { store.saving = false }
  }
  const removeEntry = async entry => {
    try {
      await ElMessageBox.confirm('删除后这条内容不再参与画像；如果该维度没有其他内容，对应装备会自动收起。',
        '删除画像内容', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '保留' })
      await deletePersonaEntry(entry.id)
      ElMessage.success('画像内容已删除')
      await load()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') throw error
    }
  }
  const acceptMaterial = async material => {
    await acceptPersonaMaterial(material.id, materialDrafts[material.id]?.trim())
    ElMessage.success('素材已由你确认并加入画像')
    await load()
  }
  const rejectMaterial = async material => {
    await rejectPersonaMaterial(material.id)
    ElMessage.success('已忽略这条素材')
    await load()
  }

  return { store, snapshot, editVisible, editForm, drafts,
    materialDrafts, load, addEntry, openEdit, saveEdit, removeEntry, acceptMaterial, rejectMaterial }
}
