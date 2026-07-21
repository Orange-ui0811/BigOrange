<template>
  <div class="opensource-page">
    <PageTour
      :key="activeTab"
      ref="tourRef"
      :storage-key="`open-source-${activeTab}-v2`"
      :steps="tourSteps"
    />

    <header class="source-hero">
      <div class="hero-toolbar">
        <button type="button" class="back-link" @click="router.push('/ai')"><ArrowLeft />返回 AI 工具</button>
        <button type="button" class="tour-trigger" @click="tourRef?.restart()"><Guide />使用引导</button>
      </div>
      <div class="hero-main">
        <div>
          <span class="eyebrow">OPEN SOURCE PATH</span>
          <h2>从一个合适的 Issue，开始真实的开源贡献。</h2>
          <p>先核验任务是否值得参与，再让 AI 帮你选择、理解岗位价值，并生成一份可执行的完成指南。</p>
        </div>
        <div class="quota-strip" aria-label="AI 功能配额">
          <span><small>AI 匹配</small><strong>{{ config.matchCost }}</strong></span>
          <span><small>岗位匹配</small><strong>{{ config.jobMatchCost }}</strong></span>
          <span><small>贡献指南</small><strong>{{ config.guideCost }}</strong></span>
        </div>
      </div>
    </header>

    <nav class="stage-nav" role="tablist" aria-label="开源贡献阶段">
      <button
        v-for="stage in stages"
        :id="`source-tab-${stage.id}`"
        :key="stage.id"
        type="button"
        role="tab"
        :aria-selected="activeTab === stage.id"
        aria-controls="source-stage-panel"
        :class="{ active: activeTab === stage.id }"
        @click="switchTab(stage.id)"
      >
        <span class="stage-index">{{ stage.index }}</span>
        <span class="stage-icon"><component :is="stage.icon" /></span>
        <span class="stage-copy"><strong>{{ stage.title }}</strong><small>{{ stage.description }}</small></span>
      </button>
    </nav>

    <section id="source-stage-panel" class="stage-content" role="tabpanel" :aria-labelledby="`source-tab-${activeTab}`">
      <header class="stage-heading">
        <div><span>{{ currentStage.index }} · {{ currentStage.kicker }}</span><h3>{{ currentStage.heading }}</h3><p>{{ currentStage.intro }}</p></div>
        <span v-if="currentStage.cost" class="stage-cost">本次 {{ currentStage.cost }} 配额</span>
      </header>

      <template v-if="activeTab === 'discover'">
        <div class="filter-panel">
          <label class="search-field"><span>关键词</span><el-input v-model="filters.keyword" clearable placeholder="搜索标题或摘要" @keyup.enter="searchIssues"><template #prefix><Search /></template></el-input></label>
          <label><span>技术方向</span><el-select v-model="filters.tech" clearable placeholder="全部技术"><el-option v-for="item in techOptions" :key="item" :label="item" :value="item" /></el-select></label>
          <label><span>难度</span><el-select v-model="filters.difficulty" clearable placeholder="全部难度"><el-option label="入门" value="BEGINNER" /><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /></el-select></label>
          <label><span>参与状态</span><el-select v-model="filters.participationStatus" clearable placeholder="全部状态"><el-option label="可尝试" value="TRY" /><el-option label="可能被认领" value="POSSIBLY_CLAIMED" /><el-option label="不建议" value="NOT_RECOMMENDED" /></el-select></label>
          <el-button type="primary" :loading="loading" @click="searchIssues">应用筛选</el-button>
        </div>

        <div class="candidate-bar">
          <div><span>候选清单</span><strong>{{ selectedIds.length }}/20</strong><small>{{ selectedIds.length >= 3 ? '已满足 AI 匹配条件' : '至少选择 3 项后可进行 AI 匹配' }}</small></div>
          <el-button type="primary" plain :disabled="selectedIds.length < 3" @click="switchTab('match')">用候选 Issue 进行匹配 <ArrowRight /></el-button>
        </div>

        <el-skeleton v-if="loading" :rows="8" animated />
        <div v-else-if="!issues.length" class="empty-state"><Search /><h4>没有找到符合条件的 Issue</h4><p>放宽技术方向或参与状态后再试一次。</p><el-button @click="resetFilters">清空筛选</el-button></div>
        <div v-else class="issue-grid">
          <article v-for="issue in issues" :key="issue.id" class="issue-card">
            <div class="issue-topline">
              <span class="repository-name">{{ issue.repositoryFullName || 'GitHub Issue' }}</span>
              <el-checkbox :model-value="selectedIds.includes(issue.id)" :aria-label="`将 ${issueTitle(issue)} 加入候选`" @change="toggleCandidate(issue.id)">候选</el-checkbox>
            </div>
            <h4>{{ issueTitle(issue) }}</h4>
            <p>{{ issue.summaryZh || '中文摘要生成中，可先查看 Issue 原文与核验信息。' }}</p>
            <div class="issue-signals">
              <span :class="['state-pill', statusClass(issue.participationStatus)]">{{ statusText(issue.participationStatus) }}</span>
              <span class="difficulty-pill">{{ difficultyText(issue.difficulty) }}</span>
              <span>{{ issue.commentsCount || 0 }} 条评论</span>
            </div>
            <div class="issue-footer"><span>{{ formatRelative(issue.githubUpdatedAt) }}</span><button type="button" @click="openIssue(issue.id)">查看详情 <ArrowRight /></button></div>
          </article>
        </div>
        <div class="pagination-row"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadIssues" /></div>
      </template>

      <template v-else-if="activeTab === 'match'">
        <div class="match-workspace">
          <div class="match-form">
            <div class="form-number">01</div>
            <h4>描述你的能力和限制</h4>
            <p>{{ candidateSourceText }}</p>
            <label><span>个人情况</span><el-input v-model="matchForm.profile" type="textarea" :rows="7" maxlength="2000" show-word-limit placeholder="例如：熟悉 React、TypeScript 和 Vitest，第一次参与开源，希望两天内完成，不使用 Docker。" /></label>
            <div class="match-fields">
              <label><span>优先技术</span><el-select v-model="matchForm.tech" clearable placeholder="不限"><el-option v-for="item in techOptions" :key="item" :label="item" :value="item" /></el-select></label>
              <label><span>最高难度</span><el-select v-model="matchForm.difficulty" clearable placeholder="不限"><el-option label="入门" value="BEGINNER" /><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /></el-select></label>
            </div>
            <el-button type="primary" :loading="matching" @click="runMatch"><MagicStick />开始 AI 匹配 · {{ config.matchCost }} 配额</el-button>
          </div>
          <div class="match-results" aria-live="polite">
            <div class="results-heading"><div><span>02</span><h4>候选结果</h4></div><small v-if="matches.length">共 {{ matches.length }} 项</small></div>
            <el-skeleton v-if="matching" :rows="8" animated />
            <div v-else-if="!matches.length" class="empty-state compact"><MagicStick /><h4>等待开始匹配</h4><p>AI 会给出 3–5 个候选，并标出相对容易完成的一项。</p></div>
            <article v-for="(item, index) in matches" :key="item.issue.id" :class="['match-result', { recommended: item.issue.id === recommendedIssueId }]">
              <div class="result-rank">{{ String(index + 1).padStart(2, '0') }}</div>
              <div><span class="result-label">{{ item.issue.id === recommendedIssueId ? '优先建议' : difficultyText(item.issue.difficulty) }}</span><h4>{{ issueTitle(item.issue) }}</h4><p>{{ item.reason }}</p><small>{{ item.risk }}</small></div>
              <button type="button" @click="openIssue(item.issue.id)">查看 <ArrowRight /></button>
            </article>
          </div>
        </div>
      </template>

      <template v-else-if="activeTab === 'job'">
        <div v-if="!jobCandidates.length" class="empty-state"><Briefcase /><h4>还没有可分析的 Issue</h4><p>先去发现页加入候选，或完成一次 AI 匹配。</p><el-button type="primary" @click="switchTab('discover')">前往发现 Issue</el-button></div>
        <div v-else class="job-workspace">
          <div class="job-targets">
            <div class="column-heading"><span>01</span><div><h4>选择 Issue</h4><p>优先展示 AI 结果或已加入候选的项目。</p></div></div>
            <button v-for="issue in jobCandidates" :key="issue.id" type="button" :class="{ active: jobTarget?.id === issue.id }" @click="selectJobTarget(issue)">
              <span>{{ issueTitle(issue) }}</span><small>{{ statusText(issue.participationStatus) }} · {{ difficultyText(issue.difficulty) }}</small><ArrowRight />
            </button>
          </div>
          <div class="job-analysis" aria-live="polite">
            <div class="column-heading"><span>02</span><div><h4>岗位能力映射</h4><p>确认后再调用 AI，不会因选择列表而扣费。</p></div></div>
            <el-skeleton v-if="jobMatching" :rows="7" animated />
            <div v-else-if="!jobTarget" class="empty-state compact"><Briefcase /><h4>请选择左侧 Issue</h4><p>选择后可以先确认标题，再决定是否开始分析。</p></div>
            <div v-else-if="!jobMatches.length" class="analysis-ready"><span>已选择</span><h4>{{ issueTitle(jobTarget) }}</h4><p>将分析它对应的岗位方向、匹配原因和需要重点学习的能力。</p><el-button type="primary" @click="runJobMatchFor(jobTarget)">开始岗位分析 · {{ config.jobMatchCost }} 配额</el-button></div>
            <div v-else class="job-results"><div class="selected-issue">{{ issueTitle(jobTarget) }}</div><article v-for="job in jobMatches" :key="job.position"><strong>{{ job.position }}</strong><p>{{ job.reason }}</p><small>{{ job.focus }}</small></article></div>
          </div>
        </div>
      </template>

      <template v-else>
        <el-skeleton v-if="guidesLoading" :rows="7" animated />
        <div v-else-if="!guides.length" class="empty-state"><DocumentChecked /><h4>还没有贡献指南</h4><p>在 Issue 详情中确认任务后，即可生成并保存完成指南。</p><el-button type="primary" @click="switchTab('discover')">选择一个 Issue</el-button></div>
        <div v-else class="guide-workspace">
          <div class="guide-summary"><span>{{ guides.length }}</span><div><strong>份已保存指南</strong><small>点击任意记录查看完整内容</small></div></div>
          <div class="guide-list">
            <button v-for="guide in guides" :key="guide.id" type="button" @click="openGuide(guide.id)">
              <DocumentChecked /><span><strong>{{ guide.issueTitle }}</strong><small>{{ guide.repositoryFullName }} · {{ formatTime(guide.updateTime) }}</small></span><ArrowRight />
            </button>
          </div>
        </div>
      </template>
    </section>

    <el-drawer v-model="detailOpen" size="min(720px, 94vw)" title="Issue 详情" destroy-on-close>
      <el-skeleton v-if="detailLoading" :rows="10" animated />
      <div v-else-if="detail" class="issue-detail">
        <div class="detail-heading"><div><small>{{ detail.repositoryFullName }} · ★ {{ detail.stars || 0 }}</small><h2>{{ issueTitle(detail) }}</h2></div><a :href="detail.htmlUrl" target="_blank" rel="noopener">GitHub 原文</a></div>
        <p class="detail-summary">{{ detail.summaryZh || '中文摘要生成中，请以英文原文为准。' }}</p>
        <dl class="facts">
          <div><dt>最近核验</dt><dd>{{ formatTime(detail.verifiedAt) || '尚未核验' }}</dd></div><div><dt>参与判断</dt><dd>{{ statusText(detail.participationStatus) }}</dd></div>
          <div><dt>关联 PR</dt><dd>{{ detail.linkedPrCount || 0 }}</dd></div><div><dt>认领线索</dt><dd>{{ detail.claimStatus || '无' }}</dd></div>
          <div><dt>Node</dt><dd>{{ detail.nodeVersion || '需查看仓库' }}</dd></div><div><dt>包管理器</dt><dd>{{ detail.packageManager || '需查看仓库' }}</dd></div>
          <div><dt>系统要求</dt><dd>{{ detail.systemRequirement || '仓库未说明' }}</dd></div><div><dt>内存 / 磁盘</dt><dd>{{ detail.memoryRequirement || '未说明' }} / {{ detail.diskRequirement || '未说明' }}</dd></div>
        </dl>
        <section><h3>英文原文</h3><pre>{{ detail.bodyOriginal || detail.titleOriginal }}</pre></section>
        <section><h3>环境提示</h3><p>{{ detail.environmentNotes || '请在贡献前阅读仓库 README 与贡献指南。' }}</p></section>
        <p v-if="detail.contributionUrl" class="contribution-link"><a :href="detail.contributionUrl" target="_blank" rel="noopener">查看仓库贡献指南</a></p>
        <div class="detail-actions"><el-button :loading="verifying" @click="verifyCurrent">实时重新核验</el-button><el-button :loading="jobMatching" @click="runJobMatch">匹配岗位 · {{ config.jobMatchCost }}</el-button><el-button type="primary" :loading="guideGenerating" @click="generateGuide">生成完成指南 · {{ config.guideCost }}</el-button></div>
        <div v-if="jobMatches.length" class="job-results drawer-results"><h3>匹配岗位</h3><article v-for="job in jobMatches" :key="job.position"><strong>{{ job.position }}</strong><p>{{ job.reason }}</p><small>{{ job.focus }}</small></article></div>
      </div>
    </el-drawer>

    <el-drawer v-model="guideOpen" size="min(760px, 94vw)" title="开源贡献指南" destroy-on-close>
      <el-skeleton v-if="guideLoading" :rows="10" animated />
      <div v-else class="markdown-body" v-html="renderMarkdown(currentGuide?.content)"></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'

const route = useRoute()
const router = useRouter()
const tourRef = ref(null)
const activeTab = ref('discover')
const loading = ref(false), matching = ref(false), detailOpen = ref(false), detailLoading = ref(false)
const verifying = ref(false), jobMatching = ref(false), guideGenerating = ref(false)
const guidesLoading = ref(false), guideLoading = ref(false), guideOpen = ref(false)
const issues = ref([]), matches = ref([]), guides = ref([]), jobMatches = ref([])
const detail = ref(null), currentGuide = ref(null), recommendedIssueId = ref(null), jobTarget = ref(null)
const selectedIds = ref([]), page = ref(1), total = ref(0)
const pageSize = 12
const config = ref({ matchCost: 40, jobMatchCost: 40, guideCost: 50 })
const filters = ref({ keyword: '', tech: '', difficulty: '', participationStatus: '', updatedDays: 30 })
const matchForm = ref({ profile: '', tech: '', difficulty: '' })
const techOptions = ['TypeScript', 'JavaScript', 'React', 'Next.js', 'Vue', 'Vite', 'Storybook', 'Playwright', 'Node.js']
const stages = [
  { id: 'discover', index: '01', title: '发现 Issue', icon: 'Search', description: '筛选与核验', kicker: 'DISCOVER', heading: '先找到值得投入的任务', intro: '浏览近期仍可参与的 Issue，查看难度、认领风险和更新时间。' },
  { id: 'match', index: '02', title: 'AI 匹配', icon: 'MagicStick', description: '选择候选', kicker: 'MATCH', heading: '让 AI 帮你缩小选择范围', intro: '结合你的技术栈、时间和环境限制，从候选中选择相对合适的一项。', cost: computed(() => config.value.matchCost) },
  { id: 'job', index: '03', title: '岗位匹配', icon: 'Briefcase', description: '理解能力价值', kicker: 'CAREER', heading: '理解这项贡献对应的岗位能力', intro: '把 Issue 所需能力映射到岗位方向，明确值得深入学习的部分。', cost: computed(() => config.value.jobMatchCost) },
  { id: 'guides', index: '04', title: '贡献指南', icon: 'DocumentChecked', description: '按步骤完成', kicker: 'GUIDE', heading: '保存你的开源贡献完成指南', intro: '查看已经生成的指南，按仓库要求完成复现、修改、测试和提交。' },
]
const validTabs = new Set(stages.map(item => item.id))
const currentStage = computed(() => {
  const stage = stages.find(item => item.id === activeTab.value) || stages[0]
  return { ...stage, cost: stage.cost?.value || null }
})
const jobCandidates = computed(() => {
  const matched = matches.value.map(item => item.issue).filter(Boolean)
  if (matched.length) return matched
  const selected = issues.value.filter(item => selectedIds.value.includes(item.id))
  return selected.length ? selected : issues.value.slice(0, 5)
})
const candidateSourceText = computed(() => selectedIds.value.length >= 3 ? `将比较你选择的 ${selectedIds.value.length} 个候选 Issue。` : '候选不足 3 项时，将从当前筛选条件中寻找合适任务。')
const stageTourSteps = {
  discover: [
    { selector: '.filter-panel', text: '按关键词、技术、难度和参与状态缩小范围。浏览和筛选 Issue 不消耗配额。', expression: 'sparkle' },
    { selector: '.candidate-bar', text: '把感兴趣的 Issue 加入候选，选择至少 3 项后即可进入 AI 匹配。', expression: 'smile' },
  ],
  match: [
    { selector: '.match-form', text: '描述你的技术能力、可投入时间和环境限制，信息越具体，匹配结果越实用。', expression: 'sparkle' },
    { selector: '.match-results', text: '结果会比较候选 Issue 并解释推荐理由。点击开始前会明确显示本次配额。', expression: 'smile' },
  ],
  job: [
    { selector: '.stage-content', text: '先选择一个候选 Issue，再分析它对应的岗位方向、能力价值和学习重点。选择本身不会扣除配额。', expression: 'wink' },
    { selector: '.job-analysis, .empty-state', text: '确认目标后再启动岗位分析；页面会在调用 AI 前显示所需配额。', expression: 'smile' },
  ],
  guides: [
    { selector: '.stage-content', text: '这里保存已经生成的贡献指南。没有记录时，可以先回到发现页选择一个 Issue。', expression: 'wink' },
    { selector: '.guide-workspace, .empty-state', text: '指南会整理复现、修改、测试、提交 PR 和结项检查事项，帮助你按步骤完成贡献。', expression: 'smile' },
  ],
}
const tourSteps = computed(() => [
  { selector: '.source-hero', text: '这里展示开源贡献路径和三项 AI 操作的配额。', expression: 'smile' },
  { selector: '.stage-nav', text: '四个阶段可以独立进入，页面地址会保留当前阶段。', expression: 'wink' },
  ...(stageTourSteps[activeTab.value] || stageTourSteps.discover),
])

const requestOk = response => response?.code === 1
const issueTitle = issue => issue?.titleZh || issue?.titleOriginal || '未命名 Issue'
const statusText = value => ({ TRY: '可尝试', POSSIBLY_CLAIMED: '可能被认领', NOT_RECOMMENDED: '不建议' }[value] || '待核验')
const statusClass = value => ({ TRY: 'try', POSSIBLY_CLAIMED: 'claimed', NOT_RECOMMENDED: 'avoid' }[value] || '')
const difficultyText = value => ({ BEGINNER: '入门', EASY: '简单', MEDIUM: '中等' }[value] || '待评估')
const formatTime = value => value ? String(value).replace('T', ' ').substring(0, 16) : ''
const formatRelative = value => value ? `更新于 ${formatTime(value)}` : '更新时间未知'
const renderMarkdown = value => DOMPurify.sanitize(marked.parse(value || ''))

const loadIssues = async () => {
  loading.value = true
  try {
    const response = await request.get('/app/open-source/issues', { params: { page: page.value, size: pageSize, ...filters.value } })
    if (requestOk(response)) { issues.value = response.data.records || []; total.value = Number(response.data.total || 0) }
  } finally { loading.value = false }
}
const searchIssues = () => { page.value = 1; loadIssues() }
const resetFilters = () => { filters.value = { keyword: '', tech: '', difficulty: '', participationStatus: '', updatedDays: 30 }; searchIssues() }
const toggleCandidate = id => { selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(item => item !== id) : [...selectedIds.value, id].slice(-20) }
const openIssue = async id => {
  detailOpen.value = true; detailLoading.value = true; detail.value = null; jobMatches.value = []
  try { const response = await request.get(`/app/open-source/issues/${id}`); if (requestOk(response)) detail.value = response.data }
  finally { detailLoading.value = false }
}
const verifyCurrent = async () => {
  verifying.value = true
  try { const response = await request.post(`/app/open-source/issues/${detail.value.id}/verify`, {}, { timeout: 90000 }); if (requestOk(response)) { detail.value = response.data; ElMessage.success('已按 GitHub 当前状态重新核验') } }
  finally { verifying.value = false }
}
const runMatch = async () => {
  if (matchForm.value.profile.trim().length < 10) { ElMessage.warning('请先简要描述你的技术能力、时间或环境限制'); return }
  matching.value = true; matches.value = []
  try {
    const response = await request.post('/app/open-source/match', { ...matchForm.value, selectedIssueIds: selectedIds.value }, { timeout: 150000 })
    if (requestOk(response)) { matches.value = response.data.matches || []; recommendedIssueId.value = response.data.recommendedIssueId; ElMessage.success('匹配完成') }
  } finally { matching.value = false }
}
const selectJobTarget = issue => { jobTarget.value = issue; jobMatches.value = [] }
const runJobMatchFor = async issue => {
  if (!issue?.id) return
  jobTarget.value = issue; jobMatches.value = []; jobMatching.value = true
  try { const response = await request.post(`/app/open-source/issues/${issue.id}/job-match`, {}, { timeout: 150000 }); if (requestOk(response)) jobMatches.value = response.data.matches || [] }
  finally { jobMatching.value = false }
}
const runJobMatch = () => runJobMatchFor(detail.value)
const generateGuide = async () => {
  guideGenerating.value = true
  try {
    const response = await request.post(`/app/open-source/issues/${detail.value.id}/guide`, {}, { timeout: 150000 })
    if (requestOk(response)) { currentGuide.value = response.data.guide; guideOpen.value = true; await loadGuides() }
  } finally { guideGenerating.value = false }
}
const loadGuides = async () => {
  guidesLoading.value = true
  try { const response = await request.get('/app/open-source/guides'); if (requestOk(response)) guides.value = response.data || [] }
  finally { guidesLoading.value = false }
}
const openGuide = async id => {
  guideOpen.value = true; guideLoading.value = true
  try { const response = await request.get(`/app/open-source/guides/${id}`); if (requestOk(response)) currentGuide.value = response.data }
  finally { guideLoading.value = false }
}
const switchTab = name => {
  if (!validTabs.has(name)) return
  activeTab.value = name
  router.replace({ path: route.path, query: { tab: name } })
  if (name === 'guides') loadGuides()
}

watch(() => route.query.tab, value => {
  if (validTabs.has(value) && activeTab.value !== value) { activeTab.value = value; if (value === 'guides') loadGuides() }
})
watch(() => route.query.issue, value => { if (value) openIssue(value) })

onMounted(async () => {
  if (validTabs.has(route.query.tab)) activeTab.value = route.query.tab
  try { const response = await request.get('/app/open-source/config'); if (requestOk(response)) config.value = response.data } catch {}
  await loadIssues()
  if (activeTab.value === 'guides') await loadGuides()
  if (route.query.issue) openIssue(route.query.issue)
})
</script>

<style scoped>
.opensource-page { max-width: 1280px; margin: 0 auto; }
.source-hero { padding: 20px 24px 24px; border: 1px solid #bfdbfe; border-radius: 16px; background: linear-gradient(120deg,#f8fbff 0%,#fff 72%); box-shadow: var(--shadow-card); }
.hero-toolbar { display: flex; align-items: center; justify-content: space-between; }.back-link,.tour-trigger { display: inline-flex; min-height: 44px; align-items: center; gap: 7px; padding: 0 10px; border: 0; background: transparent; color: var(--text-secondary); font-weight: 700; cursor: pointer; }.back-link svg,.tour-trigger svg { width: 16px; }.back-link:hover,.back-link:focus-visible,.tour-trigger:hover,.tour-trigger:focus-visible { color: #1d4ed8; outline: 2px solid rgba(37,99,235,.2); outline-offset: 1px; }
.hero-main { display: flex; align-items: flex-end; justify-content: space-between; gap: 28px; padding-top: 14px; }.eyebrow { color: #1d4ed8; font: 900 11px/1.2 var(--font-mono); }.source-hero h2 { max-width: 800px; margin: 8px 0; font: 900 clamp(27px,4vw,39px)/1.16 var(--font-mono); letter-spacing: 0; }.source-hero p { max-width: 760px; margin: 0; color: var(--text-secondary); font-size: 14px; line-height: 1.75; }
.quota-strip { display: grid; grid-template-columns: repeat(3,76px); flex-shrink: 0; border: 1px solid #dbeafe; border-radius: 8px; overflow: hidden; background: #dbeafe; gap: 1px; }.quota-strip span { display: grid; min-height: 68px; place-content: center; background: #fff; text-align: center; }.quota-strip small { color: var(--text-muted); font-size: 10px; }.quota-strip strong { margin-top: 4px; color: #1d4ed8; font: 900 18px/1 var(--font-mono); }
.stage-nav { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 1px; margin-top: 18px; border: 1px solid var(--border-weak); border-radius: 8px; overflow: hidden; background: var(--border-weak); }
.stage-nav button { position: relative; display: grid; min-height: 92px; grid-template-columns: 24px 34px minmax(0,1fr); align-items: center; gap: 10px; padding: 14px; border: 0; background: #fff; color: var(--text-primary); text-align: left; cursor: pointer; transition: background .18s,color .18s; }.stage-nav button:hover,.stage-nav button:focus-visible { z-index: 1; background: #f8fbff; outline: 2px solid #2563eb; outline-offset: -2px; }.stage-nav button.active { color: #1d4ed8; background: #eff6ff; box-shadow: inset 0 -3px 0 #2563eb; }
.stage-index { color: var(--text-muted); font: 900 10px/1 var(--font-mono); }.stage-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 7px; background: #f5f7fa; }.active .stage-icon { background: #dbeafe; }.stage-icon :deep(svg) { width: 17px; }.stage-copy strong,.stage-copy small { display: block; }.stage-copy strong { font: 800 13px/1.35 var(--font-mono); }.stage-copy small { margin-top: 4px; color: var(--text-muted); font-size: 10px; }
.stage-content { margin-top: 18px; padding: 0 2px 30px; }.stage-heading { display: flex; min-height: 90px; align-items: flex-end; justify-content: space-between; gap: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--border-weak); }.stage-heading span { color: #1d4ed8; font: 900 10px/1.2 var(--font-mono); }.stage-heading h3 { margin: 7px 0 5px; font: 900 22px/1.3 var(--font-mono); letter-spacing: 0; }.stage-heading p { margin: 0; color: var(--text-secondary); font-size: 13px; line-height: 1.65; }.stage-heading .stage-cost { align-self: center; padding: 8px 10px; border: 1px solid #bfdbfe; border-radius: 999px; background: #eff6ff; white-space: nowrap; }
.filter-panel { display: grid; grid-template-columns: minmax(220px,1fr) repeat(3,150px) auto; align-items: end; gap: 10px; padding: 18px 0; }.filter-panel label,.match-form label { display: grid; gap: 7px; color: var(--text-secondary); font-size: 11px; font-weight: 800; }.filter-panel :deep(.el-input__wrapper),.filter-panel :deep(.el-select__wrapper),.match-fields :deep(.el-select__wrapper) { min-height: 44px; }.filter-panel > :deep(.el-button) { min-height: 44px; }
.candidate-bar { display: flex; min-height: 70px; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 16px; padding: 12px 16px; border: 1px solid #fed7aa; border-left: 3px solid var(--brand); border-radius: 8px; background: #fffaf5; }.candidate-bar > div { display: flex; align-items: baseline; gap: 9px; }.candidate-bar span { color: var(--brand-strong); font-weight: 800; }.candidate-bar strong { font: 900 18px/1 var(--font-mono); }.candidate-bar small { color: var(--text-muted); }.candidate-bar :deep(.el-button) { min-height: 44px; }
.issue-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; }.issue-card { display: flex; min-height: 244px; flex-direction: column; padding: 18px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; transition: border-color .18s,box-shadow .18s; }.issue-card:hover { border-color: #93c5fd; box-shadow: var(--shadow-card); }.issue-topline,.issue-signals,.issue-footer { display: flex; align-items: center; }.issue-topline,.issue-footer { justify-content: space-between; gap: 12px; }.repository-name { overflow: hidden; color: #1d4ed8; font: 800 11px/1.3 var(--font-mono); text-overflow: ellipsis; white-space: nowrap; }.issue-card h4 { margin: 15px 0 8px; font: 800 16px/1.5 var(--font-mono); letter-spacing: 0; }.issue-card > p { display: -webkit-box; overflow: hidden; margin: 0; color: var(--text-secondary); font-size: 13px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.issue-signals { flex-wrap: wrap; gap: 7px; margin-top: 15px; color: var(--text-muted); font-size: 11px; }.state-pill,.difficulty-pill { padding: 4px 8px; border: 1px solid #dcdfe6; border-radius: 999px; }.state-pill.try { color: #166534; border-color: #bbf7d0; background: #f0fdf4; }.state-pill.claimed { color: #92400e; border-color: #fde68a; background: #fffbeb; }.state-pill.avoid { color: #991b1b; border-color: #fecaca; background: #fef2f2; }.difficulty-pill { color: #475569; background: #f8fafc; }.issue-footer { margin-top: auto; padding-top: 15px; border-top: 1px solid var(--border-weak); color: var(--text-muted); font-size: 11px; }.issue-footer button { display: inline-flex; min-height: 44px; align-items: center; gap: 5px; padding: 0 4px; border: 0; background: transparent; color: #1d4ed8; font-weight: 800; cursor: pointer; }.issue-footer button svg { width: 14px; }.issue-footer button:focus-visible { outline: 2px solid #2563eb; }
.pagination-row { display: flex; justify-content: center; margin: 24px 0; }
.match-workspace,.job-workspace { display: grid; grid-template-columns: minmax(310px,.78fr) minmax(0,1.22fr); gap: 26px; padding-top: 20px; }.match-form { align-self: start; padding: 20px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; }.form-number,.results-heading span,.column-heading > span { color: #1d4ed8; font: 900 11px/1.2 var(--font-mono); }.match-form h4,.results-heading h4,.column-heading h4 { margin: 7px 0; font: 900 17px/1.4 var(--font-mono); }.match-form > p,.column-heading p { margin: 0 0 16px; color: var(--text-secondary); font-size: 12px; line-height: 1.6; }.match-form > label { margin-top: 18px; }.match-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 14px 0; }.match-form > :deep(.el-button) { width: 100%; min-height: 46px; margin-top: 4px; }
.match-results { min-width: 0; }.results-heading { display: flex; min-height: 58px; align-items: flex-start; justify-content: space-between; }.results-heading > div { display: flex; align-items: baseline; gap: 9px; }.results-heading small { color: var(--text-muted); }.match-result { display: grid; grid-template-columns: 34px minmax(0,1fr) 52px; gap: 13px; padding: 17px 0; border-bottom: 1px solid var(--border-weak); }.match-result.recommended { margin: 0 -14px; padding: 17px 14px; border: 1px solid #fed7aa; border-radius: 8px; background: #fffaf5; }.result-rank { color: var(--text-muted); font: 900 13px/1.3 var(--font-mono); }.result-label { color: var(--brand-strong); font-size: 11px; font-weight: 800; }.match-result h4 { margin: 5px 0; font: 800 15px/1.5 var(--font-mono); }.match-result p { margin: 0 0 6px; color: var(--text-secondary); font-size: 13px; line-height: 1.65; }.match-result small { color: #92400e; line-height: 1.5; }.match-result > button { display: inline-flex; min-height: 44px; align-items: center; justify-content: flex-end; gap: 4px; border: 0; background: transparent; color: #1d4ed8; font-weight: 800; cursor: pointer; }.match-result > button svg { width: 14px; }
.column-heading { display: flex; gap: 12px; padding-bottom: 14px; border-bottom: 1px solid var(--border-weak); }.column-heading h4 { margin-top: 0; }.job-targets > button { display: grid; width: 100%; min-height: 76px; grid-template-columns: minmax(0,1fr) 16px; gap: 5px 10px; align-items: center; padding: 13px 10px; border: 0; border-bottom: 1px solid var(--border-weak); background: #fff; color: var(--text-primary); text-align: left; cursor: pointer; }.job-targets > button span { font: 800 13px/1.45 var(--font-mono); }.job-targets > button small { color: var(--text-muted); }.job-targets > button svg { grid-row: 1 / 3; grid-column: 2; width: 15px; color: var(--text-muted); }.job-targets > button:hover,.job-targets > button:focus-visible,.job-targets > button.active { background: #eff6ff; outline: none; }.job-targets > button.active span { color: #1d4ed8; }.job-analysis { min-height: 320px; }.analysis-ready { padding: 28px 0; }.analysis-ready > span { color: #1d4ed8; font: 900 11px/1.2 var(--font-mono); }.analysis-ready h4 { margin: 8px 0; font: 900 18px/1.45 var(--font-mono); }.analysis-ready p { margin: 0 0 17px; color: var(--text-secondary); font-size: 13px; line-height: 1.7; }.analysis-ready :deep(.el-button) { min-height: 44px; }
.selected-issue { margin: 18px 0 4px; color: var(--text-muted); font-size: 12px; }.job-results article { padding: 16px 0; border-bottom: 1px solid var(--border-weak); }.job-results article strong { font: 800 15px/1.4 var(--font-mono); }.job-results p { margin: 7px 0; color: var(--text-secondary); font-size: 13px; line-height: 1.7; }.job-results small { color: #92400e; line-height: 1.6; }
.guide-workspace { display: grid; grid-template-columns: 220px minmax(0,1fr); gap: 24px; padding-top: 20px; }.guide-summary { display: flex; height: fit-content; align-items: center; gap: 12px; padding: 18px; border-left: 3px solid #2563eb; background: #eff6ff; }.guide-summary > span { color: #1d4ed8; font: 900 30px/1 var(--font-mono); }.guide-summary strong,.guide-summary small { display: block; }.guide-summary strong { font-size: 13px; }.guide-summary small { margin-top: 4px; color: var(--text-muted); font-size: 10px; }.guide-list { display: grid; gap: 1px; border: 1px solid var(--border-weak); border-radius: 8px; overflow: hidden; background: var(--border-weak); }.guide-list button { display: grid; min-height: 78px; grid-template-columns: 34px minmax(0,1fr) 18px; align-items: center; gap: 12px; padding: 14px 16px; border: 0; background: #fff; color: var(--text-primary); text-align: left; cursor: pointer; }.guide-list button:hover,.guide-list button:focus-visible { z-index: 1; background: #f8fbff; outline: 2px solid #2563eb; outline-offset: -2px; }.guide-list button > svg { width: 20px; color: #1d4ed8; }.guide-list span strong,.guide-list span small { display: block; }.guide-list span strong { font: 800 14px/1.45 var(--font-mono); }.guide-list span small { margin-top: 5px; color: var(--text-muted); font-size: 11px; }
.empty-state { display: grid; min-height: 300px; place-items: center; align-content: center; padding: 30px; text-align: center; }.empty-state.compact { min-height: 260px; }.empty-state > svg { width: 32px; height: 32px; padding: 7px; border-radius: 8px; color: #1d4ed8; background: #eff6ff; }.empty-state h4 { margin: 13px 0 5px; font: 800 16px/1.4 var(--font-mono); }.empty-state p { max-width: 430px; margin: 0 0 15px; color: var(--text-secondary); font-size: 13px; line-height: 1.65; }
.detail-heading { display: flex; justify-content: space-between; gap: 16px; }.detail-heading h2 { margin: 6px 0 0; font-size: 22px; line-height: 1.4; }.detail-heading small { color: var(--text-muted); }.detail-heading a { flex-shrink: 0; color: #1d4ed8; }.detail-summary { margin: 18px 0; padding: 14px; border-left: 3px solid #2563eb; background: #eff6ff; line-height: 1.7; }.facts { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 1px; border: 1px solid #ebeef5; background: #ebeef5; }.facts div { padding: 12px; background: #fff; }.facts dt { color: var(--text-muted); font-size: 11px; }.facts dd { margin: 5px 0 0; font-size: 13px; }.issue-detail section { margin-top: 20px; }.issue-detail section h3 { font-size: 14px; }.issue-detail pre { max-height: 320px; overflow: auto; padding: 14px; background: #f6f7f9; white-space: pre-wrap; word-break: break-word; font: 12px/1.7 var(--font-mono); }.detail-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 20px; }.detail-actions :deep(.el-button) { min-height: 44px; margin-left: 0; }.contribution-link a { color: #1d4ed8; font-weight: 700; }.drawer-results { margin-top: 20px; }
.markdown-body { color: var(--text-primary); line-height: 1.8; }.markdown-body :deep(pre) { overflow-x: auto; padding: 14px; background: #25272a; color: #f5f7fa; }.markdown-body :deep(table) { display: block; width: 100%; overflow-x: auto; border-collapse: collapse; }.markdown-body :deep(td),.markdown-body :deep(th) { padding: 8px; border: 1px solid #ddd; }
@media (max-width: 1040px) { .stage-nav { grid-template-columns: repeat(2,minmax(0,1fr)); }.filter-panel { grid-template-columns: 1fr 1fr; }.filter-panel .search-field { grid-column: 1 / -1; }.match-workspace,.job-workspace { grid-template-columns: 1fr; }.guide-workspace { grid-template-columns: 1fr; } }
@media (max-width: 700px) { .source-hero { padding: 16px 18px 20px; border-radius: 12px; }.hero-main,.stage-heading,.candidate-bar { align-items: flex-start; flex-direction: column; }.quota-strip { width: 100%; grid-template-columns: repeat(3,1fr); }.stage-nav { grid-template-columns: 1fr; }.stage-nav button { min-height: 76px; }.filter-panel,.issue-grid,.match-fields { grid-template-columns: 1fr; }.filter-panel .search-field { grid-column: auto; }.candidate-bar > div { align-items: flex-start; flex-wrap: wrap; }.issue-card { min-height: 230px; }.guide-summary { width: 100%; }.facts { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .stage-nav button,.issue-card { transition: none; } }
</style>
