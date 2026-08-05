import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { AI_AUTO_RETRY_NOTICE } from '../utils/aiRetry'

const requestOk = response => response?.code === 1

export const useContributionGuides = ({ config, confirmCharge, closeDetail, openGuides }) => {
  const guideGenerating = ref(false)
  const guidesLoading = ref(false)
  const guideLoading = ref(false)
  const guideDeletingId = ref(null)
  const guides = ref([])
  const currentGuide = ref(null)
  const guidesError = ref('')
  const guideError = ref('')
  let guidesRequestId = 0
  let guideRequestId = 0

  const openGuide = async id => {
    const requestId = ++guideRequestId
    guideLoading.value = true
    guideError.value = ''
    try {
      const response = await request.get(`/app/open-source/guides/${id}`)
      if (requestId !== guideRequestId) return
      if (!requestOk(response)) throw new Error(response?.msg || '指南内容读取失败')
      currentGuide.value = response.data
    } catch (error) {
      if (requestId === guideRequestId) guideError.value = error?.message || '请稍后重试'
    } finally {
      if (requestId === guideRequestId) guideLoading.value = false
    }
  }

  const loadGuides = async (selectFirst = true) => {
    const requestId = ++guidesRequestId
    guidesLoading.value = true
    guidesError.value = ''
    try {
      const response = await request.get('/app/open-source/guides')
      if (requestId !== guidesRequestId) return
      if (!requestOk(response)) throw new Error(response?.msg || '指南列表读取失败')
      guides.value = response.data || []
      if (selectFirst && guides.value.length && !currentGuide.value) await openGuide(guides.value[0].id)
    } catch (error) {
      if (requestId === guidesRequestId) guidesError.value = error?.message || '请检查网络连接后重试'
    } finally {
      if (requestId === guidesRequestId) guidesLoading.value = false
    }
  }

  const generateGuideFor = async issue => {
    if (guideGenerating.value) return
    if (!issue?.id) return
    if (!(await confirmCharge('生成贡献指南', config.value.guideCost, '2～4 分钟'))) return
    guideGenerating.value = true
    try {
      const response = await request.post(`/app/open-source/issues/${issue.id}/guide`, {}, { timeout: 0 })
      if (!requestOk(response)) throw new Error(response?.msg || 'AI 未返回贡献指南')
      currentGuide.value = response.data?.guide
      closeDetail()
      await loadGuides(false)
      openGuides()
      ElMessage.success('贡献指南已生成')
    } catch (error) {
      ElMessage.info(error?.response?.data?.msg || AI_AUTO_RETRY_NOTICE)
    } finally {
      guideGenerating.value = false
    }
  }

  const deleteGuide = async guide => {
    if (guideDeletingId.value) return
    await ElMessageBox.confirm(`删除“${guide.issueTitle || '这份指南'}”？删除后无法恢复。`,
      '删除贡献指南', { type: 'warning' })
    guideDeletingId.value = guide.id
    try {
      await request.delete(`/app/open-source/guides/${guide.id}`)
      if (currentGuide.value?.id === guide.id) currentGuide.value = null
      await loadGuides(false)
      if (!currentGuide.value && guides.value.length) await openGuide(guides.value[0].id)
      ElMessage.success('贡献指南已删除')
    } finally {
      guideDeletingId.value = null
    }
  }

  return {
    guideGenerating, guidesLoading, guideLoading, guideDeletingId, guides, currentGuide,
    guidesError, guideError, openGuide, loadGuides, generateGuideFor, deleteGuide,
  }
}
