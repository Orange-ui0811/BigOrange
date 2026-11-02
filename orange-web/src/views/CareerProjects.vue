<template>
  <div class="career-page">
    <section class="career-intro">
      <div><span class="eyebrow">CAREER GROWTH</span><h3>把开源项目做成可证明的职业能力</h3><p>依据真实岗位样本、仓库工程证据和作品完整度，找到值得写进简历、也值得在面试中展开讨论的项目。</p></div>
      <div class="score-legend"><strong>五维证据评分</strong><span>岗位相关度 · 能力证明 · 知识覆盖 · 作品完整度 · 面试价值</span></div>
    </section>

    <section class="weekly-section" aria-labelledby="career-weekly-title">
      <div class="section-heading"><div><span>01</span><h3 id="career-weekly-title">本周职业精选</h3></div><small>每周 3 个 · 可同时适合多个岗位</small></div>
      <div v-if="weeklyLoading" class="weekly-grid"><el-skeleton v-for="i in 3" :key="i" :rows="5" animated /></div>
      <div v-else-if="weekly.length" class="weekly-grid">
        <article v-for="(project, index) in weekly" :key="project.id" class="weekly-card">
          <div class="weekly-top"><span class="rank">0{{ index + 1 }}</span><span class="score">{{ project.overallScore }}<small>/100</small></span></div>
          <span class="role-label">{{ project.bestRoleName }}</span><h4>{{ project.summaryZh || project.repositoryName }}</h4><p>{{ project.careerValue || project.descriptionOriginal }}</p>
          <button type="button" class="detail-link" @click="openDetail(project.id)">查看职业价值<ArrowRight /></button>
        </article>
      </div>
      <el-empty v-else description="职业精选生成中，请稍后再来" />
    </section>

    <section class="library-section" aria-labelledby="career-library-title">
      <div class="section-heading"><div><span>02</span><h3 id="career-library-title">职业项目库</h3></div><small>{{ total }} 个已核验项目</small></div>
      <div class="filters">
        <el-input v-model="filters.keyword" clearable placeholder="搜索仓库、能力或技术栈" @keyup.enter="search"><template #prefix><Search /></template></el-input>
        <el-select v-model="filters.roleCode" clearable placeholder="目标岗位" @change="search"><el-option v-for="role in roles" :key="role.code" :label="role.name" :value="role.code" /></el-select>
        <el-select v-model="filters.language" clearable placeholder="语言" @change="search"><el-option v-for="item in languages" :key="item" :label="item" :value="item" /></el-select>
        <el-select v-model="filters.difficulty" clearable placeholder="难度" @change="search"><el-option label="入门" value="ENTRY" /><el-option label="进阶" value="INTERMEDIATE" /><el-option label="高级" value="ADVANCED" /></el-select>
        <el-button type="primary" :icon="Search" @click="search">筛选</el-button>
      </div>
      <div v-loading="loading" class="project-grid" aria-live="polite">
        <article v-for="project in projects" :key="project.id" class="project-card" tabindex="0" @click="openDetail(project.id)" @keyup.enter="openDetail(project.id)">
          <div class="project-top"><span>{{ project.primaryLanguage || 'Repository' }}</span><el-tag size="small" effect="plain">{{ difficultyText(project.difficulty) }}</el-tag></div>
          <h4>{{ project.summaryZh || project.repositoryName }}</h4><p>{{ project.careerValue || project.descriptionOriginal }}</p>
          <div class="role-row"><Briefcase /><span>{{ project.bestRoleName }}</span></div><div class="project-footer"><span><b>{{ project.overallScore }}</b>/100</span><span>{{ formatStars(project.stars) }} Stars</span></div>
        </article>
      </div>
      <el-empty v-if="!loading && !projects.length" description="没有符合条件的职业项目，请放宽筛选条件" />
      <div class="pagination"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadProjects" /></div>
    </section>

    <el-drawer v-model="detailVisible" size="min(780px, 96vw)" :with-header="false">
      <div v-if="detail" class="detail-content">
        <header class="detail-header"><div><span>{{ detail.fullName }}</span><h3>{{ detail.summaryZh || detail.repositoryName }}</h3></div><el-button circle aria-label="关闭详情" @click="detailVisible = false"><Close /></el-button></header>
        <p class="detail-summary">{{ detail.careerValue || detail.descriptionOriginal }}</p>
        <div class="detail-actions"><el-button type="primary" :icon="Briefcase" @click="openAssessment">按目标岗位评估</el-button><el-button :icon="DocumentAdd" @click="importPlan(false)">一键导入项目计划</el-button><el-button :icon="MagicStick" @click="importPlan(true)">生成差异化改造方案</el-button><el-button text @click="openGithub(detail.htmlUrl)">GitHub<ArrowRight /></el-button></div>
        <dl class="facts"><div><dt>综合职业分</dt><dd>{{ detail.overallScore }}/100</dd></div><div><dt>最匹配岗位</dt><dd>{{ detail.bestRoleName }}</dd></div><div><dt>难度</dt><dd>{{ difficultyText(detail.difficulty) }}</dd></div><div><dt>预计投入</dt><dd>{{ detail.estimatedDuration || '待评估' }}</dd></div></dl>
        <section><h4>能证明哪些能力</h4><p>{{ detail.proofPoints || '等待职业说明生成' }}</p></section><section><h4>面试可以讨论什么</h4><p>{{ detail.interviewTopics || '等待职业说明生成' }}</p></section><section><h4>差异化改造方向</h4><p>{{ detail.modificationPlan || '等待职业说明生成' }}</p></section><section><h4>环境与风险</h4><p>{{ detail.mainRisks || environmentSummary(detail) }}</p></section>
        <section class="score-section"><h4>岗位能力评分</h4><div v-for="role in detail.roleScores" :key="role.roleCode" class="role-score"><div><strong>{{ role.roleName }}</strong><span>{{ role.totalScore }}/100</span></div><el-progress :percentage="role.totalScore" :stroke-width="7" :show-text="false" /><small>岗位 {{ role.roleRelevance }} · 证明 {{ role.provableAbility }} · 知识 {{ role.knowledgeCoverage }} · 完整 {{ role.portfolioCompleteness }} · 面试 {{ role.interviewValue }}</small></div></section>
      </div>
    </el-drawer>

    <el-dialog v-model="assessmentVisible" title="按目标岗位评估" width="min(560px, 94vw)" destroy-on-close>
      <el-form label-position="top"><el-form-item label="目标岗位类别"><el-select v-model="assessment.roleCode" placeholder="选择岗位方向" style="width:100%" @change="loadJobs"><el-option v-for="role in roles" :key="role.code" :label="role.name" :value="role.code" /></el-select></el-form-item><el-form-item label="具体岗位（可选）"><el-select v-model="assessment.jobId" clearable filterable placeholder="使用岗位类别综合评估" style="width:100%"><el-option v-for="job in jobs" :key="job.id" :label="job.position" :value="job.id" /></el-select></el-form-item><p class="quota-note">岗位匹配将消耗 {{ Number.isFinite(jobMatchCost) ? `${jobMatchCost} 配额` : '管理端当前定价' }}；确认时会显示余额和扣除后的预计剩余配额。</p></el-form>
      <div v-if="assessmentResult.length" class="assessment-results"><article v-for="item in assessmentResult" :key="item.position"><strong>{{ item.position }}</strong><p>{{ item.reason }}</p><small>{{ item.focus }}</small></article></div>
      <template #footer><el-button @click="assessmentVisible = false">取消</el-button><el-button type="primary" :loading="assessing" :disabled="!assessment.roleCode || !Number.isFinite(jobMatchCost)" @click="assess">开始评估</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Briefcase, Close, DocumentAdd, MagicStick, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { confirmQuotaUsage } from '../utils/quotaConfirm'
import { openExternalSafely } from '../composables/useSafeExternalNavigation'

const router = useRouter()
const weekly = ref([]), projects = ref([]), roles = ref([]), jobs = ref([]), total = ref(0), page = ref(1)
const pageSize = 12
const loading = ref(false), weeklyLoading = ref(false), detailVisible = ref(false), assessmentVisible = ref(false), assessing = ref(false)
const jobMatchCost = ref(null)
const assessmentResult = ref([]), detail = ref(null)
const filters = reactive({ keyword: '', roleCode: '', language: '', difficulty: '' })
const assessment = reactive({ roleCode: '', jobId: null })
const languages = ['TypeScript', 'JavaScript', 'Python', 'Java', 'Go', 'HTML']
const loadRoles = async () => { const r = await request.get('/app/career-projects/role-categories'); roles.value = r.data || [] }
const loadWeekly = async () => { weeklyLoading.value = true; try { const r = await request.get('/app/career-projects/weekly'); weekly.value = r.data || [] } finally { weeklyLoading.value = false } }
const loadProjects = async () => { loading.value = true; try { const r = await request.get('/app/career-projects', { params: { ...filters, page: page.value, size: pageSize } }); projects.value = r.data?.records || []; total.value = Number(r.data?.total || 0) } finally { loading.value = false } }
const search = () => { page.value = 1; loadProjects() }
const openDetail = async id => { const r = await request.get(`/app/career-projects/${id}`); detail.value = r.data; detailVisible.value = true }
const openGithub = url => openExternalSafely(url)
const openAssessment = () => { assessment.roleCode = detail.value?.bestRoleCode || ''; assessment.jobId = null; assessmentResult.value = []; assessmentVisible.value = true; if (assessment.roleCode) loadJobs() }
const loadJobs = async () => { assessment.jobId = null; jobs.value = []; if (!assessment.roleCode || !detail.value) return; const r = await request.get(`/app/career-projects/${detail.value.id}/jobs`, { params: { roleCode: assessment.roleCode } }); jobs.value = r.data || [] }
const loadPricing = async () => {
  try {
    const response = await request.get('/app/config/ai-pricing')
    const value = Number(response.data?.jobMatch)
    jobMatchCost.value = Number.isFinite(value) && value >= 0 ? value : null
  } catch {
    jobMatchCost.value = null
  }
}
const assess = async () => {
  if (!Number.isFinite(jobMatchCost.value)) {
    ElMessage.error('岗位匹配价格尚未加载，请刷新页面后重试')
    return
  }
  const confirmed = await confirmQuotaUsage({
    actionLabel: 'AI 岗位匹配',
    cost: jobMatchCost.value,
    title: '确认岗位匹配',
    confirmButtonText: '确认匹配',
    cancelButtonText: '暂不匹配',
    type: 'info',
    notice: '确认后才会调用 AI；失败时配额会自动退回。',
    estimatedTime: '1～3 分钟',
  })
  if (!confirmed) return
  assessing.value = true
  try {
    const r = await request.post(`/app/career-projects/${detail.value.id}/job-assessment`, {
      roleCode: assessment.roleCode,
      jobId: assessment.jobId,
    }, { timeout: 0 })
    assessmentResult.value = r.data?.matches || []
    ElMessage.success('岗位评估已完成')
  } finally {
    assessing.value = false
  }
}
const importPlan = differentiated => { const p = detail.value; sessionStorage.setItem('plan_import', [`参考仓库：${p.fullName}`, `GitHub：${p.htmlUrl}`, `项目说明：${p.summaryZh || p.descriptionOriginal || ''}`, `目标岗位：${p.bestRoleName}`, `希望证明的能力：${p.proofPoints || ''}`, `面试可讨论内容：${p.interviewTopics || ''}`, `建议改造方向：${p.modificationPlan || ''}`, `项目风险：${p.mainRisks || environmentSummary(p)}`, differentiated ? '设计目标：不要简单复刻原仓库，要围绕目标岗位生成有明确差异、可演示、可写入简历的改造方案。' : '设计目标：基于参考仓库形成一个完整、可运行、可展示的个人项目。'].join('\n\n')); router.push('/plan') }
const environmentSummary = p => [p.requiresDocker && '需要 Docker', p.requiresDatabase && '需要数据库', p.requiresSecrets && '需要密钥', p.requiresExternalService && '依赖外部服务'].filter(Boolean).join('；') || '未检测到明显的重型环境要求'
const difficultyText = value => ({ ENTRY: '入门', INTERMEDIATE: '进阶', ADVANCED: '高级' }[value] || '待评估')
const formatStars = value => Number(value || 0) >= 1000 ? `${(Number(value) / 1000).toFixed(1)}K` : String(value || 0)
onMounted(() => Promise.all([loadRoles(), loadWeekly(), loadProjects(), loadPricing()]))
</script>

<style scoped>
.career-page{max-width:1240px;margin:0 auto;color:var(--text-primary)}.career-intro{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;padding:22px 24px;border:1px solid var(--border-weak);border-left:4px solid #0f766e;border-radius:8px;background:#fff}.eyebrow,.section-heading span{color:#0f766e;font:900 11px/1 var(--font-mono)}.career-intro h3{margin:8px 0;font:900 25px/1.25 var(--font-mono)}.career-intro p{max-width:720px;margin:0;color:var(--text-secondary);line-height:1.7}.score-legend{max-width:310px;padding-left:16px;border-left:1px solid var(--border-weak)}.score-legend strong,.score-legend span{display:block}.score-legend span{margin-top:6px;color:var(--text-muted);font-size:12px;line-height:1.6}.weekly-section,.library-section{margin-top:26px}.section-heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:13px}.section-heading>div{display:flex;align-items:center;gap:10px}.section-heading h3{margin:0;font:900 20px/1.2 var(--font-mono)}.section-heading small{color:var(--text-muted)}.weekly-grid,.project-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.weekly-card,.project-card{border:1px solid var(--border-weak);border-radius:8px;background:#fff}.weekly-card{display:flex;min-height:285px;flex-direction:column;padding:20px}.weekly-top,.project-top,.project-footer{display:flex;align-items:center;justify-content:space-between;gap:12px}.rank{color:#b7ded8;font:900 37px/1 var(--font-mono)}.score{color:#0f766e;font:900 24px/1 var(--font-mono)}.score small{font-size:10px}.role-label{align-self:flex-start;margin-top:18px;padding:5px 8px;border-radius:4px;background:#ecfdf5;color:#0f766e;font-size:11px;font-weight:800}.weekly-card h4,.project-card h4{margin:12px 0 8px;font:850 17px/1.45 var(--font-mono)}.weekly-card p,.project-card p{display:-webkit-box;overflow:hidden;margin:0;color:var(--text-secondary);font-size:13px;line-height:1.7;-webkit-box-orient:vertical;-webkit-line-clamp:3}.detail-link{display:flex;align-items:center;gap:5px;margin-top:auto;padding:16px 0 0;border:0;border-top:1px solid var(--border-weak);background:transparent;color:#0f766e;font-weight:800;cursor:pointer}.detail-link svg{width:15px}.filters{display:grid;grid-template-columns:minmax(230px,1.5fr) repeat(3,minmax(130px,.8fr)) auto;gap:8px;padding:12px;border:1px solid var(--border-weak);border-radius:8px;background:#fff}.project-grid{min-height:180px;margin-top:14px}.project-card{display:flex;min-height:255px;flex-direction:column;padding:18px;cursor:pointer;transition:border-color .18s,transform .18s,box-shadow .18s}.project-card:hover,.project-card:focus-visible{border-color:#0f766e;box-shadow:var(--shadow-lift);outline:none;transform:translateY(-2px)}.project-top{color:var(--text-muted);font-size:11px}.role-row{display:flex;align-items:center;gap:6px;margin-top:14px;color:#0f766e;font-size:11px;font-weight:750}.role-row svg{width:14px}.project-footer{margin-top:auto;padding-top:14px;border-top:1px solid var(--border-weak);color:var(--text-muted);font-size:11px}.project-footer b{color:#0f766e;font:900 19px/1 var(--font-mono)}.pagination{display:flex;justify-content:flex-end;margin-top:18px}.detail-content{padding:22px}.detail-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.detail-header span{color:#0f766e;font-size:12px;font-weight:800}.detail-header h3{margin:7px 0 0;font:900 23px/1.35 var(--font-mono)}.detail-summary{color:var(--text-secondary);line-height:1.75}.detail-actions{display:flex;flex-wrap:wrap;gap:8px;margin:18px 0}.facts{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;padding:1px;background:var(--border-weak)}.facts div{padding:13px;background:#fff}.facts dt{color:var(--text-muted);font-size:11px}.facts dd{margin:5px 0 0;font-weight:800}.detail-content section{margin-top:22px}.detail-content section h4{margin:0 0 8px;font:850 15px/1.3 var(--font-mono)}.detail-content section p{margin:0;color:var(--text-secondary);line-height:1.75;white-space:pre-line}.role-score{padding:12px 0;border-bottom:1px solid var(--border-weak)}.role-score>div{display:flex;justify-content:space-between;margin-bottom:7px}.role-score>div span{color:#0f766e;font-weight:850}.role-score small{display:block;margin-top:7px;color:var(--text-muted);line-height:1.5}.quota-note{margin:0;padding:10px 12px;border-left:3px solid var(--brand);background:#fff7ed;color:var(--text-secondary);font-size:12px}.assessment-results{display:grid;gap:10px;margin-top:16px}.assessment-results article{padding:13px;border:1px solid var(--border-weak);border-radius:6px}.assessment-results p,.assessment-results small{color:var(--text-secondary);line-height:1.6}.assessment-results small{display:block}.assessment-results strong{color:#0f766e}
@media(max-width:920px){.weekly-grid,.project-grid{grid-template-columns:1fr 1fr}.filters{grid-template-columns:1fr 1fr}.filters>*:first-child{grid-column:1/-1}.score-legend{display:none}.facts{grid-template-columns:1fr 1fr}}@media(max-width:620px){.career-intro{padding:18px}.career-intro h3{font-size:22px}.weekly-grid,.project-grid,.filters{grid-template-columns:1fr}.filters>*:first-child{grid-column:auto}.section-heading{align-items:flex-start;flex-direction:column;gap:6px}.facts{grid-template-columns:1fr}.detail-actions :deep(.el-button){width:100%;margin-left:0}}
</style>
