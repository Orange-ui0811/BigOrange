import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import request from '../api/request'
import { confirmQuotaUsage } from '../utils/quotaConfirm'
import { AI_AUTO_RETRY_NOTICE } from '../utils/aiRetry'

export const usePlanDesigner = () => {
const router = useRouter()

const tourSteps = [
  { selector: '.plan-main textarea, .plan-main .el-textarea__inner', text: '在这里写下你的项目想法！描述清楚想做什么、有什么特殊需求，AI 会帮你规划得更精准～', expression: 'smile' },
  { selector: '.mode-select-row', text: '选「详细模式」让 AI 深度生成完整的六阶段方案，选「精简模式」快速出框架。', expression: 'wink' },
  { selector: '.phase-nav', text: '这六个阶段是 AI 帮你一步步规划的：想法→需求→标准→风险→任务→匹配。点任意阶段可以切换查看～', expression: 'sparkle' },
  { selector: '.btn-row', text: '不满意？点「编辑」自己改内容，点「重新生成」让 AI 再来一次，点「保存方案」存到记录里随时回看。', expression: 'smile' },
]

const summary = ref('')
const extra = ref('')
const started = ref(false)
const planMode = ref('full')
const currentPhase = ref(1)
const phaseError = ref('')
const planStageCost = ref(null)
const jobMatchCost = ref(null)

const phases = reactive([
  { title: '想法描述', done: false, generating: false, content: '' },
  { title: '需求理解', done: false, generating: false, content: '' },
  { title: '成功标准', done: false, generating: false, content: '' },
  { title: '约束风险', done: false, generating: false, content: '' },
  { title: '任务计划', done: false, generating: false, content: '' },
  { title: '岗位匹配', done: false, generating: false, content: '' }
])
const phaseSubtitles = [
  '描述你的项目想法、技术偏好和限制条件',
  '准确复述需求，识别核心模块，划定 MVP 边界',
  '定义可验证的成功标准和完成证据',
  '识别技术约束、业务风险和明确不做的事',
  '拆解为可落地的步骤，生成执行计划',
  '基于方案匹配最适合的AI实习岗位'
]

const renderedContent = computed(() => {
  const c = phases[currentPhase.value-1]?.content || ''
  return marked.parse(c)
})

const getPrevContents = (n) => {
  const parts = []
  for (let i = 0; i < n-1; i++) {
    if (phases[i].done) {
      parts.push(`【阶段${i}：${phases[i].title}】\n${phases[i].content}`)
    }
  }
  return parts.join('\n\n')
}

const doJobMatch = async () => {
  if (!Number.isFinite(jobMatchCost.value)) {
    ElMessage.error('岗位匹配价格尚未加载，请刷新页面后重试')
    return
  }
  const planContent = phases[4].content + '\n\n方案概要：' + phases[0].content.substring(0, 200)
  if (!(await confirmQuotaUsage({
    actionLabel: 'AI 岗位匹配',
    cost: jobMatchCost.value,
    title: '确认岗位匹配',
    confirmButtonText: '确认匹配',
    type: 'info',
    estimatedTime: '1～3 分钟',
  }))) return
  const p5 = phases[5]; p5.generating = true
  try {
    const r = await request.post('/app/job/match', {
      planContent,
      historyId: activePlanId.value
    }, { timeout: 0 })
    if (r.code === 1 && Array.isArray(r.data?.matches) && r.data.matches.length) {
      const matches = r.data.matches
      let content = ''
      for (const m of matches) {
        content += `### ${m.position}\n\n📌 **匹配原因**：${m.reason}\n\n📚 **学习重点**：${m.focus}\n\n---\n\n`
      }
      p5.content = content; p5.done = true
      activePlanId.value = r.data.historyId || activePlanId.value
      await persistPlan(true)
    } else { ElMessage.error(r.msg || '匹配失败') }
  } catch { ElMessage.info(AI_AUTO_RETRY_NOTICE) }
  finally { p5.generating = false }
}

const generatePhase = async (n) => {
  if (n === undefined || n === null) n = currentPhase.value
  if (n === 1) {
    phases[0].content = summary.value + (extra.value ? '\n\n补充：' + extra.value : '')
    phases[0].done = true
    await persistPlan(true)
    return
  }
  if (n === 6) { await doJobMatch(); return }
  if (!Number.isFinite(planStageCost.value)) {
    ElMessage.error('方案设计价格尚未加载，请刷新页面后重试')
    return
  }
  if (!(await confirmQuotaUsage({
    actionLabel: `生成“${phases[n - 1]?.title || '本阶段'}”`,
    cost: planStageCost.value,
    title: '确认生成',
    confirmButtonText: '确认生成',
    type: 'info',
    estimatedTime: '2～4 分钟',
  }))) return
  const p = phases[n-1]
  if (!p) { phaseError.value = 'Phase ' + n + ' 不存在, phases长度=' + phases.length; return }
  p.generating = true; phaseError.value = ''
  try {
    const r = await request.post('/app/plan/generate', {
      phase: n-1,
      mode: planMode.value,
      summary: summary.value,
      extra: extra.value,
      prevContents: getPrevContents(n),
      historyId: activePlanId.value,
      phase1: phases[0].content,
      phase2: phases[1].content,
      phase3: phases[2].content,
      phase4: phases[3].content,
      phase5: phases[4].content,
      phase6: phases[5].content
    }, { timeout: 0 })
    if (r.code === 1 && r.data?.content) {
      p.content = r.data.content
      p.done = true
      activePlanId.value = r.data.historyId || activePlanId.value
      await loadPlanHistory()
    } else {
      phaseError.value = r.msg || '生成失败'
    }
  } catch (e) {
    phaseError.value = e.response?.data?.msg || AI_AUTO_RETRY_NOTICE
  } finally { p.generating = false }
}

const editing = ref(false)
const editContent = ref('')

const startEdit = () => {
  editContent.value = phases[currentPhase.value - 1].content
  editing.value = true
}
const saveEdit = async () => {
  phases[currentPhase.value - 1].content = editContent.value
  editing.value = false
  await persistPlan(true)
}
const cancelEdit = () => { editing.value = false }

const cleanProjectName = (value) => {
  const firstLine = String(value || '').split(/\r?\n/).find(line => line.trim()) || '未命名项目'
  return firstLine.replace(/^#+\s*/, '').replace(/[*_`]/g, '').trim().slice(0, 128) || '未命名项目'
}

const importToLaunchCheck = () => {
  const sections = [
    ['项目概要', summary.value],
    ['需求理解', phases[1].content],
    ['成功标准', phases[2].content],
    ['约束风险', phases[3].content],
    ['任务计划', phases[4].content]
  ]
  const projectSummary = sections
    .filter(([, content]) => String(content || '').trim())
    .map(([title, content]) => `## ${title}\n${String(content).trim()}`)
    .join('\n\n')
    .slice(0, 10000)

  sessionStorage.setItem('launch_check_import', JSON.stringify({
    projectName: cleanProjectName(summary.value),
    projectSummary
  }))
  ElMessage.success('任务计划已整理，正在前往上线体检')
  router.push('/launch-check')
}

const hasToken = computed(() => !!sessionStorage.getItem('appToken'))
const planHistory = ref([])
const planSaving = ref(false)
const activePlanId = ref(null)
const allDone = computed(() => phases.slice(0,5).every(p => p.done))

const loadPlanHistory = async () => {
  if (!hasToken.value) return
  try {
    const r = await request.get('/app/plan/history', { params: { limit: 200 } })
    if (r.code === 1) planHistory.value = r.data
  } catch {}
}
const historyPayload = () => ({
  historyId: activePlanId.value,
  summary: summary.value,
  phase1: phases[0].content,
  phase2: phases[1].content,
  phase3: phases[2].content,
  phase4: phases[3].content,
  phase5: phases[4].content,
  phase6: phases[5].content
})

const persistPlan = async (silent = true) => {
  if (!hasToken.value || !phases.some(phase => phase.done) || planSaving.value) return false
  planSaving.value = true
  try {
    const response = await request.post('/app/plan/history/save', historyPayload())
    if (response.code !== 1) return false
    activePlanId.value = response.data?.id || activePlanId.value
    await loadPlanHistory()
    if (!silent) ElMessage.success('方案已保存')
    return true
  } catch {
    ElMessage.error('方案自动保存失败，请稍后重试')
    return false
  }
  finally { planSaving.value = false }
}
const savePlan = () => persistPlan(false)
const loadPlan = (h) => {
  activePlanId.value = h.id || null
  summary.value = h.summary || ''
  started.value = true
  for (const phase of phases) {
    phase.content = ''
    phase.done = false
    phase.generating = false
  }
  if (h.phase1) { phases[0].content = h.phase1; phases[0].done = true }
  if (h.phase2) { phases[1].content = h.phase2; phases[1].done = true }
  if (h.phase3) { phases[2].content = h.phase3; phases[2].done = true }
  if (h.phase4) { phases[3].content = h.phase4; phases[3].done = true }
  if (h.phase5) { phases[4].content = h.phase5; phases[4].done = true }
  if (h.phase6) { phases[5].content = h.phase6; phases[5].done = true }
  currentPhase.value = 1
}
const delPlanHistory = async (i, id) => {
  try {
    await request.delete('/app/plan/history/' + id)
    if (activePlanId.value === id) activePlanId.value = null
    await loadPlanHistory()
  } catch {}
}
const clearPlanHistory = async () => {
  try {
    await ElMessageBox.confirm('清空后无法恢复全部方案记录。', '清空方案记录', { type: 'warning' })
    await request.delete('/app/plan/history/clear')
    planHistory.value = []
    activePlanId.value = null
    ElMessage.success('方案记录已清空')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('清空失败')
  }
}

onMounted(async () => {
  try {
    const response = await request.get('/app/config/ai-pricing')
    if (Number.isFinite(Number(response.data?.plan))) {
      planStageCost.value = Number(response.data.plan)
    }
    if (Number.isFinite(Number(response.data?.jobMatch))) {
      jobMatchCost.value = Number(response.data.jobMatch)
    }
  } catch {
    planStageCost.value = null
    jobMatchCost.value = null
  }
  loadPlanHistory()
  const imported = sessionStorage.getItem('plan_import')
  if (imported) {
    summary.value = imported
    sessionStorage.removeItem('plan_import')
  }
})

const switchPhase = (n) => {
  currentPhase.value = n
  phaseError.value = ''
  editing.value = false
}
  return { PageTour, tourSteps, summary, extra, planMode, currentPhase, phaseError,
    planStageCost, phases, phaseSubtitles, renderedContent,
    generatePhase, editing, editContent, startEdit, saveEdit, cancelEdit, importToLaunchCheck,
    hasToken, planHistory, planSaving, allDone, savePlan, loadPlan, delPlanHistory,
    clearPlanHistory, switchPhase }}
