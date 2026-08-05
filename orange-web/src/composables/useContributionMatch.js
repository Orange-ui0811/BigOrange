import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { confirmQuotaUsage } from '../utils/quotaConfirm'
import { AI_AUTO_RETRY_NOTICE } from '../utils/aiRetry'

const requestOk = response => response?.code === 1
const hasPrice = value => value !== null && value !== '' && Number.isFinite(Number(value))

export const useContributionMatch = selection => {
  const matching = ref(false)
  const jobMatching = ref(false)
  const matches = ref([])
  const jobMatches = ref([])
  const recommendedIssueId = ref(null)
  const jobTarget = ref(null)
  const matchError = ref('')
  const jobError = ref('')
  const matchForm = ref({ profile: '', tech: '', difficulty: '' })
  const config = ref({ matchCost: null, jobMatchCost: null, guideCost: null })
  const configError = ref('')
  const pricingReady = computed(() => Object.values(config.value).every(hasPrice))
  const jobCandidates = computed(() => {
    const byId = new Map()
    selection.candidates.value.forEach(issue => byId.set(Number(issue.id), issue))
    matches.value.map(item => item.issue).filter(Boolean).forEach(issue => byId.set(Number(issue.id), issue))
    return [...byId.values()]
  })
  const candidateSourceText = computed(() => selection.selectedIds.value.length >= 3
    ? `将比较你保留的 ${selection.selectedIds.value.length} 个候选 Issue。`
    : '候选不足 3 项时，将结合当前筛选条件寻找合适任务。')

  const confirmCharge = async (title, cost, estimatedTime) => {
    if (!hasPrice(cost)) {
      ElMessage.warning('最新配额仍在读取，请稍后再试')
      return false
    }
    return confirmQuotaUsage({
      actionLabel: title,
      cost: Number(cost),
      title: '确认使用额度',
      confirmButtonText: '确认开始',
      cancelButtonText: '暂不开始',
      notice: '确认后才会开始调用 AI。',
      estimatedTime,
    })
  }

  const appendProfilePreset = preset => {
    if (matchForm.value.profile.includes(preset)) return
    matchForm.value.profile = [matchForm.value.profile.trim(), preset].filter(Boolean).join('；')
  }

  const runMatch = async () => {
    if (matching.value) return
    if (matchForm.value.profile.trim().length < 10) {
      ElMessage.warning('请先简要描述你的技术能力、时间或环境限制')
      return
    }
    if (!(await confirmCharge('AI 匹配', config.value.matchCost, '1～3 分钟'))) return
    matching.value = true
    matchError.value = ''
    matches.value = []
    try {
      const response = await request.post('/app/open-source/match', {
        ...matchForm.value,
        selectedIssueIds: selection.selectedIds.value,
      }, { timeout: 0 })
      if (!requestOk(response)) throw new Error(response?.msg || 'AI 未返回匹配结果')
      matches.value = response.data?.matches || []
      recommendedIssueId.value = response.data?.recommendedIssueId
      matches.value.map(item => item.issue).filter(Boolean).forEach(issue => {
        if (!selection.includes(issue.id) && selection.candidates.value.length < selection.maxCandidates) {
          selection.toggle(issue)
        }
      })
      ElMessage.success('匹配完成')
    } catch (error) {
      matchError.value = error?.response?.data?.msg || AI_AUTO_RETRY_NOTICE
    } finally {
      matching.value = false
    }
  }

  const selectJobTarget = issue => {
    jobTarget.value = issue
    jobMatches.value = []
    jobError.value = ''
  }

  const runJobMatchFor = async issue => {
    if (jobMatching.value) return
    if (!issue?.id) return
    if (!(await confirmCharge('岗位匹配', config.value.jobMatchCost, '1～3 分钟'))) return
    selectJobTarget(issue)
    jobMatching.value = true
    try {
      const response = await request.post(`/app/open-source/issues/${issue.id}/job-match`, {}, { timeout: 0 })
      if (!requestOk(response)) throw new Error(response?.msg || 'AI 未返回岗位匹配结果')
      jobMatches.value = response.data?.matches || []
    } catch (error) {
      jobError.value = error?.response?.data?.msg || AI_AUTO_RETRY_NOTICE
    } finally {
      jobMatching.value = false
    }
  }

  const loadConfig = async () => {
    try {
      const response = await request.get('/app/open-source/config')
      if (!requestOk(response)) throw new Error(response?.msg || '配额读取失败')
      config.value = {
        matchCost: response.data?.matchCost ?? null,
        jobMatchCost: response.data?.jobMatchCost ?? null,
        guideCost: response.data?.guideCost ?? null,
      }
      if (!pricingReady.value) throw new Error('配额配置不完整')
    } catch (error) {
      configError.value = error?.message || '配额配置读取失败'
    }
  }

  return {
    matching, jobMatching, matches, jobMatches, recommendedIssueId, jobTarget, matchError,
    jobError, matchForm, config, configError, pricingReady, jobCandidates, candidateSourceText,
    confirmCharge, appendProfilePreset, runMatch, selectJobTarget, runJobMatchFor, loadConfig,
  }
}
