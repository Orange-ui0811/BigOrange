import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Briefcase, OfficeBuilding, Present, TopRight,
  Plus, Search, Trophy, Warning,
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'
import { getCompetitions, submitCompetition } from '../api/competition'
import { getDirections, getJobDetail, getJobsByDirection } from '../api/job'
import { DEFAULT_REWARDS, getRewardPricing } from '../api/rewards'
import { LEGAL_CONTACT } from '../content/legal'

export const useCareerCenter = () => {
const rewards = ref({ ...DEFAULT_REWARDS })
const rewardText = (amount, prefix) => amount > 0 ? `${prefix} ${amount} 配额奖励` : '当前暂不发放配额奖励'

const tourSteps = computed(() => [
  { selector: '.section-switch', text: '这里可以切换大赛列表和工作能力参考，两类信息彼此独立。', expression: 'smile' },
  { selector: '.competition-reward', text: rewardText(rewards.value.competition, '发现真实比赛并投稿，审核通过可获得'), expression: 'wink' },
  { selector: '.competition-grid', text: '卡片会告诉你比赛内容、举办方和时间，点击查看详情会打开官方页面。', expression: 'sparkle' },
])

const section = ref('competitions')
const competitionScope = ref('ACTIVE')
const competitions = ref([])
const competitionLoading = ref(false)
const competitionError = ref('')
const competitionPage = ref(1)
const competitionPageSize = 12
const competitionTotal = ref(0)
const competitionDialog = ref(false)
const competitionSubmitting = ref(false)
const blankCompetition = () => ({ name: '', sourceUrl: '', summary: '', organizer: '', startTime: '', endTime: '' })
const competitionForm = ref(blankCompetition())

const loadCompetitions = async () => {
  competitionLoading.value = true
  competitionError.value = ''
  try {
    const response = await getCompetitions(competitionScope.value, competitionPage.value, competitionPageSize)
    if (response.code !== 1) throw new Error(response.msg || '请求失败')
    competitions.value = response.data?.records || []
    competitionTotal.value = response.data?.total || 0
  } catch (error) {
    competitions.value = []
    competitionError.value = error?.response?.data?.msg || error?.message || '请稍后重试'
  } finally {
    competitionLoading.value = false
  }
}

const changeScope = scope => {
  competitionScope.value = scope
  competitionPage.value = 1
  loadCompetitions()
}

const phaseText = phase => ({ UPCOMING: '即将开始', ONGOING: '进行中', ENDED: '已结束', UNSCHEDULED: '时间待公布' }[phase] || '待确认')
const formatTime = value => {
  if (!value) return '待确认'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date)
}
const openCompetition = value => {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:') throw new Error()
    window.open(url.href, '_blank', 'noopener,noreferrer')
  } catch {
    ElMessage.error('比赛链接无效')
  }
}

const submitCompetitionForm = async () => {
  const form = competitionForm.value
  if (!form.name.trim() || !form.sourceUrl.trim() || !form.summary.trim() || !form.organizer.trim()) {
    ElMessage.warning('请完整填写比赛信息')
    return
  }
  if (!form.sourceUrl.toLowerCase().startsWith('https://')) {
    ElMessage.warning('官方网站必须使用 HTTPS')
    return
  }
  if (form.startTime && form.endTime && new Date(form.endTime) <= new Date(form.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  competitionSubmitting.value = true
  try {
    const response = await submitCompetition({
      ...form,
      startTime: form.startTime || null,
      endTime: form.endTime || null,
    })
    if (response.code !== 1) throw new Error(response.msg || '投稿失败')
    ElMessage.success(rewardText(rewards.value.competition, '投稿成功，审核通过后将奖励'))
    competitionDialog.value = false
    competitionForm.value = blankCompetition()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '投稿失败')
  } finally {
    competitionSubmitting.value = false
  }
}

const jobDirections = ref([])
const submitDirections = ref([])
const jobDirection = ref('')
const jobList = ref([])
const jobId = ref(null)
const jobDetail = ref('')
const jobDetailHtml = ref('')
const jobLoading = ref(false)
const jobDialog = ref(false)
const jobAgreed = ref(false)
const jobSubmitting = ref(false)
const jobForm = ref({ position: '', direction: '', content: '' })

const loadDirections = async () => {
  try {
    const response = await getDirections()
    jobDirections.value = response.data || []
    submitDirections.value = response.data?.length ? response.data : ['AI Agent', 'RAG', '前端开发', '后端开发', '产品设计']
  } catch {
    submitDirections.value = ['AI Agent', 'RAG', '前端开发', '后端开发', '产品设计']
  }
}
const selectDirection = async direction => {
  jobDirection.value = direction
  jobId.value = null
  jobDetail.value = ''
  jobDetailHtml.value = ''
  try {
    const response = await getJobsByDirection(direction)
    jobList.value = response.data || []
  } catch {
    jobList.value = []
    ElMessage.error('岗位列表加载失败')
  }
}
const selectJob = async id => {
  jobId.value = id
  jobLoading.value = true
  try {
    const response = await getJobDetail(id)
    jobDetail.value = response.data?.displayContent || ''
    jobDetailHtml.value = marked.parse(jobDetail.value)
  } catch {
    ElMessage.error('岗位详情加载失败')
  } finally {
    jobLoading.value = false
  }
}
const selectedJobName = computed(() => jobList.value.find(item => item.id === jobId.value)?.position || '')
const jobTechStack = computed(() => {
  if (!jobDetail.value) return ''
  const keywords = ['Python', 'Java', 'Go', 'Rust', 'TypeScript', 'JavaScript', 'Vue', 'React', 'Spring', 'AI', 'LLM', 'RAG']
  const found = keywords.filter(item => jobDetail.value.toLowerCase().includes(item.toLowerCase()))
  return found.length ? found.join(' / ') : '详见岗位描述'
})
const submitJobForm = async () => {
  if (!jobForm.value.position.trim() || !jobForm.value.direction || !jobForm.value.content.trim()) {
    ElMessage.warning('请完整填写岗位信息')
    return
  }
  jobSubmitting.value = true
  try {
    const response = await request.post('/app/job/submit', jobForm.value)
    if (response.code !== 1) throw new Error(response.msg || '投稿失败')
    ElMessage.success(rewardText(rewards.value.job, '投稿成功，审核通过后将奖励'))
    jobDialog.value = false
    jobAgreed.value = false
    jobForm.value = { position: '', direction: '', content: '' }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '投稿失败')
  } finally {
    jobSubmitting.value = false
  }
}

onMounted(() => {
  getRewardPricing().then(value => { rewards.value = value })
  loadCompetitions()
  loadDirections()
})
  return { PageTour, ArrowLeft, Briefcase, OfficeBuilding, Present, TopRight, Plus, Search, Trophy,
    Warning, LEGAL_CONTACT, rewards, rewardText, tourSteps, section, competitionScope, competitions,
    competitionLoading, competitionError, competitionPage, competitionPageSize, competitionTotal,
    competitionDialog, competitionSubmitting, competitionForm, loadCompetitions, changeScope,
    phaseText, formatTime, openCompetition, submitCompetitionForm, jobDirections, submitDirections,
    jobDirection, jobList, jobId, jobDetailHtml, jobLoading, jobDialog, jobAgreed, jobSubmitting,
    jobForm, selectDirection, selectJob, selectedJobName, jobTechStack, submitJobForm }}
