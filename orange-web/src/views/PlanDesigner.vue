<template>
  <div class="plan-app">
    <PageTour storageKey="plan" :steps="tourSteps" />
    <div class="plan-main">
      <!-- Compact Head -->
      <div class="compact-head">
        <div>
          <span class="eyebrow">方案设计</span>
          <h2>从一个想法，生成六阶段项目方案。</h2>
          <p>AI 驱动项目规划：想法、需求、成功标准、风险、任务、岗位匹配。</p>
        </div>
        <el-popover placement="bottom-end" width="320" trigger="click" v-if="hasToken">
          <template #reference><el-button size="small">记录 ({{ planHistory.length }})</el-button></template>
          <div class="history-popover">
            <div v-if="!planHistory.length" class="history-empty">暂无记录</div>
            <div v-for="(h,i) in planHistory" :key="i" class="history-item" @click="loadPlan(h)">
              <div class="history-item-text">
                <span class="history-summary">{{ (h.summary||'').substring(0,40) }}</span>
                <span class="history-time">{{ (h.createTime||'').substring(0,16) }}</span>
              </div>
              <el-button size="small" text type="danger" @click.stop="delPlanHistory(i,h.id)">✕</el-button>
            </div>
          </div>
        </el-popover>
      </div>

      <!-- 阶段导航始终可见 -->
      <div class="phase-nav">
        <div v-for="(p, i) in phases" :key="i"
          class="phase-nav-item" :class="{ active: currentPhase === i+1, done: p.done, generating: p.generating }"
          @click="switchPhase(i+1)">
          <div class="phase-dot">{{ p.done ? '✓' : i+1 }}</div>
          <div>
            <div class="phase-nav-label">{{ p.title }}</div>
            <div class="phase-nav-status" v-if="p.generating">生成中...</div>
            <div class="phase-nav-status" v-else-if="!p.done && currentPhase !== i+1">待生成</div>
          </div>
        </div>
      </div>

      <div v-if="allDone && hasToken" class="save-row">
        <button class="orange-btn save-btn" @click="savePlan">保存方案到我的记录</button>
      </div>

      <div class="phase-content">
        <div class="phase-title">{{ phases[currentPhase-1]?.title }}</div>
        <div class="phase-subtitle">{{ phaseSubtitles[currentPhase-1] }}</div>

        <!-- Phase 0 输入区 -->
        <div v-if="currentPhase === 1 && !phases[0].done" class="card">
          <el-input v-model="summary" type="textarea" :rows="4" placeholder="描述你的项目想法，越详细越好..." :autosize="{minRows:4,maxRows:12}" />
          <el-input v-model="extra" type="textarea" :rows="2" placeholder="补充信息（可选）：技术偏好、时间限制、特殊需求..." class="mt-12" :autosize="{minRows:2,maxRows:8}" />
          <div class="mode-select-row">
            <span class="mode-label">生成模式</span>
            <el-radio-group v-model="planMode" size="small">
              <el-radio-button value="full">详细方案</el-radio-button>
              <el-radio-button value="lite">精简方案</el-radio-button>
            </el-radio-group>
          </div>
          <button class="orange-btn mt-16" @click="generatePhase(1)" :disabled="!summary.trim()">确认想法</button>
        </div>

        <!-- 未生成状态 -->
        <div v-else-if="!phases[currentPhase-1]?.done && !phases[currentPhase-1]?.generating" class="card">
          <p class="card-placeholder">点击下方按钮，AI 将基于项目描述生成此阶段的分析内容。</p>
          <button class="orange-btn mt-16" @click="generatePhase(currentPhase)">生成本阶段</button>
        </div>

        <!-- 生成中 -->
        <div v-if="phases[currentPhase-1]?.generating" class="card generating-card">
          <span class="generating-spinner">🍊</span>
          <p class="generating-text">AI 正在分析和生成...</p>
        </div>

        <!-- 已生成 -->
        <div v-if="phases[currentPhase-1]?.done" class="card result-card">
          <div v-if="!editing" class="result-content" v-safe-html="renderedContent" @dblclick="startEdit"></div>
          <el-input v-else v-model="editContent" type="textarea" :rows="12" class="mb-12" />
          <div class="btn-row">
            <template v-if="!editing">
              <button class="orange-btn" @click="generatePhase(currentPhase)">重新生成</button>
              <button class="nav-btn" @click="startEdit">编辑</button>
              <button v-if="currentPhase === 5" class="nav-btn import-launch-btn" @click="importToLaunchCheck">
                一键导入上线体检
              </button>
              <button v-if="currentPhase < 6" class="nav-btn nav-btn-next" @click="switchPhase(currentPhase+1)">下一步 →</button>
            </template>
            <template v-else>
              <button class="orange-btn" @click="saveEdit">保存修改</button>
              <button class="nav-btn" @click="cancelEdit">取消</button>
            </template>
          </div>
        </div>

        <!-- 报错 -->
        <div v-if="phaseError" class="card error-card">
          <p class="error-text">{{ phaseError }}</p>
          <button class="orange-btn mt-8" @click="generatePhase(currentPhase)">重试</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { h, ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import PixelOrangeSlice from '../components/PixelOrangeSlice.vue'
import request from '../api/request'

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
const matchLoading = ref(false)
const matchResult = ref(null)

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

const startPlan = () => {
  if (!summary.value.trim()) return
  started.value = true
}

const autoGenerateNext = () => {
  const p = phases[currentPhase.value]
  if (p && !p.done) generatePhase(currentPhase)
}

const getPrevContents = (n) => {
  const parts = []
  for (let i = 0; i < n-1; i++) {
    if (phases[i].done) {
      parts.push(`【阶段${i}：${phases[i].title}】\n${phases[i].content}`)
    }
  }
  return parts.join('\n\n')
}

const onPhaseJump = (n) => { phaseError.value = ''; matchResult.value = null }

const doJobMatch = async () => {
  const planContent = phases[4].content + '\n\n方案概要：' + phases[0].content.substring(0, 200)
  try { await ElMessageBox.confirm(
    h('span', { style: 'display:flex;align-items:center;gap:6px' }, ['匹配岗位将消耗 50 ', h(PixelOrangeSlice, { size: 16 })]),
    '确认', { confirmButtonText:'确认',cancelButtonText:'取消',type:'info' }
  ) } catch { return }
  const p5 = phases[5]; p5.generating = true
  try {
    const r = await request.post('/app/job/match', { planContent: planContent }, { timeout: 120000 })
    if (r.code === 1 && Array.isArray(r.data?.matches) && r.data.matches.length) {
      const matches = r.data.matches
      let content = ''
      for (const m of matches) {
        content += `### ${m.position}\n\n📌 **匹配原因**：${m.reason}\n\n📚 **学习重点**：${m.focus}\n\n---\n\n`
      }
      p5.content = content; p5.done = true
    } else { ElMessage.error(r.msg || '匹配失败') }
  } catch (e) { ElMessage.error(e.response?.data?.msg || '匹配失败，本次配额会自动退回') }
  finally { p5.generating = false }
}

const generatePhase = async (n) => {
  if (n === undefined || n === null) n = currentPhase.value
  if (n === 1) {
    phases[0].content = summary.value + (extra.value ? '\n\n补充：' + extra.value : '')
    phases[0].done = true
    return
  }
  if (n === 6) { await doJobMatch(); return }
  try { await ElMessageBox.confirm(
    h('span', { style: 'display:flex;align-items:center;gap:6px' }, ['本阶段将消耗 40 ', h(PixelOrangeSlice, { size: 16 })]),
    '确认生成', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
  ) } catch { return }
  const p = phases[n-1]
  if (!p) { phaseError.value = 'Phase ' + n + ' 不存在, phases长度=' + phases.length; return }
  p.generating = true; phaseError.value = ''
  try {
    const r = await request.post('/app/plan/generate', {
      phase: n-1,
      mode: planMode.value,
      summary: summary.value,
      extra: extra.value,
      prevContents: getPrevContents(n)
    }, { timeout: 240000 })
    if (r.code === 1 && r.data?.content) {
      p.content = r.data.content
      p.done = true
    } else {
      phaseError.value = r.msg || '生成失败'
    }
  } catch (e) {
    const status = e.response?.status
    if (status === 502 || status === 504) {
      phaseError.value = 'AI 生成等待超时，请稍后重试；本次配额会自动退回'
    } else if (e.response?.data?.msg) {
      phaseError.value = e.response.data.msg
    } else {
      phaseError.value = '请求未完成，请检查网络后重试'
    }
  } finally { p.generating = false }
}

const editing = ref(false)
const editContent = ref('')

const startEdit = () => {
  editContent.value = phases[currentPhase.value - 1].content
  editing.value = true
}
const saveEdit = () => {
  phases[currentPhase.value - 1].content = editContent.value
  editing.value = false
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
const allDone = computed(() => phases.slice(0,5).every(p => p.done))

const loadPlanHistory = async () => {
  if (!hasToken.value) return
  try { const r = await request.get('/app/plan/history'); if (r.code === 1) planHistory.value = r.data } catch {}
}
const savePlan = async () => {
  if (!hasToken.value || !allDone.value) return
  try {
    const response = await request.post('/app/plan/history/save', {
      summary: summary.value, phase1: phases[0].content, phase2: phases[1].content,
      phase3: phases[2].content, phase4: phases[3].content,
      phase5: phases[4].content, phase6: phases[5].content
    })
    if (response.code !== 1) return
    await loadPlanHistory()
    ElMessage.success('方案已保存')
  } catch { ElMessage.error('保存失败') }
}
const loadPlan = (h) => {
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
  try { await request.delete('/app/plan/history/' + id); loadPlanHistory() } catch {}
}
const clearPlanHistory = async () => {
  try { await request.delete('/app/plan/history/clear'); planHistory.value = [] } catch {}
}

onMounted(() => {
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
</script>

<style scoped>
/* ============================================================
   PIXEL-MINIMALIST DESIGN SYSTEM — PlanDesigner
   ============================================================ */

/* --- APP SHELL --- */
.plan-app {
  display: flex;
  min-height: calc(100vh - 136px);
  background: transparent;
  overflow: hidden;
}
.plan-main {
  flex: 1;
  padding: 0;
  max-width: 980px;
  margin: 0 auto;
  overflow-y: auto;
}

/* --- COMPACT HEAD --- */
.compact-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
  padding: 22px;
  border: 1px solid rgba(254, 215, 170, .76);
  border-radius: 28px;
  background: rgba(255,255,255,.86);
  box-shadow: var(--shadow-card-lg);
  flex-wrap: wrap;
}
.compact-head h2 {
  margin: 0;
  font-family: var(--font-mono);
  font-size: clamp(22px, 3.5vw, 32px);
  line-height: 1.08;
  letter-spacing: -.05em;
}
.compact-head p {
  max-width: 680px;
  margin: 6px 0 0;
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 13px;
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid var(--border-orange);
  border-radius: 999px;
  color: var(--brand-strong);
  background: #fffaf5;
  font-size: 12px;
  font-weight: 900;
  font-family: var(--font-mono);
}

/* --- HISTORY POPOVER --- */
.history-popover {
  max-height: 300px;
  overflow: auto;
}
.history-empty {
  text-align: center;
  color: #bfb8b0;
  padding: 20px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}
.history-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0ece6;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
}
.history-item-text {
  flex: 1;
  min-width: 0;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 12px;
}
.history-summary {
  font-family: "Courier New", "SimHei", monospace;
  color: #3a302a;
  font-weight: 500;
}
.history-time {
  color: #bfb8b0;
  margin-left: 6px;
  font-size: 11px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

/* --- SAVE ROW --- */
.save-row {
  text-align: center;
  margin-bottom: 8px;
}

/* --- PHASE NAV --- */
.phase-nav {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  padding: 4px;
  border: 1px solid var(--border-orange);
  border-radius: 14px;
  background: #fff7ed;
  overflow: hidden;
}
.phase-nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 8px;
  border-radius: 10px;
  cursor: pointer;
  flex: 1;
  font-size: 12px;
  transition: all .15s;
  min-width: 0;
  overflow: hidden;
}
.phase-nav-item:hover {
  background: #fff0e0;
}
.phase-nav-item.active {
  background: var(--brand);
  color: #fff;
  box-shadow: 2px 2px 0 rgba(154,83,28,.12);
}
.phase-nav-item.done {
  color: var(--text-secondary);
}
.phase-nav-item.generating {
  color: var(--brand);
}
.phase-dot {
  width: 22px;
  height: 22px;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 900;
  flex-shrink: 0;
  background: #fff;
  color: var(--text-secondary);
}
.phase-nav-item.active .phase-dot {
  background: #fff;
  color: var(--brand);
}
.phase-nav-item.done .phase-dot {
  background: var(--brand-soft);
  color: var(--brand-strong);
}
.phase-nav-label {
  font-family: var(--font-mono);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.phase-nav-status {
  font-family: var(--font-body);
  font-size: 10px;
  opacity: .7;
  white-space: nowrap;
}

/* --- PHASE CONTENT --- */
.phase-content {
  max-width: 780px;
}
.phase-title {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -.03em;
}
.phase-subtitle {
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 20px;
}

/* --- CARDS --- */
.card {
  background: #ffffff;
  border: 1px solid var(--border-orange);
  border-radius: 20px;
  padding: 20px 24px;
  margin-bottom: 16px;
  border-left: 4px solid var(--brand);
  box-shadow: var(--shadow-card);
}
.result-card {
  border-left-color: var(--brand);
}
.error-card {
  border-left: 4px solid #dc2626;
}
.generating-card {
  text-align: center;
  padding: 40px;
}
.card-placeholder {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #9f9890;
  margin: 0;
}

/* --- MODE SELECT --- */
.mode-select-row {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.mode-label {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 600;
}

/* --- SPACING UTILITIES --- */
.mt-8  { margin-top: 8px; }
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.mb-12 { margin-bottom: 12px; }

/* --- GENERATING STATE --- */
.generating-spinner {
  font-size: 36px;
  display: inline-block;
  animation: spin 1.5s ease-in-out infinite;
}
@keyframes spin {
  0%,100% { transform: scale(1); }
  50% { transform: scale(1.2) rotate(10deg); }
}
.generating-text {
  font-family: var(--font-mono);
  color: var(--brand);
  margin-top: 8px;
}

/* --- RESULT CONTENT --- */
.result-content {
  line-height: 1.8;
  font-family: var(--font-body);
  font-size: 14px;
}

/* --- BUTTONS --- */
.btn-row {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
.orange-btn {
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 14px;
  padding: 10px 24px;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all .15s;
  box-shadow: 0 3px 0 rgba(197,83,18,0.20);
  letter-spacing: 0.5px;
}
.orange-btn:hover {
  background: var(--brand-strong);
  transform: translateY(-1px);
}
.orange-btn:disabled {
  opacity: .5;
  cursor: not-allowed;
}
.save-btn {
  padding: 10px 24px;
}
.nav-btn {
  background: #ffffff;
  color: var(--brand-strong);
  border: 1px solid var(--border-orange);
  border-radius: 14px;
  padding: 10px 20px;
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  transition: all .15s;
}
.nav-btn:hover {
  background: #faf9f7;
  border-color: #f37021;
}
.nav-btn-next {
  /* inherits nav-btn */
}

/* --- ERROR --- */
.error-text {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #dc2626;
  margin: 0;
}
</style>
