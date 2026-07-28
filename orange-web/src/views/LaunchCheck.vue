<template>
  <div class="launch-page">
    <header class="launch-header">
      <div>
        <span class="eyebrow">AI 工具 · 上线体检</span>
        <h2>从代码准备到持续运维，逐阶段完成上线验收。</h2>
      </div>
      <div class="header-actions">
        <el-button :icon="Clock" @click="viewMode = 'history'; loadHistory()">报告记录</el-button>
        <el-button v-if="assessment" :icon="Plus" type="primary" @click="startNew">新建体检</el-button>
      </div>
    </header>

    <div class="mode-tabs" role="tablist" aria-label="上线体检视图">
      <button type="button" :class="{ active: viewMode === 'workspace' }" role="tab"
              :aria-selected="viewMode === 'workspace'" @click="viewMode = 'workspace'">
        体检工作台
      </button>
      <button type="button" :class="{ active: viewMode === 'history' }" role="tab"
              :aria-selected="viewMode === 'history'" @click="viewMode = 'history'; loadHistory()">
        报告记录 <span>{{ history.length }}</span>
      </button>
    </div>

    <section v-if="viewMode === 'history'" class="history-view" aria-labelledby="history-title">
      <div class="section-heading">
        <div>
          <h3 id="history-title">报告记录</h3>
          <p>按最近更新时间排列。</p>
        </div>
        <el-button :icon="Plus" type="primary" @click="startNew">新建体检</el-button>
      </div>

      <el-skeleton v-if="historyLoading" :rows="5" animated />
      <el-empty v-else-if="!history.length" description="还没有上线体检报告">
        <el-button type="primary" @click="startNew">创建第一个项目</el-button>
      </el-empty>
      <div v-else class="history-list">
        <article v-for="item in history" :key="item.id" class="history-item">
          <button type="button" class="history-main" @click="openAssessment(item.id)">
            <span class="history-status" :class="item.status === 'COMPLETED' ? 'complete' : 'progress'">
              {{ item.status === 'COMPLETED' ? '已完成' : `阶段 ${item.currentStage || 0}/8` }}
            </span>
            <strong>{{ item.projectName }}</strong>
            <small>{{ formatTime(item.updateTime) }}</small>
          </button>
          <el-button :icon="Delete" text type="danger" aria-label="删除体检报告"
                     @click="removeAssessment(item)" />
        </article>
      </div>
    </section>

    <section v-else-if="!assessment" class="create-view" aria-labelledby="create-title">
      <div class="section-heading">
        <div>
          <h3 id="create-title">创建上线项目</h3>
          <p>当前版本基于你填写的项目资料评估；仓库地址仅用于报告标识，不会自动读取代码。</p>
        </div>
        <div class="cost-hint"><PixelOrangeSlice :size="15" /> 每阶段 {{ stageCost }}</div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="project-form"
               @submit.prevent="createAssessment">
        <div class="form-grid">
          <el-form-item label="项目名称" prop="projectName">
            <el-input v-model="form.projectName" maxlength="128" placeholder="例如：AI 创意服务平台" />
          </el-form-item>
          <el-form-item label="代码仓库（可选）" prop="repositoryUrl">
            <el-input v-model="form.repositoryUrl" maxlength="512" placeholder="https://github.com/owner/repo" />
          </el-form-item>
          <el-form-item label="目标用户">
            <el-input v-model="form.targetAudience" maxlength="128" placeholder="例如：个人开发者和小型团队" />
          </el-form-item>
          <el-form-item label="预算">
            <el-input v-model="form.budget" maxlength="128" placeholder="例如：每月 300 元以内" />
          </el-form-item>
          <el-form-item label="部署地域">
            <el-select v-model="form.region" placeholder="选择地域" clearable>
              <el-option label="中国内地" value="中国内地" />
              <el-option label="中国香港" value="中国香港" />
              <el-option label="海外" value="海外" />
              <el-option label="暂未确定" value="暂未确定" />
            </el-select>
          </el-form-item>
          <el-form-item label="预计用户规模">
            <el-input v-model="form.expectedUsers" maxlength="128" placeholder="例如：首月 1000 注册、50 同时在线" />
          </el-form-item>
          <el-form-item label="上传内容">
            <el-input v-model="form.uploadTypes" maxlength="128" placeholder="例如：图片、短视频、文档" />
          </el-form-item>
          <el-form-item label="真实支付">
            <el-switch v-model="form.paymentRequired" inline-prompt active-text="需要" inactive-text="不需要" />
          </el-form-item>
        </div>
        <el-form-item label="项目说明" prop="projectSummary">
          <el-input v-model="form.projectSummary" type="textarea" :rows="7" maxlength="10000"
                    show-word-limit placeholder="可以填写项目方案或系统架构，包括技术栈、已有功能、运行方式、数据库、缓存、文件上传、AI 服务和当前遇到的上线问题。" />
          <p class="summary-tip">方案设计的“任务计划”支持一键整理并导入到这里。</p>
        </el-form-item>
        <div class="form-actions">
          <el-button type="primary" native-type="submit" :loading="creating">创建体检项目</el-button>
        </div>
      </el-form>
    </section>

    <section v-else class="workspace" aria-label="上线体检工作台">
      <div class="project-strip">
        <div class="project-title">
          <el-button :icon="ArrowLeft" text aria-label="返回报告记录" @click="viewMode = 'history'; loadHistory()" />
          <div>
            <h3>{{ assessment.projectName }}</h3>
            <p>{{ assessment.repositoryUrl || '未绑定代码仓库' }}</p>
          </div>
        </div>
        <div class="project-progress">
          <span>{{ completedCount }}/8</span>
          <el-progress :percentage="Math.round(completedCount / 8 * 100)" :stroke-width="8" :show-text="false" />
        </div>
        <el-button :icon="Download" :disabled="!completedCount" @click="downloadReport">导出 Markdown</el-button>
      </div>

      <div class="workspace-grid">
        <nav class="stage-nav" aria-label="体检阶段">
          <button v-for="stage in stageDefinitions" :key="stage.stageNo" type="button"
                  :class="{ active: currentStage === stage.stageNo, done: stageContent(stage.stageNo), locked: isLocked(stage.stageNo) }"
                  :disabled="isLocked(stage.stageNo)" @click="currentStage = stage.stageNo">
            <span class="stage-index">{{ String(stage.stageNo).padStart(2, '0') }}</span>
            <span class="stage-copy"><strong>{{ stage.title }}</strong><small>{{ stageContent(stage.stageNo) ? '报告已生成' : isLocked(stage.stageNo) ? '完成前序阶段后解锁' : '等待生成' }}</small></span>
            <el-icon v-if="stageContent(stage.stageNo)"><CircleCheck /></el-icon>
            <el-icon v-else-if="isLocked(stage.stageNo)"><Lock /></el-icon>
          </button>
        </nav>

        <main class="report-panel">
          <header class="report-heading">
            <div>
              <span>阶段 {{ currentStage }} / 8</span>
              <h3>{{ currentDefinition.title }}</h3>
              <p>{{ currentDefinition.description }}</p>
            </div>
            <div class="stage-cost"><PixelOrangeSlice :size="15" /> {{ stageCost }} / 次</div>
          </header>

          <div v-if="generating" class="generation-state" aria-live="polite">
            <el-icon class="is-loading"><Loading /></el-icon>
            <strong>正在生成阶段报告</strong>
            <p>完成后会自动保存，可以离开当前页面后从报告记录继续查看。</p>
          </div>

          <div v-else-if="currentContent" class="report-content" v-html="renderedReport"></div>

          <div v-else class="empty-report">
            <DocumentChecked />
            <strong>本阶段还没有报告</strong>
            <p>生成时将读取项目资料和已经完成的前序报告。</p>
          </div>

          <footer class="report-actions">
            <el-button v-if="currentStage > 1" @click="currentStage--">上一阶段</el-button>
            <el-button type="primary" :loading="generating" :disabled="isLocked(currentStage)"
                       @click="confirmGenerate">
              {{ currentContent ? '重新生成本阶段' : '生成本阶段报告' }}
            </el-button>
            <el-button v-if="currentContent && currentStage < 8" @click="currentStage++">下一阶段</el-button>
          </footer>
        </main>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, CircleCheck, Clock, Delete, DocumentChecked, Download, Loading, Lock, Plus } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import PixelOrangeSlice from '../components/PixelOrangeSlice.vue'
import request from '../api/request'

const viewMode = ref('workspace')
const assessment = ref(null)
const history = ref([])
const historyLoading = ref(false)
const creating = ref(false)
const generating = ref(false)
const currentStage = ref(1)
const stageCost = ref(40)
const stageDefinitions = ref(Array.from({ length: 8 }, (_, i) => ({ stageNo: i + 1, title: `阶段 ${i + 1}`, description: '' })))
const formRef = ref()

const form = reactive({
  projectName: '', repositoryUrl: '', projectSummary: '', targetAudience: '', budget: '',
  region: '', expectedUsers: '', uploadTypes: '', paymentRequired: false
})
const rules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectSummary: [
    { required: true, message: '请输入项目说明', trigger: 'blur' },
    { min: 20, message: '项目说明至少需要 20 个字', trigger: 'blur' }
  ],
  repositoryUrl: [{ pattern: /^(|https:\/\/(github\.com|gitee\.com)\/[^\s]+)$/i, message: '请输入 GitHub 或 Gitee 的 HTTPS 仓库地址', trigger: 'blur' }]
}

const stages = computed(() => assessment.value?.stages || [])
const completedCount = computed(() => stages.value.length)
const currentDefinition = computed(() => stageDefinitions.value.find(s => s.stageNo === currentStage.value) || stageDefinitions.value[0])
const currentContent = computed(() => stageContent(currentStage.value))
const renderedReport = computed(() => DOMPurify.sanitize(marked.parse(currentContent.value || '')))

const stageContent = (stageNo) => stages.value.find(s => Number(s.stageNo) === stageNo)?.content || ''
const isLocked = (stageNo) => stageNo > 1 && !stageContent(stageNo - 1)
const formatTime = (value) => value ? String(value).replace('T', ' ').substring(0, 19) : ''

const loadConfig = async () => {
  const response = await request.get('/app/launch-check/config')
  if (response.code === 1) {
    stageCost.value = response.data.stageCost
    stageDefinitions.value = response.data.stages
  }
}

const loadHistory = async () => {
  historyLoading.value = true
  try {
    const response = await request.get('/app/launch-check/projects', { params: { limit: 200 } })
    if (response.code === 1) history.value = response.data || []
  } finally { historyLoading.value = false }
}

const createAssessment = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const response = await request.post('/app/launch-check/projects', form)
    if (response.code === 1) {
      assessment.value = response.data
      currentStage.value = 1
      ElMessage.success('体检项目已创建')
    }
  } finally { creating.value = false }
}

const openAssessment = async (id) => {
  const response = await request.get(`/app/launch-check/projects/${id}`)
  if (response.code === 1) {
    assessment.value = response.data
    currentStage.value = Math.min(8, Number(response.data.currentStage || 0) + 1)
    viewMode.value = 'workspace'
  }
}

const startNew = () => {
  assessment.value = null
  currentStage.value = 1
  Object.assign(form, { projectName: '', repositoryUrl: '', projectSummary: '', targetAudience: '', budget: '', region: '', expectedUsers: '', uploadTypes: '', paymentRequired: false })
  viewMode.value = 'workspace'
}

const confirmGenerate = async () => {
  const hasCurrent = !!currentContent.value
  const message = hasCurrent
    ? `重新生成将消耗 ${stageCost.value} 配额，并使后续阶段报告失效。`
    : `生成本阶段报告将消耗 ${stageCost.value} 配额。`
  try {
    await ElMessageBox.confirm(message, hasCurrent ? '确认重新生成' : '确认生成', {
      type: 'warning', confirmButtonText: '确认并生成', cancelButtonText: '取消'
    })
  } catch { return }

  generating.value = true
  try {
    const response = await request.post(
      `/app/launch-check/projects/${assessment.value.id}/stages/${currentStage.value}/generate`,
      {}, { timeout: 150000 }
    )
    if (response.code === 1) {
      await openAssessment(assessment.value.id)
      currentStage.value = response.data.stageNo
      ElMessage.success('阶段报告已生成并保存')
    }
  } catch {
    ElMessage.error('生成失败，已消费的配额将自动退回')
  } finally { generating.value = false }
}

const removeAssessment = async (item) => {
  try {
    await ElMessageBox.confirm(`删除“${item.projectName}”的全部体检报告？`, '删除报告', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch { return }
  await request.delete(`/app/launch-check/projects/${item.id}`)
  if (assessment.value?.id === item.id) assessment.value = null
  await loadHistory()
  ElMessage.success('报告已删除')
}

const downloadReport = () => {
  const lines = [`# ${assessment.value.projectName} · 上线体检报告`, '', `更新时间：${formatTime(assessment.value.updateTime)}`, '']
  for (const definition of stageDefinitions.value) {
    const content = stageContent(definition.stageNo)
    if (content) lines.push(`## ${definition.stageNo}. ${definition.title}`, '', content, '')
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${assessment.value.projectName}-上线体检报告.md`
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  const imported = sessionStorage.getItem('launch_check_import')
  if (imported) {
    try {
      const payload = JSON.parse(imported)
      form.projectName = String(payload.projectName || '').slice(0, 128)
      form.projectSummary = String(payload.projectSummary || '').slice(0, 10000)
      ElMessage.success('已导入方案设计中的项目资料，请检查并补充后创建体检')
    } catch {
      ElMessage.warning('导入的项目资料格式无效，请重新从方案设计导入')
    } finally {
      sessionStorage.removeItem('launch_check_import')
    }
  }
  await Promise.all([loadConfig(), loadHistory()])
})
</script>

<style scoped>
.launch-page { max-width: 1320px; margin: 0 auto; }
.launch-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.eyebrow { color: var(--brand-strong); font: 800 12px/1.4 var(--font-mono); }
.launch-header h2 { max-width: 820px; margin: 8px 0 0; color: var(--text-primary); font: 900 28px/1.25 var(--font-mono); letter-spacing: 0; }
.header-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.mode-tabs { display: flex; gap: 4px; padding-bottom: 12px; border-bottom: 1px solid var(--border-weak); margin-bottom: 24px; }
.mode-tabs button { min-height: 44px; padding: 0 16px; border: 0; border-radius: 8px; background: transparent; color: var(--text-secondary); font: 800 13px var(--font-mono); cursor: pointer; }
.mode-tabs button:hover, .mode-tabs button:focus-visible { background: #fff7ed; color: var(--brand-strong); }
.mode-tabs button.active { background: var(--brand-soft); color: var(--brand-strong); }
.mode-tabs span { margin-left: 5px; color: var(--text-muted); }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.section-heading h3, .project-title h3, .report-heading h3 { margin: 0; color: var(--text-primary); font: 900 20px/1.35 var(--font-mono); letter-spacing: 0; }
.section-heading p, .project-title p, .report-heading p { margin: 5px 0 0; color: var(--text-secondary); font-size: 13px; line-height: 1.6; }
.cost-hint, .stage-cost { display: inline-flex; align-items: center; gap: 6px; min-height: 34px; padding: 0 10px; border: 1px solid var(--border-orange); border-radius: 8px; color: var(--brand-strong); background: #fffaf5; font: 800 12px var(--font-mono); white-space: nowrap; }
.create-view, .history-view { padding: 24px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 20px; }
.project-form :deep(.el-input__wrapper), .project-form :deep(.el-select__wrapper), .project-form :deep(.el-textarea__inner) { min-height: 44px; border-radius: 6px; }
.project-form :deep(.el-textarea__inner) { line-height: 1.65; }
.summary-tip { margin: 7px 0 0; color: var(--text-muted); font-size: 12px; line-height: 1.5; }
.form-actions { display: flex; justify-content: flex-end; }
.history-list { border-top: 1px solid var(--border-weak); }
.history-item { display: flex; align-items: center; gap: 10px; min-height: 76px; border-bottom: 1px solid var(--border-weak); }
.history-main { display: grid; grid-template-columns: 92px minmax(0, 1fr) 170px; align-items: center; gap: 16px; flex: 1; min-width: 0; min-height: 64px; border: 0; background: transparent; text-align: left; cursor: pointer; }
.history-main:hover strong, .history-main:focus-visible strong { color: var(--brand-strong); }
.history-main strong { overflow: hidden; color: var(--text-primary); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.history-main small { color: var(--text-muted); font-variant-numeric: tabular-nums; }
.history-status { display: inline-flex; align-items: center; justify-content: center; width: fit-content; min-height: 26px; padding: 0 9px; border-radius: 6px; font-size: 11px; font-weight: 800; }
.history-status.complete { color: #166534; background: #ecfdf3; }
.history-status.progress { color: #92400e; background: #fffbeb; }
.project-strip { display: grid; grid-template-columns: minmax(0, 1fr) minmax(180px, 260px) auto; align-items: center; gap: 18px; padding: 16px 18px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; margin-bottom: 16px; }
.project-title { display: flex; align-items: center; min-width: 0; }
.project-title h3, .project-title p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.project-progress { display: grid; grid-template-columns: 36px minmax(0, 1fr); align-items: center; gap: 8px; }
.project-progress > span { color: var(--brand-strong); font: 900 12px var(--font-mono); }
.workspace-grid { display: grid; grid-template-columns: 285px minmax(0, 1fr); gap: 16px; align-items: start; }
.stage-nav { display: flex; flex-direction: column; gap: 4px; padding: 8px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; }
.stage-nav button { display: grid; grid-template-columns: 34px minmax(0, 1fr) 20px; align-items: center; gap: 10px; width: 100%; min-height: 62px; padding: 8px 10px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: var(--text-secondary); text-align: left; cursor: pointer; transition: background .18s, border-color .18s, color .18s; }
.stage-nav button:hover:not(:disabled), .stage-nav button:focus-visible { border-color: var(--border-orange); background: #fffaf5; }
.stage-nav button.active { border-color: var(--brand); background: var(--brand-soft); color: var(--brand-strong); }
.stage-nav button.done:not(.active) { color: #166534; }
.stage-nav button.locked { cursor: not-allowed; opacity: .56; }
.stage-index { font: 900 12px var(--font-mono); font-variant-numeric: tabular-nums; }
.stage-copy { min-width: 0; }
.stage-copy strong, .stage-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.stage-copy strong { font: 800 12px var(--font-mono); }
.stage-copy small { margin-top: 4px; color: var(--text-muted); font-size: 10px; }
.report-panel { min-height: 680px; padding: 24px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; }
.report-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; padding-bottom: 18px; border-bottom: 1px solid var(--border-weak); }
.report-heading > div:first-child > span { color: var(--brand-strong); font: 800 11px var(--font-mono); }
.report-heading h3 { margin-top: 5px; }
.report-content { padding: 22px 4px; color: var(--text-primary); font-size: 14px; line-height: 1.75; }
.report-content :deep(h1), .report-content :deep(h2), .report-content :deep(h3) { margin: 24px 0 10px; font-family: var(--font-mono); letter-spacing: 0; }
.report-content :deep(h1) { font-size: 22px; }.report-content :deep(h2) { font-size: 18px; }.report-content :deep(h3) { font-size: 15px; }
.report-content :deep(table) { width: 100%; border-collapse: collapse; display: block; overflow-x: auto; }
.report-content :deep(th), .report-content :deep(td) { min-width: 120px; padding: 9px 10px; border: 1px solid var(--border-weak); text-align: left; vertical-align: top; }
.report-content :deep(pre) { overflow-x: auto; padding: 14px; border-radius: 6px; background: #2d2926; color: #fff8ef; }
.report-content :deep(code) { font-family: var(--font-mono); }
.generation-state, .empty-report { display: flex; min-height: 430px; align-items: center; justify-content: center; flex-direction: column; gap: 10px; color: var(--text-secondary); text-align: center; }
.generation-state .el-icon { font-size: 32px; color: var(--brand); }
.empty-report > svg { width: 38px; color: var(--brand); }
.generation-state strong, .empty-report strong { color: var(--text-primary); font: 900 15px var(--font-mono); }
.generation-state p, .empty-report p { max-width: 460px; margin: 0; font-size: 13px; line-height: 1.6; }
.report-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 18px; border-top: 1px solid var(--border-weak); }
button:focus-visible, :deep(.el-button:focus-visible) { outline: 3px solid rgba(243,112,33,.28); outline-offset: 2px; }
@media (max-width: 980px) { .workspace-grid { grid-template-columns: 1fr; }.stage-nav { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }.report-panel { min-height: 560px; } }
@media (max-width: 720px) {
  .launch-header, .section-heading, .report-heading { flex-direction: column; }
  .launch-header h2 { font-size: 22px; }
  .form-grid, .stage-nav { grid-template-columns: 1fr; }
  .create-view, .history-view, .report-panel { padding: 16px; }
  .project-strip { grid-template-columns: 1fr; }
  .history-main { grid-template-columns: 1fr; gap: 4px; padding: 10px 0; }
  .history-main small { order: 3; }
  .report-actions { flex-wrap: wrap; justify-content: stretch; }
  .report-actions :deep(.el-button) { flex: 1; min-height: 44px; margin-left: 0; }
}
@media (prefers-reduced-motion: reduce) { .stage-nav button { transition: none; } }
</style>
