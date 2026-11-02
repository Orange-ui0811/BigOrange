import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { confirmQuotaUsage } from '../utils/quotaConfirm'
import { AI_AUTO_RETRY_NOTICE } from '../utils/aiRetry'

export const useTrendAnalysis = (profile, onHistorySaved) => {
  const analyzing = ref(false)
  const result = ref(null)
  const error = ref('')
  const juicing = ref(false)
  const squeezeText = ref('🍊 正在收集数据...')
  const trendV2Cost = ref(50)
  const pricingReady = ref(false)
  let squeezeTimer
  const squeezeTexts = ['🍊 正在榨取分析精华...', '🍊 找到了！为你生成推荐...']

  const startJuicing = async currentStep => {
    if (!profile.topics.length && !profile.usePersona) {
      error.value = '请先选择一个技术方向，或开启“使用橙子画像进行分析”'
      ElMessage.warning(error.value)
      currentStep.value = 0
      return
    }
    if (!(await loadPricing())) {
      error.value = '暂时无法获取最新配额定价，请稍后重试'
      ElMessage.warning(error.value)
      return
    }
    if (!(await confirmQuotaUsage({ actionLabel: '本次趋势分析', cost: trendV2Cost.value,
      title: '确认分析', confirmButtonText: '确认分析', type: 'info', estimatedTime: '1～3 分钟' }))) return
    juicing.value = true
    analyzing.value = true
    error.value = ''
    result.value = null
    squeezeText.value = '🍊 正在收集数据...'
    let index = 0
    squeezeTimer = setInterval(() => {
      index = (index + 1) % squeezeTexts.length
      squeezeText.value = squeezeTexts[index]
    }, 3000)
    await new Promise(resolve => setTimeout(resolve, 2500))
    juicing.value = false
    try {
      const response = await request.post('/app/trend/analyze-v2', {
        topics: Array.isArray(profile.topics) ? profile.topics : [], purpose: profile.purpose || '',
        experience: profile.experience || '', languages: Array.isArray(profile.languages) ? profile.languages : [],
        timeCommitment: profile.timeCommitment || '', customTopics: profile.customTopics || '',
        customPurpose: profile.customPurpose || '', customLang: profile.customLang || '',
        customTime: profile.customTime || '', useRag: profile.useRag, usePersona: profile.usePersona,
        preferBeginnerProjects: profile.preferBeginnerProjects,
        preferCareerProjects: profile.preferCareerProjects,
      }, { timeout: 0 })
      if (response.code === 1 && response.data) {
        result.value = response.data
        if (response.data.historySaved === false) {
          ElMessage.warning('分析已完成，但历史记录暂未保存，请复制结果后稍后重试')
        } else await onHistorySaved?.()
      } else error.value = response.msg || '分析失败'
    } catch { error.value = AI_AUTO_RETRY_NOTICE }
    finally { analyzing.value = false; juicing.value = false; clearInterval(squeezeTimer) }
  }

  const loadPricing = async () => {
    try {
      const response = await request.get('/app/config/ai-pricing')
      const v2 = Number(response.data?.trendV2)
      if (!Number.isFinite(v2)) throw new Error('invalid pricing response')
      trendV2Cost.value = v2
      pricingReady.value = true
      return true
    } catch {
      pricingReady.value = false
      return false
    }
  }
  const dispose = () => clearInterval(squeezeTimer)
  return { analyzing, v2Result: result, v2Error: error, juicing, squeezeText,
    trendV2Cost, pricingReady,
    startJuicing, loadPricing, dispose }
}
