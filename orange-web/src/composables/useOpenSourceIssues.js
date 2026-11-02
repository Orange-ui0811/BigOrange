import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const requestOk = response => response?.code === 1
const responseMessage = (response, fallback) => response?.msg || fallback

export const useOpenSourceIssues = selection => {
  const advancedFiltersOpen = ref(false)
  const loading = ref(false)
  const detailOpen = ref(false)
  const detailLoading = ref(false)
  const verifying = ref(false)
  const issues = ref([])
  const detail = ref(null)
  const detailId = ref(null)
  const page = ref(1)
  const total = ref(0)
  const issuesError = ref('')
  const detailError = ref('')
  const filters = ref({ keyword: '', tech: '', difficulty: '', participationStatus: '', updatedDays: 30 })
  const pageSize = 12
  let issuesRequestId = 0
  let detailRequestId = 0

  const loadIssues = async () => {
    const requestId = ++issuesRequestId
    loading.value = true
    issuesError.value = ''
    try {
      const response = await request.get('/app/open-source/issues', {
        params: { page: page.value, size: pageSize, ...filters.value },
      })
      if (requestId !== issuesRequestId) return
      if (!requestOk(response)) throw new Error(responseMessage(response, '服务暂时没有返回 Issue'))
      issues.value = response.data?.records || []
      total.value = Number(response.data?.total || 0)
      selection.hydrate(issues.value)
    } catch (error) {
      if (requestId === issuesRequestId) issuesError.value = error?.message || '请检查网络连接后重试'
    } finally {
      if (requestId === issuesRequestId) loading.value = false
    }
  }

  const searchIssues = () => {
    page.value = 1
    return loadIssues()
  }

  const resetFilters = () => {
    filters.value = { keyword: '', tech: '', difficulty: '', participationStatus: '', updatedDays: 30 }
    advancedFiltersOpen.value = false
    return searchIssues()
  }

  const openIssue = async id => {
    const requestId = ++detailRequestId
    detailId.value = id
    detailOpen.value = true
    detailLoading.value = true
    detailError.value = ''
    detail.value = null
    try {
      const response = await request.get(`/app/open-source/issues/${id}`)
      if (requestId !== detailRequestId) return
      if (!requestOk(response)) throw new Error(responseMessage(response, 'Issue 详情读取失败'))
      detail.value = response.data
      selection.hydrate([response.data])
    } catch (error) {
      if (requestId === detailRequestId) detailError.value = error?.message || '请稍后重试'
    } finally {
      if (requestId === detailRequestId) detailLoading.value = false
    }
  }

  const verifyCurrent = async () => {
    if (!detail.value?.id) return
    verifying.value = true
    try {
      const response = await request.post(`/app/open-source/issues/${detail.value.id}/verify`, {}, { timeout: 90000 })
      if (!requestOk(response)) throw new Error(responseMessage(response, '核验失败'))
      detail.value = response.data
      selection.hydrate([response.data])
      ElMessage.success('已按 GitHub 当前状态重新核验')
    } catch (error) {
      ElMessage.error(error?.message || '实时核验失败')
    } finally {
      verifying.value = false
    }
  }

  return {
    advancedFiltersOpen, loading, detailOpen, detailLoading, verifying, issues, detail,
    detailId, page, total, issuesError, detailError, filters, pageSize,
    loadIssues, searchIssues, resetFilters, openIssue, verifyCurrent,
  }
}
