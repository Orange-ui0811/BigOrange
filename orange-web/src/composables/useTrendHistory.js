import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

export const useTrendHistory = ({ profile, result, currentStep }) => {
  const history = ref([])
  const hasAppToken = computed(() => Boolean(sessionStorage.getItem('appToken')))
  const loadHistoryFromDB = async () => {
    if (!hasAppToken.value) return
    try {
      const response = await request.get('/app/trend/history', { params: { limit: 200 } })
      if (response.code === 1) history.value = (response.data || []).map(item => ({
        ...item, time: item.createTime,
      }))
    } catch { ElMessage.error('趋势记录加载失败，请稍后重试') }
  }
  const delHistory = async index => {
    try {
      const response = await request.delete(`/app/trend/history/${history.value[index].id}`)
      if (response.code === 1) await loadHistoryFromDB()
    } catch { ElMessage.error('删除记录失败') }
  }
  const clearHistory = async () => {
    try {
      await ElMessageBox.confirm('清空全部趋势分析记录？此操作不可撤销。', '清空记录', {
        type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消',
      })
      const response = await request.delete('/app/trend/history/clear')
      if (response.code === 1) history.value = []
    } catch { /* cancellation keeps history unchanged */ }
  }
  const loadHistory = item => {
    if (item.corrupted || !item.result) {
      ElMessage.error('这条历史记录已损坏，无法恢复；你仍可单独删除它')
      return
    }
    result.value = item.result
    for (const key of ['purpose', 'experience', 'timeCommitment', 'customTopics', 'customPurpose',
      'customExp', 'customLang', 'customTime']) profile[key] = item[key] || ''
    profile.topics = Array.isArray(item.topics) ? item.topics : []
    profile.languages = Array.isArray(item.languages) ? item.languages : []
    profile.useRag = item.useRag !== false
    profile.preferBeginnerProjects = item.preferBeginnerProjects === true
    profile.preferCareerProjects = item.preferCareerProjects === true
    currentStep.value = 3
  }
  return { history, hasAppToken, loadHistoryFromDB, delHistory, clearHistory, loadHistory }
}
