<template>
  <div class="beginner-page">
    <header v-if="!embedded" class="page-hero">
      <button class="back-button" type="button" @click="router.push('/project-tools')">
        <ArrowLeft />返回项目工具
      </button>
      <div class="hero-copy">
        <div>
          <span class="eyebrow">BEGINNER FRIENDLY</span>
          <h2>从一个能跑起来的项目开始。</h2>
          <p>平台核验仓库状态、README、技术栈和环境依赖，再挑出适合学习、展示和二次开发的项目。</p>
        </div>
        <div class="hero-fact"><strong>确定性评分</strong><span>AI 只负责中文说明，不决定项目是否合格</span></div>
      </div>
    </header>

    <section class="weekly-section" aria-labelledby="weekly-title">
      <div class="section-heading">
        <div><span class="section-index">01</span><h3 id="weekly-title">本周精选</h3></div>
        <span>每周 3 个 · 最近核验优先</span>
      </div>
      <div v-if="weeklyLoading" class="weekly-grid"><el-skeleton v-for="i in 3" :key="i" :rows="4" animated /></div>
      <div v-else-if="weekly.length" class="weekly-grid">
        <article v-for="(project, index) in weekly" :key="project.id" class="weekly-card">
          <div class="weekly-rank">0{{ index + 1 }}</div>
          <div class="project-meta"><span>{{ project.primaryLanguage || '多技术栈' }}</span><span>{{ formatStars(project.stars) }} Stars</span></div>
          <h4>{{ project.descriptionZh || project.repositoryName }}</h4>
          <p>{{ project.beginnerReason }}</p>
          <div class="card-footer"><span>{{ difficultyText(project.difficulty) }} · {{ project.estimatedDuration || '时间待评估' }}</span><button type="button" @click="openDetail(project.id)">查看详情<ArrowRight /></button></div>
        </article>
      </div>
      <el-empty v-else description="本周精选生成中，请稍后再来" />
    </section>

    <section class="library-section" aria-labelledby="library-title">
      <div class="section-heading">
        <div><span class="section-index">02</span><h3 id="library-title">项目库</h3></div>
        <span>{{ total }} 个已核验项目</span>
      </div>
      <div class="filters">
        <el-input v-model="filters.keyword" clearable placeholder="搜索仓库、中文说明或适合原因" @keyup.enter="search"><template #prefix><Search /></template></el-input>
        <el-select v-model="filters.language" clearable placeholder="语言" @change="search"><el-option v-for="item in languages" :key="item" :label="item" :value="item" /></el-select>
        <el-select v-model="filters.difficulty" clearable placeholder="难度" @change="search"><el-option label="入门" value="BEGINNER" /><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /></el-select>
        <el-input v-model="filters.topic" clearable placeholder="方向，如 React、AI tools" @keyup.enter="search" />
        <el-button type="primary" :icon="Search" @click="search">筛选</el-button>
      </div>

      <div v-loading="loading" class="project-grid" aria-live="polite">
        <article v-for="project in projects" :key="project.id" class="project-card" tabindex="0" @click="openDetail(project.id)" @keyup.enter="openDetail(project.id)">
          <div class="project-card-top"><span class="language-dot">{{ project.primaryLanguage || 'Repository' }}</span><el-tag size="small" effect="plain">{{ difficultyText(project.difficulty) }}</el-tag></div>
          <h4>{{ project.descriptionZh || project.repositoryName }}</h4>
          <p>{{ project.beginnerReason || project.descriptionOriginal }}</p>
          <div class="risk-row">
            <span v-if="!hasRisks(project)" class="risk-safe"><CircleCheck />环境轻量</span>
            <span v-if="project.requiresDocker">Docker</span><span v-if="project.requiresDatabase">数据库</span>
            <span v-if="project.requiresSecrets">密钥</span><span v-if="project.requiresExternalService">外部服务</span>
          </div>
          <div class="score-row"><span><b>{{ project.beginnerScore }}</b>/100 新手分</span><span>{{ formatStars(project.stars) }} Stars</span></div>
        </article>
      </div>
      <el-empty v-if="!loading && !projects.length" description="没有符合当前条件的项目，请放宽筛选条件" />
      <div class="pagination"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadProjects" /></div>
    </section>

    <el-drawer v-model="detailVisible" size="min(720px, 96vw)" :with-header="false" class="project-drawer">
      <div v-if="detail.project" class="detail-content">
        <div class="detail-header"><div><span>{{ detail.project.fullName }}</span><h3>{{ detail.project.descriptionZh || detail.project.repositoryName }}</h3></div><el-button circle aria-label="关闭" @click="detailVisible = false"><Close /></el-button></div>
        <p class="detail-summary">{{ detail.project.descriptionZh || detail.project.descriptionOriginal }}</p>
        <div class="detail-actions"><el-button type="primary" @click="openGithub(detail.project.htmlUrl)">打开 GitHub<ArrowRight /></el-button><el-button @click="importToPlan(detail.project)">用于方案设计</el-button></div>
        <dl class="fact-grid"><div><dt>新手难度</dt><dd>{{ difficultyText(detail.project.difficulty) }}</dd></div><div><dt>预计时间</dt><dd>{{ detail.project.estimatedDuration || '待评估' }}</dd></div><div><dt>技术栈</dt><dd>{{ detail.project.techStack || detail.project.primaryLanguage || '-' }}</dd></div><div><dt>最近核验</dt><dd>{{ formatDate(detail.project.lastVerifiedAt) }}</dd></div></dl>
        <section><h4>本周表现</h4><p>{{ detail.project.weeklyPerformance }}</p></section>
        <section><h4>它是什么</h4><p>{{ detail.project.whatIsIt || detail.project.descriptionOriginal }}</p></section>
        <section><h4>为什么适合新手</h4><p>{{ detail.project.beginnerReason }}</p></section>
        <section><h4>适合做什么</h4><p>{{ detail.project.modificationIdeas }}</p></section>
        <section><h4>主要风险</h4><p>{{ detail.project.mainRisks || riskSummary(detail.project) }}</p></section>
        <section class="recommendation"><h4>最终建议</h4><p>{{ detail.project.finalRecommendation || '建议先按 README 跑通基础示例，再选择一个小改造方向做成可展示作品。' }}</p></section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, CircleCheck, Close, Search } from '@element-plus/icons-vue'
import request from '../api/request'

defineProps({ embedded: { type: Boolean, default: false } })

const router = useRouter()
const weekly = ref([])
const projects = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const loading = ref(false)
const weeklyLoading = ref(false)
const detailVisible = ref(false)
const detail = reactive({ project: null })
const filters = reactive({ keyword: '', language: '', difficulty: '', topic: '' })
const languages = ['TypeScript', 'JavaScript', 'Python', 'HTML', 'CSS']

const loadWeekly = async () => { weeklyLoading.value = true; try { const r = await request.get('/app/beginner-projects/weekly'); weekly.value = r.data || [] } finally { weeklyLoading.value = false } }
const loadProjects = async () => { loading.value = true; try { const r = await request.get('/app/beginner-projects', { params: { ...filters, page: page.value, size: pageSize } }); projects.value = r.data?.records || []; total.value = Number(r.data?.total || 0) } finally { loading.value = false } }
const search = () => { page.value = 1; loadProjects() }
const openDetail = async id => { const r = await request.get(`/app/beginner-projects/${id}`); detail.project = r.data?.project; detailVisible.value = true }
const openGithub = url => window.open(url, '_blank', 'noopener,noreferrer')
const importToPlan = project => {
  const text = [
    `项目想法：基于 ${project.fullName} 做一个适合展示的 Vibe Coding 项目`,
    `参考仓库：${project.htmlUrl}`,
    `它是什么：${project.whatIsIt || project.descriptionOriginal || ''}`,
    `为什么适合新手：${project.beginnerReason || ''}`,
    `计划改造方向：${project.modificationIdeas || ''}`,
    `预计时间：${project.estimatedDuration || '待评估'}`,
    `需要规避的风险：${project.mainRisks || riskSummary(project)}`
  ].join('\n\n')
  sessionStorage.setItem('plan_import', text)
  router.push('/plan')
}
const hasRisks = p => p.requiresDocker || p.requiresDatabase || p.requiresSecrets || p.requiresExternalService
const riskSummary = project => {
  const risks = []
  if (project.requiresDocker) risks.push('需要 Docker 环境')
  if (project.requiresDatabase) risks.push('需要数据库')
  if (project.requiresSecrets) risks.push('需要 API Key 或密钥')
  if (project.requiresExternalService) risks.push('依赖外部服务')
  if (project.requiresGpu) risks.push('需要 GPU')
  if (project.requiresNativeBuild) risks.push('需要本地编译工具链')
  return risks.length ? risks.join('；') : '未检测到明显的重型环境依赖，仍建议先阅读 README 并跑通最小示例。'
}
const difficultyText = value => ({ BEGINNER: '入门', EASY: '简单', MEDIUM: '中等' }[value] || '待评估')
const formatStars = value => Number(value || 0) >= 1000 ? `${(Number(value) / 1000).toFixed(1)}K` : String(value || 0)
const formatDate = value => value ? String(value).replace('T', ' ').slice(0, 16) : '待核验'
onMounted(() => { loadWeekly(); loadProjects() })
</script>

<style scoped>
.beginner-page{max-width:1240px;margin:0 auto;color:var(--text-primary)}.page-hero{padding:24px 28px;border:1px solid var(--border-orange);border-radius:12px;background:#fffaf5}.back-button{display:inline-flex;min-height:44px;align-items:center;gap:7px;border:0;background:transparent;color:var(--text-secondary);font-weight:750;cursor:pointer}.back-button svg,.card-footer svg{width:16px}.hero-copy{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;margin-top:18px}.eyebrow,.section-index{color:var(--brand-strong);font:900 11px/1 var(--font-mono)}.hero-copy h2{margin:9px 0;font:900 36px/1.18 var(--font-mono);letter-spacing:0}.hero-copy p{max-width:680px;margin:0;color:var(--text-secondary);line-height:1.75}.hero-fact{max-width:260px;padding:14px 16px;border-left:3px solid var(--brand);background:#fff}.hero-fact strong,.hero-fact span{display:block}.hero-fact span{margin-top:5px;color:var(--text-muted);font-size:12px;line-height:1.6}.weekly-section,.library-section{margin-top:28px}.section-heading{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:14px}.section-heading>div{display:flex;align-items:center;gap:10px}.section-heading h3{margin:0;font:900 21px/1.2 var(--font-mono)}.section-heading>span{color:var(--text-muted);font-size:12px}.weekly-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.weekly-card,.project-card{border:1px solid var(--border-weak);border-radius:8px;background:#fff}.weekly-card{display:flex;min-height:280px;flex-direction:column;padding:20px}.weekly-rank{color:#fed7aa;font:900 38px/1 var(--font-mono)}.project-meta,.score-row,.project-card-top,.card-footer{display:flex;align-items:center;justify-content:space-between;gap:12px}.project-meta{margin-top:18px;color:var(--text-muted);font-size:11px}.weekly-card h4,.project-card h4{margin:14px 0 8px;font:850 17px/1.45 var(--font-mono)}.weekly-card p,.project-card p{display:-webkit-box;overflow:hidden;margin:0;color:var(--text-secondary);font-size:13px;line-height:1.7;-webkit-box-orient:vertical;-webkit-line-clamp:3}.card-footer{margin-top:auto;padding-top:18px;border-top:1px solid var(--border-weak);color:var(--text-muted);font-size:11px}.card-footer button{display:inline-flex;align-items:center;gap:5px;border:0;background:transparent;color:var(--brand-strong);font-weight:800;cursor:pointer}.filters{display:grid;grid-template-columns:minmax(240px,1.7fr) repeat(2,minmax(120px,.7fr)) minmax(170px,1fr) auto;gap:8px;padding:12px;border:1px solid var(--border-weak);border-radius:8px;background:#fff}.project-grid{display:grid;min-height:180px;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:14px}.project-card{display:flex;min-height:255px;flex-direction:column;padding:18px;cursor:pointer;transition:border-color .18s,transform .18s,box-shadow .18s}.project-card:hover,.project-card:focus-visible{border-color:var(--brand);box-shadow:var(--shadow-lift);outline:none;transform:translateY(-2px)}.language-dot{color:var(--brand-strong);font-size:11px;font-weight:850}.risk-row{display:flex;min-height:24px;flex-wrap:wrap;gap:6px;margin-top:14px}.risk-row span{padding:4px 7px;border-radius:4px;background:#fff7ed;color:#9a3412;font-size:10px;font-weight:750}.risk-row .risk-safe{display:inline-flex;align-items:center;gap:4px;background:#f0fdf4;color:#166534}.risk-safe svg{width:13px}.score-row{margin-top:auto;padding-top:14px;border-top:1px solid var(--border-weak);color:var(--text-muted);font-size:11px}.score-row b{color:var(--brand-strong);font:900 19px/1 var(--font-mono)}.pagination{display:flex;justify-content:flex-end;margin-top:18px}.detail-content{padding:22px}.detail-header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.detail-header span{color:var(--brand-strong);font-size:12px;font-weight:800}.detail-header h3{margin:7px 0 0;font:900 23px/1.35 var(--font-mono)}.detail-summary{color:var(--text-secondary);line-height:1.75}.detail-actions{display:flex;gap:8px;margin:18px 0}.fact-grid{display:grid;grid-template-columns:1fr 1fr;gap:1px;padding:1px;background:var(--border-weak)}.fact-grid div{padding:13px;background:#fff}.fact-grid dt{color:var(--text-muted);font-size:11px}.fact-grid dd{margin:5px 0 0;font-weight:800}.detail-content section{margin-top:22px}.detail-content section h4{margin:0 0 8px;font:850 15px/1.3 var(--font-mono)}.detail-content section p{margin:0;color:var(--text-secondary);line-height:1.75;white-space:pre-line}.evidence-list{display:grid;gap:8px}.evidence-list div{display:grid;grid-template-columns:110px 50px 1fr;gap:8px;padding:10px 12px;border:1px solid var(--border-weak);border-radius:6px}.evidence-list b{color:var(--brand-strong)}.evidence-list small{color:var(--text-muted);line-height:1.5}
@media(max-width:900px){.weekly-grid,.project-grid{grid-template-columns:1fr 1fr}.filters{grid-template-columns:1fr 1fr}.filters>*:first-child{grid-column:1/-1}.hero-fact{display:none}}@media(max-width:620px){.page-hero{padding:20px}.hero-copy h2{font-size:28px}.weekly-grid,.project-grid,.filters{grid-template-columns:1fr}.filters>*:first-child{grid-column:auto}.section-heading{align-items:flex-start;flex-direction:column;gap:6px}.fact-grid{grid-template-columns:1fr}.evidence-list div{grid-template-columns:90px 45px 1fr}}
.detail-content .recommendation{padding:16px;border-left:3px solid var(--brand);background:#fff7ed}
</style>
